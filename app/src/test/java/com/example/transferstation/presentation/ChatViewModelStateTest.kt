package com.example.transferstation.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证聊天页面会话切换期间的异步消息读取保护。 */
class ChatViewModelStateTest {
    /** 只允许当前会话的最新消息读取结果更新页面。 */
    @Test
    fun `stale session message loads are ignored`() {
        assertTrue(
            shouldApplySessionMessages(
                requestedSessionId = "session-new",
                activeSessionId = "session-new",
                requestedActionGeneration = 3,
                currentActionGeneration = 3,
                requestedLoadGeneration = 8,
                currentLoadGeneration = 8,
            ),
        )
        assertFalse(
            shouldApplySessionMessages(
                requestedSessionId = "session-old",
                activeSessionId = "session-new",
                requestedActionGeneration = 2,
                currentActionGeneration = 3,
                requestedLoadGeneration = 7,
                currentLoadGeneration = 8,
            ),
        )
        assertFalse(
            shouldApplySessionMessages(
                requestedSessionId = "session-new",
                activeSessionId = "session-new",
                requestedActionGeneration = 3,
                currentActionGeneration = 3,
                requestedLoadGeneration = 7,
                currentLoadGeneration = 8,
            ),
        )
    }
}
