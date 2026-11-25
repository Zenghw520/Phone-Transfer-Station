package com.example.transferstation.ui.theme

import com.example.transferstation.domain.model.AppearanceSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证系统玻璃采样层在不兼容设备上的安全回退。 */
class LiquidGlassCompatibilityTest {
    /** 关闭设置或运行于小米设备时不创建 SoftGlass RenderNode 层。 */
    @Test
    fun `glass backdrop is disabled for Xiaomi render thread`() {
        val enabledAppearance = AppearanceSettings(liquidGlassEnabled = true)

        assertFalse(shouldUseSoftGlassBackdrop("Xiaomi", 35, enabledAppearance))
        assertFalse(shouldUseSoftGlassBackdrop("Redmi", 35, enabledAppearance))
        assertFalse(shouldUseSoftGlassBackdrop("Google", 32, enabledAppearance))
        assertFalse(shouldUseSoftGlassBackdrop("Google", 35, AppearanceSettings()))
    }

    /** 兼容设备在 Android 13 及以上且开启玻璃时使用 SoftGlass 效果。 */
    @Test
    fun `glass backdrop remains enabled on compatible devices`() {
        assertTrue(
            shouldUseSoftGlassBackdrop(
                manufacturer = "Google",
                sdkVersion = 35,
                appearance = AppearanceSettings(inputLiquidGlassEnabled = true),
            ),
        )
    }
}
