package com.zouliangbu.footprint.ui.tracks

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.export.GpxExporter
import com.zouliangbu.footprint.ui.map.MapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 详情页状态。加载完一次即可，没有实时刷新需求。 */
data class TrackDetailUi(
    val loading: Boolean = true,
    val session: TrackSession? = null,
    val points: List<MapPoint> = emptyList(),
    /** 原始点数（含低质量点）。[points] 是喂地图的投影，将来抽稀后两者会不等。 */
    val rawPointCount: Int = 0
)

/**
 * 轨迹详情 ViewModel。
 *
 * 一次性把会话 + 全部点读进内存：单条徒步轨迹通常几千到几万点，
 * 全量 List 在内存里约几百 KB，比边滚边查库简单得多。
 * 点数超过 2 万时该在这里接抽稀（ROADMAP P2.5）——**抽稀放这里，别放 Compose 里**，
 * 否则每次重组都重算一遍。
 */
class TrackDetailViewModel(
    app: Application,
    private val sessionId: Long
) : AndroidViewModel(app) {

    private val repo = (app as FootprintApp).repository

    private val _state = MutableStateFlow(TrackDetailUi())
    val state: StateFlow<TrackDetailUi> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val session = repo.getSession(sessionId)
            val raw = repo.getPointsForSession(sessionId)
            _state.value = TrackDetailUi(
                loading = false,
                session = session,
                points = raw.map { MapPoint(it.lat, it.lng) },
                rawPointCount = raw.size
            )
        }
    }

    /** 导出 GPX 并给分享 Intent；没有轨迹点时回调 null（UI 侧 toast）。 */
    fun exportGpx(onReady: (Intent?) -> Unit) {
        val session = _state.value.session ?: return onReady(null)
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) {
                val points = repo.getPointsForSession(session.id)
                if (points.isEmpty()) return@withContext null
                val app = getApplication<Application>()
                GpxExporter.shareIntent(GpxExporter.export(app, session, points))
            }
            onReady(intent)
        }
    }

    companion object {
        fun factory(app: Application, sessionId: Long) = viewModelFactory {
            initializer { TrackDetailViewModel(app, sessionId) }
        }
    }
}
