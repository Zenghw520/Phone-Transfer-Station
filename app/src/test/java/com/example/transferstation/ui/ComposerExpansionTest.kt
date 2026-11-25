package com.example.transferstation.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证输入框全屏展开入口仅在超过三行后显示。 */
class ComposerExpansionTest {
    /** 三行及以下保持紧凑，第四行起允许打开全屏编辑器。 */
    @Test
    fun `expand icon appears after three visible lines`() {
        assertFalse(shouldShowComposerExpandIcon(1))
        assertFalse(shouldShowComposerExpandIcon(3))
        assertTrue(shouldShowComposerExpandIcon(4))
    }
}
