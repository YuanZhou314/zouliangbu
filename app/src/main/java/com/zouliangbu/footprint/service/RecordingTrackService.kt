package com.zouliangbu.footprint.service

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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.MainActivity
import com.zouliangbu.footprint.R
import com.zouliangbu.footprint.data.RecordingTrackState
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.util.Distance
import com.zouliangbu.footprint.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 轨迹记录的前台 Service。
 *
 * 生命周期：
 *   - 收到 ACTION_START + sessionId → 前台启动 → 订阅 LocationProvider → 落库 TrackPoint → 更新通知
 *   - 收到 ACTION_STOP              → 结束当前会话（写入 endTime/distance）→ stopForeground → stopSelf
 *   - START_STICKY：被系统杀掉后会重启动，但没有 sessionId 会进入空跑（只发一个空通知）
 *
 * 数据过滤：
 *   - accuracy <= 30 m 的点计入距离；accuracy > 30 m 的点仍落库（保持轨迹连续）但**不计距离**
 *   - 与上一个点间隔 < 1 s 的点丢弃
 *   - 单段增量 > 500 m 视为漂移，跳过该段距离
 */
class RecordingTrackService : Service() {

    companion object {
        const val ACTION_START = "com.zouliangbu.footprint.action.START"
        const val ACTION_STOP = "com.zouliangbu.footprint.action.STOP"
        const val EXTRA_SESSION_ID = "sessionId"

        private const val NOTIF_CHANNEL = "track_recording"
        private const val NOTIF_ID = 0x701
        private const val TAG = "TrackService"

        fun startIntent(context: Context, sessionId: Long): Intent =
            Intent(context, RecordingTrackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingTrackService::class.java).apply {
                action = ACTION_STOP
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: FootprintApp

    private var sessionId: Long = -1L
    private var startElapsedRealtime: Long = 0L
    private var lastPoint: TrackPoint? = null
    private var distanceMeters: Float = 0f
    private var pointCount: Int = 0
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as FootprintApp
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // try/catch 兜底：把异常落 logcat，下次崩溃有迹可循，避免把进程直接拖死。
        try {
            when (intent?.action) {
                ACTION_START -> {
                    val incomingId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                    if (incomingId > 0L) {
                        sessionId = incomingId
                        startTracking()
                    } else {
                        Log.w(TAG, "ACTION_START but EXTRA_SESSION_ID missing/invalid: $incomingId")
                    }
                }
                ACTION_STOP -> stopTracking()
                null -> Log.w(TAG, "onStartCommand with null intent")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onStartCommand failed (action=${intent?.action})", t)
            // 兜底：清理前台通知 + 自杀，避免残留前台服务。
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
            stopSelf()
        }
        return START_STICKY
    }

    private fun startTracking() {
        startForegroundCompat(buildNotification(0L, 0f, 0))
        startElapsedRealtime = SystemClock.elapsedRealtime()
        lastPoint = null
        distanceMeters = 0f
        pointCount = 0

        // 立刻给 UI 一个 Snapshot，后续每条点更新都会刷新。
        publishSnapshot()

        collectJob = scope.launch {
            app.locationProvider.location.collect { loc ->
                // 1. 精度过滤：
                //    - accuracy <= 30 m：可信点，正常入库 + 记距离
                //    - accuracy > 30 m：低质量点（室内/隧道/遮挡），仍落库（保证轨迹连续），
                //      但**不计入距离**，避免把抖动/漂移算进去。
                //    用准确性作为距离的可信度，而不是直接丢弃导致记录"看起来卡住"。
                val accurate = loc.accuracy <= 30f

                val nowMs = System.currentTimeMillis()

                // 2. 时间间隔过滤：< 1 s 的连续点丢弃（防止静止时连续打点刷距离）
                val prev = lastPoint
                if (prev != null && (nowMs - prev.timestamp) < 1_000L) return@collect

                val newPoint = TrackPoint(
                    sessionId = sessionId,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    altitude = loc.altitude,
                    timestamp = nowMs,
                    accuracy = loc.accuracy
                )

                // 3. 距离增量（仅在有上一条点时计算）
                if (prev != null && accurate) {
                    val inc = Distance.haversineMeters(
                        prev.lat, prev.lng,
                        newPoint.lat, newPoint.lng
                    ).toFloat()
                    // 单段 > 500 m 视为 GPS 漂移，不计入距离
                    if (inc < 500f) {
                        distanceMeters += inc
                    }
                }

                // 4. 落库（低质量点也落库，保持轨迹连续；准确性已存进字段）
                app.repository.appendPoint(newPoint)
                lastPoint = newPoint
                pointCount += 1

                // 5. 推 UI + 刷通知
                publishSnapshot()
                val elapsed = SystemClock.elapsedRealtime() - startElapsedRealtime
                notify(distanceMeters, elapsed, pointCount)
            }
        }
    }

    private fun stopTracking() {
        collectJob?.cancel()
        collectJob = null
        val sid = sessionId
        val endTime = System.currentTimeMillis()
        val distance = distanceMeters
        // 清空 UI 状态
        RecordingTrackState.update(null)
        scope.launch {
            if (sid > 0L) {
                app.repository.finalizeSession(sid, endTime, distance)
            }
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun publishSnapshot() {
        val sid = sessionId
        if (sid <= 0L) return
        RecordingTrackState.update(
            RecordingTrackState.Snapshot(
                sessionId = sid,
                distanceMeters = distanceMeters,
                elapsedMs = SystemClock.elapsedRealtime() - startElapsedRealtime,
                pointCount = pointCount
            )
        )
    }

    private fun buildNotification(elapsedMs: Long, distance: Float, count: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在记录轨迹")
            .setContentText("${TimeFmt.distance(distance)} · ${TimeFmt.elapsed(elapsedMs)} · $count 点")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun notify(distance: Float, elapsedMs: Long, count: Int) {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(NOTIF_ID, buildNotification(elapsedMs, distance, count))
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(NOTIF_CHANNEL) == null) {
            val ch = NotificationChannel(
                NOTIF_CHANNEL,
                "轨迹记录",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "户外 GPS 轨迹记录的运行状态"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun startForegroundCompat(notif: Notification) {
        // 3 参数 startForeground(id, notif, foregroundServiceType) 从 API 29 (Q) 引入。
        // 在 API 34+ 上是强制的（不带 type 在 Android 14 上会抛 SecurityException）。
        // 注意：不能用 Build.VERSION_CODES.UPSIDE_DOWN_CAKE（=34），因为 compileSdk=33 看不到这个常量。
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        collectJob?.cancel()
        scope.cancel()
    }
}