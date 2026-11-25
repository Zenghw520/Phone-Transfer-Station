package com.example.transferstation.data.tokenization

import com.example.transferstation.domain.model.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证模型 token 计数器的精确编码选择、估算回退和速度换算。 */
class OutputTokenCounterTest {
    /** 为已知 GPT 模型选择本地编码，为未知自定义模型标记估算结果。 */
    @Test
    fun `known models use local encoding and unknown models use estimate`() {
        val exact = OutputTokenCounter.forModel(ProviderKind.OPENAI, "gpt-4o-mini").count("Hello 世界")
        val legacyExact = OutputTokenCounter.forModel(ProviderKind.OPENAI, "gpt-4-turbo").count("Hello 世界")
        val estimated = OutputTokenCounter.forModel(ProviderKind.CUSTOM, "company-model").count("Hello 世界")

        assertFalse(exact.estimated)
        assertFalse(legacyExact.estimated)
        assertTrue(exact.count > 0)
        assertTrue(legacyExact.count > 0)
        assertTrue(estimated.estimated)
        assertTrue(estimated.count > 0)
    }

    /** 计算平均输出速度并拒绝无效的零 token 或零时长输入。 */
    @Test
    fun `tokens per second uses elapsed milliseconds`() {
        assertEquals(20.0, calculateTokensPerSecond(40, 2000L)!!, 0.001)
        assertNull(calculateTokensPerSecond(0, 2000L))
        assertNull(calculateTokensPerSecond(40, 0L))
        assertNotNull(calculateTokensPerSecond(1, 1L))
    }
}
