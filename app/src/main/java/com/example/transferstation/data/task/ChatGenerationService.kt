package com.example.transferstation.data.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.transferstation.MainActivity
import com.example.transferstation.MainApplication
import com.example.transferstation.R
import com.example.transferstation.domain.model.GenerationState
import com.example.transferstation.domain.model.GenerationTaskInfo
import com.example.transferstation.domain.model.ModelProviderCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch

/** 在 Android 前台服务中维持生成请求并管理状态栏及完成通知。 */
class ChatGenerationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startIdentifier = 0
    private var statusUpdateJob: Job? = null

    /** 初始化任务通知通道。 */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /** 更新前台服务和任务状态通知，并委托给进程级聊天仓库。 */
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
        notifyTaskStatus(task)
        statusUpdateJob?.cancel()
        statusUpdateJob = serviceScope.launch {
            while (isActive) {
                repository.snapshots.value.tasks
                    .filter { it.state in runningStates }
                    .forEach(::notifyTaskStatus)
                delay(1_000)
            }
        }
        repository.runTask(taskId) {
            serviceScope.launch {
                val latest = repository.snapshots.value.tasks.firstOrNull { it.id == taskId }
                if (latest != null && latest.state !in runningStates && latest.state != GenerationState.QUEUED) {
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
                .forEach { task ->
                    repository.stop(task.id)
                    repository.snapshots.value.tasks.firstOrNull { it.id == task.id }?.let { latest ->
                        notifyTaskCompleted(this@ChatGenerationService, latest)
                    }
                }
            stopIfIdle(startId)
        }
        super.onTimeout(startId, foregroundServiceType)
    }

    /** 前台服务没有绑定式客户端，返回 null。 */
    override fun onBind(intent: Intent?): IBinder? = null

    /** 结束当前服务后释放用于任务完成清理的协程。 */
    override fun onDestroy() {
        statusUpdateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 从应用界面以 Android 要求的前台启动方式启动生成服务。 */
    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val CHANNEL_ACTIVE = "generation_active"
        private const val CHANNEL_TASK_STATUS = "generation_island_status_v2"
        private const val TAG = "ChatGenerationService"
        private const val NOTIFICATION_ACTIVE_ID = 7300
        private const val NOTIFICATION_TASK_OFFSET = 10000
        private const val SERVICE_IDLE_CHECK_MS = 250L
        private val runningStates = setOf(GenerationState.CONNECTING, GenerationState.STREAMING)
        private val modelIconCache = ConcurrentHashMap<String, Bitmap>()

        /** 从前台界面启动后台聊天生成服务。 */
        fun startTask(context: Context, taskId: String) {
            val intent = Intent(context, ChatGenerationService::class.java).putExtra(EXTRA_TASK_ID, taskId)
            context.startForegroundService(intent)
        }

        /** 更新任务终态通知，并用会话标识提供返回入口。 */
        fun notifyTaskCompleted(context: Context, task: GenerationTaskInfo) {
            notifyTaskStatus(context, task)
        }

        /** 为单条任务构造包含模型标识、状态和系统计时器的持续通知。 */
        private fun buildTaskStatusNotification(context: Context, task: GenerationTaskInfo): Notification {
            val isRunning = task.state in runningStates
            val isFailure = task.state == GenerationState.FAILED
            val keepIslandVisible = isRunning || (
                XiaomiIslandNotification.isXiaomiDevice() && task.state == GenerationState.COMPLETED
            )
            val openConversation = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SESSION_ID, task.sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                context,
                task.id.hashCode(),
                openConversation,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val status = when {
                task.state == GenerationState.COMPLETED -> "已完成"
                isFailure -> "生成失败"
                task.state == GenerationState.QUEUED -> "排队中"
                task.state == GenerationState.STOPPED -> "已停止"
                task.state == GenerationState.INTERRUPTED -> "已中断"
                else -> "运行中"
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_TASK_STATUS)
                .setSmallIcon(modelNotificationIcon(context, task))
                .setLargeIcon(cachedModelIcon(task))
                .setContentTitle(task.modelName)
                .setContentText("$status · ${task.sessionTitle}")
                .setSubText(status)
                .setContentIntent(pendingIntent)
                .setOngoing(keepIslandVisible)
                .setAutoCancel(!keepIslandVisible)
                .setOnlyAlertOnce(true)
                .setShortCriticalText(status)
                .setRequestPromotedOngoing(isRunning)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            if (isRunning && task.startedAt != null) {
                builder.setWhen(task.startedAt).setUsesChronometer(true).setProgress(0, 0, true)
            }
            if (task.state == GenerationState.COMPLETED && task.completedAt != null) {
                builder.setWhen(task.completedAt).setShowWhen(true)
            }
            if (isFailure) builder.setContentText("生成失败 · ${task.sessionTitle}")
            val notification = builder.build()
            XiaomiIslandNotification.addIslandExtras(
                context = context,
                notification = notification,
                task = task,
                modelIcon = cachedModelIcon(task),
            )
            return notification
        }

        /** 选择与当前模型提供商匹配的状态栏和超级岛小图标。 */
        private fun modelNotificationIcon(context: Context, task: GenerationTaskInfo): IconCompat {
            val resource = when (ModelProviderCatalog.identify(task.providerKind, task.modelId, task.modelName)) {
                com.example.transferstation.domain.model.ModelProvider.OPENAI -> R.drawable.ic_provider_openai_light
                com.example.transferstation.domain.model.ModelProvider.ANTHROPIC -> R.drawable.ic_provider_anthropic_light
                com.example.transferstation.domain.model.ModelProvider.GOOGLE -> R.drawable.ic_provider_gemini_light
                com.example.transferstation.domain.model.ModelProvider.DEEPSEEK -> R.drawable.ic_provider_deepseek_light
                else -> R.drawable.ic_notification
            }
            return IconCompat.createWithResource(context, resource)
        }

        /** 按任务标识创建独立通知编号，支持多个会话同时更新状态。 */
        private fun taskNotificationId(taskId: String): Int = NOTIFICATION_TASK_OFFSET + taskId.hashCode().and(0x3fffffff)

        /** 通知权限被拒绝时保留应用内状态，避免影响生成任务。 */
        private fun notifyTaskStatus(context: Context, task: GenerationTaskInfo) {
            try {
                context.getSystemService(NotificationManager::class.java)
                    .notify(taskNotificationId(task.id), buildTaskStatusNotification(context, task))
            } catch (exception: SecurityException) {
                Log.w(TAG, "无法发布模型任务通知，生成状态仍保存在应用内。")
            }
        }

        /** 将当前模型系列图标绘制为通知可展示的圆形缩略图。 */
        private fun buildModelIcon(task: GenerationTaskInfo): Bitmap {
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val hue = Math.floorMod(task.modelId.hashCode(), 360).toFloat()
                color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.48f, 0.72f))
            }
            canvas.drawCircle(48f, 48f, 48f, backgroundPaint)
            val brand = ModelProviderCatalog.identify(task.providerKind, task.modelId, task.modelName)?.brand.orEmpty()
            val label = brand.firstOrNull()?.uppercaseChar()?.toString() ?: "AI"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = if (label.length == 1) 46f else 30f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            val baseline = 48f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, 48f, baseline, textPaint)
            return bitmap
        }

        /** 缓存模型图标位图，避免任务计时通知每秒重复绘制。 */
        private fun cachedModelIcon(task: GenerationTaskInfo): Bitmap {
            val key = "${task.providerKind.name}:${task.modelId}:${task.modelName}"
            return modelIconCache.computeIfAbsent(key) { buildModelIcon(task) }
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

    /** 创建前台服务和任务状态两个通知渠道。 */
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
                NotificationChannel(CHANNEL_TASK_STATUS, "模型生成与超级岛", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "展示模型运行计时、完成状态，并在支持的小米设备显示超级岛。"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    /** 构造只显示会话和模型状态、不包含回答正文的前台通知。 */
    private fun buildForegroundNotification(task: GenerationTaskInfo): Notification =
        NotificationCompat.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(buildTaskIcon(task))
            .setContentTitle("正在生成 · ${task.modelName}")
            .setContentText(task.sessionTitle)
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

    /** 按模型系列生成 Android 前台通知可展示的图标。 */
    private fun buildTaskIcon(task: GenerationTaskInfo): Bitmap = cachedModelIcon(task)

    /** 更新任务独立状态通知，系统可在兼容设备上显示为岛状态。 */
    private fun notifyTaskStatus(task: GenerationTaskInfo) {
        notifyTaskStatus(this, task)
    }

    /** 任务全部结束后移除常驻通知并结束该次服务。 */
    private fun stopIfIdle(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }
}
