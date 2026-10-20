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
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amap.api.location.AMapLocation
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.MainActivity
import com.zouliangbu.footprint.R
import com.zouliangbu.footprint.data.RecordingTrackState
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.notify.TrackAlerts
import com.zouliangbu.footprint.util.Elevation
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        /**
         * 落库缓冲的刷新间隔（毫秒，ROADMAP P2.6）。
         *
         * 取 5 秒：点本身 2 秒一个，5 秒攒 2–3 个点，fsync 次数降到 1/3；
         * 同时最坏情况下 5 秒内的点会丢（进程被 kill 时），对「1 小时徒步丢 5 秒」可接受。
         */
        private const val FLUSH_INTERVAL_MS = 5_000L

        /** 缓冲里攒到多少个点就立刻落库（不等定时器）。 */
        private const val FLUSH_MAX_BATCH = 20

        /**
         * 唤醒锁的超时（毫秒）。
         *
         * 给一个上限是防漏释放：正常路径在 stopTracking / onDestroy 里 release，
         * 但如果哪条异常路径漏了，超时能兜底把锁还回去（不至于永久耗电）。
         * 取 12 小时：一整天连续记录也覆盖得到。
         */
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60L * 60L * 1000L

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
    private var flushJob: Job? = null

    /**
     * 待落库的点缓冲（ROADMAP P2.6）。
     *
     * **所有读写都在 [flushMutex] 保护下**：采集协程负责 add，定时 flush 协程负责取出并清空，
     * 两者是不同协程、可能同时跑（Dispatchers.Default 多线程），不锁就是数据竞争
     * （ArrayList 在并发 add/clear 下会丢点甚至抛 IndexOutOfBounds）。
     */
    private val pendingPoints = ArrayList<TrackPoint>()
    private val flushMutex = Mutex()

    /**
     * 记录期间持有的唤醒锁（ROADMAP P2.8）。
     *
     * 息屏后 CPU 会进入休眠，GPS 回调被推迟，采样间隔从 2 秒拉长到几分钟。
     * `PARTIAL_WAKE_LOCK` 只阻止休眠、不阻止屏幕熄灭，是前台服务的标准做法。
     * 必须在 [onDestroy] 里释放，否则会漏电。
     */
    private var wakeLock: PowerManager.WakeLock? = null

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

        // 一次性通知 channel 建好（P5.1 / P5.2）。
        TrackAlerts.ensureChannels(this)

        // 立刻给 UI 一个 Snapshot，后续每点 + 每秒心跳都会刷新。
        publishSnapshot()
        acquireWakeLock()
        startCollecting()
        startTicker()
        startFlushing()
    }

    /**
     * 落库刷新循环（ROADMAP P2.6）。
     *
     * 每 [FLUSH_INTERVAL_MS] 把缓冲里的点一次性 insertAll。
     * 攒够 [FLUSH_MAX_BATCH] 个点会立即触发一次，不必等定时器。
     */
    private fun startFlushing() {
        flushJob?.cancel()
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                if (sessionId <= 0L) break
                flushPendingPoints()
            }
        }
    }

    /**
     * 把一个点放进缓冲。攒够 [FLUSH_MAX_BATCH] 个就立刻落库，不等定时器。
     */
    private suspend fun enqueuePoint(point: TrackPoint) {
        val needFlush = flushMutex.withLock {
            pendingPoints.add(point)
            pendingPoints.size >= FLUSH_MAX_BATCH
        }
        if (needFlush) flushPendingPoints()
    }

    /** 把缓冲区里的点批量落库。 */
    private suspend fun flushPendingPoints() {
        val batch = flushMutex.withLock {
            if (pendingPoints.isEmpty()) return@withLock emptyList()
            val copy = pendingPoints.toList()
            pendingPoints.clear()
            copy
        }
        if (batch.isEmpty()) return
        runCatching { app.repository.appendPoints(batch) }
            .onFailure { Log.e(TAG, "flush ${batch.size} point(s) failed", it) }
    }

    /**
     * 持有唤醒锁（ROADMAP P2.8）。
     *
     * `PARTIAL_WAKE_LOCK`：CPU 不休眠，但屏幕可以熄灭（用户该锁屏还是要锁屏）。
     * `setReferenceCounted(false)`：我们自己 acquire/release 配对，不依赖引用计数。
     * 拿不到锁不致命——只是息屏后采样会稀疏一点，不能因此让记录崩掉。
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(PowerManager::class.java) ?: return
            val lock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "zouliangbu:track-recording"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            wakeLock = lock
        }.onFailure { Log.w(TAG, "acquire wake lock failed", it) }
    }

    /** 释放唤醒锁。 */
    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            .onFailure { Log.w(TAG, "release wake lock failed", it) }
        wakeLock = null
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
                checkAlerts()
            }
        }
    }

    /**
     * 记录中的周期性提醒（ROADMAP P5.1 / P5.2）。
     *
     * 心跳每秒跑一次，提醒判定本身是幂等的（每会话最多一次），
     * 所以放这里既省一条线程，又能保证是「一直在记录时」的实时判定。
     */
    private fun checkAlerts() {
        val sid = sessionId
        if (sid <= 0L) return
        val now = SystemClock.elapsedRealtime()

        // P5.1：超时未动。
        val idleMs = if (lastPointElapsed > 0L) now - lastPointElapsed else 0L
        if (TrackAlerts.shouldNotifyIdle(sid, idleMs, distanceMeters)) {
            val minutes = idleMs / 60_000L
            runCatching { TrackAlerts.notifyIdle(this, sid, minutes) }
                .onFailure { Log.w(TAG, "notifyIdle failed", it) }
        }

        // P5.2：低电量。
        val percent = batteryPercent()
        if (percent >= 0 && TrackAlerts.shouldNotifyLowBattery(sid, percent)) {
            runCatching { TrackAlerts.notifyLowBattery(this, sid, percent) }
                .onFailure { Log.w(TAG, "notifyLowBattery failed", it) }
        }
    }

    /** 读当前电量百分比（0..100），拿不到返回 -1。 */
    private fun batteryPercent(): Int {
        val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?: return -1
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100) / scale
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
        // 这里只入缓冲，不直接写库（ROADMAP P2.6）：逐点 insert = 逐次事务 + 逐次 fsync。
        enqueuePoint(point)
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
        flushJob?.cancel()
        flushJob = null
        releaseWakeLock()

        val sid = sessionId
        if (sid > 0L) TrackAlerts.reset(sid)
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
                // 先把缓冲区里剩下的点冲掉，再收尾会话——顺序反了会丢最后几个点。
                flushPendingPoints()
                runCatching { app.repository.finalizeSession(sid, endTime, distance, totalPaused) }
                    .onFailure { Log.e(TAG, "finalizeSession failed session=$sid", it) }
                // 爬升/下降：停止后对整条轨迹做中值滤波 + 阈值累加（ROADMAP P3.1）。
                // 失败不影响主流程（爬升只是附加信息），只记日志。
                runCatching {
                    val points = app.repository.getPointsForSession(sid)
                    val ascent = Elevation.ascentOf(points)
                    app.repository.updateAscent(sid, ascent.ascentMeters, ascent.descentMeters)
                }.onFailure { Log.w(TAG, "updateAscent failed session=$sid", it) }
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
        flushJob?.cancel()
        // 释放唤醒锁：漏了会一直耗电（ROADMAP P2.8）。
        releaseWakeLock()
        scope.cancel()
    }
}
