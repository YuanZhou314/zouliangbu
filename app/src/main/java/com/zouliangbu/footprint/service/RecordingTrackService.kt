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
import com.amap.api.location.AMapLocation
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.MainActivity
import com.zouliangbu.footprint.R
import com.zouliangbu.footprint.data.RecordingTrackState
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.util.TrackClock
import com.zouliangbu.footprint.util.TrackMetrics
import com.zouliangbu.footprint.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 轨迹记录的前台 Service。
 *
 * 动作：
 *   - ACTION_START + sessionId → 前台启动 → 订阅共享定位 → 落库 TrackPoint → 每秒刷通知与浮层
 *   - ACTION_PAUSE             → 退订定位，暂停计时（时长冻结、不采点、不计距离）
 *   - ACTION_RESUME            → 累加暂停时长，重新订阅定位
 *   - ACTION_STOP              → 写入 endTime / distance / pausedMs → stopForeground → stopSelf
 *
 * 数据过滤：
 *   - accuracy <= 30 m 的点计入距离；accuracy > 30 m 的点仍落库（保持轨迹连续）但**不计距离**
 *   - 与上一个点间隔 < 1 s 的点丢弃（用 elapsedRealtime 判断，见下）
 *   - 单段增量 > 500 m 视为漂移，跳过该段距离
 *   规则集中在 [TrackMetrics]，与「崩溃恢复重算距离」「导入校验」共用同一份实现。
 *
 * 时间基准：**全部用 [SystemClock.elapsedRealtime]**（单调递增，不受用户改系统时间/NTP 校时影响）。
 * 只有落库的 `TrackPoint.timestamp` 用 `System.currentTimeMillis()`——那是给用户看的绝对时间。
 *
 * 重启策略：`START_NOT_STICKY`。系统不再替我们拉起 Service——因为重启时拿不到 sessionId，
 * 起来只能空跑，还会在 Android 14 上因"没在 5 秒内 startForeground"被判成 ANR。
 * 被杀的残留会话由 [com.zouliangbu.footprint.data.SessionRecovery] 在下次启动时收尾。
 */
class RecordingTrackService : Service() {

    companion object {
        const val ACTION_START = "com.zouliangbu.footprint.action.START"
        const val ACTION_STOP = "com.zouliangbu.footprint.action.STOP"
        const val ACTION_PAUSE = "com.zouliangbu.footprint.action.PAUSE"
        const val ACTION_RESUME = "com.zouliangbu.footprint.action.RESUME"
        const val EXTRA_SESSION_ID = "sessionId"

        /** 相邻两点的最小间隔。定位本身是 2s 一次，1s 只是防抖下限。 */
        private const val MIN_POINT_INTERVAL_MS = 1_000L

        private const val NOTIF_CHANNEL = "track_recording"
        private const val NOTIF_ID = 0x701
        private const val TAG = "TrackService"

        fun startIntent(context: Context, sessionId: Long): Intent =
            Intent(context, RecordingTrackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }

        fun stopIntent(context: Context): Intent = actionIntent(context, ACTION_STOP)
        fun pauseIntent(context: Context): Intent = actionIntent(context, ACTION_PAUSE)
        fun resumeIntent(context: Context): Intent = actionIntent(context, ACTION_RESUME)

        private fun actionIntent(context: Context, action: String): Intent =
            Intent(context, RecordingTrackService::class.java).apply { this.action = action }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: FootprintApp

    private var sessionId: Long = -1L
    private var startElapsedRealtime: Long = 0L
    private var lastPoint: TrackPoint? = null
    private var lastPointElapsed: Long = 0L
    private var distanceMeters: Float = 0f
    private var pointCount: Int = 0

    /** 已累计的暂停时长（不含当前这次）。 */
    private var pausedMs: Long = 0L

    /** 本次暂停开始的 elapsedRealtime；0 表示未暂停。 */
    private var pauseStartedAt: Long = 0L

    private var collectJob: Job? = null
    private var tickerJob: Job? = null

    private val isPaused: Boolean get() = pauseStartedAt > 0L
    private val isTracking: Boolean get() = sessionId > 0L && collectJob != null

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
                ACTION_START -> onStartAction(intent.getLongExtra(EXTRA_SESSION_ID, -1L))
                ACTION_PAUSE -> pauseTracking()
                ACTION_RESUME -> resumeTracking()
                ACTION_STOP -> stopTracking()
                null -> {
                    // START_NOT_STICKY 下正常不会走到这里（系统不再重启我们）。
                    // 万一被别的路径拉起且没有动作，立刻退出，别留一个不发通知的前台服务。
                    Log.w(TAG, "onStartCommand with null intent → stopSelf")
                    stopSelf()
                }
                else -> Log.w(TAG, "unknown action ${intent.action}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onStartCommand failed (action=${intent?.action})", t)
            // 兜底：清理前台通知 + 自杀，避免残留前台服务。
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun onStartAction(incomingId: Long) {
        when {
            incomingId <= 0L ->
                Log.w(TAG, "ACTION_START but EXTRA_SESSION_ID missing/invalid: $incomingId")

            // 重复 START（比如 Activity 重建后按钮又被点了一次）：什么都不做，
            // 否则会把 distance/pointCount 清零，用户看到数字突然归零。
            sessionId == incomingId && collectJob != null ->
                Log.i(TAG, "already tracking session=$incomingId, ignore duplicate START")

            // 另一条会话正在记录：拒绝新会话而不是静默顶掉它——
            // 顶掉会让前一条会话永远停在 endTime=null（悬挂）。
            sessionId > 0L && collectJob != null ->
                Log.w(TAG, "session=$sessionId is recording, refuse to start $incomingId")

            else -> {
                sessionId = incomingId
                startTracking()
            }
        }
    }

    private fun startTracking() {
        startForegroundCompat(buildNotification())
        startElapsedRealtime = SystemClock.elapsedRealtime()
        lastPoint = null
        lastPointElapsed = 0L
        distanceMeters = 0f
        pointCount = 0
        pausedMs = 0L
        pauseStartedAt = 0L

        // 立刻给 UI 一个 Snapshot，后续每点 + 每秒心跳都会刷新。
        publishSnapshot()
        startCollecting()
        startTicker()
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = scope.launch {
            app.locationProvider.location.collect { loc -> onLocation(loc) }
        }
    }

    /**
     * 心跳：每秒钟推一次快照/通知。
     *
     * 没有心跳时，浮层上的时长只在**有新点落库**时才走——静止站在山顶看风景，
     * 计时器会停在原地，用户以为记录卡住了。暂停时也照跳，只是 elapsed 不再增长。
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                if (sessionId <= 0L) break
                publishSnapshot()
                updateNotification()
            }
        }
    }

    private suspend fun onLocation(loc: AMapLocation) {
        // 暂停期间不应该还有点进来（collectJob 已取消 + 定位上游 5s 后停），
        // 但恢复瞬间可能有一个在路上的点，这里再挡一次。
        if (isPaused || sessionId <= 0L) return

        val nowElapsed = SystemClock.elapsedRealtime()
        val prev = lastPoint

        // 时间间隔过滤用单调时钟：原来比的是 currentTimeMillis，
        // 用户改一次系统时间（或 NTP 跳变）就会连续漏点/多点，见 ROADMAP 2.7。
        if (prev != null && (nowElapsed - lastPointElapsed) < MIN_POINT_INTERVAL_MS) return

        val point = TrackPoint(
            sessionId = sessionId,
            lat = loc.latitude,
            lng = loc.longitude,
            altitude = loc.altitude,
            // 绝对时间给用户看，用墙上时钟；间隔判断用上面的 elapsedRealtime。
            timestamp = System.currentTimeMillis(),
            accuracy = loc.accuracy
        )

        // 距离增量：精度过滤 + 漂移过滤都在 TrackMetrics 里，三处共用一份规则。
        if (prev != null) {
            TrackMetrics.countedSegment(prev, point)?.let { distanceMeters += it }
        }

        // 低质量点也落库，保持轨迹连续（精度已存进字段，后续统计可以据此降权）。
        app.repository.appendPoint(point)
        lastPoint = point
        lastPointElapsed = nowElapsed
        pointCount += 1

        publishSnapshot()
        updateNotification()
    }

    private fun pauseTracking() {
        if (sessionId <= 0L) return
        if (isPaused) return
        collectJob?.cancel()
        collectJob = null
        pauseStartedAt = SystemClock.elapsedRealtime()
        // 断开"上一点"：恢复后的第一段不计距离——暂停期间走的路本来就不该算进轨迹里程。
        // （地图上的直线连接是坐标系层面的必然结果，这个后面靠抽稀/分段处理。）
        lastPoint = null
        publishSnapshot()
        updateNotification()
    }

    private fun resumeTracking() {
        if (sessionId <= 0L) return
        if (!isPaused) return
        val now = SystemClock.elapsedRealtime()
        pausedMs += (now - pauseStartedAt).coerceAtLeast(0L)
        pauseStartedAt = 0L
        lastPoint = null
        startCollecting()
        publishSnapshot()
        updateNotification()
    }

    private fun stopTracking() {
        collectJob?.cancel()
        collectJob = null
        tickerJob?.cancel()
        tickerJob = null

        val sid = sessionId
        val endTime = System.currentTimeMillis()
        val distance = distanceMeters
        // 仍在暂停中就直接停止：这段没结束的暂停也要计进去，否则时长会多算。
        val totalPaused = pausedMs +
                if (isPaused) (SystemClock.elapsedRealtime() - pauseStartedAt).coerceAtLeast(0L) else 0L

        sessionId = -1L
        pausedMs = 0L
        pauseStartedAt = 0L
        // 清空 UI 状态：UI 只把这里当作"是否在记录"的唯一依据。
        RecordingTrackState.update(null)

        scope.launch {
            if (sid > 0L) {
                runCatching { app.repository.finalizeSession(sid, endTime, distance, totalPaused) }
                    .onFailure { Log.e(TAG, "finalizeSession failed session=$sid", it) }
            }
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun activeElapsedMs(): Long =
        TrackClock.activeElapsedMs(
            nowElapsed = SystemClock.elapsedRealtime(),
            startedElapsed = startElapsedRealtime,
            pausedMs = pausedMs,
            pausedAtElapsed = pauseStartedAt
        )

    private fun publishSnapshot() {
        val sid = sessionId
        if (sid <= 0L) return
        RecordingTrackState.update(
            RecordingTrackState.Snapshot(
                sessionId = sid,
                distanceMeters = distanceMeters,
                elapsedMs = activeElapsedMs(),
                pointCount = pointCount,
                // 地图折线只吃增量：UI 侧按 timestamp 去重后追加（重进页面时先从 DB 补齐）。
                lastPoint = lastPoint,
                paused = isPaused,
                updatedAtElapsed = SystemClock.elapsedRealtime()
            )
        )
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (isPaused) "记录已暂停" else "正在记录轨迹")
            .setContentText(
                "${TimeFmt.distance(distanceMeters)} · ${TimeFmt.elapsed(activeElapsedMs())} · " +
                        "$pointCount 点"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // 动作按钮：暂停/继续 + 停止。图标传 0——Android 7 起动作图标不再显示，
        // 给一个不相关的资源只会误导；文字标签才是用户看到的。
        if (isPaused) {
            builder.addAction(0, "继续", servicePendingIntent(ACTION_RESUME))
        } else {
            builder.addAction(0, "暂停", servicePendingIntent(ACTION_PAUSE))
        }
        builder.addAction(0, "停止", servicePendingIntent(ACTION_STOP))
        return builder.build()
    }

    private fun servicePendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            // requestCode 必须区分：相同会被当成"同一个 PendingIntent"，点暂停却触发停止。
            action.hashCode(),
            Intent(this, RecordingTrackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun updateNotification() {
        if (sessionId <= 0L) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        runCatching { mgr.notify(NOTIF_ID, buildNotification()) }
            .onFailure { Log.w(TAG, "notify failed", it) }
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
        tickerJob?.cancel()
        scope.cancel()
    }
}
