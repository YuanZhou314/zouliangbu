package com.zouliangbu.footprint.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zouliangbu.footprint.weather.Astronomy
import com.zouliangbu.footprint.weather.Landscape

/**
 * 天气页（天气系统第 6 项）：日出日落 / 朝霞 / 晚霞 / 云海 / 观星 / 雷达占位。
 *
 * 设计为纯展示 + 下拉刷新触发 ViewModel.load。景观预报给三档定性，不给百分比。
 */
@Composable
fun WeatherScreen(
    onBack: () -> Unit,
    vm: WeatherViewModel = viewModel(),
) {
    // TODO: 接 cityProvider 取定位坐标；定位未就绪时用北京兜底。
    val lat = 39.90
    val lng = 116.40
    val state by vm.state.collectAsState()
    LaunchedEffect(lat, lng) { vm.load(lat, lng) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("景观天气", style = MaterialTheme.typography.headlineMedium)
        when (val s = state) {
            is WeatherUiState.Loading -> Row(
                Modifier.padding(32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { CircularProgressIndicator(); Spacer(Modifier.width(12.dp)); Text("加载中…") }
            is WeatherUiState.Error -> Text("加载失败：${s.message}", color = MaterialTheme.colorScheme.error)
            is WeatherUiState.Ready -> ReadyContent(s)
        }
    }
}

@Composable
private fun ReadyContent(s: WeatherUiState.Ready) {
    s.nowcastText?.let {
        Card(Modifier.fillMaxWidth()) {
            Text("当前：$it", Modifier.padding(16.dp))
        }
    }
    SunTimesCard(s.sunTimes)
    ForecastCard("朝霞", s.morningGlow)
    ForecastCard("晚霞", s.eveningGlow)
    ForecastCard("云海", s.cloudSea)
    ForecastCard("观星", s.stargazing)
    RadarPlaceholderCard()
}

@Composable
private fun SunTimesCard(st: Astronomy.SunTimes) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("日出日落", style = MaterialTheme.typography.titleMedium)
            Text("日出 ${fmtMin(st.sunrise)}    日落 ${fmtMin(st.sunset)}")
            Text("黄金时刻 ${fmtMin(st.goldenHour)} - ${fmtMin(st.goldenHourEnd)}")
            Text("可观星 ${fmtMin(st.nightStart)} - ${fmtMin(st.nightEnd)}")
        }
    }
}

@Composable
private fun ForecastCard(title: String, f: Landscape.Forecast) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ratingColor(f.rating)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(f.rating.name, style = MaterialTheme.typography.labelLarge)
            }
            Text(f.reason)
            f.bestMinute?.let { Text("最佳：${fmtMin(it)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun RadarPlaceholderCard() {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text("降水雷达", style = MaterialTheme.typography.titleMedium)
        Text("接入中：待服务端对接和风雷达拼图 API", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

private fun ratingColor(r: Landscape.Rating): Color = when (r) {
    Landscape.Rating.POOR  -> Color(0xFFE8E0E0)
    Landscape.Rating.FAIR -> Color(0xFFFFF4E0)
    Landscape.Rating.GOOD -> Color(0xFFE0F0E0)
}

/** 把「距 UTC 零点的分钟数」转 HH:mm。景观预报不依赖本地时区即可读。 */
private fun fmtMin(min: Double?): String {
    if (min == null) return "—"
    val m = ((min % 1440 + 1440) % 1440).toInt()
    return "%02d:%02d".format(m / 60, m % 60)
}