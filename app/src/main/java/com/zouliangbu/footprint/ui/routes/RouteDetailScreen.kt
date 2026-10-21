package com.zouliangbu.footprint.ui.routes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.data.remote.ContentApiFactory
import com.zouliangbu.footprint.data.remote.RouteDetail
import com.zouliangbu.footprint.data.remote.TrackPointDto
import com.zouliangbu.footprint.data.remote.MarkerDto
import com.zouliangbu.footprint.ui.components.CoverPlaceholder
import com.zouliangbu.footprint.ui.map.MapPoint
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 线路详情页。布局对齐两步路（用户参考截图）：
 * 封面 → 线路轨迹地图（点击进全屏预览） → 高度/上升/下降数据条 → 爬升/平缓/下坡分段
 * → 海拔曲线 → 途经景点 → 正文 → 来源。
 *
 * 颜色全部走 MaterialTheme（用户要求：深色模式下正文不能是灰色）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: Long,
    onBack: () -> Unit,
    onOpenTrackPreview: (Long) -> Unit = {}
) {
    var detail by remember { mutableStateOf<RouteDetail?>(null) }
    var trackPoints by remember { mutableStateOf<List<TrackPointDto>>(emptyList()) }
    var markers by remember { mutableStateOf<List<MarkerDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(routeId) {
        withContext(Dispatchers.IO) {
            runCatching {
                val d = ContentApiFactory.service.getRoute(routeId)
                val t = ContentApiFactory.service.getTrackPoints(routeId)
                val m = ContentApiFactory.service.getMarkers(routeId)
                detail = d.data
                trackPoints = t.data ?: emptyList()
                markers = m.data ?: emptyList()
            }.onFailure { loadError = true }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.name ?: "线路详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { inner ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (detail == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "线路不存在或已下线",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("返回") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                // 0. 封面
                detail?.let { d ->
                    item {
                        CoverPlaceholder(
                            seed = d.coverStyle,
                            aspectRatio = 1.6f,
                            label = d.region,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            coverUrl = d.coverUrl
                        )
                    }
                }

                // 0.5 核心指标行：里程 / 爬升 / 难度 / 耗时
                detail?.let { d -> item { SummaryStatsRow(d) } }

                // 1. 线路轨迹地图（点击进全屏预览；无轨迹给占位提示）
                item {
                    val pts = trackPoints.map { MapPoint(it.lat, it.lng) }
                    SectionTitle("线路轨迹")
                    if (pts.isNotEmpty()) {
                        MapViewWrapper(
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenTrackPreview(routeId) },
                            track = pts,
                            showEndpoints = true,
                            autoZoom = true
                        )
                        Text(
                            "点击地图查看大图轨迹",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        Text(
                            "该线路暂无轨迹数据",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 2. 高度 / 上升 / 下降 数据条（对齐截图）
                if (trackPoints.isNotEmpty()) {
                    item { AltitudeStatsRow(trackPoints) }

                    // 3. 爬升 / 平缓 / 下坡 分段
                    item { SplitsBar(trackPoints) }

                    // 4. 海拔曲线
                    item { ElevationChartSection(trackPoints) }
                }

                // 5. 途经景点（标注点）
                val spots = markers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
                if (spots.isNotEmpty()) {
                    item { SectionTitle("途经景点") }
                    item { SpotChips(spots) }
                }

                // 6. 正文
                detail?.let { d ->
                    item { BodySection("简介", d.introHtml?.takeIf { it.isNotBlank() }) }
                    d.contentHtml?.takeIf { it.isNotBlank() }?.let { content ->
                        item { BodySection("详细描述", content) }
                    }
                    d.trafficHtml?.takeIf { it.isNotBlank() }?.let { traffic ->
                        item { BodySection("交通攻略", traffic) }
                    }
                }

                // 7. 来源
                item { detail?.let { SourceFooter(it) } }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(4.dp).height(18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ===== 核心指标行 =====
@Composable
private fun SummaryStatsRow(d: RouteDetail) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(if (d.distanceKm > 0) "%.1f".format(d.distanceKm) else "-", "公里")
        StatCell(if (d.ascentMeters > 0) "${d.ascentMeters}" else "-", "爬升/米")
        StatCell(d.difficulty?.takeIf { it.isNotBlank() } ?: "-", "难度")
        StatCell(d.durationName?.takeIf { it.isNotBlank() } ?: "-", "耗时/小时")
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===== 高度 / 上升 / 下降 数据条（对齐截图：上行三大项 + 下行三小项）=====
@Composable
private fun AltitudeStatsRow(points: List<TrackPointDto>) {
    val alts = points.mapNotNull { it.altitude }
    if (alts.isEmpty()) return
    var ascent = 0.0
    var descent = 0.0
    for (i in 1 until alts.size) {
        val diff = alts[i] - alts[i - 1]
        if (diff > 0) ascent += diff else descent += -diff
    }
    val avg = alts.average()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AltitudeItem("高度", "%.0f".format(alts.max()), "最大 %.0f".format(alts.max()), up = true)
            AltitudeItem("上升", "%.0f".format(ascent), "最小 %.0f".format(alts.min()), up = true)
            AltitudeItem("下降", "%.0f".format(descent), "平均 %.0f".format(avg), up = false)
        }
    }
}

@Composable
private fun AltitudeItem(label: String, value: String, sub: String, up: Boolean) {
    val accent = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text("米 ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (up) "↑" else "↓", fontSize = 13.sp, color = accent)
        }
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===== 爬升 / 平缓 / 下坡 分段（占比条 + 图例）=====
@Composable
private fun SplitsBar(points: List<TrackPointDto>) {
    // 按相邻点间坡度归类距离：>2% 上坡、<-2% 下坡、其余平缓
    var climb = 0.0; var flat = 0.0; var descend = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        val dx = approxDistanceM(a.lat, a.lng, b.lat, b.lng)
        val dy = (b.altitude ?: a.altitude ?: 0.0) - (a.altitude ?: 0.0)
        if (dx <= 0.0) continue
        val slope = dy / dx
        when {
            slope > 0.02 -> climb += dx
            slope < -0.02 -> descend += dx
            else -> flat += dx
        }
    }
    val total = climb + flat + descend
    if (total <= 0.0) return

    val cPct = (climb / total * 100).toInt()
    val fPct = (flat / total * 100).toInt()
    val dPct = (100 - cPct - fPct).coerceAtLeast(0)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Box(Modifier.weight(cPct.toFloat().coerceAtLeast(0.5f)).background(MaterialTheme.colorScheme.primary))
            Box(Modifier.weight(fPct.toFloat().coerceAtLeast(0.5f)).background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.weight(dPct.toFloat().coerceAtLeast(0.5f)).background(MaterialTheme.colorScheme.tertiary))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Legend("↑ 爬升 %.2f 公里(%d%%)".format(climb / 1000, cPct), MaterialTheme.colorScheme.primary)
            Legend("— 平缓 %.2f 公里(%d%%)".format(flat / 1000, fPct), MaterialTheme.colorScheme.outlineVariant)
            Legend("↓ 下坡 %.2f 公里(%d%%)".format(descend / 1000, dPct), MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun approxDistanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h = Math.sin(dLat / 2).pow2() + Math.cos(Math.toRadians(lat1)) *
            Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).pow2()
    return 2 * r * Math.asin(Math.sqrt(h))
}

private fun Double.pow2(): Double = this * this

// ===== 海拔曲线 =====
@Composable
private fun ElevationChartSection(points: List<TrackPointDto>) {
    val alts = points.mapNotNull { it.altitude }
    if (alts.size < 2) return
    val minA = alts.min()
    val maxA = alts.max()
    val range = (maxA - minA).coerceAtLeast(1.0)
    val chartPrimary = MaterialTheme.colorScheme.primary
    val chartFill = MaterialTheme.colorScheme.surfaceVariant

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val w = size.width
            val h = size.height
            val step = w / (alts.size - 1).coerceAtLeast(1)

            val areaPath = Path().apply {
                moveTo(0f, h)
                for (i in alts.indices) {
                    val x = i * step
                    val y = h - ((alts[i] - minA) / range * h * 0.85f).toFloat()
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(areaPath, chartPrimary.copy(alpha = 0.25f))

            val linePath = Path().apply {
                for (i in alts.indices) {
                    val x = i * step
                    val y = h - ((alts[i] - minA) / range * h * 0.85f).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(linePath, chartPrimary, style = Stroke(width = 2f))
        }
        // X 轴刻度：起点 / 终点距离
        val distKm = approxDistanceM(
            points.first().lat, points.first().lng, points.last().lat, points.last().lng
        ) / 1000
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0.0公里", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.1f公里".format(distKm), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== 正文 =====
@Composable
private fun BodySection(title: String, html: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(title)
        val text = (html ?: "")
            .replace(Regex("<p>"), "")
            .replace(Regex("</p>"), "\n")
            .replace(Regex("<br[^>]*>"), "\n")
            .replace(Regex("<li>"), "\u2022 ")
            .replace(Regex("</li>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .trim()
        if (text.isBlank()) {
            Text(
                "暂无正文",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text, fontSize = 14.sp, lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ===== 来源 =====
@Composable
private fun SourceFooter(d: RouteDetail) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "内容来源：" + (d.sourceName ?: "未知") + (d.sourceAuthor?.let { " · " + it } ?: ""),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        d.sourceUrl?.let { url ->
            Text(
                url, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "版权归原作者所有，仅供个人学习参考",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== 途经景点（chips）=====
@Composable
private fun SpotChips(spots: List<String>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        spots.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { name ->
                    Text(
                        name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
