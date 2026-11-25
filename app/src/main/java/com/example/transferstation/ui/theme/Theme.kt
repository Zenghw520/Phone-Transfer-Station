package com.example.transferstation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 套用轻量、简洁的深色 AI 聊天主题。 */
private val ChatDarkColors = darkColorScheme(
    primary = Color(0xFF83AEFF),
    onPrimary = Color(0xFF081A3D),
    primaryContainer = Color(0xFF273C64),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFF9FC8E5),
    background = Color(0xFF101113),
    onBackground = Color(0xFFEEF0F4),
    surface = Color(0xFF161719),
    onSurface = Color(0xFFEEF0F4),
    surfaceVariant = Color(0xFF25272B),
    onSurfaceVariant = Color(0xFFB8BDC7),
    outline = Color(0xFF383C43),
    error = Color(0xFFFFB4AB),
)

/** 套用暖白背景和柔和蓝色重点的浅色 AI 聊天主题。 */
private val ChatLightColors = lightColorScheme(
    primary = Color(0xFF315FBA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FC),
    onPrimaryContainer = Color(0xFF132D5B),
    secondary = Color(0xFF486274),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFEEEFF2),
    onSurfaceVariant = Color(0xFF565C66),
    outline = Color(0xFFD7D9DE),
    error = Color(0xFFBA1A1A),
)

/** 根据设置主题和 Android 当前主题应用 Chat Material 配色。 */
@Composable
fun TransferStationTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !useDarkTheme
            bars.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) ChatDarkColors else ChatLightColors,
        typography = Typography,
        content = content,
    )
}
