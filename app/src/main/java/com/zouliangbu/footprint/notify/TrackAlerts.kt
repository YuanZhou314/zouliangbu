package com.zouliangbu.footprint.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zouliangbu.footprint.R

/**
 * 记录中的提醒（ROADMAP P5.1 / P5.2）。
 *
 * - P5.1：**超时未动提醒**——连续 30 分钟没动、累计距离 < 100 m，给一次提醒。
 * - P5.2：**低电量提醒**——记录中电量 < 15%，提醒一次。
 *
 * 设计要点：
 *   1. **只提醒一次**，不重复轰炸（用内存状态记「已提醒过」）。
 *   2. **独立通知 channel**，不混在 track_recording 里，让用户能单独关。
 *   3. **提醒不打断记录**——只是通知，Service 不停。
 *   4. 低电量通过 BatteryManager 轮询获取。
 */
object TrackAlerts {

    /** 通知渠道：超时未动（P5.1）。 */
    const val CHANNEL_IDLE = "track_idle"

    /** 通知渠道：低电量（P5.2）。 */
    const val CHANNEL_BATTERY = "track_battery"

    private const val NOTIF_ID_IDLE = 0x702
    private const val NOTIF_ID_BATTERY = 0x703

    /** P5.1 阈值：连续 30 分钟没动、累计距离 < 100 m。 */
    const val IDLE_THRESHOLD_MS = 30L * 60L * 1000L
    const val IDLE_DISTANCE_M = 100.0f

    /** P5.2 阈值：电量 < 15%。 */
    const val LOW_BATTERY_PERCENT = 15

    /** 已提醒过 idle 的会话 id（-1 表示无）。 */
    private var idleNotifiedSessionId: Long = -1L

    /** 已提醒过 battery 的会话 id（-1 表示无）。 */
    private var batteryNotifiedSessionId: Long = -1L

    /**
     * 创建两个通知渠道。
     *
     * 在 Service.onCreate 里调一次。Android 8.0 以下不用 channel。
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_IDLE,
                "记录提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "长时间没动时提醒"
                enableLights(true)
                lightColor = Color.YELLOW
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BATTERY,
                "低电量提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "记录中电量低时提醒"
            }
        )
    }

    /**
     * 是否应该提醒「超时未动」。
     *
     * 三个条件全满足才提醒：
     *   - 连续 IDLE_THRESHOLD_MS 没动
     *   - 累计距离 < IDLE_DISTANCE_M
     *   - 本次会话还没提醒过
     */
    fun shouldNotifyIdle(sessionId: Long, idleDurationMs: Long, totalDistanceM: Float): Boolean {
        if (sessionId <= 0L) return false
        if (idleDurationMs < IDLE_THRESHOLD_MS) return false
        if (totalDistanceM >= IDLE_DISTANCE_M) return false
        return idleNotifiedSessionId != sessionId
    }

    /** 发「超时未动」通知。 */
    fun notifyIdle(context: Context, sessionId: Long, idleMinutes: Long) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_IDLE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("可能忘了停止记录")
            .setContentText("已经 " + idleMinutes + " 分钟没动了，记得去点一下停止。")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context, sessionId))
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_IDLE, notification)
        idleNotifiedSessionId = sessionId
    }

    /** 是否应该提醒「低电量」（一次会话最多一次）。 */
    fun shouldNotifyLowBattery(sessionId: Long, percent: Int): Boolean {
        if (sessionId <= 0L) return false
        if (percent > LOW_BATTERY_PERCENT) return false
        return batteryNotifiedSessionId != sessionId
    }

    /** 发「低电量」通知。 */
    fun notifyLowBattery(context: Context, sessionId: Long, percent: Int) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_BATTERY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("电量只剩 " + percent + "%")
            .setContentText("记录还在进行中。低电量时记录可能中断，建议尽快停止或连接充电器。")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context, sessionId))
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_BATTERY, notification)
        batteryNotifiedSessionId = sessionId
    }

    /** 点通知跳回 App。 */
    private fun launchIntent(context: Context, sessionId: Long): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                putExtra("open_session_id", sessionId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            (0x7000 + sessionId).toInt(),
            intent,
            flags
        )
    }

    /** 会话结束时清掉「已提醒」标记，下次记录重新算。 */
    fun reset(sessionId: Long) {
        if (idleNotifiedSessionId == sessionId) idleNotifiedSessionId = -1L
        if (batteryNotifiedSessionId == sessionId) batteryNotifiedSessionId = -1L
    }

    /** 清掉所有标记（进程重启时用）。 */
    fun resetAll() {
        idleNotifiedSessionId = -1L
        batteryNotifiedSessionId = -1L
    }
}
