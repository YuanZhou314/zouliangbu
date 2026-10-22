package com.zouliangbu.footprint.ui.tracks

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.TrackSummary
import com.zouliangbu.footprint.ui.components.MonthHeader
import com.zouliangbu.footprint.ui.components.TrackCard
import com.zouliangbu.footprint.ui.components.TrackSummaryCard
import com.zouliangbu.footprint.ui.components.monthKey
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
                    TrackSummaryCard(
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
                            TrackCard(
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
            },            confirmButton = {
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

/** 月度分组：把已结束的轨迹按月倒序切分，供列表与首页时间轴共用。 */
fun groupByMonth(tracks: List<TrackSummary>): List<Pair<String, List<TrackSummary>>> =
    tracks.groupBy { monthKey(it.startTime) }
        .toSortedMap(compareByDescending { it })
        .map { it.key to it.value }

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
                "点底栏中间的「出发」开始第一次记录；\n也可以导入别人分享的 GPX / KML 文件。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onImport) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入 GPX / KML")
            }
        }
    }
}

/** 弹窗里的完整时间：2026-10-18 07:55。 */
private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
