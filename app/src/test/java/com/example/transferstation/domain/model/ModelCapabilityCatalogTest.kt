package com.example.transferstation.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证模型目录能力推断与后台任务状态的稳定边界。 */
class ModelCapabilityCatalogTest {
    /** 根据兼容接口返回的模型 ID 与显示名称识别主流模型提供商。 */
    @Test
    fun `custom model listings identify the models owning provider`() {
        assertEquals(ModelProvider.OPENAI, ModelProviderCatalog.identify(ProviderKind.CUSTOM, "gpt-5.1", null))
        assertEquals(ModelProvider.DEEPSEEK, ModelProviderCatalog.identify(ProviderKind.CUSTOM, "deepseek-reasoner", null))
        assertEquals(ModelProvider.ANTHROPIC, ModelProviderCatalog.identify(ProviderKind.CUSTOM, "org/claude-sonnet-4-5", null))
        assertEquals(ModelProvider.GOOGLE, ModelProviderCatalog.identify(ProviderKind.CUSTOM, "models/gemini-2.5-flash", null))
        assertEquals(ModelProvider.ALIBABA, ModelProviderCatalog.identify(ProviderKind.CUSTOM, "qwen3-max", null))
        assertEquals(ModelProvider.MOONSHOT, ModelProviderCatalog.identify(ProviderKind.CUSTOM, "provider-model", "Kimi-K2"))
        assertEquals(null, ModelProviderCatalog.identify(ProviderKind.CUSTOM, "company-gpt-proxy", null))
    }

    /** 识别支持图片的常见主流模型并保留模型目录名称。 */
    @Test
    fun `known image models expose vision support`() {
        assertTrue(ModelCapabilityCatalog.describe(ProviderKind.OPENAI, "gpt-4o", "GPT-4o").supportsImages)
        assertTrue(ModelCapabilityCatalog.describe(ProviderKind.ANTHROPIC, "claude-3-7-sonnet").supportsImages)
        assertTrue(ModelCapabilityCatalog.describe(ProviderKind.GEMINI, "models/gemini-2.5-flash").supportsImages)
        assertTrue(ModelCapabilityCatalog.describe(ProviderKind.DEEPSEEK, "deepseek-vl-2").supportsImages)
        assertFalse(ModelCapabilityCatalog.describe(ProviderKind.DEEPSEEK, "deepseek-chat").supportsImages)
    }

    /** 为实际支持推理强度的系列提供低中高三档设置。 */
    @Test
    fun `reasoning levels are limited to supported model families`() {
        assertEquals(listOf("低", "中", "高"), ModelCapabilityCatalog.describe(ProviderKind.OPENAI, "o3-mini").reasoningLevels)
        assertEquals(listOf("低", "中", "高"), ModelCapabilityCatalog.describe(ProviderKind.DEEPSEEK, "deepseek-reasoner").reasoningLevels)
        assertEquals(emptyList<String>(), ModelCapabilityCatalog.describe(ProviderKind.OPENAI, "gpt-4o").reasoningLevels)
    }

    /** 自定义 OpenAI 兼容接口对已知 GPT 推理型号沿用应用能力目录。 */
    @Test
    fun `custom connections recognize known GPT reasoning models`() {
        val listedModel = ModelCapabilityCatalog.describe(ProviderKind.CUSTOM, "gpt-5.1")
        val manualModel = ModelCapabilityCatalog.manual(ProviderKind.CUSTOM, "o3-mini", false, emptyList())

        assertEquals(listOf("低", "中", "高"), listedModel.reasoningLevels)
        assertEquals(listOf("低", "中", "高"), manualModel.reasoningLevels)
        assertFalse(listedModel.supportsImages)
    }

    /** 对用户声明的自定义模型能力按输入值保存，其他手动模型保持安全默认。 */
    @Test
    fun `manual model capability defaults remain conservative`() {
        val custom = ModelCapabilityCatalog.manual(ProviderKind.CUSTOM, "my-vision-model", true, listOf("中", "中", "高"))
        assertTrue(custom.supportsImages)
        assertEquals(listOf("中", "高"), custom.reasoningLevels)
        assertFalse(ModelCapabilityCatalog.manual(ProviderKind.OPENAI, "unknown-model", true, listOf("高")).supportsImages)
        assertTrue(ModelCapabilityCatalog.manual(ProviderKind.OPENAI, "unknown-model", true, listOf("高")).reasoningLevels.isEmpty())
    }

    /** 确保所有内置 API 配置具有加密传输所需的 HTTPS 起始地址。 */
    @Test
    fun `built in providers use secure default base urls`() {
        ProviderKind.entries.filterNot { it == ProviderKind.CUSTOM }.forEach { provider ->
            assertTrue(provider.displayName, provider.defaultBaseUrl().startsWith("https://"))
        }
        assertEquals("", ProviderKind.CUSTOM.defaultBaseUrl())
    }

    /** 检查用户可见的后台任务状态文字完整且与内部枚举对应。 */
    @Test
    fun `all generation states have meaningful labels`() {
        assertEquals("排队中", GenerationState.QUEUED.displayLabel())
        assertEquals("生成中", GenerationState.STREAMING.displayLabel())
        assertEquals("已完成", GenerationState.COMPLETED.displayLabel())
        assertEquals("已中断", GenerationState.INTERRUPTED.displayLabel())
        assertEquals(GenerationState.entries.size, GenerationState.entries.map(GenerationState::displayLabel).distinct().size)
    }
}
