package com.example.transferstation.data.repository

import com.example.transferstation.domain.model.ChatMessage
import com.example.transferstation.domain.model.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证摘要输入清理、失败回退边界及并发结果版本检查。 */
class ConversationSummaryPolicyTest {
    /** 摘要提示只包含最近文字和图片标记，不包含本地图片路径。 */
    @Test
    fun `summary prompt uses text and image markers only`() {
        val prompt = buildConversationSummaryPrompt(
            listOf(
                message(MessageRole.USER, "问题\n内容", imagePath = "/private/image.jpg"),
                message(MessageRole.ASSISTANT, "回答"),
            ),
        )

        assertTrue(prompt?.contains("用户：问题 内容") == true)
        assertTrue(prompt?.contains("[图片]") == true)
        assertTrue(prompt?.contains("/private/image.jpg") == false)
    }

    /** 空摘要输出保持会话已有预览，旧回答的摘要不能覆盖最新摘要。 */
    @Test
    fun `blank or stale summary is rejected`() {
        assertNull(buildConversationSummaryPrompt(emptyList()))
        assertTrue(normalizeConversationSummary("  ").isBlank())
        assertTrue(shouldApplyConversationSummary("assistant-2", "assistant-2", "新摘要"))
        assertFalse(shouldApplyConversationSummary("assistant-1", "assistant-2", "旧摘要"))
        assertFalse(shouldApplyConversationSummary("assistant-2", "assistant-2", ""))
    }

    /** 创建摘要策略测试使用的文字或图片消息。 */
    private fun message(role: MessageRole, text: String, imagePath: String? = null) = ChatMessage(
        id = "$role-$text",
        sessionId = "session",
        role = role,
        text = text,
        imagePaths = imagePath?.let(::listOf).orEmpty(),
        createdAt = 1L,
        taskId = "completed-task",
    )
}
