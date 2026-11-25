package com.example.transferstation.data.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证思考强度选择映射到服务商请求参数的规则。 */
class ProviderApiTest {
    /** 将界面档位转换为 OpenAI 兼容请求使用的标准值。 */
    @Test
    fun `reasoning levels map to provider values`() {
        val providerApi = ProviderApi()

        assertEquals("low", providerApi.mapReasoningLevel("低"))
        assertEquals("medium", providerApi.mapReasoningLevel("中"))
        assertEquals("high", providerApi.mapReasoningLevel("高"))
    }
}
