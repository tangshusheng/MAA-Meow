package com.aliothmoon.maameow.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.EventNotificationLevel
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

class MaaEventNotifier(
    context: Context,
    private val appSettingsManager: AppSettingsManager,
) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_DEFAULT = "maa_events_low"
        private const val CHANNEL_HIGH = "maa_events_high"

        private const val ID_TASK_STATUS = 9001
        private val eventIdGenerator = AtomicInteger(9100)
    }

    init {
        ensureChannels()
    }

    private fun ensureChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_DEFAULT,
                "任务事件（静默）",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "任务完成、出错、公招高星等事件通知（静默，不弹出）"
                setSound(null, null)
                enableVibration(false)
            },
            NotificationChannel(
                CHANNEL_HIGH,
                "任务事件（弹出）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "任务完成、出错、公招高星等事件通知（弹出横幅）"
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun notifyAllTasksCompleted(summary: String) {
        send("全部任务完成", summary, ID_TASK_STATUS)
    }

    fun notifyTaskError(taskName: String) {
        send("任务出错", taskName, ID_TASK_STATUS)
    }

    fun notifyRecruitSpecialTag(tag: String) {
        send("招募提示", "发现稀有 Tag: $tag", eventIdGenerator.getAndIncrement())
    }

    fun notifyRecruitRobotTag(tag: String) {
        send("招募提示", "发现小车 Tag: $tag", eventIdGenerator.getAndIncrement())
    }

    fun notifyRecruitHighRarity(level: Int) {
        send("招募提示", "发现 $level ★ 组合", eventIdGenerator.getAndIncrement())
    }

    fun notifySubTaskFailure(message: String) {
        send("子任务异常", message, eventIdGenerator.getAndIncrement())
    }

    private fun send(title: String, text: String, notifyId: Int) {
        val level = appSettingsManager.eventNotificationLevel.value
        if (level == EventNotificationLevel.OFF) return

        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Timber.w("Notification is disabled or missing POST_NOTIFICATIONS permission")
            return
        }

        val channelId = if (level == EventNotificationLevel.HIGH) CHANNEL_HIGH else CHANNEL_DEFAULT
        val priority = if (level == EventNotificationLevel.HIGH) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_LOW
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notifyId, // 使用 notifyId 作为 requestCode 避免不同通知的 Intent 互相覆盖
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .apply {
                if (level == EventNotificationLevel.HIGH) {
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                }
                // 出错类通知增加红色装饰色，增强视觉提示
                if (title.contains("出错") || title.contains("异常")) {
                    color = 0xFFD32F2F.toInt()
                }
            }
            .build()

        try {
            manager.notify(notifyId, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to notify: $title")
        }
    }
}
