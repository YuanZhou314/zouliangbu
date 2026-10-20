package com.zouliangbu.footprint.data

import com.zouliangbu.footprint.data.db.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Service ↔ UI 之间传递的「正在记录」实时状态。
 * 由 RecordingTrackService 写入，由记录页订阅以更新浮层 / 通知 / 地图折线。
 *
 * **这里只代表"当前进程里有一个活着的 Service 在记录"。**
 * 进程被杀后本对象随之清空，DB 里却会留下 `endTime IS NULL` 的悬挂会话——
 * 那部分由 [SessionRecovery] 在 App 启动时收尾（P2.2）。
 * 换句话说：UI 判断"是否在记录"只看这里，**不要**再去查 DB，否则被杀之后会永远显示记录中。
 */
object RecordingTrackState {

    data class Snapshot(
        val sessionId: Long,
        val distanceMeters: Float,
        /** 活动时长（已扣暂停）。暂停期间这个值不再增长。 */
        val elapsedMs: Long,
        val pointCount: Int,
        /**
         * 最近一个**已落库**的点，供地图实时画折线。
         * 刚开始记录时还没采到点，为 null；重进记录页时 UI 用
         * `TrackRepository.getPointsForSession()` 从 DB 补齐历史，本字段只负责增量。
         */
        val lastPoint: TrackPoint? = null,
        /** 是否处于暂停状态。暂停时不采点、不计距离、不计时长。 */
        val paused: Boolean = false,
        /**
         * 本快照的发布时间（elapsedRealtime）。Service 每秒心跳一次，
         * 所以 UI 可以用它判断快照是否"过期"——Service 被系统干掉但进程还在时，
         * 快照会停在被杀前的最后一刻，光看 null/非 null 是发现不了的。
         */
        val updatedAtElapsed: Long = 0L
    )

    private val _current = MutableStateFlow<Snapshot?>(null)
    val current = _current

    internal fun update(snapshot: Snapshot?) { _current.value = snapshot }

    /**
     * 心跳超时 → 认为 Service 已经不在了（被系统杀掉、崩溃），清掉状态并返回 true。
     *
     * 为什么需要：进程活着但 Service 死掉时，本对象会永远停在最后一帧，
     * 光判断"非 null"会让 UI 永久显示记录中（老问题）。Service 每秒心跳一次，
     * 15 秒没更新基本可以确定它没了。
     */
    internal fun clearIfStale(nowElapsed: Long, staleAfterMs: Long): Boolean {
        val snap = _current.value ?: return false
        if (nowElapsed - snap.updatedAtElapsed <= staleAfterMs) return false
        _current.value = null
        return true
    }
}
