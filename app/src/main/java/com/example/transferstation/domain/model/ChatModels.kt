package com.example.transferstation.domain.model

/** API 服务商类型，用于选择对应的鉴权、模型目录和聊天协议。 */
enum class ProviderKind(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Claude"),
    GEMINI("Gemini"),
    DEEPSEEK("DeepSeek"),
    CUSTOM("兼容接口");

    /** 返回聊天接口的默认地址，便于创建服务商配置。 */
    fun defaultBaseUrl(): String = when (this) {
        OPENAI -> "https://api.openai.com/v1"
        ANTHROPIC -> "https://api.anthropic.com/v1"
        GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
        DEEPSEEK -> "https://api.deepseek.com/v1"
        CUSTOM -> ""
    }
}

/** 单个模型在本应用中的可验证能力描述。 */
data class ModelInfo(
    val id: String,
    val name: String,
    val supportsImages: Boolean = false,
    val reasoningLevels: List<String> = emptyList(),
    val capabilitySource: String = "服务商目录",
)

/** 用户本机保存的一条模型服务商连接。API Key 字段仅在内存中保存明文。 */
data class ProviderConnection(
    val id: String,
    val kind: ProviderKind,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val customSupportsImages: Boolean = false,
    val customReasoningLevels: List<String> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val lastValidatedAt: Long? = null,
)

/** 可公开给界面的连接摘要，不包含 API Key 明文。 */
data class ProviderConnectionInfo(
    val id: String,
    val kind: ProviderKind,
    val name: String,
    val baseUrl: String,
    val credentialsAvailable: Boolean,
    val customSupportsImages: Boolean,
    val customReasoningLevels: List<String>,
    val models: List<ModelInfo>,
    val lastValidatedAt: Long?,
)

/** 会话记录及其最近更新时间。 */
data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 一条聊天消息，包括本地图片路径和回答模型快照。 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val text: String,
    val imagePaths: List<String> = emptyList(),
    val createdAt: Long,
    val providerName: String? = null,
    val modelName: String? = null,
    val reasoningLevel: String? = null,
    val reasoningSummary: String? = null,
    val requestDurationMs: Long? = null,
    val taskId: String? = null,
)

/** 消息角色，用于隔离用户、模型与任务状态。 */
enum class MessageRole {
    USER,
    ASSISTANT,
}

/** 生成任务持久化状态。 */
enum class GenerationState {
    QUEUED,
    CONNECTING,
    STREAMING,
    COMPLETED,
    FAILED,
    STOPPED,
    INTERRUPTED;

    /** 返回供会话列表和对话页展示的中文状态。 */
    fun displayLabel(): String = when (this) {
        QUEUED -> "排队中"
        CONNECTING -> "连接中"
        STREAMING -> "生成中"
        COMPLETED -> "已完成"
        FAILED -> "失败"
        STOPPED -> "已停止"
        INTERRUPTED -> "已中断"
    }
}

/** 已提交的生成任务，包含请求时使用的配置快照和部分输出。 */
data class GenerationTask(
    val id: String,
    val sessionId: String,
    val userMessageId: String,
    val assistantMessageId: String,
    val providerId: String,
    val providerKind: ProviderKind,
    val providerName: String,
    val baseUrl: String,
    val modelId: String,
    val modelName: String,
    val supportsImages: Boolean,
    val apiKey: String,
    val reasoningLevel: String?,
    val state: GenerationState,
    val assistantText: String = "",
    val reasoningSummary: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
)

/** 供聊天界面展示的任务摘要，不暴露加密前的密钥快照。 */
data class GenerationTaskInfo(
    val id: String,
    val sessionId: String,
    val sessionTitle: String,
    val userMessageId: String,
    val providerId: String,
    val providerKind: ProviderKind,
    val providerName: String,
    val modelId: String,
    val modelName: String,
    val reasoningLevel: String?,
    val state: GenerationState,
    val assistantText: String,
    val reasoningSummary: String?,
    val startedAt: Long?,
    val completedAt: Long?,
    val errorMessage: String?,
)

/** 对话界面当前选择的服务商、模型与主题偏好。 */
data class UserPreferences(
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val themeMode: String = "system",
    val confirmedProviderIds: Set<String> = emptySet(),
)

/** 用户下一条消息的附件草稿。 */
data class PendingAttachment(
    val localPath: String,
    val mimeType: String,
)
