package com.zouliangbu.footprint.ui.routes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
import com.zouliangbu.footprint.ui.map.MapPoint
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 线路详情页。布局对齐两步路 App：
 * 统计行 → 信息面板 → 地图轨迹 → 海拔统计+图表 → 正文分段 → 来源标注。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: Long,
    onBack: () -> Unit
) {
    var detail by remember { mutableStateOf<RouteDetail?>(null) }
    var trackPoints by remember { mutableStateOf<List<TrackPointDto>>(emptyList()) }
    var markers by remember { mutableStateOf<List<MarkerDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(routeId) {
        withContext(Dispatchers.IO) {
            runCatching {
                val d = ContentApiFactory.service.getRoute(routeId)
                val t = ContentApiFactory.service.getTrackPoints(routeId)
                val m = ContentApiFactory.service.getMarkers(routeId)
                detail = d.data
                trackPoints = t.data ?: emptyList()
                markers = m.data ?: emptyList()
            }
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
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                // 1. 统计行（对齐截图：4 大指标）
                item { StatsRow(detail, trackPoints) }

                // 2. 信息面板（难度/风险/风景/适宜月份）
                item { InfoPanel(detail) }

                // 3. 地图轨迹
                item {
                    val pts = trackPoints.map { MapPoint(it.lat, it.lng) }
                    if (pts.isNotEmpty()) {
                        MapViewWrapper(
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            track = pts,
                            showEndpoints = true,
                            autoZoom = true
                        )
                    }
                }

                // 4. 海拔统计 + 图表
                if (trackPoints.isNotEmpty()) {
                    item { ElevationSection(trackPoints) }
                }

                // 5. 正文
                detail?.introHtml?.takeIf { it.isNotBlank() }?.let { intro ->
                    item { BodySection("简介", intro) }
                }
                detail?.contentHtml?.takeIf { it.isNotBlank() }?.let { content ->
                    item { BodySection("详细描述", content) }
                }
                detail?.trafficHtml?.takeIf { it.isNotBlank() }?.let { traffic ->
                    item { BodySection("交通攻略", traffic) }
                }

                // 6. 来源
                item { detail?.let { SourceFooter(it) } }

                // 7. 标注点
                if (markers.isNotEmpty()) {
                    item {
                        SectionTitle("途经标注点 · " + markers.size)
                    }
                    items(markers.filter { !it.name.isNullOrBlank() }) { m -> MarkerRow(m) }
                }
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
        Box(Modifier.width(4.dp).height(18.dp).background(Color(0xFF4CAF50), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ===== 1. 统计行 =====
@Composable
private fun StatsRow(detail: RouteDetail?, trackPoints: List<TrackPointDto>) {
    val distText = detail?.let { if (it.distanceKm > 0) "%.2f".format(it.distanceKm) else "-" } ?: "-"
    val maxAlt = trackPoints.maxOfOrNull { it.altitude ?: 0.0 } ?: 0.0
    val climb = detail?.ascentMeters?.takeIf { it > 0 }?.toString() ?: "-"
    val timeText = detail?.durationName ?: "-"

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(distText, "公里", "总里程")
        StatItem("%.0f".format(maxAlt), "米", "最高海拔")
        StatItem(climb, "米", "累计爬升")
        StatItem(timeText, "小时", "参考耗时")
    }
}

@Composable
private fun StatItem(value: String, unit: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 11.sp, color = Color(0xFF888888), modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF888888))
    }
}

// ===== 2. 信息面板 =====
@Composable
private fun InfoPanel(detail: RouteDetail?) {
    if (detail == null) return
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            InfoRow("适宜月份", detail.suitableMonths ?: "-", null)
            Divider(Modifier.padding(vertical = 8.dp))
            InfoRow("风景", detail.viewType ?: "-", Color(0xFFFFF176))
            InfoRow("难度", detail.difficulty ?: "-", Color(0xFFFFB74D))
            InfoRow("风险", detail.risk ?: "-", Color(0xFFFFB74D))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, badgeColor: Color?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(72.dp))
        if (badgeColor != null) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp))
                    .background(badgeColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Text(value, fontSize = 13.sp, color = Color(0xFF666666))
        }
    }
}

// ===== 4. 海拔统计 + 图表 =====
@Composable
private fun ElevationSection(trackPoints: List<TrackPointDto>) {
    val alts = trackPoints.mapNotNull { it.altitude }
    if (alts.isEmpty()) return
    val maxAlt = alts.max()
    val minAlt = alts.min()
    val avgAlt = alts.average()
    // 累计爬升/下降
    var ascent = 0.0
    var descent = 0.0
    for (i in 1 until alts.size) {
        val diff = alts[i] - alts[i - 1]
        if (diff > 0) ascent += diff else descent += -diff
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle("海拔")
        // 统计行
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("%.0f".format(maxAlt), "米", "最大")
            StatItem("%.0f".format(minAlt), "米", "最小")
            StatItem("%.0f".format(avgAlt), "米", "平均")
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("%.0f".format(ascent), "米", "上升")
            StatItem("%.0f".format(descent), "米", "下降")
            StatItem("%.1f".format(alts.size.toDouble()), "点", "轨迹点")
        }
        Spacer(Modifier.height(12.dp))
        // 海拔曲线图（绿色面积图）
        ElevationChart(alts)
    }
}

@Composable
private fun ElevationChart(alts: List<Double>) {
    if (alts.size < 2) return
    val minA = alts.min()
    val maxA = alts.max()
    val range = (maxA - minA).coerceAtLeast(1.0)

    Canvas(
        Modifier.fillMaxWidth().height(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
    ) {
        val w = size.width
        val h = size.height
        val step = w / (alts.size - 1).coerceAtLeast(1)

        // 面积路径
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
        drawPath(areaPath, Color(0x404CAF50))

        // 线条
        val linePath = Path().apply {
            for (i in alts.indices) {
                val x = i * step
                val y = h - ((alts[i] - minA) / range * h * 0.85f).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(linePath, Color(0xFF4CAF50), style = Stroke(width = 2f))
    }
}

// ===== 正文 =====
@Composable
private fun BodySection(title: String, html: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(title)
        val text = html
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
        Text(text, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFF333333))
    }
}

// ===== 来源 =====
@Composable
private fun SourceFooter(d: RouteDetail) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Spacer(Modifier.height(8.dp))
        Text(
            "内容来源：" + (d.sourceName ?: "未知") + (d.sourceAuthor?.let { " · " + it } ?: ""),
            fontSize = 11.sp,
            color = Color(0xFF999999)
        )
        d.sourceUrl?.let { url ->
            Text(url, fontSize = 10.sp, color = Color(0xFFBBBBBB), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("版权归原作者所有，仅供个人学习参考", fontSize = 10.sp, color = Color(0xFFCCCCCC))
    }
}

@Composable
private fun MarkerRow(marker: MarkerDto) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(6.dp).height(6.dp).background(Color(0xFF4CAF50), RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(10.dp))
        Text(marker.name ?: "", fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("%.0f m".format(marker.altitude ?: 0.0), fontSize = 11.sp, color = Color(0xFF999999))
    }
}
