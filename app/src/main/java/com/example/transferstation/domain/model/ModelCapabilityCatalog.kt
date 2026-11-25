package com.example.transferstation.domain.model

import java.util.Locale

/** 根据可维护的模型 ID 规则推导应用可展示的安全默认能力。 */
object ModelCapabilityCatalog {
    /** 基于服务商、模型 ID 和名称建立可追溯的模型能力描述。 */
    fun describe(
        provider: ProviderKind,
        rawId: String,
        displayName: String? = null,
        source: String = "服务商模型目录",
    ): ModelInfo {
        val id = rawId.removePrefix("models/")
        val normalized = id.lowercase(Locale.ROOT)
        val images = when {
            provider == ProviderKind.CUSTOM -> false
            provider == ProviderKind.GEMINI -> !normalized.contains("embedding") && !normalized.contains("aqa") && !normalized.contains("tts")
            provider == ProviderKind.ANTHROPIC -> normalized.startsWith("claude-3") || normalized.startsWith("claude-opus-4") || normalized.startsWith("claude-sonnet-4") || normalized.startsWith("claude-haiku-4")
            provider == ProviderKind.OPENAI -> normalized.startsWith("gpt-4o") || normalized.startsWith("gpt-4.1") || normalized.startsWith("gpt-4.5") || normalized.startsWith("gpt-5") || normalized.startsWith("o1") || normalized.startsWith("o3") || normalized.startsWith("o4")
            else -> normalized.contains("vision") || normalized.contains("-vl")
        }
        val reasoning = when {
            provider in setOf(ProviderKind.OPENAI, ProviderKind.CUSTOM) && isOpenAiReasoningModel(normalized) -> openAiReasoningLevels(normalized)
            provider == ProviderKind.DEEPSEEK && (normalized.contains("reasoner") || normalized.contains("r1")) -> listOf("低", "中", "高")
            provider == ProviderKind.GEMINI && (normalized.contains("gemini-2.5") || normalized.contains("gemini-3")) -> listOf("低", "中", "高")
            provider == ProviderKind.ANTHROPIC && (normalized.contains("claude-sonnet-4-5") || normalized.contains("claude-sonnet-4-6") || normalized.contains("claude-opus-4-5") || normalized.contains("claude-opus-4-6")) -> listOf("低", "中", "高")
            else -> emptyList()
        }
        return ModelInfo(
            id = id,
            name = displayName?.takeIf(String::isNotBlank) ?: id,
            supportsImages = images,
            reasoningLevels = reasoning,
            capabilitySource = source,
        )
    }

    /** 给用户手动填写的未知模型返回保守的能力默认值。 */
    fun manual(provider: ProviderKind, modelId: String, imageSupport: Boolean, reasoningLevels: List<String>): ModelInfo {
        if (provider != ProviderKind.CUSTOM) return describe(provider, modelId, source = "内置模型能力目录，待实际 API 验证")
        val normalizedModelId = modelId.trim()
        val knownReasoningLevels = describe(provider, normalizedModelId).reasoningLevels
        return ModelInfo(
            id = normalizedModelId,
            name = normalizedModelId,
            supportsImages = imageSupport,
            reasoningLevels = knownReasoningLevels.ifEmpty { reasoningLevels.distinct() },
            capabilitySource = if (knownReasoningLevels.isNotEmpty()) "本地 GPT 能力目录，待实际 API 验证" else "用户能力声明，待实际 API 验证",
        )
    }

    /** 判断自定义接口返回的模型 ID 是否属于已知 OpenAI 推理模型系列。 */
    private fun isOpenAiReasoningModel(normalizedModelId: String): Boolean =
        normalizedModelId.startsWith("gpt-5") || normalizedModelId.startsWith("o1") ||
            normalizedModelId.startsWith("o3") || normalizedModelId.startsWith("o4")

    /** 按已知 OpenAI 模型系列返回接口支持的 Codex 风格思考档位。 */
    private fun openAiReasoningLevels(normalizedModelId: String): List<String> = when {
        normalizedModelId.startsWith("gpt-5.1") || normalizedModelId.startsWith("gpt-5.2") ->
            listOf("minimal", "low", "medium", "high", "xhigh")
        normalizedModelId.startsWith("gpt-5") -> listOf("minimal", "low", "medium", "high")
        else -> listOf("low", "medium", "high")
    }
}
