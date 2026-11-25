package com.example.transferstation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.example.transferstation.domain.model.AppearanceSettings

/** 当前界面使用的持久化外观配置。 */
val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }

/** 套用系统深浅主题、Monet 配色、自定义强调色和全局页面缩放。 */
@Composable
fun TransferStationTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val isDarkTheme = when (appearance.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val baseColorScheme = if (appearance.monetColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDarkTheme) ChatDarkColors else ChatLightColors
    }
    val colorScheme = if (appearance.monetColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        baseColorScheme
    } else {
        baseColorScheme.withAccent(parseAccentColor(appearance.accentColorHex), isDarkTheme)
    }.let { scheme ->
        if (appearance.liquidGlassEnabled) {
            scheme.copy(
                background = scheme.background.copy(alpha = 0.90f),
                surface = scheme.surface.copy(alpha = 0.82f),
                surfaceVariant = scheme.surfaceVariant.copy(alpha = 0.74f),
            )
        } else {
            scheme
        }
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val scale = appearance.scaleFactor.coerceIn(MIN_PAGE_SCALE, MAX_PAGE_SCALE)
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !isDarkTheme
            bars.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearance,
        LocalDensity provides Density(
            density = density.density * scale,
            fontScale = density.fontScale * scale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** 解析六位十六进制强调色，遇到旧值或无效输入时沿用默认蓝色。 */
private fun parseAccentColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF315FBA))

/** 将用户强调色应用到基础浅色或深色 Material 色板。 */
private fun androidx.compose.material3.ColorScheme.withAccent(accent: Color, isDark: Boolean) = copy(
    primary = accent,
    onPrimary = if (accent.luminance() > 0.52f) Color.Black else Color.White,
    primaryContainer = accent.copy(alpha = if (isDark) 0.30f else 0.16f),
    onPrimaryContainer = if (isDark) Color.White else accent,
    secondary = accent,
)

private const val MIN_PAGE_SCALE = 0.8f
private const val MAX_PAGE_SCALE = 1.2f

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
