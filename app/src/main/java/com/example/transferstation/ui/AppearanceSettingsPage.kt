package com.example.transferstation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.transferstation.domain.model.AppearanceSettings

private val AccentColorOptions = listOf(
    "#315FBA", "#6558C8", "#A34882", "#C45151", "#C4772C", "#27846D", "#397B9E", "#59636F",
)

/** 展示深浅外观、Monet 动态取色、液态玻璃和缩放选项。 */
@Composable
fun AppearanceSettingsPage(
    appearance: AppearanceSettings,
    onAppearanceChanged: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
) {
    var accentInput by remember { mutableStateOf(appearance.accentColorHex) }
    LaunchedEffect(appearance.accentColorHex) { accentInput = appearance.accentColorHex }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text("主题", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("外观模式", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                    FilterChip(
                        selected = appearance.themeMode == value,
                        onClick = { onAppearanceChanged(appearance.copy(themeMode = value)) },
                        label = { Text(label) },
                    )
                }
            }
            HorizontalDivider()
            AppearanceSwitchRow(
                title = "莫奈动态取色",
                detail = "使用 Android 壁纸生成的系统色板",
                checked = appearance.monetColorEnabled,
                onCheckedChange = { onAppearanceChanged(appearance.copy(monetColorEnabled = it)) },
            )
            if (!appearance.monetColorEnabled) {
                Text("主题色", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentColorOptions.forEach { value ->
                        val color = parseAccent(value)
                        Surface(
                            modifier = Modifier.size(38.dp).clip(CircleShape).clickable {
                                accentInput = value
                                onAppearanceChanged(appearance.copy(accentColorHex = value))
                            }.semantics { contentDescription = "主题色 $value" },
                            shape = CircleShape,
                            color = color,
                            border = if (value.equals(appearance.accentColorHex, ignoreCase = true)) {
                                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            },
                        ) {}
                    }
                }
                OutlinedTextField(
                    value = accentInput,
                    onValueChange = { input ->
                        accentInput = input.take(7)
                        val normalized = input.removePrefix("#")
                        if (normalized.matches(Regex("[0-9A-Fa-f]{6}"))) {
                            onAppearanceChanged(appearance.copy(accentColorHex = "#${normalized.uppercase()}"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义颜色（HEX）") },
                    singleLine = true,
                    supportingText = { Text("输入六位十六进制颜色，例如 #315FBA") },
                )
            }
            HorizontalDivider()
            Text("液态玻璃", style = MaterialTheme.typography.titleMedium)
            AppearanceSwitchRow(
                title = "整体界面",
                detail = "为主要页面表面启用柔光玻璃材质",
                checked = appearance.liquidGlassEnabled,
                onCheckedChange = { onAppearanceChanged(appearance.copy(liquidGlassEnabled = it)) },
            )
            AppearanceSwitchRow(
                title = "输入栏",
                detail = "单独控制聊天输入栏的玻璃材质",
                checked = appearance.inputLiquidGlassEnabled,
                onCheckedChange = { onAppearanceChanged(appearance.copy(inputLiquidGlassEnabled = it)) },
            )
            HorizontalDivider()
            Text("页面缩放", style = MaterialTheme.typography.titleMedium)
            Text("${(appearance.scaleFactor.coerceIn(0.8f, 1.2f) * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = appearance.scaleFactor.coerceIn(0.8f, 1.2f),
                onValueChange = { onAppearanceChanged(appearance.copy(scaleFactor = it)) },
                valueRange = 0.8f..1.2f,
                steps = 7,
            )
            Spacer(Modifier.size(2.dp))
        }
    }
}

/** 呈现有标题、说明和开关的设置项。 */
@Composable
private fun AppearanceSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** 将六位十六进制主题色转换为 Compose 颜色。 */
private fun parseAccent(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF315FBA))
