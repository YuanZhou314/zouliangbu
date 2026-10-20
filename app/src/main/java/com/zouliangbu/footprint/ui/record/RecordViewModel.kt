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

    /** 喂给地图的折线。只做类型转换（TrackPoint → MapPoint），抽稀留到详情页（P2.5）。 */
    val track: StateFlow<List<MapPoint>> = rawPoints
        .map { list -> list.map { MapPoint(it.lat, it.lng) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** 创建一条新会话（endTime = NULL），返回 sessionId 用于启动前台服务。 */
    fun startSession(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.startSession(System.currentTimeMillis())
            onReady(id)
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
