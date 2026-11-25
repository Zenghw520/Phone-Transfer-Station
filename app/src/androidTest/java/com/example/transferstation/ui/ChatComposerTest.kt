package com.example.transferstation.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** 验证聊天输入超过三行后可打开全屏编辑回调。 */
@RunWith(AndroidJUnit4::class)
class ChatComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 三行输入不显示展开入口，第四行出现入口并响应点击。 */
    @Test
    fun expandActionAppearsAfterFourthLine() {
        var text by mutableStateOf("")
        var expanded = false
        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    text = text,
                    attachments = emptyList(),
                    selectedProvider = null,
                    selectedModel = null,
                    reasoningLevel = null,
                    canSend = false,
                    onTextChanged = { text = it },
                    onAttach = {},
                    onReasoning = {},
                    onRemoveAttachment = {},
                    onSend = {},
                    onModel = {},
                    onExpand = { expanded = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("消息输入框").performTextInput("第一行\n第二行\n第三行\n第四行")
        composeRule.onNodeWithContentDescription("全屏展开输入框").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(expanded) }
    }
}
