package com.example.transferstation.data.repository

import com.example.transferstation.domain.model.ChatMessage
import com.example.transferstation.domain.model.MessageRole

/** 整理最近已完成的文字轮次，并把图片内容降为附件标记。 */
internal fun buildConversationSummaryPrompt(messages: List<ChatMessage>): String? {
    val conversationText = messages.takeLast(SUMMARY_MESSAGE_LIMIT).mapNotNull { message ->
        val role = if (message.role == MessageRole.USER) "用户" else "助手"
        val text = message.text.replace(Regex("\\s+"), " ").trim()
        val content = listOfNotNull(
            text.takeIf(String::isNotBlank),
            "[图片]".takeIf { message.imagePaths.isNotEmpty() },
        ).joinToString(" ")
        if (content.isBlank()) null else "$role：${content.take(SUMMARY_MESSAGE_CHAR_LIMIT)}"
    }.joinToString("\n").takeLast(SUMMARY_INPUT_CHAR_LIMIT)
    if (conversationText.isBlank()) return null
    return "请用对话的主要语言，将下面最近的聊天内容整理成一句简洁预览，最多 60 个字符。只输出摘要，不要标题、引号或列表。\n\n$conversationText"
}

/** 清理模型摘要的首行并限制本地显示长度。 */
internal fun normalizeConversationSummary(value: String): String = value.lineSequence().firstOrNull().orEmpty()
    .trim().trim('"', '“', '”', '「', '」').take(SUMMARY_OUTPUT_CHAR_LIMIT)

/** 仅当摘要仍对应当前最新回答且输出非空时允许写入会话。 */
internal fun shouldApplyConversationSummary(expectedMessageId: String, currentMessageId: String?, summary: String): Boolean =
    currentMessageId == expectedMessageId && summary.isNotBlank()

private const val SUMMARY_MESSAGE_LIMIT = 24
private const val SUMMARY_MESSAGE_CHAR_LIMIT = 1800
private const val SUMMARY_INPUT_CHAR_LIMIT = 12000
internal const val SUMMARY_OUTPUT_CHAR_LIMIT = 120
