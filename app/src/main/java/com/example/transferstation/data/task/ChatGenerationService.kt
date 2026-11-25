package com.example.transferstation.data.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.transferstation.MainActivity
import com.example.transferstation.MainApplication
import com.example.transferstation.R
import com.example.transferstation.domain.model.GenerationState
import com.example.transferstation.domain.model.GenerationTaskInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 在 Android 前台服务中维持生成请求并管理状态栏及完成通知。 */
class ChatGenerationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startIdentifier = 0

    /** 初始化任务通知通道。 */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /** 将任务提升为前台执行请求，并委托给进程级聊天仓库。 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startIdentifier = startId
        val repository = (application as MainApplication).chatRepository
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val task = taskId?.let { id -> repository.snapshots.value.tasks.firstOrNull { it.id == id } }
        if (taskId == null || task == null) {
            stopIfIdle(startId)
            return START_NOT_STICKY
        }

        startForegroundCompat(buildForegroundNotification(task))
        repository.runTask(taskId) {
            serviceScope.launch {
                val latest = repository.snapshots.value.tasks.firstOrNull { it.id == taskId }
                if (latest != null && latest.state in setOf(GenerationState.COMPLETED, GenerationState.FAILED)) {
                    notifyTaskCompleted(this@ChatGenerationService, latest)
                }
                delay(SERVICE_IDLE_CHECK_MS)
                val hasActiveTasks = repository.snapshots.value.tasks.any { runningStates.contains(it.state) }
                if (!hasActiveTasks) stopIfIdle(startIdentifier)
            }
        }
        return START_NOT_STICKY
    }

    /** 超过系统 dataSync 后台时限时中断当前任务，并保留最后一次持久化输出。 */
    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        val repository = (application as MainApplication).chatRepository
        serviceScope.launch {
            repository.snapshots.value.tasks
                .filter { runningStates.contains(it.state) }
                .forEach { repository.stop(it.id) }
            stopIfIdle(startId)
        }
        super.onTimeout(startId, foregroundServiceType)
    }

    /** 前台服务没有绑定式客户端，返回 null。 */
    override fun onBind(intent: Intent?): IBinder? = null

    /** 结束当前服务后释放用于任务完成清理的协程。 */
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 从应用界面以 Android 要求的前台启动方式启动生成服务。 */
    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val CHANNEL_ACTIVE = "generation_active"
        private const val CHANNEL_COMPLETE = "generation_complete"
        private const val NOTIFICATION_ACTIVE_ID = 7300
        private const val NOTIFICATION_TASK_OFFSET = 10000
        private const val SERVICE_IDLE_CHECK_MS = 250L
        private val runningStates = setOf(GenerationState.CONNECTING, GenerationState.STREAMING)

        /** 从前台界面启动后台聊天生成服务。 */
        fun startTask(context: Context, taskId: String) {
            val intent = Intent(context, ChatGenerationService::class.java).putExtra(EXTRA_TASK_ID, taskId)
            context.startForegroundService(intent)
        }

        /** 发送任务完成或失败通知，并用会话标识提供返回入口。 */
        fun notifyTaskCompleted(context: Context, task: GenerationTaskInfo) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_COMPLETE, "对话完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "模型回答完成后提醒您返回对应会话。"
                        setShowBadge(false)
                    },
                )
            }
            val openConversation = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SESSION_ID, task.sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                context,
                task.sessionId.hashCode(),
                openConversation,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val isFailure = task.state == GenerationState.FAILED
            val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (isFailure) "${task.sessionTitle} · 请求未完成" else "${task.sessionTitle} · 对话已完成")
                .setContentText(if (isFailure) "${task.modelName} 遇到错误，打开会话查看详情。" else "${task.modelName} 已生成回答。")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            manager.notify(NOTIFICATION_TASK_OFFSET + task.id.hashCode().and(0x3fff), notification)
        }
    }

    /** 按 Android 版本指定数据同步前台服务类型和非打扰通知。 */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ACTIVE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ACTIVE_ID, notification)
        }
    }

    /** 创建前台状态和任务完成两个通知渠道。 */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ACTIVE, "回答生成中", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示仍在运行的模型回答任务。"
                    setShowBadge(false)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_COMPLETE, "对话完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "模型回答完成后提醒您返回对应会话。"
                    setShowBadge(false)
                },
            )
        }
    }

    /** 构造只显示会话和模型状态、不包含回答正文的前台通知。 */
    private fun buildForegroundNotification(task: GenerationTaskInfo): Notification =
        NotificationCompat.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在生成 · ${task.sessionTitle}")
            .setContentText("${task.providerName} · ${task.modelName}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    task.sessionId.hashCode(),
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SESSION_ID, task.sessionId)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    /** 任务全部结束后移除常驻通知并结束该次服务。 */
    private fun stopIfIdle(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }
}
