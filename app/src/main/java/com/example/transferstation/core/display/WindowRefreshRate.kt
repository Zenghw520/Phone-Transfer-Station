package com.example.transferstation.core.display

import android.view.Display
import android.view.Window
import kotlin.math.abs

private const val PREFERRED_REFRESH_RATE = 120f

/** 屏幕在当前分辨率支持约 120 Hz 时向系统声明刷新率偏好，其余情况使用系统默认值。 */
internal fun Window.preferHighRefreshRate(display: Display?) {
    val activeMode = display?.mode
    val refreshRate = display?.supportedModes.orEmpty()
        .firstOrNull { mode ->
            mode.physicalWidth == activeMode?.physicalWidth &&
                mode.physicalHeight == activeMode.physicalHeight &&
                abs(mode.refreshRate - PREFERRED_REFRESH_RATE) < 1f
        }?.refreshRate ?: 0f
    val currentAttributes = attributes
    if (currentAttributes.preferredRefreshRate != refreshRate) {
        currentAttributes.preferredRefreshRate = refreshRate
        attributes = currentAttributes
    }
}
