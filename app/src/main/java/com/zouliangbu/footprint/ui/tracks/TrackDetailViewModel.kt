package com.zouliangbu.footprint.ui.tracks

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.DeviceId
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.data.remote.ProposalDto
import com.zouliangbu.footprint.data.repo.ProposalRepository
import com.zouliangbu.footprint.export.GpxExporter
import com.zouliangbu.footprint.plants.PlantSpecies
import com.zouliangbu.footprint.ui.map.MapPoint
import com.zouliangbu.footprint.util.Distance
import com.zouliangbu.footprint.util.Elevation
import com.zouliangbu.footprint.util.Pace
import com.zouliangbu.footprint.util.Polyline
import com.zouliangbu.footprint.util.SegmentMatch
import com.zouliangbu.footprint.util.TrackSeries
import com.zouliangbu.footprint.util.TrackSplits
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
    /** 原始点数（含低质量点）。[points] 是喂地图的投影，抽稀后两者会不等。 */
    val rawPointCount: Int = 0,
    /** 速度/配速统计（ROADMAP P3.2）。 */
    val paceStats: Pace.Stats = Pace.Stats.ZERO,
    /** 爬升/下降（米，ROADMAP P3.1）。 */
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    /** 最高海拔（米）。没有高程数据时为 0，UI 显示成 "—" 而不是 0。 */
    val maxAltitude: Double = 0.0,
    /** 最低海拔（米），同样 0 表示没有高程数据。 */
    val minAltitude: Double = 0.0,
    /** 每公里分段（ROADMAP P3.5）。 */
    val splits: List<TrackSplits.Split> = emptyList(),
    /** 图表序列：速度 / 配速 / 海拔 / 累计爬升下降（见 TrackSeries）。 */
    val series: TrackSeries.Bundle = TrackSeries.Bundle(),
    /** 与历史记录的共同路段对比（记录层做深的核心功能）。 */
    val compare: CompareUi = CompareUi(),
    /** 沿途常见植物：起点落入已覆盖行政区才有值，否则 null（UI 整块隐藏）。 */
    val plants: PlantGuideUi? = null
)

/** 沿途常见植物区块的 UI 状态（来自本地 assets 图鉴，离线可用）。 */
data class PlantGuideUi(
    val regionName: String,
    val species: List<PlantSpecies>
)

/**
 * 「与上次对比」的状态。
 *
 * [checked] 用来区分「还没算」和「算完了但没有可比对象」——
 * 没有这个标志 UI 会在加载中一直显示"还没有历史记录"，很吓人。
 */
data class CompareUi(
    val checked: Boolean = false,
    val other: TrackSession? = null,
    val comparison: SegmentMatch.Comparison? = null,
    /** 没有可比对象时的说明文案。 */
    val note: String? = null
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
    private val plantRepo = (app as FootprintApp).plantGuideRepository

    private val _state = MutableStateFlow(TrackDetailUi())
    val state: StateFlow<TrackDetailUi> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val session = repo.getSession(sessionId)
            val raw = repo.getPointsForSession(sessionId)

            // 抽稀（ROADMAP P2.5）：只有喂地图的折线用抽稀后的点，
            // 统计（距离/配速/爬升/分段）一律用**原始点**——抽稀会丢掉细节，
            // 用抽稀后的点算距离会少算几十米。
            val simplified = Polyline.simplifyPoints(raw)
            val renderedPoints = simplified.map { MapPoint(it.lat, it.lng) }

            // 爬升：会话表里存的是停止时算好的值（v4）。老记录是 0，
            // 这时用当前点集现算一次，让历史记录也能看到爬升。
            val storedAscent = session?.ascentMeters ?: 0.0
            val storedDescent = session?.descentMeters ?: 0.0
            val computed = Elevation.ascentOf(raw)
            val ascent = if (storedAscent > 0.0) storedAscent else computed.ascentMeters
            val descent = if (storedDescent > 0.0) storedDescent else computed.descentMeters

            _state.value = TrackDetailUi(
                loading = false,
                session = session,
                points = renderedPoints,
                rawPointCount = raw.size,
                paceStats = Pace.statsOf(raw),
                ascentMeters = ascent,
                descentMeters = descent,
                maxAltitude = raw.maxOfOrNull { it.altitude } ?: 0.0,
                minAltitude = raw.filter { it.altitude > 0.0 }.minOfOrNull { it.altitude } ?: 0.0,
                splits = TrackSplits.splitsOf(raw),
                series = TrackSeries.build(raw)
            )

            // 沿途常见植物：起点行政区命中才有。纯 assets 解析 + 几何判定，
            // 很快，但失败/未覆盖都要静默 —— 这是锦上添花模块，不配拥有报错。
            val startPoint = raw.firstOrNull()
            if (startPoint != null) {
                val region = runCatching {
                    plantRepo.regionFor(startPoint.lat, startPoint.lng)
                }.getOrNull()
                if (region != null && region.species.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        plants = PlantGuideUi(region.regionName, region.species)
                    )
                }
            }

            // 对比单独一步：它要读别的会话的点集，慢且可能失败，不能拖累首屏。
            if (session != null) {
                val compare = buildCompare(session, raw)
                _state.value = _state.value.copy(compare = compare)
            }
        }
    }

    /**
     * 找「同一条线的上一次记录」并算出共同路段差异。
     *
     * 匹配策略（顺序即成本从低到高）：
     *  1. 只取最近 8 条已结束会话 —— 户外爱好者不可能把同一条线隔几年再比。
     *  2. 粗筛：起点或终点相距 < 300 m，且总距离差 < 40%。这一步排除绝大多数无关轨迹，
     *     避免对几万点做 O(n·m) 的投影匹配（手机上那是秒级卡顿）。
     *  3. 粗筛通过才交给 [SegmentMatch.compare]（内部还有 30% 重合度门槛）。
     *
     * 用几何而不是"轨迹名"匹配：自己记录的会话 name 通常是 null，
     * 靠名字永远匹配不上；导入的 GPX 才带名字。
     */
    private suspend fun buildCompare(
        current: TrackSession,
        currentPoints: List<TrackPoint>
    ): CompareUi = withContext(Dispatchers.IO) {
        if (currentPoints.size < 2) {
            return@withContext CompareUi(checked = true, note = "轨迹点太少，无法对比")
        }
        val curSamples = currentPoints.map {
            SegmentMatch.Sample(it.lat, it.lng, it.timestamp, it.altitude)
        }
        val candidates = repo.recentCompletedSessions(current.id, 8)
        for (cand in candidates) {
            val pts = repo.getPointsForSession(cand.id)
            if (pts.size < 2) continue

            val startGap = Distance.haversineMeters(
                currentPoints.first().lat, currentPoints.first().lng,
                pts.first().lat, pts.first().lng
            )
            val endGap = Distance.haversineMeters(
                currentPoints.last().lat, currentPoints.last().lng,
                pts.last().lat, pts.last().lng
            )
            val curDist = current.totalDistanceMeters.toDouble()
            val candDist = cand.totalDistanceMeters.toDouble()
            val near = startGap < 300.0 || endGap < 300.0
            val similarLength = curDist <= 0.0 || candDist <= 0.0 ||
                    kotlin.math.abs(curDist - candDist) / maxOf(curDist, candDist) < 0.4
            if (!near || !similarLength) continue

            val candSamples = pts.map {
                SegmentMatch.Sample(it.lat, it.lng, it.timestamp, it.altitude)
            }
            val cmp = SegmentMatch.compare(curSamples, candSamples) ?: continue
            return@withContext CompareUi(checked = true, other = cand, comparison = cmp)
        }
        CompareUi(
            checked = true,
            note = "还没找到同一条线路的历史记录，下次再走同一条线就能对比了"
        )
    }

    /**
     * 把这条轨迹贡献到公共线路库（UPLOAD 提案）。
     *
     * 服务端规则层会校验点数与坐标范围，不合格会 400 —— 错误信息原样回给 UI。
     */
    fun contribute(
        name: String,
        region: String,
        intro: String,
        nickname: String,
        onDone: (Result<ProposalDto>) -> Unit
    ) {
        val session = _state.value.session ?: return onDone(
            Result.failure(IllegalStateException("记录未加载"))
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val points = repo.getPointsForSession(session.id)
                    val app = getApplication<Application>()
                    ProposalRepository().submitUpload(
                        session = session,
                        points = points,
                        name = name,
                        region = region,
                        intro = intro,
                        proposerUuid = DeviceId.get(app),
                        proposerName = nickname
                    )
                }
            }
            onDone(result)
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
