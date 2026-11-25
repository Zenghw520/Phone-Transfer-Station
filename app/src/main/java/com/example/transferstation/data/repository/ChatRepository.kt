package com.example.transferstation.data.repository

import android.content.Context
import android.content.Intent
import com.example.transferstation.data.local.ImageStore
import com.example.transferstation.data.local.LocalDataStore
import com.example.transferstation.data.provider.ProviderApi
import com.example.transferstation.domain.model.ChatMessage
import com.example.transferstation.domain.model.ChatSession
import com.example.transferstation.domain.model.GenerationState
import com.example.transferstation.domain.model.GenerationTask
import com.example.transferstation.domain.model.GenerationTaskInfo
import com.example.transferstation.domain.model.MessageRole
import com.example.transferstation.domain.model.ModelInfo
import com.example.transferstation.domain.model.PendingAttachment
import com.example.transferstation.domain.model.ProviderConnection
import com.example.transferstation.domain.model.ProviderConnectionInfo
import com.example.transferstation.domain.model.UserPreferences
import com.example.transferstation.data.task.ChatGenerationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/** 聊天页面所需的本机数据快照。 */
data class RepositorySnapshot(
    val sessions: List<ChatSession> = emptyList(),
    val connections: List<ProviderConnectionInfo> = emptyList(),
    val tasks: List<GenerationTaskInfo> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
)

/** 协调 SQLite、服务商适配、图片附件和多个聊天生成任务。 */
class ChatRepository(private val context: Context) {
    private val dataStore = LocalDataStore(context)
    private val imageStore = ImageStore(context)
    private val providerApi = ProviderApi()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSnapshot = MutableStateFlow(RepositorySnapshot())
    private val runningJobs = linkedMapOf<String, Job>()
    private val activeTaskIds = linkedSetOf<String>()
    private val snapshotLock = Any()

    /** 向界面公开持久化的会话、服务商和任务状态流。 */
    val snapshots: StateFlow<RepositorySnapshot> = mutableSnapshot.asStateFlow()

    /** 读取本机数据，在进程首次启动时将遗留的请求安全标记为中断。 */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        dataStore.markUnfinishedTasksInterrupted()
        publishSnapshot()
    }

    /** 异步刷新会话、配置、任务和偏好快照。 */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        publishSnapshot()
    }

    /** 获取单个会话的本地消息及其附件引用。 */
    suspend fun messages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        dataStore.getMessages(sessionId)
    }

    /** 获取当前保存的模型偏好。 */
    suspend fun preferences(): UserPreferences = withContext(Dispatchers.IO) {
        dataStore.getPreferences()
    }

    /** 根据本地配置标识读取连接，供 API Key 编辑时保留已有密钥。 */
    suspend fun connection(connectionId: String): ProviderConnection? = withContext(Dispatchers.IO) {
        dataStore.getConnection(connectionId)
    }

    /** 创建或更新一条服务商配置。 */
    suspend fun saveConnection(connection: ProviderConnection) = withContext(Dispatchers.IO) {
        dataStore.saveConnection(connection)
        publishSnapshot()
    }

    /** 删除一条服务商配置并保持历史消息快照。 */
    suspend fun deleteConnection(connectionId: String) = withContext(Dispatchers.IO) {
        dataStore.deleteConnection(connectionId)
        val preferences = dataStore.getPreferences()
        if (preferences.selectedProviderId == connectionId) {
            dataStore.savePreferences(preferences.copy(selectedProviderId = null, selectedModelId = null))
        }
        publishSnapshot()
    }

    /** 从服务商模型目录刷新模型列表及最新能力标记。 */
    suspend fun refreshModels(connectionId: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val connection = dataStore.getConnection(connectionId) ?: error("找不到该服务商配置。")
        val models = providerApi.fetchModels(connection)
        require(models.isNotEmpty()) { "服務商没有返回可用模型，可手动输入模型 ID。" }
        dataStore.updateConnectionModels(connectionId, models, System.currentTimeMillis())
        publishSnapshot()
        models
    }

    /** 验证连接是否可用，成功时同步刷新服务商模型目录。 */
    suspend fun validateConnection(connectionId: String): List<ModelInfo> = refreshModels(connectionId)

    /** 新建本机会话并更新全局会话列表。 */
    suspend fun createSession(): ChatSession = withContext(Dispatchers.IO) {
        dataStore.createSession().also { publishSnapshot() }
    }

    /** 更新会话标题并同步通知聊天页面。 */
    suspend fun renameSession(session: ChatSession, title: String) = withContext(Dispatchers.IO) {
        dataStore.saveSession(session.copy(title = title.trim().take(MAX_TITLE_LENGTH), updatedAt = System.currentTimeMillis()))
        publishSnapshot()
    }

    /** 删除会话及其引用的本地图片附件。 */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val removedTaskIds = dataStore.getTasks().filter { it.sessionId == sessionId }.map(GenerationTask::id)
        val jobs = synchronized(snapshotLock) { removedTaskIds.mapNotNull(runningJobs::get) }
        removedTaskIds.forEach(providerApi::cancel)
        jobs.forEach { it.cancel(CancellationException("会话已删除。")) }
        jobs.forEach { it.join() }
        val imagePaths = dataStore.getMessages(sessionId).flatMap(ChatMessage::imagePaths)
        dataStore.deleteSession(sessionId)
        imageStore.deleteImages(imagePaths)
        publishSnapshot()
        scheduleQueuedTasks()
    }

    /** 将照片选择器或拍照得到的 URI 压缩复制到应用私有目录。 */
    suspend fun importImage(uri: android.net.Uri): PendingAttachment = withContext(Dispatchers.IO) {
        PendingAttachment(imageStore.importImage(uri), "image/jpeg")
    }

    /** 丢弃用户尚未发送的本地图片附件。 */
    suspend fun discardAttachment(attachment: PendingAttachment) = withContext(Dispatchers.IO) {
        imageStore.deleteImages(listOf(attachment.localPath))
    }

    /** 保存新的用户消息及回复占位符并将请求加入三路并行生成队列。 */
    suspend fun sendMessage(
        sessionId: String,
        text: String,
        attachments: List<PendingAttachment>,
        connectionId: String,
        model: ModelInfo,
        reasoningLevel: String?,
    ): String = withContext(Dispatchers.IO) {
        val connection = dataStore.getConnection(connectionId) ?: error("请先配置模型服务商 API。")
        require(connection.apiKey.isNotBlank()) { "请在服务商设置中录入 API Key。" }
        require(text.isNotBlank() || attachments.isNotEmpty()) { "请输入消息或添加图片。" }
        require(attachments.isEmpty() || model.supportsImages || connection.customSupportsImages) { "当前模型不支持图片输入。" }

        val now = System.currentTimeMillis()
        val currentMessages = dataStore.getMessages(sessionId)
        val session = dataStore.getSessions().firstOrNull { it.id == sessionId } ?: error("会话已删除，请新建会话后重试。")
        if (currentMessages.isEmpty()) {
            val suggested = text.trim().replace(Regex("\\s+"), " ").ifBlank { "图片对话" }
            dataStore.saveSession(session.copy(title = suggested.take(MAX_TITLE_LENGTH), updatedAt = now))
        } else {
            dataStore.saveSession(session.copy(updatedAt = now))
        }

        val taskId = UUID.randomUUID().toString()
        val userMessageId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()
        dataStore.saveMessage(
            ChatMessage(
                id = userMessageId,
                sessionId = sessionId,
                role = MessageRole.USER,
                text = text,
                imagePaths = attachments.map(PendingAttachment::localPath),
                createdAt = now,
                taskId = taskId,
            ),
        )
        dataStore.saveMessage(
            ChatMessage(
                id = assistantMessageId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                text = "",
                createdAt = now + 1,
                providerName = connection.name,
                modelName = model.name,
                reasoningLevel = reasoningLevel,
                taskId = taskId,
            ),
        )
        dataStore.saveTask(
            GenerationTask(
                id = taskId,
                sessionId = sessionId,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                providerId = connection.id,
                providerKind = connection.kind,
                providerName = connection.name,
                baseUrl = connection.baseUrl,
                modelId = model.id,
                modelName = model.name,
                supportsImages = model.supportsImages || connection.customSupportsImages,
                apiKey = connection.apiKey,
                reasoningLevel = reasoningLevel,
                state = GenerationState.QUEUED,
            ),
        )
        publishSnapshot()
        scheduleQueuedTasks()
        taskId
    }

    /** 按当前服务商连接和模型重试失败或中断的用户请求。 */
    suspend fun retry(taskId: String): String = withContext(Dispatchers.IO) {
        val previous = dataStore.getTask(taskId) ?: error("找不到该生成任务。")
        require(previous.state in setOf(GenerationState.FAILED, GenerationState.STOPPED, GenerationState.INTERRUPTED)) {
            "当前任务仍在运行或已经完成。"
        }
        val connection = dataStore.getConnection(previous.providerId) ?: error("请先重新配置原服务商 API。")
        val originalUserMessage = dataStore.getMessages(previous.sessionId)
            .firstOrNull { it.id == previous.userMessageId } ?: error("找不到需要重试的原始用户消息。")
        val session = dataStore.getSessions().firstOrNull { it.id == previous.sessionId } ?: error("会话已删除，无法重试。")
        val retryId = UUID.randomUUID().toString()
        val retryAssistantId = UUID.randomUUID().toString()
        val retryModel = connection.models.firstOrNull { it.id == previous.modelId }
            ?: ModelInfo(previous.modelId, previous.modelName, supportsImages = previous.supportsImages, reasoningLevels = previous.reasoningLevel?.let(::listOf).orEmpty())
        val retryTask = GenerationTask(
            id = retryId,
            sessionId = previous.sessionId,
            userMessageId = previous.userMessageId,
            assistantMessageId = retryAssistantId,
            providerId = connection.id,
            providerKind = connection.kind,
            providerName = connection.name,
            baseUrl = connection.baseUrl,
            modelId = previous.modelId,
            modelName = previous.modelName,
            supportsImages = retryModel.supportsImages || connection.customSupportsImages,
            apiKey = connection.apiKey,
            reasoningLevel = previous.reasoningLevel,
            state = GenerationState.QUEUED,
        )
        dataStore.saveSession(session.copy(updatedAt = System.currentTimeMillis()))
        dataStore.saveMessage(
            ChatMessage(
                id = retryAssistantId,
                sessionId = session.id,
                role = MessageRole.ASSISTANT,
                text = "",
                createdAt = System.currentTimeMillis(),
                providerName = connection.name,
                modelName = previous.modelName,
                reasoningLevel = previous.reasoningLevel,
                taskId = retryId,
            ),
        )
        dataStore.saveTask(retryTask)
        publishSnapshot()
        scheduleQueuedTasks()
        retryId
    }

    /** 请求停止尚在运行的模型生成任务并保留已经收到的回答。 */
    suspend fun stop(taskId: String) = withContext(Dispatchers.IO) {
        val job = synchronized(snapshotLock) { runningJobs[taskId] }
        providerApi.cancel(taskId)
        job?.cancel(CancellationException("用户已停止此生成任务。"))
        if (job == null) {
            dataStore.getTask(taskId)?.takeIf { it.state == GenerationState.QUEUED }?.let { task ->
                persistTask(task.copy(state = GenerationState.STOPPED, completedAt = System.currentTimeMillis()))
            }
        }
        publishSnapshot()
        scheduleQueuedTasks()
    }

    /** 保存外观、选定模型和已确认服务商的本机偏好。 */
    suspend fun savePreferences(value: UserPreferences) = withContext(Dispatchers.IO) {
        dataStore.savePreferences(value)
        publishSnapshot()
    }

    /** 由前台服务运行单条流式生成任务并逐步写入可恢复回答。 */
    fun runTask(taskId: String, onFinished: () -> Unit) {
        synchronized(snapshotLock) {
            if (runningJobs.containsKey(taskId)) return
            val job = backgroundScope.launch {
                try {
                    val task = dataStore.getTask(taskId) ?: return@launch
                    if (task.state != GenerationState.QUEUED) return@launch
                    val startedAt = System.currentTimeMillis()
                    var active = task.copy(state = GenerationState.CONNECTING, startedAt = startedAt)
                    val generationContext = currentCoroutineContext()
                    persistTask(active)
                    publishSnapshot()
                    val history = requestHistory(active)
                    providerApi.streamChat(
                        task = active,
                        history = history,
                        onText = { delta ->
                            if (delta.isNotEmpty()) {
                                active = active.copy(state = GenerationState.STREAMING, assistantText = active.assistantText + delta)
                                persistTask(active)
                                publishSnapshot()
                            }
                        },
                        onReasoningSummary = { summaryDelta ->
                            if (summaryDelta.isNotEmpty()) {
                                active = active.copy(reasoningSummary = active.reasoningSummary.orEmpty() + summaryDelta)
                                persistTask(active)
                                publishSnapshot()
                            }
                        },
                        isCancelled = { !generationContext.isActive },
                    )
                    generationContext.ensureActive()
                    val completedAt = System.currentTimeMillis()
                    persistTask(active.copy(state = GenerationState.COMPLETED, completedAt = completedAt))
                    publishSnapshot()
                } catch (exception: CancellationException) {
                    dataStore.getTask(taskId)?.let { task ->
                        if (task.state in setOf(GenerationState.CONNECTING, GenerationState.STREAMING)) {
                            persistTask(task.copy(state = GenerationState.STOPPED, completedAt = System.currentTimeMillis(), errorMessage = "用户已停止生成。"))
                        }
                    }
                    throw exception
                } catch (exception: Exception) {
                    dataStore.getTask(taskId)?.let { task ->
                        val stopped = !currentCoroutineContext().isActive
                        persistTask(task.copy(
                            state = if (stopped) GenerationState.STOPPED else GenerationState.FAILED,
                            completedAt = System.currentTimeMillis(),
                            errorMessage = if (stopped) "用户已停止生成。" else exception.message?.take(MAX_ERROR_LENGTH) ?: "生成失败，请检查网络和模型配置。",
                        ))
                    }
                } finally {
                    synchronized(snapshotLock) {
                        runningJobs.remove(taskId)
                        activeTaskIds.remove(taskId)
                    }
                    withContext(NonCancellable + Dispatchers.IO) {
                        publishSnapshot()
                        scheduleQueuedTasks()
                        onFinished()
                    }
                }
            }
            runningJobs[taskId] = job
        }
    }

    /** 取消任务执行作用域并关闭本地数据库。 */
    fun close() {
        synchronized(snapshotLock) { runningJobs.keys.toList() }.forEach(providerApi::cancel)
        backgroundScope.cancel()
        dataStore.close()
    }

    /** 选取当前用户消息之前完整的历史轮次，排除失败的半截模型回答。 */
    private fun requestHistory(task: GenerationTask): List<ChatMessage> {
        val messages = dataStore.getMessages(task.sessionId)
        val currentUserIndex = messages.indexOfFirst { it.id == task.userMessageId }
        if (currentUserIndex < 0) return emptyList()
        val currentMessages = messages.take(currentUserIndex + 1)
        val assistantTaskStatuses = dataStore.getTasks().associate { it.id to it.state }
        return currentMessages.filter { message ->
            message.role == MessageRole.USER || assistantTaskStatuses[message.taskId] == GenerationState.COMPLETED
        }.map { message -> if (task.supportsImages) message else message.copy(imagePaths = emptyList()) }
    }

    /** 持久化任务状态和回答正文、摘要及耗时的一致快照。 */
    private fun persistTask(task: GenerationTask) {
        dataStore.saveTask(task)
        val userMessage = dataStore.getMessages(task.sessionId).firstOrNull { it.id == task.userMessageId }
        val elapsed = if (task.startedAt != null && task.completedAt != null) task.completedAt - task.startedAt else null
        dataStore.saveMessage(
            ChatMessage(
                id = task.assistantMessageId,
                sessionId = task.sessionId,
                role = MessageRole.ASSISTANT,
                text = task.assistantText,
                createdAt = userMessage?.createdAt?.plus(1) ?: System.currentTimeMillis(),
                providerName = task.providerName,
                modelName = task.modelName,
                reasoningLevel = task.reasoningLevel,
                reasoningSummary = task.reasoningSummary,
                requestDurationMs = elapsed,
                taskId = task.id,
            ),
        )
    }

    /** 维护最多三条跨会话并行请求并为同会话请求保留先後顺序。 */
    private fun scheduleQueuedTasks() {
        synchronized(snapshotLock) {
            val tasks = dataStore.getTasks()
            val active = tasks.filter { it.state == GenerationState.CONNECTING || it.state == GenerationState.STREAMING }
            active.forEach { activeTaskIds.add(it.id) }
            val occupiedSessions = tasks.filter { task -> task.id in activeTaskIds }.mapTo(linkedSetOf()) { it.sessionId }
            while (activeTaskIds.size < MAX_CONCURRENT_TASKS) {
                val next = tasks.firstOrNull { it.state == GenerationState.QUEUED && it.id !in activeTaskIds && it.sessionId !in occupiedSessions }
                    ?: break
                activeTaskIds.add(next.id)
                occupiedSessions.add(next.sessionId)
                try {
                    ChatGenerationService.startTask(context, next.id)
                } catch (exception: Exception) {
                    activeTaskIds.remove(next.id)
                    persistTask(next.copy(
                        state = GenerationState.FAILED,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = exception.message?.take(MAX_ERROR_LENGTH) ?: "Android 后台服务启动失败。",
                    ))
                }
            }
        }
    }

    /** 将当前本地 SQLite 状态发布给 Compose 页面。 */
    private fun publishSnapshot() {
        val sessions = dataStore.getSessions()
        mutableSnapshot.value = RepositorySnapshot(
            sessions = sessions,
            connections = dataStore.getConnections().map { connection ->
                ProviderConnectionInfo(
                    id = connection.id,
                    kind = connection.kind,
                    name = connection.name,
                    baseUrl = connection.baseUrl,
                    credentialsAvailable = connection.apiKey.isNotBlank(),
                    customSupportsImages = connection.customSupportsImages,
                    customReasoningLevels = connection.customReasoningLevels,
                    models = connection.models,
                    lastValidatedAt = connection.lastValidatedAt,
                )
            },
            tasks = dataStore.getTasks().map { task ->
                GenerationTaskInfo(
                    id = task.id,
                    sessionId = task.sessionId,
                    sessionTitle = sessions.firstOrNull { it.id == task.sessionId }?.title ?: "新对话",
                    userMessageId = task.userMessageId,
                    providerId = task.providerId,
                    providerKind = task.providerKind,
                    providerName = task.providerName,
                    modelId = task.modelId,
                    modelName = task.modelName,
                    reasoningLevel = task.reasoningLevel,
                    state = task.state,
                    assistantText = task.assistantText,
                    reasoningSummary = task.reasoningSummary,
                    startedAt = task.startedAt,
                    completedAt = task.completedAt,
                    errorMessage = task.errorMessage,
                )
            },
            preferences = dataStore.getPreferences(),
        )
    }

    private companion object {
        const val MAX_CONCURRENT_TASKS = 3
        const val MAX_TITLE_LENGTH = 56
        const val MAX_ERROR_LENGTH = 320
    }
}
