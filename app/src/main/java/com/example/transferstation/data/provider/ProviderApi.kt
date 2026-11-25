package com.example.transferstation.data.provider

import android.util.Base64
import com.example.transferstation.domain.model.ChatMessage
import com.example.transferstation.domain.model.GenerationTask
import com.example.transferstation.domain.model.MessageRole
import com.example.transferstation.domain.model.ModelInfo
import com.example.transferstation.domain.model.ModelCapabilityCatalog
import com.example.transferstation.domain.model.ProviderConnection
import com.example.transferstation.domain.model.ProviderKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** 通过服务商原生 HTTP API 查询模型并处理流式对话响应。 */
class ProviderApi {
    private val activeStreams = ConcurrentHashMap<String, HttpURLConnection>()

    /** 关闭指定生成任务的流式 HTTP 连接，以便主动停止取消阻塞读取。 */
    fun cancel(taskId: String) {
        activeStreams.remove(taskId)?.disconnect()
    }

    /** 查询当前服务商 API Key 可访问的模型目录，并附加已知的应用能力说明。 */
    fun fetchModels(connection: ProviderConnection): List<ModelInfo> {
        val url = when (connection.kind) {
            ProviderKind.GEMINI -> endpoint(connection, "models")
            else -> endpoint(connection, "models")
        }
        val result = requestJson(connection, url, "GET", null)
        val models = when (connection.kind) {
            ProviderKind.GEMINI -> result.optJSONArray("models").toModelList(connection.kind, "name", "displayName")
            ProviderKind.ANTHROPIC -> result.optJSONArray("data").toModelList(connection.kind, "id", "display_name")
            else -> result.optJSONArray("data").toModelList(connection.kind, "id", "id")
        }
        val declaredModels = if (connection.kind == ProviderKind.CUSTOM) models.map { model ->
            val hasKnownReasoningLevels = model.reasoningLevels.isNotEmpty()
            model.copy(
                supportsImages = connection.customSupportsImages,
                reasoningLevels = if (hasKnownReasoningLevels) model.reasoningLevels else connection.customReasoningLevels.distinct(),
                capabilitySource = if (hasKnownReasoningLevels) "本地 GPT 能力目录，图片能力由用户声明" else "用户能力声明，待实际 API 验证",
            )
        } else models
        return declaredModels.filter { model ->
            val id = model.id.lowercase(Locale.ROOT)
            connection.kind == ProviderKind.GEMINI ||
                !id.contains("embedding") && !id.contains("moderation") && !id.contains("whisper") && !id.contains("tts") &&
                !id.contains("dall-e") && !id.contains("transcribe") && !id.contains("audio") && !id.contains("realtime")
        }.distinctBy(ModelInfo::id).take(MAX_MODELS)
    }

    /** 使用提交时的服务商、模型和消息快照发送流式请求。 */
    fun streamChat(
        task: GenerationTask,
        history: List<ChatMessage>,
        onText: (String) -> Unit,
        onReasoningSummary: (String) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        if (isCancelled()) throw java.util.concurrent.CancellationException("用户已停止此生成任务。")
        val urlAndBody = createChatRequest(task, history)
        val response = openConnection(urlAndBody.first, task.providerKind, task.apiKey, "POST")
        activeStreams[task.id] = response
        if (isCancelled()) {
            activeStreams.remove(task.id, response)
            response.disconnect()
            throw java.util.concurrent.CancellationException("用户已停止此生成任务。")
        }
        response.doOutput = true
        response.setRequestProperty("Accept", "text/event-stream")
        response.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            response.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer -> writer.write(urlAndBody.second.toString()) }
            val statusCode = response.responseCode
            if (statusCode !in 200..299) throw readProviderError(response, task.providerName, statusCode)
            response.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                var eventType: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("event:")) eventType = line.substringAfter(':').trim()
                    if (line.startsWith("data:")) {
                        val payload = line.substringAfter(':').trim()
                        if (payload.isNotEmpty() && payload != "[DONE]") {
                            consumeStreamEvent(task.providerKind, payload, eventType, onText, onReasoningSummary)
                        }
                    }
                    if (line.isBlank()) eventType = null
                }
            }
        } finally {
            activeStreams.remove(task.id, response)
            response.disconnect()
        }
    }

    /** 根据服务商协议构造模型目录查询和对话请求的安全 HTTPS 连接。 */
    private fun requestJson(connection: ProviderConnection, url: URL, method: String, body: JSONObject?): JSONObject {
        val request = openConnection(url, connection.kind, connection.apiKey, method)
        try {
            if (body != null) {
                request.doOutput = true
                request.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                request.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = request.responseCode
            if (code !in 200..299) throw readProviderError(request, connection.name, code)
            val responseBody = request.inputStream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
            return try {
                JSONObject(responseBody)
            } catch (exception: org.json.JSONException) {
                val isHtml = request.contentType?.substringBefore(';')?.equals("text/html", ignoreCase = true) == true
                val details = if (connection.kind == ProviderKind.CUSTOM && isHtml) {
                    "请检查 API Base URL 是否包含版本路径，例如 /v1。"
                } else {
                    "请检查服务商地址和接口配置。"
                }
                throw IllegalStateException("${connection.name} 返回了无效的 JSON 响应。$details", exception)
            }
        } finally {
            request.disconnect()
        }
    }

    /** 创建每种服务商各自需要的流式聊天 URL 和 JSON 请求体。 */
    private fun createChatRequest(task: GenerationTask, history: List<ChatMessage>): Pair<URL, JSONObject> {
        val base = ProviderConnection(
            id = task.providerId,
            kind = task.providerKind,
            name = task.providerName,
            baseUrl = task.baseUrl,
            apiKey = task.apiKey,
        )
        return when (task.providerKind) {
            ProviderKind.GEMINI -> {
                val contents = JSONArray()
                history.forEach { message -> contents.put(geminiMessage(message)) }
                val request = JSONObject().put("contents", contents).put("generationConfig", JSONObject())
                task.reasoningLevel?.let { level ->
                    val config = JSONObject()
                    if (task.modelId.contains("gemini-2.5", ignoreCase = true)) {
                        val budget = when (level) {
                            "低" -> 1024
                            "高" -> 24576
                            else -> 8192
                        }
                        config.put("thinkingBudget", budget)
                    } else {
                            config.put("thinkingLevel", mapReasoningLevel(level).uppercase(Locale.ROOT))
                    }
                    request.getJSONObject("generationConfig").put("thinkingConfig", config)
                }
                URL(endpoint(base, "models/${encodeSegment(task.modelId)}:streamGenerateContent", "alt", "sse").toExternalForm()) to request
            }

            ProviderKind.ANTHROPIC -> {
                val messages = JSONArray()
                history.forEach { message -> messages.put(anthropicMessage(message)) }
                val request = JSONObject()
                    .put("model", task.modelId)
                    .put("max_tokens", anthropicResponseTokenLimit(task.modelId))
                    .put("stream", true)
                    .put("messages", messages)
                if (task.reasoningLevel != null) {
                    request.put("output_config", JSONObject().put("effort", mapReasoningLevel(task.reasoningLevel)))
                }
                URL(endpoint(base, "messages").toExternalForm()) to request
            }

            ProviderKind.OPENAI, ProviderKind.DEEPSEEK, ProviderKind.CUSTOM -> {
                val messages = JSONArray()
                history.forEach { message -> messages.put(openAiMessage(message)) }
                val request = JSONObject()
                    .put("model", task.modelId)
                    .put("stream", true)
                    .put("messages", messages)
                if (usesOpenAiReasoningLimit(task.modelId)) {
                    request.put("max_completion_tokens", MAX_RESPONSE_TOKENS)
                } else {
                    request.put("max_tokens", MAX_RESPONSE_TOKENS)
                }
                task.reasoningLevel?.let { request.put("reasoning_effort", mapReasoningLevel(it)) }
                URL(endpoint(base, "chat/completions").toExternalForm()) to request
            }
        }
    }

    /** 创建 OpenAI Chat Completions 历史消息并内嵌本地图片附件。 */
    private fun openAiMessage(message: ChatMessage): JSONObject {
        val role = if (message.role == MessageRole.USER) "user" else "assistant"
        if (message.role != MessageRole.USER || message.imagePaths.isEmpty()) {
            return JSONObject().put("role", role).put("content", message.text)
        }
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", message.text))
        message.imagePaths.forEach { image ->
            val imageData = Base64.encodeToString(Files.readAllBytes(Paths.get(image)), Base64.NO_WRAP)
            content.put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$imageData"),
                ),
            )
        }
        return JSONObject().put("role", role).put("content", content)
    }

    /** 创建 Gemini contents 消息并以 inlineData 附带本地图片。 */
    private fun geminiMessage(message: ChatMessage): JSONObject {
        val parts = JSONArray()
        if (message.text.isNotBlank()) parts.put(JSONObject().put("text", message.text))
        if (message.role == MessageRole.USER) message.imagePaths.forEach { image ->
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().put("mimeType", "image/jpeg").put(
                        "data",
                        Base64.encodeToString(Files.readAllBytes(Paths.get(image)), Base64.NO_WRAP),
                    ),
                ),
            )
        }
        return JSONObject().put("role", if (message.role == MessageRole.USER) "user" else "model").put("parts", parts)
    }

    /** 创建 Anthropic Messages API 历史消息并附带图片内容块。 */
    private fun anthropicMessage(message: ChatMessage): JSONObject {
        val role = if (message.role == MessageRole.USER) "user" else "assistant"
        if (message.role != MessageRole.USER || message.imagePaths.isEmpty()) {
            return JSONObject().put("role", role).put("content", message.text)
        }
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", message.text))
        message.imagePaths.forEach { image ->
            content.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", Base64.encodeToString(Files.readAllBytes(Paths.get(image)), Base64.NO_WRAP)),
                ),
            )
        }
        return JSONObject().put("role", role).put("content", content)
    }

    /** 将服务商 SSE 数据块解析为正文增量或正式定义的思考摘要事件。 */
    private fun consumeStreamEvent(
        kind: ProviderKind,
        payload: String,
        eventType: String?,
        onText: (String) -> Unit,
        onReasoningSummary: (String) -> Unit,
    ) {
        val data = runCatching { JSONObject(payload) }.getOrNull() ?: return
        when (kind) {
            ProviderKind.GEMINI -> data.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.let { parts ->
                    for (index in 0 until parts.length()) onText(parts.optJSONObject(index)?.optString("text").orEmpty())
                }

            ProviderKind.ANTHROPIC -> when (data.optString("type", eventType.orEmpty())) {
                "content_block_delta" -> data.optJSONObject("delta")?.let { delta ->
                    if (delta.optString("type") == "text_delta") onText(delta.optString("text"))
                    if (delta.optString("type") == "thinking_summary_delta") onReasoningSummary(delta.optString("thinking"))
                }

                "error" -> throw IllegalStateException(safeErrorMessage(data))
            }

            ProviderKind.OPENAI, ProviderKind.DEEPSEEK, ProviderKind.CUSTOM -> {
                val error = data.optJSONObject("error")
                if (error != null) throw IllegalStateException(safeErrorMessage(error))
                data.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.let { delta ->
                    onText(delta.optString("content").takeUnless { it == "null" }.orEmpty())
                }
                val summaryDelta = data.optJSONObject("response")
                    ?.optJSONObject("output")?.optJSONArray("content")?.optJSONObject(0)
                    ?.optString("summary_text").orEmpty()
                if (summaryDelta.isNotBlank()) onReasoningSummary(summaryDelta)
            }
        }
    }

    /** 创建不记录 API Key 到 URL 的 HTTPS 请求，并按服务商设置鉴权头。 */
    private fun openConnection(url: URL, kind: ProviderKind, apiKey: String, method: String): HttpURLConnection {
        require(url.protocol == "https") { "仅支持 HTTPS 服务商地址。" }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            when (kind) {
                ProviderKind.ANTHROPIC -> {
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", ANTHROPIC_API_VERSION)
                }

                ProviderKind.GEMINI -> setRequestProperty("x-goog-api-key", apiKey)
                ProviderKind.OPENAI, ProviderKind.DEEPSEEK, ProviderKind.CUSTOM -> setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
    }

    /** 拼接服务商 REST 地址并对查询参数和模型路径正确编码。 */
    private fun endpoint(connection: ProviderConnection, path: String, vararg query: String): URL {
        val baseUrl = connection.baseUrl.trim().trimEnd('/')
        require(baseUrl.startsWith("https://", ignoreCase = true)) { "服务商地址必须使用 HTTPS。" }
        val joinedPath = if (baseUrl.endsWith("/v1") && path.startsWith("v1/")) path.removePrefix("v1/") else path
        val queryString = if (query.isEmpty()) "" else query.asList().chunked(2)
            .joinToString("&", prefix = "?") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return URL("$baseUrl/$joinedPath$queryString")
    }

    /** 基于服务商模型目录推断有限且保守的图像输入和思考强度能力。 */
    private fun JSONArray?.toModelList(kind: ProviderKind, idKey: String, nameKey: String): List<ModelInfo> {
        val models = this ?: return emptyList()
        return buildList {
            for (index in 0 until models.length()) {
                val item = models.optJSONObject(index) ?: continue
                val rawId = item.optString(idKey).takeIf(String::isNotBlank) ?: continue
                add(ModelCapabilityCatalog.describe(kind, rawId, item.optString(nameKey), "服务商目录与应用能力目录"))
            }
        }
    }

    /** 在模型列表或错误响应中清除 API Key、控制字符和超长错误文本。 */
    private fun safeErrorMessage(error: JSONObject): String {
        val nested = error.optJSONObject("error")
        val rawMessage = nested?.optString("message") ?: error.optString("message").takeIf(String::isNotBlank)
            ?: error.optString("type").takeIf(String::isNotBlank) ?: "服务商返回了未识别错误。"
        return rawMessage.replace(Regex("(?i)(sk-[A-Za-z0-9_-]{12,}|AIza[A-Za-z0-9_-]{12,}|Bearer\\s+\\S+)"), "[已隐藏密钥]")
            .replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ")
            .take(MAX_ERROR_LENGTH)
    }

    /** 读取服务商错误响应并保留 HTTP 状态和经过清理的错误摘要。 */
    private fun readProviderError(connection: HttpURLConnection, provider: String, statusCode: Int): IllegalStateException {
        val errorBody = runCatching {
            connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
        }.getOrDefault("")
        val details = runCatching { safeErrorMessage(JSONObject(errorBody)) }.getOrDefault("服务商未提供可读取的错误说明。")
        return IllegalStateException("$provider 请求失败（HTTP $statusCode）。$details")
    }

    /** 将中文思考强度档位转换为服务商协议使用的英文参数。 */
    internal fun mapReasoningLevel(value: String): String = when (value) {
        "低" -> "low"
        "中" -> "medium"
        "高" -> "high"
        else -> value.lowercase(Locale.ROOT)
    }

    /** 对 URL 路径中的模型名称进行 UTF-8 编码。 */
    private fun encodeSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** 判断模型是否使用需要 completion token 限额的新式推理接口。 */
    private fun usesOpenAiReasoningLimit(modelId: String): Boolean {
        val normalized = modelId.lowercase(Locale.ROOT)
        return normalized.startsWith("o1") || normalized.startsWith("o3") || normalized.startsWith("o4") || normalized.startsWith("gpt-5")
    }

    /** 为旧 Claude 3 模型提供服务商支持的 4096 token 输出上限。 */
    private fun anthropicResponseTokenLimit(modelId: String): Int =
        if (modelId.contains("claude-3-opus", ignoreCase = true) || modelId.contains("claude-3-haiku", ignoreCase = true)) 4096
        else MAX_RESPONSE_TOKENS

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 90_000
        const val MAX_RESPONSE_TOKENS = 8192
        const val MAX_MODELS = 200
        const val MAX_ERROR_LENGTH = 320
        const val ANTHROPIC_API_VERSION = "2023-06-01"
    }
}
