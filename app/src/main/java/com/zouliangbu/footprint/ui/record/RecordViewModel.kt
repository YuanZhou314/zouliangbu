package com.zouliangbu.footprint.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.RecordingTrackState
import com.zouliangbu.footprint.data.db.TrackSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 记录页（出发）ViewModel。
 *
 * - recording：Service 通过 RecordingTrackState 推送的实时快照
 * - sessions：Room 会话列表（停止后摘要卡从这取最终数据）
 */
class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as FootprintApp).repository

    val recording: StateFlow<RecordingTrackState.Snapshot?> =
        RecordingTrackState.current.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessions: StateFlow<List<TrackSession>> =
        repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 创建一条新会话（endTime = NULL），返回 sessionId 用于启动前台服务。 */
    fun startSession(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.startSession(System.currentTimeMillis())
            onReady(id)
        }
    }
}
