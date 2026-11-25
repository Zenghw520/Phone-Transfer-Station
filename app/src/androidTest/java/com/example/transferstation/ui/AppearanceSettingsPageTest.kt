package com.example.transferstation.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.transferstation.domain.model.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** 验证主题二级页面能够展示设置并即时更新外观配置。 */
@RunWith(AndroidJUnit4::class)
class AppearanceSettingsPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 页面显示主题、动态色和缩放选项，点击后通知外观配置更新。 */
    @Test
    fun themeControlsUpdateAppearanceSettings() {
        var appearance by mutableStateOf(AppearanceSettings())
        composeRule.setContent {
            MaterialTheme {
                AppearanceSettingsPage(
                    appearance = appearance,
                    onAppearanceChanged = { appearance = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("主题").assertIsDisplayed()
        composeRule.onNodeWithText("页面缩放").assertIsDisplayed()
        composeRule.onNodeWithText("深色").performClick()
        composeRule.onNodeWithText("莫奈动态取色").performClick()

        composeRule.runOnIdle {
            assertEquals("dark", appearance.themeMode)
            assertTrue(appearance.monetColorEnabled)
        }
    }
}
