package com.zouliangbu.footprint.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.weather.HourlyPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// ===== 逐小时多维表（行 = 指标，列 = 小时） =====

private val TABLE_DATE_H = 30.dp
private val TABLE_ROW_H = 34.dp
private val TABLE_CELL_W = 48.dp
private val TABLE_LABEL_W = 92.dp

/**
 * 逐小时多维表（莉景天气风格改版）：行 = 指标，列 = 小时，横向滑动看未来 36h。
 *
 * 布局：左侧指标列固定（图标 + 指标名 + 单位），右侧数据区每行共享同一个横向
 * ScrollState 同步滚动；所有行固定行高，左右严格对齐。当前小时整列淡色高亮，
 * 小时格再叠一层胶囊强调。图标全部来自 material-icons-extended（已在依赖里），
 * 风向箭头用 Navigation 图标按角度旋转，无新增依赖。
 */
@Composable
internal fun HourlyTableBlock(s: WeatherUiState.Ready) {
    val rows = s.hourly
    if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val zone = remember { ZoneId.systemDefault() }
    val localDates = remember(rows) {
        rows.map { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timeMs), zone).toLocalDate() }
    }
    val hoursOfDay = remember(rows) {
        rows.map { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timeMs), zone).hour }
    }
    val dayBreaks = remember(localDates) { dayBreakIndices(localDates) }
    // 当前小时列 = 与「现在」同属一个整点的列；找不到（跨小时边界竞态）取第 0 列兜底
    val currentIdx = remember(rows) {
        val hourStart = System.currentTimeMillis() / 3_600_000L * 3_600_000L
        rows.indexOfFirst { it.timeMs == hourStart }.coerceAtLeast(0)
    }
    val hScroll = rememberScrollState()

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("逐小时多维表", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    "未来 ${rows.size} 小时",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Row {
                LabelColumn()
                // 每行各自 horizontalScroll 但共享同一 ScrollState：滑动任一行整表同步
                Column(Modifier.horizontalScroll(hScroll)) {
                    DateRow(localDates, dayBreaks, rows.size)
                    MetricRow(rows.size, currentIdx) { i -> HourCell(hoursOfDay[i], i == currentIdx) }
                    MetricRow(rows.size, currentIdx) { i ->
                        WeatherIconCell(rows[i].weatherCode, hoursOfDay[i])
                    }
                    MetricRow(rows.size, currentIdx) { i ->
                        ValueCell("%.0f".format(rows[i].elements.temperatureC), cs.onSurface)
                    }
                    MetricRow(rows.size, currentIdx) { i ->
                        ValueCell("%.0f".format(rows[i].elements.relativeHumidity), cs.onSurface)
                    }
                    MetricRow(rows.size, currentIdx) { i -> PrecipCell(rows[i]) }
                    MetricRow(rows.size, currentIdx) { i ->
                        ValueCell("%.1f".format(rows[i].elements.windSpeedMs), cs.onSurface)
                    }
                    MetricRow(rows.size, currentIdx) { i -> WindDirCell(rows[i]) }
                    MetricRow(rows.size, currentIdx) { i -> AqiCell(rows[i]) }
                    MetricRow(rows.size, currentIdx) { i ->
                        val km = rows[i].elements.visibilityMeters / 1000.0
                        ValueCell(
                            if (km >= 10.0) "%.0f".format(km) else "%.1f".format(km),
                            cs.onSurface,
                        )
                    }
                    MetricRow(rows.size, currentIdx) { i -> CloudCoverCell(rows[i]) }
                }
            }
            Text(
                "左列指标固定，横向滑动查看；风向箭头指向风吹去的方向",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** 表格一行：[cellCount] 个等宽单元格 + 行底细分隔线；当前小时列铺淡色高亮条。 */
@Composable
private fun MetricRow(
    cellCount: Int,
    currentIdx: Int,
    rowHeight: Dp = TABLE_ROW_H,
    cell: @Composable (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(TABLE_CELL_W * cellCount)) {
        Row(Modifier.height(rowHeight)) {
            repeat(cellCount) { i ->
                Box(
                    Modifier
                        .width(TABLE_CELL_W)
                        .fillMaxHeight()
                        .background(
                            if (i == currentIdx) cs.primary.copy(alpha = 0.10f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center,
                ) { cell(i) }
            }
        }
        LabelDivider()
    }
}

/** 左侧固定指标列：与右侧数据区逐行同高（日期行空占位 + 每行指标标签 + 分隔线）。 */
@Composable
private fun LabelColumn() {
    Column(Modifier.width(TABLE_LABEL_W)) {
        Spacer(Modifier.height(TABLE_DATE_H))
        LabelDivider()
        LabelRow(Icons.Filled.Schedule, "小时", "")
        LabelDivider()
        LabelRow(Icons.Filled.WbCloudy, "天气", "")
        LabelDivider()
        LabelRow(Icons.Filled.Thermostat, "温度", "℃")
        LabelDivider()
        LabelRow(Icons.Filled.WaterDrop, "湿度", "%")
        LabelDivider()
        LabelRow(Icons.Filled.Umbrella, "雨雪", "mm")
        LabelDivider()
        LabelRow(Icons.Filled.Speed, "风速", "m/s")
        LabelDivider()
        LabelRow(Icons.Filled.Navigation, "风向", "")
        LabelDivider()
        LabelRow(Icons.Filled.Air, "空气质量", "")
        LabelDivider()
        LabelRow(Icons.Filled.Visibility, "能见度", "km")
        LabelDivider()
        LabelRow(Icons.Filled.Cloud, "云量", "低/中/高")
        LabelDivider()
    }
}

@Composable
private fun LabelRow(icon: ImageVector, name: String, unit: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(TABLE_ROW_H)
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = cs.onSurface, maxLines = 1)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(3.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LabelDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** 日期行：同一天的小时合并为一格（如「10-23 周五」），跨天底色交替区分。 */
@Composable
private fun DateRow(dates: List<LocalDate>, breaks: List<Int>, cellCount: Int) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(TABLE_CELL_W * cellCount)) {
        Row(Modifier.height(TABLE_DATE_H)) {
            breaks.forEachIndexed { bi, start ->
                val end = breaks.getOrNull(bi + 1) ?: dates.size
                Box(
                    Modifier
                        .width(TABLE_CELL_W * (end - start))
                        .fillMaxHeight()
                        .background(if (bi % 2 == 1) cs.surfaceVariant.copy(alpha = 0.30f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dateSegmentLabel(dates[start]),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
        LabelDivider()
    }
}

/** 小时行：当前小时胶囊高亮加粗。 */
@Composable
private fun HourCell(hour: Int, isCurrent: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isCurrent) cs.primary.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            "%02d".format(hour),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) cs.primary else cs.onSurface,
        )
    }
}

@Composable
private fun WeatherIconCell(code: Int?, hourOfDay: Int) {
    Icon(
        hourlyIcon(code, isNightHour(hourOfDay)), null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
    )
}

/** 雨雪格：0.0 灰显；有雨用主题蓝、有雪用前景白强调。 */
@Composable
private fun PrecipCell(p: HourlyPoint) {
    val cs = MaterialTheme.colorScheme
    val mm = p.elements.precipitationMm
    if (mm > 0.0) {
        ValueCell("%.1f".format(mm), if (isSnowCode(p.weatherCode)) cs.onSurface else cs.primary, bold = true)
    } else {
        ValueCell("0.0", cs.onSurfaceVariant.copy(alpha = 0.55f))
    }
}

/** 风向格：箭头按风向角旋转（气象角=风来的方向，+180° 后指向风吹去的方向）；无数据显示 —。 */
@Composable
private fun WindDirCell(p: HourlyPoint) {
    val cs = MaterialTheme.colorScheme
    val deg = p.windDirectionDeg
    if (deg == null) {
        ValueCell("—", cs.onSurfaceVariant)
    } else {
        Icon(
            Icons.Filled.Navigation, "风向",
            Modifier
                .size(16.dp)
                .rotate(((deg + 180.0) % 360.0).toFloat()),
            tint = cs.primary,
        )
    }
}

/** 空气质量格：数值按国标六档着色，无数据显示 —。 */
@Composable
private fun AqiCell(p: HourlyPoint) {
    val cs = MaterialTheme.colorScheme
    val grade = aqiGrade(p.usAqi, p.pm25)
    if (grade == AQI_GRADE_MISSING) {
        ValueCell("—", cs.onSurfaceVariant)
    } else {
        ValueCell("%.0f".format(p.usAqi ?: p.pm25 ?: 0.0), aqiGradeColor(grade), bold = true)
    }
}

/** 云量格：低/中/高三值合一格（如 95/20/5）。 */
@Composable
private fun CloudCoverCell(p: HourlyPoint) {
    val e = p.elements
    ValueCell(
        "${"%.0f".format(e.cloudLow)}/${"%.0f".format(e.cloudMid)}/${"%.0f".format(e.cloudHigh)}",
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ValueCell(text: String, color: Color, bold: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
        maxLines = 1,
    )
}

private fun dateSegmentLabel(d: LocalDate): String =
    "%02d-%02d 周%s".format(d.monthValue, d.dayOfMonth, "日一二三四五六"[d.dayOfWeek.value % 7])

/** WMO weather_code → 昼/夜图标：晴与少云夜间换月亮系，雾归阴云，雨雪雷全天同款。 */
private fun hourlyIcon(code: Int?, night: Boolean): ImageVector = when {
    code == null -> Icons.Filled.Cloud
    code == 0 -> if (night) Icons.Filled.DarkMode else Icons.Filled.WbSunny
    code in 1..2 -> if (night) Icons.Filled.NightsStay else Icons.Filled.WbCloudy
    isSnowCode(code) -> Icons.Filled.AcUnit
    code in 45..48 -> Icons.Filled.Cloud
    code in 51..67 || code in 80..82 -> Icons.Filled.WaterDrop
    code in 95..99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

/** 国标 AQI 六档文字色（优绿/良黄/轻度橙/中度红/重度紫/严重绛），取深色系保证浅色主题可读。 */
private fun aqiGradeColor(g: Int): Color = when (g) {
    0 -> Color(0xFF2E7D32)
    1 -> Color(0xFF9A7D0A)
    2 -> Color(0xFFE65100)
    3 -> Color(0xFFC62828)
    4 -> Color(0xFF6A1B9A)
    else -> Color(0xFF880E4F)
}
