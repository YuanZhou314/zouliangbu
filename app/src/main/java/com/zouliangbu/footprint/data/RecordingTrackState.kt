package com.zouliangbu.footprint.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Service ↔ UI 之间传递的「正在记录」实时状态。
 * 由 RecordingTrackService 写入，由 MainScreen 订阅以更新浮层和通知。
 */
object RecordingTrackState {

    data class Snapshot(
        val sessionId: Long,
        val distanceMeters: Float,
        val elapsedMs: Long,
        val pointCount: Int
    )

    private val _current = MutableStateFlow<Snapshot?>(null)
    val current = _current

    internal fun update(snapshot: Snapshot?) { _current.value = snapshot }
}