package com.example.transferstation.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** 验证通知设置子页面展示权限状态并转发页面操作。 */
@RunWith(AndroidJUnit4::class)
class NotificationSettingsPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 点击管理按钮和返回按钮时分别调用对应回调。 */
    @Test
    fun notificationActionsAreForwarded() {
        var manageClicked = false
        var backClicked = false
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsPage(
                    onBack = { backClicked = true },
                    onManageNotifications = { manageClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("通知").assertIsDisplayed()
        composeRule.onNodeWithText("通知权限").assertIsDisplayed()
        composeRule.onNodeWithText("管理 Android 通知设置").performClick()
        composeRule.onNodeWithText("‹ 返回").performClick()

        composeRule.runOnIdle {
            assertTrue(manageClicked)
            assertTrue(backClicked)
        }
    }
}
