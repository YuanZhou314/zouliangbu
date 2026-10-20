package com.zouliangbu.footprint.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.RecordingTrackState
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.ui.map.MapPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 记录页（出发）ViewModel。
 *
 * - [recording]：Service 通过 RecordingTrackState 推送的实时快照
 * - [sessions]：Room 会话列表（停止后摘要卡从这取最终数据）
 * - [track]：地图折线数据（GCJ-02）。增量来自 Service 的 `lastPoint`，
 *   首次进入 / 重进本页时先用 `getPointsForSession()` 从 DB 补齐历史。
 */
class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as FootprintApp).repository

    val recording: StateFlow<RecordingTrackState.Snapshot?> =
        RecordingTrackState.current.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessions: StateFlow<List<TrackSession>> =
        repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    }

    /** 创建一条新会话（endTime = NULL），返回 sessionId 用于启动前台服务。 */
    fun startSession(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.startSession(System.currentTimeMillis())
            onReady(id)
        }
    }
}
