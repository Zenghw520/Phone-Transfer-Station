package com.example.transferstation.domain.model

import java.util.Locale

/** 模型品牌及其提供商，用于界面展示。 */
enum class ModelProvider(val nameForDisplay: String, val brand: String = nameForDisplay) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic", "Claude"),
    GOOGLE("Google", "Gemini"),
    DEEPSEEK("DeepSeek"),
    ALIBABA("阿里云", "通义千问"),
    META("Meta", "Llama"),
    MISTRAL("Mistral AI", "Mistral"),
    XAI("xAI", "Grok"),
    MOONSHOT("月之暗面", "Kimi"),
    ZHIPU("智谱", "GLM"),
    MINIMAX("MiniMax"),
    BYTEDANCE("字节跳动", "豆包"),
    MICROSOFT("Microsoft", "Phi"),
    COHERE("Cohere"),
}

/** 从模型目录的 ID 和显示名称识别模型提供商，供兼容接口与历史消息复用。 */
object ModelProviderCatalog {
    private val modelFamilies = listOf(
        ModelProvider.OPENAI to Regex("""^(?:gpt(?:[-\s]|\d|$)|chatgpt(?:[-\s]|$)|o[134](?:[-.\s]|$))"""),
        ModelProvider.ANTHROPIC to Regex("""^claude(?:[-.\s]|\d|$)"""),
        ModelProvider.GOOGLE to Regex("""^(?:gemini|gemma)(?:[-.\s]|\d|$)"""),
        ModelProvider.DEEPSEEK to Regex("""^deep[-\s]?seek(?:[-.\s]|\d|$)"""),
        ModelProvider.ALIBABA to Regex("""^(?:qwen(?:[-.\s]|\d|$)|通义千问)"""),
        ModelProvider.META to Regex("""^llama(?:[-.\s]|\d|$)"""),
        ModelProvider.MISTRAL to Regex("""^(?:mistral|mixtral|codestral|devstral|magistral|pixtral)(?:[-.\s]|\d|$)"""),
        ModelProvider.XAI to Regex("""^grok(?:[-.\s]|\d|$)"""),
        ModelProvider.MOONSHOT to Regex("""^(?:kimi|moonshot)(?:[-.\s]|\d|$)"""),
        ModelProvider.ZHIPU to Regex("""^(?:glm|chatglm)(?:[-.\s]|\d|$)"""),
        ModelProvider.MINIMAX to Regex("""^minimax(?:[-.\s]|\d|$)"""),
        ModelProvider.BYTEDANCE to Regex("""^(?:doubao(?:[-.\s]|\d|$)|豆包)"""),
        ModelProvider.MICROSOFT to Regex("""^phi(?:[-.\s]|\d|$)"""),
        ModelProvider.COHERE to Regex("""^command[-\s](?:r|a)(?:[-.\s+]|\d|$)"""),
    )

    /**
     * 优先采用直连接口的已知提供商，兼容接口优先匹配模型 ID，再尝试模型名称。
     *
     * @return 识别到的模型提供商，未知型号返回空。
     */
    fun identify(kind: ProviderKind?, modelId: String?, modelName: String?): ModelProvider? =
        when (kind) {
            ProviderKind.OPENAI -> ModelProvider.OPENAI
            ProviderKind.ANTHROPIC -> ModelProvider.ANTHROPIC
            ProviderKind.GEMINI -> ModelProvider.GOOGLE
            ProviderKind.DEEPSEEK -> ModelProvider.DEEPSEEK
            ProviderKind.CUSTOM, null -> identifyFamily(modelId) ?: identifyFamily(modelName)
        }

    /** 识别带命名空间或版本后缀的模型系列，仅匹配名称开头以区分蒸馏模型的来源。 */
    private fun identifyFamily(value: String?): ModelProvider? {
        val name = value?.substringAfterLast('/')?.substringBefore(':')?.trim()
            ?.lowercase(Locale.ROOT) ?: return null
        return modelFamilies.firstOrNull { (_, family) -> family.containsMatchIn(name) }?.first
    }
}
