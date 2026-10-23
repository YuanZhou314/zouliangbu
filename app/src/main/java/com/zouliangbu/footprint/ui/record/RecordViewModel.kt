package com.zouliangbu.footprint.ui.record

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.RecordingTrackState
import com.zouliangbu.footprint.data.SessionRecovery
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.export.GpxExporter
import com.zouliangbu.footprint.ui.map.MapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记录页（出发）ViewModel。
 *
 * - [recording]：Service 通过 RecordingTrackState 推送的实时快照（含暂停状态）
 * - [sessions]：Room 会话列表（停止后摘要卡从这取最终数据）
 * - [track]：地图折线数据（GCJ-02）。增量来自 Service 的 `lastPoint`，
 *   首次进入 / 重进本页时先用 `getPointsForSession()` 从 DB 补齐历史
 * - [orphaned]：App 启动时被自动收尾的悬挂会话（上次记录中被系统杀掉），用于一次性提示
 */
class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as FootprintApp).repository

    val recording: StateFlow<RecordingTrackState.Snapshot?> =
        RecordingTrackState.current.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessions: StateFlow<List<TrackSession>> =
        repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 上次异常中断的记录（App 启动时已收尾保存），提示完调用 [dismissRecovery]。 */
    val orphaned: StateFlow<List<SessionRecovery.Orphaned>> =
        SessionRecovery.orphaned.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 原始轨迹点（含低质量点，与 DB 一致），只用于去重判断。 */
    private val rawPoints = MutableStateFlow<List<TrackPoint>>(emptyList())

    /** 短记录已丢弃提示（用户明确要求：太短的记录直接不计，不弹选择框）。 */
    private val _shortDiscarded = MutableStateFlow(false)
    val shortDiscarded: StateFlow<Boolean> = _shortDiscarded
    fun consumeShortDiscarded() { _shortDiscarded.value = false }

    /** 喂给地图的折线。只做类型转换（TrackPoint → MapPoint），抽稀留到详情页（P2.5）。 */
    val track: StateFlow<List<MapPoint>> = rawPoints
        .map { list -> list.map { MapPoint(it.lat, it.lng) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 参考线（循迹 b）：已缓存线路的轨迹点，叠加在实时轨迹下方。 */
    private val _referenceTrack = MutableStateFlow<List<MapPoint>>(emptyList())
    val referenceTrack: StateFlow<List<MapPoint>> = _referenceTrack.asStateFlow()

    init {
        viewModelScope.launch {
            var loadedSessionId = -1L
            RecordingTrackState.current.collect { snap ->
                // 停止后（snap == null）**不清空**折线：摘要卡弹着的时候地图上还能看到刚走的线，
                // 用户体验上比"一停线就消失"合理。下一次开始时按新 sessionId 重新加载。
                if (snap == null) return@collect

                if (snap.sessionId != loadedSessionId) {
                    loadedSessionId = snap.sessionId
                    // ViewModel 作用域挂在导航返回栈上：离开记录页再进来会重建，
                    // 内存里没有点，必须从 DB 补（否则地图上只剩蓝点，线从中间断）。
                    rawPoints.value = repo.getPointsForSession(snap.sessionId)
                }

                val point = snap.lastPoint ?: return@collect
                // 按时间戳去重：DB 补齐的点里可能已经包含这个点（补齐与推送之间有一帧竞态）。
                if (rawPoints.value.lastOrNull()?.timestamp == point.timestamp) return@collect
                rawPoints.value = rawPoints.value + point
            }
        }

        // Service 看门狗（P2.3）：进程活着但 Service 被系统杀掉时，快照会停在最后一帧，
        // UI 会永久显示"记录中"（用户既停不掉也开不了新的）。心跳超时就清掉状态。
        //
        // **这里只清 UI 状态，不碰数据库**：光凭"心跳停了"无法区分
        // 「Service 真死了」和「Service 被系统冻了一分钟」，而后者的数据还在正常写入。
        // 在活的记录脚下把会话 finalize 掉，会让这条轨迹被拆成两段。
        // 真正落库的收尾交给 Application 启动时的 SessionRecovery——那时可以确定
        // 本进程没有任何 Service 在跑。
        viewModelScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val stale = RecordingTrackState.current.value ?: continue
                if (RecordingTrackState.clearIfStale(SystemClock.elapsedRealtime(), STALE_AFTER_MS)) {
                    Log.w(
                        "RecordViewModel",
                        "service heartbeat lost for ${STALE_AFTER_MS}ms, " +
                                "session=${stale.sessionId} will be finalized on next launch"
                    )
                }
            }
        }
    }

    /** 用户看完「上次记录异常中断」提示后调用，避免下次进页面又弹。 */
    fun dismissRecovery() = SessionRecovery.consume()

    /**
     * 判断一条进行中的记录是否「太短」。
     *
     * 用户明确要求（2026-10-21）：没怎么动 / 时间太短的记录**直接不计入**，
     * 只提醒、不给用户选择。阈值：有效时长 < 60 秒 或 距离 < 50 米。
     */
    fun isTooShort(snap: RecordingTrackState.Snapshot?): Boolean {
        snap ?: return false
        return snap.elapsedMs < MIN_DURATION_MS || snap.distanceMeters < MIN_DISTANCE_M
    }

    /**
     * 丢弃一条短记录：等服务把状态清空（收尾落库完成）后，把会话连同轨迹点一起删掉。
     * 不与 Service 的收尾赛跑——先让它正常收尾，再删，时序最稳。
     */
    fun discardShortRecord(sessionId: Long) {
        viewModelScope.launch {
            // 等 Service 停止并清掉状态（停止即代表收尾事务已完成）
            RecordingTrackState.current.first { it == null || it.sessionId != sessionId }
            kotlinx.coroutines.delay(300)
            withContext(Dispatchers.IO) { repo.deleteSession(sessionId) }
            _shortDiscarded.value = true
        }
    }

    /** 创建一条新会话（endTime = NULL），返回 sessionId 用于启动前台服务。 */
    fun startSession(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.startSession(System.currentTimeMillis())
            onReady(id)
        }
    }

    /**
     * 加载一条已缓存线路作为参考线（循迹 b）。
     *
     * 无缓存（线路没下载过离线）返回 null，UI 侧提示先下载；
     * 有缓存则把点转成 [MapPoint] 推给 [referenceTrack]。
     */
    suspend fun loadReferenceRoute(routeId: Long): Boolean = withContext(Dispatchers.IO) {
        val pts = repo.getCachedRoutePoints(routeId)
        if (pts.isEmpty()) {
            false
        } else {
            _referenceTrack.value = pts.map { MapPoint(it.lat, it.lng) }
            true
        }
    }

    /**
     * 把一条会话导出成 GPX 并返回系统分享 Intent。
     *
     * 没有轨迹点时回调 null（UI 侧提示），不生成空文件——空 GPX 传给别的 App 只会让人困惑。
     * 文件读写放 IO 线程：点数上万时序列化 + 落盘不能卡 UI。
     */
    fun exportGpx(session: TrackSession, onReady: (Intent?) -> Unit) {
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) {
                val points = repo.getPointsForSession(session.id)
                if (points.isEmpty()) return@withContext null
                val app = getApplication<Application>()
                val uri = GpxExporter.export(app, session, points)
                GpxExporter.shareIntent(uri)
            }
            onReady(intent)
        }
    }

    private companion object {
        /** 看门狗检查周期。 */
        const val WATCHDOG_INTERVAL_MS = 10_000L

        /** 短记录阈值：有效时长下限。 */
        const val MIN_DURATION_MS = 60_000L

        /** 短记录阈值：距离下限（米）。 */
        const val MIN_DISTANCE_M = 50f

        /**
         * 超过这个时间没有心跳才认定 Service 已死。
         *
         * 取 60 秒而不是 15 秒：国产 ROM 在息屏/省电下可能把协程冻住几十秒，
         * 阈值太短会在「Service 其实还活着」时误清状态。Service 每秒心跳一次，
         * 60 倍余量足以覆盖任何正常调度延迟。
         */
        const val STALE_AFTER_MS = 60_000L
    }
}
