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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.zouliangbu.footprint.share.Share
import com.zouliangbu.footprint.share.ShareCardRenderer
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import com.zouliangbu.footprint.util.Pace
import com.zouliangbu.footprint.util.TimeFmt
import com.zouliangbu.footprint.util.TrackSplits
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

    var showCompare by remember { mutableStateOf(false) }
    var showContribute by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var contributeError by remember { mutableStateOf<String?>(null) }

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
            // P5.4：分享行程卡（拼一张图分享出去）。
            IconButton(
                onClick = {
                    if (ui.session == null || ui.points.isEmpty()) {
                        Toast.makeText(context, "这条记录还没有轨迹点，无法分享", Toast.LENGTH_SHORT).show()
                        return@IconButton
                    }
                    val s = ui.session
                    if (s == null) {
                        Toast.makeText(context, "记录未加载", Toast.LENGTH_SHORT).show()
                        return@IconButton
                    }
                    runCatching {
                        val card = ShareCardRenderer.render(
                            context = context,
                            name = s.name,
                            startTimeMs = s.startTime,
                            distanceMeters = s.totalDistanceMeters.toDouble(),
                            activeDurationMs = s.activeDurationMs,
                            ascentMeters = ui.ascentMeters,
                            trackPoints = ui.points
                        )
                        Share.shareImage(context, card, "我的行程卡")
                    }.onFailure {
                        Toast.makeText(context, "生成行程卡失败：" + (it.message ?: "未知错误"), Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = "分享行程卡")
            }
            // 原有：导出 GPX。
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
            // 活动时长（扣暂停）；暂停信息单独显示，避免用户以为时长算错了。
            durationText = session?.let { TimeFmt.elapsed(it.activeDurationMs) } ?: "—",
            pointText = "${ui.rawPointCount}",
            speedText = averageSpeedText(
                session?.totalDistanceMeters ?: 0f,
                session?.activeDurationMs ?: 0L
            ),
            startedAt = session?.let { formatDate(it.startTime) } ?: "—",
            pausedText = session?.pausedMs
                ?.takeIf { it > 0L }
                ?.let { "含暂停 ${TimeFmt.elapsed(it)}" },
            ascentText = ascentText(ui.ascentMeters, ui.descentMeters),
            paceText = paceText(ui.paceStats),
            splits = ui.splits,
            onCompare = { showCompare = true },
            onContribute = { showContribute = true }
        )
    }

    // ===== 与上次对比（共同路段）=====
    if (showCompare) {
        AlertDialog(
            onDismissRequest = { showCompare = false },
            title = { Text("与上次对比", fontSize = 17.sp) },
            text = {
                // 还没算完时给一句说明，不要弹个空白对话框
                if (!ui.compare.checked) {
                    Text("正在比对历史记录…", fontSize = 13.sp)
                } else {
                    TrackCompareCard(compare = ui.compare, modifier = Modifier.padding(0.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompare = false }) { Text("知道了") }
            }
        )
    }

    // ===== 贡献到线路库 =====
    if (showContribute) {
        ContributeTrackDialog(
            defaultName = session?.name ?: (session?.let { formatDate(it.startTime) } ?: ""),
            submitting = submitting,
            error = contributeError,
            onDismiss = {
                showContribute = false
                contributeError = null
            },
            onSubmit = { name, region, intro, nickname ->
                if (ui.rawPointCount < 10) {
                    contributeError = "这条轨迹只有 ${ui.rawPointCount} 个点，至少需要 10 个点"
                } else {
                    submitting = true
                    contributeError = null
                    vm.contribute(name, region, intro, nickname) { result ->
                        submitting = false
                        result.onSuccess {
                            showContribute = false
                            Toast.makeText(context, "已提交，等管理员确认后上线", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            contributeError = it.message ?: "提交失败，请稍后再试"
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun StatsPanel(
    distanceText: String,
    durationText: String,
    pointText: String,
    speedText: String,
    startedAt: String,
    pausedText: String?,
    ascentText: String,
    paceText: String,
    splits: List<TrackSplits.Split>,
    onCompare: () -> Unit,
    onContribute: () -> Unit
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
            Spacer(Modifier.height(10.dp))
            // 第二排：爬升/下降 + 配速（ROADMAP P3.1 / P3.2）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell(ascentText, "累计爬升")
                StatCell(paceText, "平均配速")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                // 暂停过的轨迹在「时长」里已扣掉暂停，不说明一句会让人以为时长算错了。
                if (pausedText != null) "开始于 $startedAt · $pausedText"
                else "开始于 $startedAt",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            // 分段统计（ROADMAP P3.5）：每公里一行。
            if (splits.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SplitList(splits)
            }

            // 社区维护入口：对比是"我的"做深，贡献是"我的"变成公共价值（见 product-philosophy）
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onCompare) {
                    Text("与上次对比", fontSize = 13.sp)
                }
                TextButton(onClick = onContribute) {
                    Text("贡献到线路库", fontSize = 13.sp)
                }
            }
        }
    }
}

/** 分段统计列表：每公里一段，最多显示 20 段（再长用户也看不过来）。 */
@Composable
private fun SplitList(splits: List<TrackSplits.Split>) {
    val shown = splits.take(20)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "分段",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        shown.forEach { split ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (split.partial) "第 ${split.index} 段（不足 1 km）" else "第 ${split.index} km",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    buildString {
                        append(TimeFmt.elapsed(split.durationMs))
                        append(" · ")
                        append("%.0f m".format(split.distanceM))
                        if (split.ascentM >= 1.0) {
                            append(" · ↑")
                            append("%.0f m".format(split.ascentM))
                        }
                        split.paceSecPerKm?.let { append(" · ").append(formatPace(it)) }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (splits.size > shown.size) {
            Text(
                "仅显示前 ${shown.size} 段，共 ${splits.size} 段",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
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

/**
 * 爬升/下降展示。两者都为 0 时给 "—"（没有高程数据时不显示 0，免得像是"确实没爬"）。
 */
private fun ascentText(ascent: Double, descent: Double): String {
    if (ascent <= 0.0 && descent <= 0.0) return "—"
    return "↑%.0f ↓%.0f m".format(ascent, descent)
}

/** 平均配速（分/公里）。速度为 0 时给 "—"，不显示 Infinity。 */
private fun paceText(stats: Pace.Stats): String {
    val secPerKm = stats.averagePaceSecPerKm ?: return "—"
    return formatPace(secPerKm)
}

/** 秒/公里 → m'ss" / h mm'ss"。 */
private fun formatPace(secPerKm: Double): String {
    if (!secPerKm.isFinite() || secPerKm <= 0.0) return "—"
    val total = secPerKm.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d'%02d".format(h, m, sec)
    else "%d'%02d".format(m, sec)
}

/** 平均速度 km/h。时长为 0 或异常时给 "—"，不显示 Infinity。 */
private fun averageSpeedText(meters: Float, activeDurationMs: Long): String {
    if (activeDurationMs <= 0L || meters <= 0f) return "—"
    val hours = activeDurationMs / 3_600_000.0
    val kmh = (meters / 1000.0) / hours
    return if (kmh.isFinite()) "%.1f km/h".format(kmh) else "—"
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
