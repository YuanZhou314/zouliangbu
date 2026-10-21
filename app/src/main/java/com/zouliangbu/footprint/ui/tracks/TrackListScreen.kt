package com.zouliangbu.footprint.ui.tracks

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSummary
import com.zouliangbu.footprint.ui.theme.ForestGreen
import com.zouliangbu.footprint.ui.theme.TrailOrange
import com.zouliangbu.footprint.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的记录」完整版列表页（参考两步路的样式）：
 * 顶部累计统计卡 + 按月分组的小节头 + 带轨迹缩略图的记录卡。
 * 交互不变：点卡片进详情，长按删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: TrackListViewModel = viewModel()
) {
    val context = LocalContext.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<TrackSummary?>(null) }

    // SAF 选文件：不申请任何存储权限。mime 用 */*，内容合法性交给解析器判。
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.importGpx(uri) { outcome ->
            when (outcome) {
                is ImportOutcome.Success -> {
                    Toast.makeText(
                        context,
                        "已导入 ${outcome.pointCount} 个轨迹点",
                        Toast.LENGTH_SHORT
                    ).show()
                    onOpenDetail(outcome.sessionId)
                }
                is ImportOutcome.Failure ->
                    Toast.makeText(context, outcome.reason, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的记录", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                            Icon(
                                Icons.Filled.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("导入")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { inner ->
        if (tracks.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(inner),
                onImport = { picker.launch(arrayOf("*/*")) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ===== 累计统计卡 =====
                item(key = "summary") {
                    SummaryCard(
                        count = stats.count,
                        totalMs = stats.totalMs,
                        totalMeters = stats.totalMeters
                    )
                }
                // ===== 按月分组 =====
                tracks.groupBy { monthKey(it.startTime) }
                    .toSortedMap(compareByDescending { it })
                    .forEach { (month, list) ->
                        item(key = "month-$month") {
                            MonthHeader(
                                month = month,
                                meters = list.sumOf { it.totalDistanceMeters.toDouble() }
                                    .toFloat(),
                                ascent = list.sumOf { it.ascentMeters },
                                durationMs = list.sumOf { it.activeDurationMs },
                                count = list.size
                            )
                        }
                        items(
                            count = list.size,
                            key = { list[it].id }
                        ) { idx ->
                            val track = list[idx]
                            TrackRow(
                                track = track,
                                thumbPoints = { vm.thumbnailPoints(track.id) },
                                onClick = { onOpenDetail(track.id) },
                                onLongClick = { pendingDelete = track }
                            )
                        }
                    }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "「${target.name ?: formatDate(target.startTime)}」的 ${target.pointCount} 个轨迹点" +
                            "会一起删除，无法恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(target.id)
                        pendingDelete = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 顶部"总运动累积记录"统计卡。 */
@Composable
private fun SummaryCard(count: Int, totalMs: Long, totalMeters: Float) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = ForestGreen.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("总运动累积记录", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryCell("$count", "累计次数")
                SummaryCell(
                    "%.1f 小时".format(totalMs / 3_600_000.0),
                    "累计耗时"
                )
                SummaryCell("%.2f 公里".format(totalMeters / 1000.0), "累计里程")
            }
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 月度小节头：2026年10月 | 公里 · 爬升 · 时长 · 次数 */
@Composable
private fun MonthHeader(
    month: String,
    meters: Float,
    ascent: Double,
    durationMs: Long,
    count: Int
) {
    Text(
        buildString {
            append(month.replace("-", "年") + "月") // "2026-10" -> "2026年10月"
            append("   ")
            append("%.2f公里".format(meters / 1000.0))
            append("  ↑%.0f米".format(ascent))
            append("  ")
            append(formatDurationShort(durationMs))
            append("  ")
            append("${count}次")
        },
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
    )
}

/**
 * 单条记录卡：左侧轨迹缩略图，右侧时间 / 距离 / 时长·速度·爬升 / 估算卡路里。
 * 卡路里为估算值（徒步 MET≈6 × 65kg × 小时数），App 没有体重数据，只能粗估。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: TrackSummary,
    thumbPoints: suspend () -> List<TrackPoint>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val durationMs = track.activeDurationMs
    val hours = durationMs / 3_600_000.0
    val avgSpeedKmh = if (hours > 0) track.totalDistanceMeters / 1000f / hours else 0f
    val kcal = (6.0 * 65.0 * hours).toInt()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackThumb(points = thumbPoints)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.name ?: formatRange(track.startTime, track.endTime),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.2f 公里".format(track.totalDistanceMeters / 1000.0),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaCell(Icons.Filled.Timer, TimeFmt.elapsed(durationMs))
                    Spacer(Modifier.width(10.dp))
                    MetaCell(Icons.Filled.Speed, "%.1f km/h".format(avgSpeedKmh))
                    Spacer(Modifier.width(10.dp))
                    MetaCell(Icons.Filled.Terrain, "%.0f米".format(track.ascentMeters))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "估算消耗 $kcal 千卡",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 轨迹缩略图：本地 Canvas 折线，不走地图 SDK（列表里嵌几十个地图实例不可接受）。 */
@Composable
private fun TrackThumb(points: suspend () -> List<TrackPoint>) {
    // produceState：进入组合才查库，离开组合协程取消；VM 里有缓存所以来回滚动不重复 IO。
    val resolved by produceState<List<TrackPoint>?>(initialValue = null, points) {
        value = points()
    }
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        val pts = resolved
        if (pts != null && pts.size >= 2) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
                val minLng = pts.minOf { it.lng }; val maxLng = pts.maxOf { it.lng }
                val spanLat = (maxLat - minLat).coerceAtLeast(1e-6)
                val spanLng = (maxLng - minLng).coerceAtLeast(1e-6)
                val w = size.width; val h = size.height
                val scale = minOf(w / spanLng.toFloat(), h / spanLat.toFloat())
                val offX = (w - spanLng.toFloat() * scale) / 2f
                val offY = (h - spanLat.toFloat() * scale) / 2f
                fun pos(p: TrackPoint) = Offset(
                    offX + ((p.lng - minLng).toFloat() * scale),
                    h - offY - ((p.lat - minLat).toFloat() * scale)
                )
                val path = Path()
                pts.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(pos(p).x, pos(p).y)
                    else path.lineTo(pos(p).x, pos(p).y)
                }
                drawPath(path, ForestGreen, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // 起终点标记
                drawCircle(TrailOrange, radius = 6f, center = pos(pts.first()))
                drawCircle(TrailOrange, radius = 6f, center = pos(pts.last()))
            }
        } else if (pts != null) {
            Icon(
                Icons.Filled.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
    }
}

@Composable
private fun MetaCell(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onImport: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                Icons.Filled.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("还没有轨迹记录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "点底栏中间的「出发」开始第一次记录；\n也可以导入别人分享的 GPX 文件。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onImport) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入 GPX")
            }
        }
    }
}

private fun monthKey(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(epochMs))

/** 同一天显示"10月18日 07:55 - 09:38"；跨天显示"10月17日 14:33 - 10月18日 04:45"。 */
private fun formatRange(startMs: Long, endMs: Long?): String {
    val fmtD = SimpleDateFormat("M月d日", Locale.getDefault())
    val fmtT = SimpleDateFormat("HH:mm", Locale.getDefault())
    val end = endMs ?: startMs
    val head = "${fmtD.format(Date(startMs))} ${fmtT.format(Date(startMs))}"
    val tail = if (fmtD.format(Date(startMs)) == fmtD.format(Date(end))) {
        fmtT.format(Date(end))
    } else {
        "${fmtD.format(Date(end))} ${fmtT.format(Date(end))}"
    }
    return "$head - $tail"
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

/** 月度小节头用：1:56 或 13:40（不显秒）。 */
private fun formatDurationShort(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}:${"%02d".format(m)}" else "${m}分"
}
