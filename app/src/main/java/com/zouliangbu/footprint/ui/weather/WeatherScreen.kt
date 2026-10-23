package com.zouliangbu.footprint.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.location.AMapLocation
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.weather.Astronomy
import com.zouliangbu.footprint.weather.Landscape
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** 定位还没就绪时的兜底坐标（深圳）：天气页首屏不能停在空态。 */
private const val FALLBACK_LAT = 22.54
private const val FALLBACK_LNG = 114.06

/**
 * 天气页（天气系统第 6 项）：实况 → 日出日落 → 朝霞/晚霞/云海/观星 → 雷达占位。
 *
 * 设计为纯展示 + 下拉滚动；坐标来自 FootprintApp 的共享定位流，定位变化即重新 load。
 * 景观预报给三档定性，不给百分比。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onBack: () -> Unit,
    vm: WeatherViewModel = viewModel(),
) {
    val app = LocalContext.current.applicationContext as FootprintApp
    // 订阅共享定位流而不是只读 lastLocation：后者是普通字段，Compose 观察不到变化。
    val fix by produceState<AMapLocation?>(initialValue = app.locationProvider.lastLocation) {
        app.locationProvider.location.collect { value = it }
    }
    val located = fix != null && fix!!.latitude != 0.0 && fix!!.longitude != 0.0
    val lat = if (located) fix!!.latitude else FALLBACK_LAT
    val lng = if (located) fix!!.longitude else FALLBACK_LNG

    val state by vm.state.collectAsState()
    LaunchedEffect(lat, lng) { vm.load(lat, lng) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("景观天气", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val s = state) {
                WeatherUiState.Loading -> LoadingBlock()
                is WeatherUiState.Error -> ErrorBlock(s.message) { vm.load(lat, lng) }
                is WeatherUiState.Ready -> {
                    HeaderCard(s)
                    SunCard(s)
                    LandscapeGrid(s)
                    DailyForecastBlock(s)
                    HourlyTableBlock(s)
                    RadarPlaceholderCard()
                    Text(
                        "日出日落与景观评级由本地天文算法推算，云海为温湿风近似估算，天气数据来自 Open-Meteo，仅供参考。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ===== 顶部实况 =====

@Composable
private fun HeaderCard(s: WeatherUiState.Ready) {
    val cs = MaterialTheme.colorScheme
    val on = cs.onSurface
    val nowMin = minuteOfUtcDay(s.updatedAtMs)
    val isNight = !dayProgress(s.sunTimes, nowMin).first
    val text = s.now?.text

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(headerColors(text, isNight))),
    ) {
        // 大号天气图标只作装饰：低透明度铺在右上角，不参与信息层级。
        Icon(
            weatherIcon(text), null,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .size(104.dp),
            tint = on.copy(alpha = 0.14f),
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Place, null,
                    tint = on.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    s.locationName ?: "当前位置",
                    style = MaterialTheme.typography.titleMedium,
                    color = on,
                )
                if (s.usingCache) {
                    Spacer(Modifier.width(8.dp))
                    SmallTag("离线数据")
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    s.currentTempC?.let { "%.0f°".format(it) } ?: "—°",
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = on,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text ?: "暂无实况",
                    style = MaterialTheme.typography.titleMedium,
                    color = on.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(bottom = 12.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaChip(Icons.Filled.WaterDrop, s.currentHumidity?.let { "湿度 %.0f%%".format(it) } ?: "湿度 —")
                MetaChip(Icons.Filled.Air, windText(s))
            }
            Text(
                "更新于 ${fmtClock(s.updatedAtMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/**
 * 头部渐变配色：全部取自 [MaterialTheme.colorScheme] 的容器色，深浅色模式自动适配。
 * 夜间用低饱和的 surfaceVariant→secondaryContainer，白天按天气现象换容器色对。
 */
@Composable
private fun headerColors(weatherText: String?, isNight: Boolean): List<Color> {
    val cs = MaterialTheme.colorScheme
    val t = weatherText.orEmpty()
    return when {
        isNight -> listOf(cs.surfaceVariant, cs.secondaryContainer)
        t.contains("雨") || t.contains("雷") -> listOf(cs.surfaceVariant, cs.primaryContainer)
        t.contains("雪") || t.contains("雾") || t.contains("霾") || t.contains("尘") ->
            listOf(cs.surfaceVariant, cs.secondaryContainer)
        t.contains("阴") || t.contains("云") -> listOf(cs.secondaryContainer, cs.surfaceVariant)
        else -> listOf(cs.primaryContainer, cs.tertiaryContainer)
    }
}

private fun weatherIcon(text: String?): ImageVector {
    val t = text.orEmpty()
    return when {
        t.contains("雪") -> Icons.Filled.AcUnit
        t.contains("雨") || t.contains("雷") -> Icons.Filled.Thunderstorm
        t.contains("阴") || t.contains("云") || t.contains("雾") || t.contains("霾") -> Icons.Filled.Cloud
        else -> Icons.Filled.WbSunny
    }
}

private fun windText(s: WeatherUiState.Ready): String {
    val speed = s.currentWindMs?.let { "%.1f m/s".format(it) } ?: "— m/s"
    val dir = s.now?.windDir
    return if (dir.isNullOrBlank()) "风 $speed" else "$dir $speed"
}

@Composable
private fun MetaChip(icon: ImageVector, text: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(cs.surface.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = cs.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = cs.onSurface)
        }
    }
}

@Composable
private fun SmallTag(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(cs.surface.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

// ===== 日出日落 =====

@Composable
private fun SunCard(s: WeatherUiState.Ready) {
    val cs = MaterialTheme.colorScheme
    val st = s.sunTimes
    val nowMin = minuteOfUtcDay(s.updatedAtMs)
    val (isDay, progress) = dayProgress(st, nowMin)

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WbSunny, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("日出日落", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isDay) "白天" else "夜间",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            SunArc(isDay, progress)
            Row(Modifier.fillMaxWidth()) {
                ArcLabel("日出", fmtMin(st.sunrise, s.updatedAtMs), TextAlign.Start, Modifier.weight(1f))
                ArcLabel("现在", fmtClock(s.updatedAtMs), TextAlign.Center, Modifier.weight(1f))
                ArcLabel("日落", fmtMin(st.sunset, s.updatedAtMs), TextAlign.End, Modifier.weight(1f))
            }
            HorizontalDivider(color = cs.outlineVariant)
            InfoRow("日出", fmtMin(st.sunrise, s.updatedAtMs))
            InfoRow("日落", fmtMin(st.sunset, s.updatedAtMs))
            InfoRow(
                "黄金时刻",
                "${fmtMin(st.goldenHour, s.updatedAtMs)} – ${fmtMin(st.goldenHourEnd, s.updatedAtMs)}",
            )
            InfoRow("观星时段", stargazeRangeText(st, s.updatedAtMs))
        }
    }
}

/**
 * 一天的日照弧线：Canvas 上半椭圆表示白昼，横线是地平线，圆点是「现在」的太阳位置。
 * 夜间没有弧线可画，把圆点放到地平线下方并沿反方向移动，读起来就是「太阳落下了」。
 */
@Composable
private fun SunArc(isDay: Boolean, progress: Double) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(132.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = 26.dp.toPx()
            val horizonY = size.height * 0.70f
            val riseHeight = horizonY - 16.dp.toPx()
            val left = padX
            val right = size.width - padX
            val rx = (right - left) / 2f
            val cx = left + rx
            val top = horizonY - riseHeight
            val arcSize = Size(right - left, riseHeight * 2f)

            // 白昼区域：半椭圆填充，给「一天」一个可读的体量
            drawArc(
                color = cs.primaryContainer,
                startAngle = 180f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(left, top), size = arcSize,
            )
            drawArc(
                color = cs.primary,
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(left, top), size = arcSize,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawLine(
                cs.outlineVariant,
                Offset(0f, horizonY), Offset(size.width, horizonY),
                strokeWidth = 1.5.dp.toPx(),
            )
            // 日出 / 日落端点
            drawCircle(cs.primary, 4.5.dp.toPx(), Offset(left, horizonY))
            drawCircle(cs.primary, 4.5.dp.toPx(), Offset(right, horizonY))

            val sunX: Float
            val sunY: Float
            if (isDay) {
                val angle = Math.toRadians(180.0 + 180.0 * progress)
                sunX = cx + rx * cos(angle).toFloat()
                sunY = horizonY + riseHeight * sin(angle).toFloat()
            } else {
                sunX = cx + rx - 2f * rx * progress.toFloat()
                sunY = horizonY + 13.dp.toPx()
            }
            // 先铺一圈底色再画实心点：无论压在弧线还是地平线上都能看清
            drawCircle(cs.surface, 9.5.dp.toPx(), Offset(sunX, sunY))
            drawCircle(if (isDay) cs.primary else cs.outline, 6.5.dp.toPx(), Offset(sunX, sunY))
        }
    }
}

@Composable
private fun ArcLabel(label: String, value: String, align: TextAlign, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier,
        horizontalAlignment = if (align == TextAlign.Start) {
            Alignment.Start
        } else if (align == TextAlign.Center) {
            Alignment.CenterHorizontally
        } else {
            Alignment.End
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
    }
}

// ===== 景观五卡（朝霞/晚霞/蓝调/云海/观星） =====

@Composable
private fun LandscapeGrid(s: WeatherUiState.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ForecastCell("朝霞", Icons.Filled.WbTwilight, s.morningGlow, s.updatedAtMs, Modifier.weight(1f))
            ForecastCell("晚霞", Icons.Filled.Flare, s.eveningGlow, s.updatedAtMs, Modifier.weight(1f))
            BlueHourCell(s.blueHour, s.updatedAtMs, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ForecastCell("云海", Icons.Filled.Cloud, s.cloudSea, s.updatedAtMs, Modifier.weight(1f))
            ForecastCell("观星", Icons.Filled.NightsStay, s.stargazing, s.updatedAtMs, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ForecastCell(
    title: String,
    icon: ImageVector,
    f: Landscape.Forecast,
    atMs: Long,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val (container, onContainer) = ratingColors(f.rating)
    ElevatedCard(
        // 定高保证两行五卡严格对齐；理由文案超长靠 maxLines + 省略号收住。
        modifier = modifier.height(196.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = onContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            RatingChip(f.rating)
            // 分档可视化：5 段刻度条按等级点亮，一眼可辨 差→优
            RatingMeter(f.rating)
            Text(
                f.reason,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule, null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "最佳 ${fmtMin(f.bestMinute, atMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 5 段分档指示条：较差点亮 1 段、一般 3 段、较好 5 段。
 * 段颜色取「差→优」色阶（POOR/FAIR/GOOD 容器色按序铺开），
 * 未点亮段用同色低透明度底，整体比纯文字等级更直观。
 */
@Composable
private fun RatingMeter(r: Landscape.Rating) {
    val lit = when (r) {
        Landscape.Rating.POOR -> 1
        Landscape.Rating.FAIR -> 3
        Landscape.Rating.GOOD -> 5
    }
    // 色阶：前 2 段偏「差」色，中间 2 段偏「一般」色，末段「优」色（全部走主题容器色）
    val scale = listOf(
        ratingColors(Landscape.Rating.POOR).first,
        ratingColors(Landscape.Rating.POOR).first,
        ratingColors(Landscape.Rating.FAIR).first,
        ratingColors(Landscape.Rating.FAIR).first,
        ratingColors(Landscape.Rating.GOOD).first,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(5) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i < lit) scale[i] else scale[i].copy(alpha = 0.18f)),
            )
        }
    }
}

/**
 * 蓝调时刻卡：与 ForecastCell 同壳（定高对齐），内容是晨 / 暮两段深蓝时段。
 * 蓝调是纯天文窗口，没有评级，展示时间区间即价值所在。
 */
@Composable
private fun BlueHourCell(
    b: Landscape.BlueHour,
    atMs: Long,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val container = cs.secondaryContainer
    val onContainer = cs.onSecondaryContainer
    ElevatedCard(
        modifier = modifier.height(196.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DarkMode, null, tint = onContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "蓝调时刻",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "太阳 -6°~-4° 的深蓝时段",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            BlueHourRow("晨", fmtRange(b.morningStart, b.morningEnd, atMs))
            BlueHourRow("暮", fmtRange(b.eveningStart, b.eveningEnd, atMs))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule, null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "适合夜景与剪影",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BlueHourRow(label: String, range: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Text(
            range,
            style = MaterialTheme.typography.titleSmall,
            color = cs.onSurface,
        )
    }
}

@Composable
private fun RatingChip(r: Landscape.Rating) {
    val (container, onContainer) = ratingColors(r)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(ratingLabel(r), style = MaterialTheme.typography.labelSmall, color = onContainer)
    }
}

/** 评级配色一律走语义容器色：较差=中性、一般=tertiary、较好=primary。 */
@Composable
private fun ratingColors(r: Landscape.Rating): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (r) {
        Landscape.Rating.POOR -> cs.surfaceVariant to cs.onSurfaceVariant
        Landscape.Rating.FAIR -> cs.tertiaryContainer to cs.onTertiaryContainer
        Landscape.Rating.GOOD -> cs.primaryContainer to cs.onPrimaryContainer
    }
}

private fun ratingLabel(r: Landscape.Rating): String = when (r) {
    Landscape.Rating.POOR -> "较差"
    Landscape.Rating.FAIR -> "一般"
    Landscape.Rating.GOOD -> "较好"
}

// ===== 未来 3 天 =====

@Composable
private fun DailyForecastBlock(s: WeatherUiState.Ready) {
    // 旧缓存 / 服务端失败时没有 daily 块：整块隐藏，不留空壳
    if (s.daily.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("未来 3 天", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            }
            s.daily.forEachIndexed { i, d ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(60.dp)) {
                        Text(
                            dailyDayLabel(i),
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurface,
                        )
                        Text(
                            dailyDateLabel(d.dateMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Icon(
                        hourlyWeatherIcon(d.weatherCode), null,
                        tint = cs.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${d.tempMinC?.let { "%.0f°".format(it) } ?: "—"} / ${d.tempMaxC?.let { "%.0f°".format(it) } ?: "—"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        d.precipProbMax?.let { "降水 %.0f%%".format(it) } ?: "降水 —",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun dailyDayLabel(i: Int): String =
    listOf("明天", "后天", "大后天").getOrElse(i) { "未来" }

private fun dailyDateLabel(dateMs: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateMs), ZoneId.systemDefault())
    return "${t.monthValue}/${t.dayOfMonth}"
}

// ===== 逐小时多维表 =====
// 表本体已拆到 HourlyTableBlock.kt（行 = 指标、列 = 小时的改版）；此处只保留与
// 逐日预报共用的 weather_code → 图标映射。

/** WMO weather_code → 图标（逐小时表 / 逐日预报共用）。 */
private fun hourlyWeatherIcon(code: Int?): ImageVector = when (code) {
    0 -> Icons.Filled.WbSunny
    1, 2 -> Icons.Filled.WbCloudy
    in 51..67, in 80..82 -> Icons.Filled.WaterDrop
    in 71..77, 85, 86 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

private fun fmtRange(startMin: Double?, endMin: Double?, atMs: Long): String =
    if (startMin == null || endMin == null) "—"
    else "${fmtMin(startMin, atMs)} – ${fmtMin(endMin, atMs)}"

// ===== 雷达占位 / 加载 / 失败 =====

@Composable
private fun RadarPlaceholderCard() {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Radar, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("降水雷达", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "接入中 · 待对接和风雷达拼图",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "正在获取天气与天文数据…",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.CloudOff, null, tint = cs.error, modifier = Modifier.size(40.dp))
            Text("天气数据暂不可用", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Text(message, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重试")
            }
        }
    }
}

// ===== 时间换算 =====

/**
 * 当前时刻在「当天 UTC 零点起的分钟数」坐标系里的位置。
 * [Astronomy.SunTimes] 用的就是这一基准，必须同基准才能算出太阳在弧线上的位置。
 */
private fun minuteOfUtcDay(ms: Long): Double = (ms % 86_400_000L) / 60_000.0

private fun utcDayStart(ms: Long): Long = Math.floorDiv(ms, 86_400_000L) * 86_400_000L

/**
 * 日出→日落的进度。
 *
 * 日出/日落都是归一化到 [0,1440) 的 UTC 分钟，中国时区下日落的数字反而比日出小
 * （跨了 UTC 零点），所以白天时长要按环形差取模算，不能直接用 `sunset - sunrise`。
 *
 * @return (是否白天, 白天内进度 0..1；夜间返回夜内进度 0..1)
 */
private fun dayProgress(st: Astronomy.SunTimes, nowMin: Double): Pair<Boolean, Double> {
    val sr = st.sunrise ?: return false to 0.0
    val ss = st.sunset ?: return false to 0.0
    val dayLen = ((ss - sr) % 1440.0 + 1440.0) % 1440.0
    if (dayLen <= 0.0) return false to 0.0
    val elapsed = ((nowMin - sr) % 1440.0 + 1440.0) % 1440.0
    return if (elapsed <= dayLen) {
        true to (elapsed / dayLen)
    } else {
        false to ((elapsed - dayLen) / (1440.0 - dayLen))
    }
}

/**
 * 把「距当天 UTC 零点的分钟数」换算成**本地时区**的日内分钟。
 * 不换算的话中国用户看到的日出会是 UTC 时刻（例如把 06:46 显示成 22:46）。
 */
private fun localMinute(min: Double, atMs: Long): Int {
    val utcMs = utcDayStart(atMs) + Math.round(min * 60_000.0)
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(utcMs), ZoneId.systemDefault())
    return t.hour * 60 + t.minute
}

private fun fmtMinuteOfDay(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** 把「距当天 UTC 零点的分钟数」按本地时区格式化成 HH:mm。 */
private fun fmtMin(min: Double?, atMs: Long): String =
    min?.let { fmtMinuteOfDay(localMinute(it, atMs)) } ?: "—"

private fun fmtClock(ms: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    return fmtMinuteOfDay(t.hour * 60 + t.minute)
}

/** 观星时段：天文暮光始 → 天文暮光终，后者常落在次日凌晨，故补「次日」。 */
private fun stargazeRangeText(st: Astronomy.SunTimes, atMs: Long): String {
    val from = st.nightStart ?: return "—"
    val to = st.nightEnd ?: return "—"
    val a = localMinute(from, atMs)
    val b = localMinute(to, atMs)
    return "${fmtMinuteOfDay(a)} – ${if (b <= a) "次日 " else ""}${fmtMinuteOfDay(b)}"
}
