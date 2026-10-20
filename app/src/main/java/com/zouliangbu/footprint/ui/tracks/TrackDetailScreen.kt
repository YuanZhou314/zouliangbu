package com.zouliangbu.footprint.ui.tracks

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.AmapKey
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import com.zouliangbu.footprint.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轨迹详情回看（全屏，无底栏）。
 *
 * 地图复用 [MapViewWrapper] 的 `autoZoom + showEndpoints`：
 * 进来直接缩放到整条轨迹的 bbox，起点/终点各一个 marker。
 * 记录页和详情页共用同一套引擎接口，所以这里一行地图交互代码都不用写。
 *
 * 没有高德 Key 时（osmdroid 兜底分支，按 D1 不画线）会在地图上盖一条说明，
 * 否则用户看到"有地图没线"会以为是数据坏了。
 */
@Composable
fun TrackDetailScreen(
    sessionId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val factory = remember(sessionId) { TrackDetailViewModel.factory(app, sessionId) }
    val vm: TrackDetailViewModel = viewModel(key = "track-detail-$sessionId", factory = factory)

    val ui by vm.state.collectAsStateWithLifecycle()
    val session = ui.session
    val title = session?.name ?: session?.let { formatDate(it.startTime) } ?: "轨迹详情"

    Column(Modifier.fillMaxSize()) {
        // 顶栏：全屏页没有 Scaffold，状态栏 inset 自己加
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    vm.exportGpx { intent ->
                        if (intent != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "这条记录还没有轨迹点，无法导出", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = "导出 GPX")
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (ui.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                MapViewWrapper(
                    modifier = Modifier.fillMaxSize(),
                    track = ui.points,
                    showEndpoints = true,
                    autoZoom = true
                )
                if (ui.points.isNotEmpty() && !AmapKey.isConfigured(context)) {
                    Text(
                        "当前未配置地图 Key，仅显示无底图视图，轨迹线不可见",
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }

        StatsPanel(
            distanceText = session?.let { TimeFmt.distance(it.totalDistanceMeters) } ?: "—",
            durationText = session?.let {
                TimeFmt.elapsed(((it.endTime ?: it.startTime) - it.startTime).coerceAtLeast(0L))
            } ?: "—",
            pointText = "${ui.rawPointCount}",
            speedText = averageSpeedText(session?.totalDistanceMeters ?: 0f, session?.startTime, session?.endTime),
            startedAt = session?.let { formatDate(it.startTime) } ?: "—"
        )
    }
}

@Composable
private fun StatsPanel(
    distanceText: String,
    durationText: String,
    pointText: String,
    speedText: String,
    startedAt: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell(distanceText, "距离")
                StatCell(durationText, "时长")
                StatCell(speedText, "平均速度")
                StatCell(pointText, "轨迹点")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "开始于 $startedAt",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 平均速度 km/h。时长为 0 或异常时给 "—"，不显示 Infinity。 */
private fun averageSpeedText(meters: Float, start: Long?, end: Long?): String {
    if (start == null || end == null || end <= start) return "—"
    val hours = (end - start) / 3_600_000.0
    if (hours <= 0.0 || meters <= 0f) return "—"
    val kmh = (meters / 1000.0) / hours
    return if (kmh.isFinite()) "%.1f km/h".format(kmh) else "—"
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
