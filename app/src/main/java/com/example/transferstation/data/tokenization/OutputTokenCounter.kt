package com.example.transferstation.data.tokenization

import com.example.transferstation.domain.model.ProviderKind
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import kotlin.math.ceil

/** 按模型编码统计流式回答 token，并为未知编码提供明确标记的估算值。 */
class OutputTokenCounter private constructor(private val encoding: Encoding?) {
    /** 返回正文 token 数及其是否来自估算算法。 */
    fun count(text: String): OutputTokenCount {
        if (text.isBlank()) return OutputTokenCount(0, encoding == null)
        return if (encoding != null) {
            OutputTokenCount(encoding.encode(text).size(), estimated = false)
        } else {
            OutputTokenCount(estimateTokens(text), estimated = true)
        }
    }

    /** 为服务商和模型 ID 选择已知的本地编码器，其他模型回退到估算算法。 */
    companion object {
        private val encodingRegistry = Encodings.newDefaultEncodingRegistry()
        private val cl100kEncoding = encodingRegistry.getEncoding(EncodingType.CL100K_BASE)
        private val o200kEncoding = encodingRegistry.getEncoding(EncodingType.O200K_BASE)

        /** 根据服务商模型目录建立对应的 token 计数器。 */
        fun forModel(provider: ProviderKind, modelId: String): OutputTokenCounter {
            val normalizedId = modelId.removePrefix("models/").lowercase()
            val encoding = when {
                provider !in setOf(ProviderKind.OPENAI, ProviderKind.CUSTOM) -> null
                normalizedId.startsWith("gpt-4o") || normalizedId.startsWith("gpt-4.1") ||
                    normalizedId.startsWith("gpt-5") || normalizedId.startsWith("o1") ||
                    normalizedId.startsWith("o3") || normalizedId.startsWith("o4") -> o200kEncoding
                normalizedId.startsWith("gpt-4") || normalizedId.startsWith("gpt-3.5") -> cl100kEncoding
                else -> null
            }
            return OutputTokenCounter(encoding)
        }

        /** 用 ASCII 词段和 Unicode 字符数估算未知模型的 token 数。 */
        private fun estimateTokens(text: String): Int {
            var tokens = 0
            var asciiRunLength = 0
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                index += Character.charCount(codePoint)
                when {
                    codePoint in 0x30..0x39 || codePoint in 0x41..0x5a || codePoint in 0x61..0x7a -> asciiRunLength++
                    Character.isWhitespace(codePoint) -> {
                        tokens += ceil(asciiRunLength / 4.0).toInt()
                        asciiRunLength = 0
                    }
                    codePoint < 128 -> {
                        tokens += ceil(asciiRunLength / 4.0).toInt() + 1
                        asciiRunLength = 0
                    }
                    else -> {
                        tokens += ceil(asciiRunLength / 4.0).toInt() + 1
                        asciiRunLength = 0
                    }
                }
            }
            return tokens + ceil(asciiRunLength / 4.0).toInt()
        }
    }
}

/** 单次 token 计数结果及其精确度标记。 */
data class OutputTokenCount(
    val count: Int,
    val estimated: Boolean,
)

/** 根据已生成 token 数和请求耗时计算平均输出速度。 */
fun calculateTokensPerSecond(tokenCount: Int, elapsedMs: Long): Double? {
    if (tokenCount <= 0 || elapsedMs <= 0L) return null
    return tokenCount * 1000.0 / elapsedMs
}
