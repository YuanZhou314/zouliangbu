package com.zouliangbu.footprint.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.db.TrackSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 我的页本地真实统计（只统计已结束的会话，避免记录中次数提前 +1）。 */
data class ProfileStats(
    val count: Int = 0,
    val totalMeters: Double = 0.0,
    val lastStartTime: Long? = null
)

/**
 * 我的页 ViewModel：从 Room 会话列表派生统计。
 * 不改 DAO / schema：observeSessions() 已是 Room 后台线程 Flow，map 派生即可。
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FootprintApp).repository

    val stats: StateFlow<ProfileStats> = repo.observeSessions()
        .map { sessions ->
            // 只统计自己记录的：导入的 GPX（source=imported）是别人的轨迹，
            // 不算自己的运动量；跟着线路自己走出来的记录不受影响。
            val completed = sessions.filter {
                it.endTime != null && it.source == TrackSession.SOURCE_RECORDED
            }
            ProfileStats(
                count = completed.size,
                totalMeters = completed.sumOf { it.totalDistanceMeters.toDouble() },
                lastStartTime = completed.maxOfOrNull { it.startTime }
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())
}
