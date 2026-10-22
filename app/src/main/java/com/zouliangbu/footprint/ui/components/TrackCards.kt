package com.zouliangbu.footprint.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSummary
import com.zouliangbu.footprint.ui.theme.ForestGreen
import com.zouliangbu.footprint.ui.theme.TrailOrange
import com.zouliangbu.footprint.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轨迹展示组件（原 `ui/tracks/TrackListScreen.kt` 内的 private 实现提到这里），
 * 供「我的记录」与首页时间轴共用，避免两处各写一套卡片。只搬实现、不改行为。
 */

/**
 * 单条记录卡：左侧轨迹缩略图，右侧名称 / 距离 / 时长·速度·爬升 / 估算卡路里。
 * 卡路里为估算值（徒步 MET≈6 × 65kg × 小时数），App 没有体重数据，只能粗估。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackCard(
    track: TrackSummary,
    thumbPoints: suspend () -> List<TrackPoint>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val durationMs = track.activeDurationMs
    val hours = durationMs / 3_600_000.0
    val avgSpeedKmh = if (hours > 0) track.totalDistanceMeters / 1000f / hours else 0f
    val kcal = (6.0 * 65.0 * hours).toInt()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(12.dp),
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
fun TrackThumb(points: suspend () -> List<TrackPoint>, sizeDp: Int = 88) {
    // produceState：进入组合才查库，离开组合协程取消；调用方通常带缓存，来回滚动不重复 IO。
    val resolved by produceState<List<TrackPoint>?>(initialValue = null, points) {
        value = points()
    }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
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
                drawPath(
                    path,
                    ForestGreen,
                    style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
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

/** 单项小指标：图标 + 文本。 */
@Composable
fun MetaCell(icon: ImageVector, text: String) {
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

/** 月度小节头：2026年10月  12.40公里  ↑620米  3:20  3次 */
@Composable
fun MonthHeader(
    month: String,
    meters: Float,
    ascent: Double,
    durationMs: Long,
    count: Int,
    modifier: Modifier = Modifier
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
        modifier = modifier.padding(top = 4.dp, start = 2.dp)
    )
}

/** 「总运动累积记录」统计卡（三宫格：次数 / 耗时 / 里程）。 */
@Composable
fun TrackSummaryCard(
    count: Int,
    totalMs: Long,
    totalMeters: Float,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ForestGreen.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("总运动累积记录", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryCell("$count", "累计次数")
                SummaryCell("%.1f 小时".format(totalMs / 3_600_000.0), "累计耗时")
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

/** "2026-10" 形式的月份键。 */
fun monthKey(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(epochMs))

/** 同一天显示"10月18日 07:55 - 09:38"；跨天显示"10月17日 14:33 - 10月18日 04:45"。 */
fun formatRange(startMs: Long, endMs: Long?): String {
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

/** 1:56 或 13:40（不显秒）。 */
fun formatDurationShort(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}:${"%02d".format(m)}" else "${m}分"
}
