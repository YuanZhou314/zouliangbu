package com.zouliangbu.footprint.ui.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.util.SegmentMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 「与上次对比」卡片：同一条线路两次记录在**共同路段**上的差异。
 *
 * 为什么只比共同路段：两次不一定走完全一样的路（绕路、提前下撤），
 * 直接拿总距离/总时长比会得出"这次快 40 分钟"这种错误结论。
 *
 * 大于 5 秒的差异才显示倾向（快/慢），否则只说"基本持平"——
 * GPS 抖动造成的几秒差异不该被说成"进步"。
 */
@Composable
fun TrackCompareCard(
    compare: CompareUi,
    modifier: Modifier = Modifier
) {
    if (!compare.checked) return

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(14.dp)
    ) {
        val cmp = compare.comparison
        if (cmp == null) {
            Text(
                "与上次对比",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                compare.note ?: "暂无可比记录",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text(
            "与上次对比 · " + formatDay(compare.other?.startTime),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "共同路段 %.2f 公里".format(cmp.overlap.meters / 1000.0),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        CompareRow(
            label = "时长",
            current = fmtDuration(cmp.aDurationMs),
            delta = deltaText(cmp.aDurationMs, cmp.bDurationMs, fasterIsBetter = true)
        )
        Spacer(Modifier.height(6.dp))
        CompareRow(
            label = "爬升",
            current = "%.0f 米".format(cmp.aAscentMeters),
            delta = ascentDeltaText(cmp.ascentDeltaMeters)
        )
        Spacer(Modifier.height(6.dp))
        CompareRow(
            label = "均速",
            current = cmp.aAvgSpeedKmh?.let { "%.1f km/h".format(it) } ?: "—",
            delta = speedDeltaText(cmp.aAvgSpeedKmh, cmp.bAvgSpeedKmh)
        )
    }
}

@Composable
private fun CompareRow(label: String, current: String, delta: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            current,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            delta,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun fmtDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return "—"
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h${m}m" else "${m} 分钟"
}

/** 当前 vs 上次的时长差（负 = 这次更快）。 */
private fun deltaText(current: Long?, previous: Long?, fasterIsBetter: Boolean): String {
    if (current == null || previous == null || current <= 0 || previous <= 0) return ""
    val diff = current - previous
    if (abs(diff) < 5_000) return "基本持平"
    val mins = abs(diff) / 60_000
    val secs = (abs(diff) % 60_000) / 1000
    val span = if (mins > 0) "${mins} 分 ${secs} 秒" else "${secs} 秒"
    val faster = diff < 0
    val good = if (fasterIsBetter) faster else !faster
    return (if (good) "快 " else "慢 ") + span
}

/** 爬升差：b - a（正 = 本次爬得更多）。 */
private fun ascentDeltaText(delta: Double): String {
    if (abs(delta) < 5.0) return "基本持平"
    return if (delta > 0) "+%.0f 米".format(delta) else "%.0f 米".format(delta)
}

private fun speedDeltaText(current: Double?, previous: Double?): String {
    if (current == null || previous == null) return ""
    val diff = current - previous
    if (abs(diff) < 0.2) return "基本持平"
    return "%+.1f km/h".format(diff)
}

private fun formatDay(epochMs: Long?): String {
    if (epochMs == null) return "上次"
    return SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(epochMs))
}
