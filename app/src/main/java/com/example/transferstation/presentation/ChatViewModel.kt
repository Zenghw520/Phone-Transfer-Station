package com.example.transferstation.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.transferstation.MainApplication
import com.example.transferstation.data.repository.ChatRepository
import com.example.transferstation.data.repository.RepositorySnapshot
import com.example.transferstation.domain.model.ChatMessage
import com.example.transferstation.domain.model.ChatSession
import com.example.transferstation.domain.model.GenerationState
import com.example.transferstation.domain.model.ModelInfo
import com.example.transferstation.domain.model.ModelCapabilityCatalog
import com.example.transferstation.domain.model.PendingAttachment
import com.example.transferstation.domain.model.ProviderConnection
import com.example.transferstation.domain.model.ProviderConnectionInfo
import com.example.transferstation.domain.model.ProviderKind
import com.example.transferstation.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** 判断异步消息读取结果是否仍属于当前会话和最近一次请求。 */
internal fun shouldApplySessionMessages(
    requestedSessionId: String,
    activeSessionId: String?,
    requestedActionGeneration: Long,
    currentActionGeneration: Long,
    requestedLoadGeneration: Long,
    currentLoadGeneration: Long,
): Boolean = requestedSessionId == activeSessionId &&
    requestedActionGeneration == currentActionGeneration &&
    requestedLoadGeneration == currentLoadGeneration

/** Compose 页面可观测的聊天工作区状态。 */
data class ChatUiState(
    val snapshot: RepositorySnapshot = RepositorySnapshot(),
    val openSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val reasoningLevel: String? = null,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isLoadingMessages: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

/** 处理聊天状态、会话流程、模型选择及持久化任务交互。 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ChatRepository = (application as MainApplication).chatRepository
    private val mutableState = MutableStateFlow(ChatUiState(isBusy = true))

    /** 记录最近一次会话选择操作，阻止较早的异步切换覆盖新选择。 */
    private var sessionActionGeneration = 0L

    /** 记录最近一次消息读取，忽略已过期的会话消息快照。 */
    private var messagesLoadGeneration = 0L

    /** 将工作区页面状态以只读流的形式暴露给 Compose。 */
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

    /** 初始化本机数据并订阅服务商、会话与后台任务变化。 */
    init {
        viewModelScope.launch {
            repository.initialize()
            val preferences = repository.preferences()
            update { it.copy(
                isBusy = false,
                selectedProviderId = preferences.selectedProviderId,
                selectedModelId = preferences.selectedModelId,
            ) }
            if (mutableState.value.openSessionId == null) repository.snapshots.value.sessions.firstOrNull()?.let { openSession(it.id) }
            launch {
                var previousSessions: List<ChatSession>? = null
                repository.snapshots.collect { snapshot ->
                    val sessionsChanged = previousSessions != snapshot.sessions
                    previousSessions = snapshot.sessions
                    val current = mutableState.value
                    val retainedProviderId = current.selectedProviderId?.takeIf { id -> snapshot.connections.any { it.id == id } }
                    val providerId = retainedProviderId
                        ?: snapshot.preferences.selectedProviderId?.takeIf { id -> snapshot.connections.any { it.id == id } }
                    val modelId = when {
                        providerId == null -> null
                        providerId == current.selectedProviderId -> current.selectedModelId ?: snapshot.preferences.selectedModelId
                        else -> snapshot.preferences.selectedModelId
                    }
                    update { it.copy(
                        snapshot = snapshot,
                        isBusy = false,
                        selectedProviderId = providerId,
                        selectedModelId = modelId,
                    ) }
                    if (sessionsChanged) {
                        current.openSessionId?.let { sessionId ->
                            if (snapshot.sessions.any { it.id == sessionId }) refreshMessages(sessionId)
                        }
                    }
                }
            }
            if (repository.snapshots.value.sessions.isEmpty()) createNewSession()
        }
    }

    /** 创建空白会话并显示在主聊天页。 */
    fun createNewSession() {
        val actionGeneration = ++sessionActionGeneration
        viewModelScope.launch {
            perform {
                discardPendingAttachments()
                if (actionGeneration != sessionActionGeneration) return@perform
                val session = repository.createSession()
                if (actionGeneration != sessionActionGeneration) {
                    repository.deleteSession(session.id)
                    return@perform
                }
                messagesLoadGeneration += 1
                update {
                    it.copy(
                        openSessionId = session.id,
                        messages = emptyList(),
                        pendingAttachments = emptyList(),
                        isLoadingMessages = false,
                    )
                }
                setInfo(null)
            }
        }
    }

    /** 打开指定历史会话并恢复其中的完整消息。 */
    fun openSession(sessionId: String) {
        val actionGeneration = ++sessionActionGeneration
        viewModelScope.launch {
            perform {
                discardPendingAttachments()
                if (actionGeneration != sessionActionGeneration) return@perform
                update {
                    it.copy(
                        openSessionId = sessionId,
                        messages = emptyList(),
                        pendingAttachments = emptyList(),
                        isLoadingMessages = true,
                    )
                }
                refreshMessages(sessionId, actionGeneration)
            }
        }
    }

    /** 更新用户选择的服务商与模型并保存选择状态。 */
    fun selectModel(connectionId: String, modelId: String) {
        viewModelScope.launch {
            val connection = repository.connection(connectionId) ?: return@launch setError("找不到该服务商配置。")
            val model = connection.models.firstOrNull { it.id == modelId }
                ?: ModelInfo(modelId.trim(), modelId.trim(), connection.customSupportsImages, connection.customReasoningLevels)
            val preferences = mutableState.value.snapshot.preferences.copy(
                selectedProviderId = connectionId,
                selectedModelId = model.id,
            )
            repository.savePreferences(preferences)
            update { it.copy(selectedProviderId = connectionId, selectedModelId = model.id, reasoningLevel = it.reasoningLevel?.takeIf { level -> model.reasoningLevels.contains(level) }, errorMessage = null) }
        }
    }

    /** 将模型按其支持的能力映射为应用显示数据。 */
    fun modelFor(connection: ProviderConnectionInfo, modelId: String): ModelInfo = selectedModel(connection, modelId)

    /** 从官方模型能力规则获取用户手动输入模型的安全默认值。 */
    fun manuallyEnteredModel(connection: ProviderConnectionInfo, modelId: String): ModelInfo =
        ModelCapabilityCatalog.manual(connection.kind, modelId, connection.customSupportsImages, connection.customReasoningLevels)

    /** 更新模型原生支持的思考强度。 */
    fun selectReasoningLevel(level: String?) {
        update { it.copy(reasoningLevel = level) }
    }

    /** 更新单个聊天图片附件的待发送列表。 */
    fun setPendingAttachments(attachments: List<PendingAttachment>) {
        update { it.copy(pendingAttachments = attachments, errorMessage = null) }
    }

    /** 删除当前草稿附件文件，避免图片选择后放弃发送时残留到本机目录。 */
    fun discardAttachment(attachment: PendingAttachment) {
        viewModelScope.launch { repository.discardAttachment(attachment) }
    }

    /** 将系统照片或相机 URI 压缩到本机并在界面上显示已导入状态。 */
    fun importPhoto(uri: android.net.Uri, onComplete: (PendingAttachment) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.importImage(uri) }
                .onSuccess { attachment -> update { it.copy(errorMessage = null) }; onComplete(attachment) }
                .onFailure { exception -> setError(exception.message ?: "读取图片失败，请重新选择。") }
        }
    }

    /** 从指定服务商查询模型列表并保存。 */
    fun refreshModels(connectionId: String, onComplete: (Result<List<ModelInfo>>) -> Unit = {}) {
        viewModelScope.launch {
            update { it.copy(isBusy = true, errorMessage = null) }
            runCatching { repository.refreshModels(connectionId) }
                .onSuccess { models ->
                    update { it.copy(isBusy = false) }
                    onComplete(Result.success(models))
                }
                .onFailure { exception ->
                    update { it.copy(isBusy = false, errorMessage = exception.message ?: "模型列表获取失败。") }
                    onComplete(Result.failure(exception))
                }
        }
    }

    /** 保存、编辑或测试服务商 API 配置。 */
    fun saveConnection(
        kind: ProviderKind,
        name: String,
        baseUrl: String,
        apiKey: String,
        existingId: String? = null,
        customSupportsImages: Boolean = false,
        reasoningLevels: List<String> = emptyList(),
        refreshModels: Boolean = true,
        onComplete: (Result<String>) -> Unit = {},
    ) {
        viewModelScope.launch {
            update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                require(name.isNotBlank()) { "请输入服务商名称。" }
                require(apiKey.isNotBlank()) { "请输入 API Key。" }
                val url = baseUrl.trim().trimEnd('/')
                require(url.startsWith("https://", ignoreCase = true)) { "请输入有效的 HTTPS 服务商地址。" }
                val id = existingId ?: UUID.randomUUID().toString()
                val old = existingId?.let { repository.connection(it) }
                val effectiveKey = apiKey.takeUnless { it == "••••••••" } ?: old?.apiKey.orEmpty()
                require(effectiveKey.isNotBlank()) { "请输入 API Key。" }
                repository.saveConnection(
                    ProviderConnection(
                        id = id,
                        kind = kind,
                        name = name.trim(),
                        baseUrl = url,
                        apiKey = effectiveKey,
                        customSupportsImages = customSupportsImages,
                        customReasoningLevels = reasoningLevels,
                        models = old?.models.orEmpty(),
                        lastValidatedAt = old?.lastValidatedAt,
                    ),
                )
                if (refreshModels) runCatching { repository.validateConnection(id) }
                id
            }.onSuccess { id ->
                update { it.copy(isBusy = false, infoMessage = "服务商配置已保存。") }
                onComplete(Result.success(id))
            }.onFailure { exception ->
                update { it.copy(isBusy = false, errorMessage = exception.message ?: "服务商配置失败。") }
                onComplete(Result.failure(exception))
            }
        }
    }

    /** 加载已有 API 密钥供设置表单短暂编辑，调用方不得持久保存到 Compose 状态。 */
    fun loadConnectionSecret(connectionId: String, onComplete: (ProviderConnection?) -> Unit) {
        viewModelScope.launch { onComplete(repository.connection(connectionId)) }
    }

    /** 删除 API 配置并保留已生成的历史回答。 */
    fun deleteConnection(connectionId: String) {
        viewModelScope.launch {
            perform { repository.deleteConnection(connectionId) }
        }
    }

    /** 在会话中确认仅发送文字后提交用户请求。 */
    fun send(text: String, sessionId: String, omitImages: Boolean) {
        viewModelScope.launch {
            val current = mutableState.value
            val connectionId = current.selectedProviderId ?: return@launch setError("请先配置模型服务商 API。")
            val connection = current.snapshot.connections.firstOrNull { it.id == connectionId } ?: return@launch setError("请选择已配置的模型服务商。")
            val model = current.selectedModelId?.let { selectedModel(connection, it) } ?: return@launch setError("请先选择一个模型。")
            val level = current.reasoningLevel?.takeIf { model.reasoningLevels.contains(it) }
            perform {
                val payload = if (omitImages) emptyList() else current.pendingAttachments
                repository.sendMessage(sessionId, text.trim(), payload, connection.id, model, level)
                update { it.copy(pendingAttachments = emptyList(), infoMessage = null) }
                refreshMessages(sessionId)
            }
        }
    }

    /** 标记用户已同意向该模型服务商发送会话内容。 */
    fun confirmProviderTransfer(connectionId: String) {
        viewModelScope.launch {
            val preferences = repository.preferences().copy(
                confirmedProviderIds = repository.preferences().confirmedProviderIds + connectionId,
            )
            repository.savePreferences(preferences)
            setInfo("已记录本机的数据传输确认。")
        }
    }

    /** 更新主题模式并保存在本机。 */
    fun setTheme(themeMode: String) {
        viewModelScope.launch {
            repository.savePreferences(repository.preferences().copy(themeMode = themeMode))
        }
    }

    /** 将失败、中断或停止的模型请求作为新请求手动重试。 */
    fun retry(taskId: String) {
        viewModelScope.launch { perform { repository.retry(taskId) } }
    }

    /** 主动停止指定任务并保留已收到内容。 */
    fun stop(taskId: String) {
        viewModelScope.launch { repository.stop(taskId) }
    }

    /** 在会话抽屉中修改本地会话标题。 */
    fun rename(session: ChatSession, title: String) {
        if (title.isBlank()) return setError("会话名称不能为空。")
        viewModelScope.launch { perform { repository.renameSession(session, title) } }
    }

    /** 删除本机会话、会话消息及其附件。 */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            perform {
                if (mutableState.value.openSessionId == sessionId) discardPendingAttachments()
                repository.deleteSession(sessionId)
                if (mutableState.value.openSessionId == sessionId) {
                    update { it.copy(openSessionId = null, messages = emptyList(), pendingAttachments = emptyList()) }
                }
                if (repository.snapshots.value.sessions.isEmpty()) createNewSession()
            }
        }
    }

    /** 显示并在阅读后收起单次操作反馈。 */
    fun dismissMessages() {
        update { it.copy(errorMessage = null, infoMessage = null) }
    }

    /** 从仓库异步恢复当前会话消息，并丢弃过期会话切换的读取结果。 */
    private suspend fun refreshMessages(sessionId: String, actionGeneration: Long = sessionActionGeneration) {
        val loadGeneration = ++messagesLoadGeneration
        val messages = repository.messages(sessionId)
        if (!shouldApplySessionMessages(
                requestedSessionId = sessionId,
                activeSessionId = mutableState.value.openSessionId,
                requestedActionGeneration = actionGeneration,
                currentActionGeneration = sessionActionGeneration,
                requestedLoadGeneration = loadGeneration,
                currentLoadGeneration = messagesLoadGeneration,
            )
        ) return
        update { it.copy(messages = messages, isLoadingMessages = false) }
    }

    /** 删除当前会话输入区中尚未发送的所有图片草稿。 */
    private suspend fun discardPendingAttachments() {
        mutableState.value.pendingAttachments.forEach { repository.discardAttachment(it) }
    }

    /** 执行界面操作并统一呈现可读的业务错误。 */
    private suspend fun perform(block: suspend () -> Unit) {
        update { it.copy(errorMessage = null, infoMessage = null) }
        runCatching { block() }
            .onFailure { exception ->
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                update { it.copy(isBusy = false, isLoadingMessages = false, errorMessage = exception.message ?: "操作失败，请稍后重试。") }
            }
    }

    /** 根据提供商和模型目录推导模型图标及可见能力信息。 */
    private fun selectedModel(connection: ProviderConnectionInfo, modelId: String): ModelInfo =
        connection.models.firstOrNull { it.id == modelId }
            ?: ModelCapabilityCatalog.manual(connection.kind, modelId, connection.customSupportsImages, connection.customReasoningLevels)

    /** 在主线程更新不可变聊天状态。 */
    private inline fun update(transform: (ChatUiState) -> ChatUiState) {
        mutableState.value = transform(mutableState.value)
    }

    /** 显示应用内部反馈，避免把任何密钥内容写入日志。 */
    private fun setError(message: String) {
        update { it.copy(errorMessage = message.take(320)) }
    }

    /** 更新非错误操作提示。 */
    private fun setInfo(message: String?) {
        update { it.copy(infoMessage = message) }
    }
}
