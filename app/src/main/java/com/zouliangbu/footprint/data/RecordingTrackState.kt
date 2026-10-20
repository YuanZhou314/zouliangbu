package com.zouliangbu.footprint.data

import com.zouliangbu.footprint.data.db.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Service ↔ UI 之间传递的「正在记录」实时状态。
 * 由 RecordingTrackService 写入，由记录页订阅以更新浮层 / 通知 / 地图折线。
 *
 * 注意：这是**进程内单例**。Service 被系统回收后 UI 与它的值会不一致
 * （重启后 UI 仍显示"记录中"），这块由 ROADMAP P2.2 / P2.3 处理，本轮不动。
 */
object RecordingTrackState {

    data class Snapshot(
        val sessionId: Long,
        val distanceMeters: Float,
        val elapsedMs: Long,
        val pointCount: Int,
        /**
         * 最近一个**已落库**的点，供地图实时画折线。
         * 刚开始记录时还没采到点，为 null；重进记录页时 UI 用
         * `TrackRepository.getPointsForSession()` 从 DB 补齐历史，本字段只负责增量。
         */
        val lastPoint: TrackPoint? = null
    )

    private val _current = MutableStateFlow<Snapshot?>(null)
    val current = _current

    internal fun update(snapshot: Snapshot?) { _current.value = snapshot }
}
