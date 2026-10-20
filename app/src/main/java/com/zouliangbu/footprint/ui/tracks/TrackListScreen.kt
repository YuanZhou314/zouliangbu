package com.zouliangbu.footprint.ui.tracks

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.TrackSummary
import com.zouliangbu.footprint.ui.theme.ForestGreen
import com.zouliangbu.footprint.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史轨迹列表（「我的 → 我的记录」的下钻页）。
 *
 * 从内层 NavHost 进入，所以底栏仍在：用户可以随时切到别的 tab，回来自动恢复。
 * 点击进详情；长按删（导入的坏文件、误记录的 0 点会话需要能清掉，否则只能清应用数据）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrackListScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: TrackListViewModel = viewModel()
) {
    val context = LocalContext.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<TrackSummary?>(null) }

    // SAF 选文件：不申请任何存储权限，也不需要 READ_EXTERNAL_STORAGE。
    // mime 用 */* —— GPX 在不同来源里常被标成 octet-stream / text/xml / 甚至 application/zip，
    // 按 mime 过滤的结果是"用户在文件选择器里根本看不到自己的 gpx"。内容合法性交给解析器判。
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
                        // 光一个下载图标语义模糊，加"导入"二字：这是本页的主要动作之一。
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
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onOpenDetail(track.id) },
                        onLongClick = { pendingDelete = track }
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: TrackSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateStr = formatDate(track.startTime)
    val title = track.name ?: dateStr
    // 活动时长（已扣暂停）：暂停两小时的长椅休息不该算进"走了多久"。
    val durationMs = track.activeDurationMs

    // 有名字时把日期挪到副行，避免同一天多条记录只看名字分不清时间。
    val meta = buildList {
        if (track.name != null) add(dateStr)
        add(TimeFmt.distance(track.totalDistanceMeters))
        add(TimeFmt.elapsed(durationMs))
        add("${track.pointCount} 个点")
    }.joinToString(" · ")

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Route,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    fontSize = 12.sp,
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

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
