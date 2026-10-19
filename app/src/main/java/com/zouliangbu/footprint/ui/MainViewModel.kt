package com.zouliangbu.footprint.ui

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

class MainViewModel(application: Application) : AndroidViewModel(application) {

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

    /** 仅供将来扩展；当前 MainActivity 直接发 STOP intent 给 service。 */
    fun stopSession() {
        viewModelScope.launch { /* no-op：service 收到 STOP intent 后会自己落库 */ }
    }
}