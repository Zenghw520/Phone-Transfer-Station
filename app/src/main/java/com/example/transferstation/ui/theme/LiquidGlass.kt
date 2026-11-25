package com.example.transferstation.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import com.example.transferstation.domain.model.AppearanceSettings
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur

private val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** 建立柔光玻璃采样层，供聊天页中浮层和输入区域共享背景。 */
@Composable
fun LiquidGlassHost(content: @Composable () -> Unit) {
    val appearance = LocalAppearanceSettings.current
    if (!shouldUseSoftGlassBackdrop(Build.MANUFACTURER, Build.VERSION.SDK_INT, appearance)) {
        Box(Modifier.fillMaxSize()) { content() }
    } else {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
        }
    }
}

/** 在小米 RenderThread 上禁用已知会触发 RenderNode 栈溢出的玻璃采样层。 */
internal fun shouldUseSoftGlassBackdrop(
    manufacturer: String,
    sdkVersion: Int,
    appearance: AppearanceSettings,
): Boolean = (appearance.liquidGlassEnabled || appearance.inputLiquidGlassEnabled) &&
    sdkVersion >= Build.VERSION_CODES.TIRAMISU &&
    !manufacturer.contains("xiaomi", ignoreCase = true) &&
    !manufacturer.contains("redmi", ignoreCase = true)

/** 按主题开关绘制柔光玻璃表面，并在采样不可用时保留半透明材质。 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    isInput: Boolean = false,
    content: @Composable () -> Unit,
) {
    val appearance = LocalAppearanceSettings.current
    val glassEnabled = appearance.liquidGlassEnabled || (isInput && appearance.inputLiquidGlassEnabled)
    val backdrop = LocalGlassBackdrop.current
    val surfaceModifier = if (glassEnabled && backdrop != null) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(14.dp.toPx()) },
        )
    } else {
        modifier
    }
    Surface(
        modifier = surfaceModifier,
        shape = shape,
        color = if (glassEnabled) color.copy(alpha = minOf(color.alpha, 0.70f)) else color,
        border = border,
        shadowElevation = shadowElevation,
        content = content,
    )
}
