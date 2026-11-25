package com.example.transferstation.ui

import android.app.NotificationManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat

/** 展示应用通知权限状态，并提供进入 Android 通知设置的入口。 */
@Composable
fun NotificationSettingsPage(
    onBack: () -> Unit,
    onManageNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var promotedNotificationsEnabled by remember { mutableStateOf(canPostPromotedNotifications(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                promotedNotificationsEnabled = canPostPromotedNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text("通知", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("通知权限", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (notificationsEnabled) "已允许通知。后台模型任务完成时可以向你发送提醒。"
                        else "通知当前已关闭。模型任务仍会继续运行，状态也会保存在应用内。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("实时活动与原子岛", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            Build.VERSION.SDK_INT < 36 -> "此 Android 版本未提供标准实时活动提升接口。Vivo 原子岛是否展示由 OriginOS 版本及系统通知策略决定。"
                            promotedNotificationsEnabled -> "Android 实时通知已允许。兼容的 Vivo OriginOS 版本可将运行中的任务显示为原子岛活动。"
                            else -> "请在系统通知设置中允许 Phone TS 发布实时通知。运行通知获准后，兼容的 OriginOS 才能显示原子岛活动。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TextButton(onClick = onManageNotifications, modifier = Modifier.fillMaxWidth()) {
                Text(if (Build.VERSION.SDK_INT >= 36) "管理通知与实时活动" else "管理 Android 通知设置")
            }
        }
    }
}

/** 查询当前 Android 版本是否允许应用发布提升的持续通知。 */
private fun canPostPromotedNotifications(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= 36 &&
        context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
