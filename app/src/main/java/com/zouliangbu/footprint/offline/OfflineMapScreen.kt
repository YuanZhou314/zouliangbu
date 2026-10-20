package com.zouliangbu.footprint.offline

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.util.TimeFmt

/**
 * 离线地图管理页（ROADMAP P4.2）。
 *
 * 入口：「我的」页 → 离线地图。
 *
 * 这一页要说明清楚一件事：**离线包只对 osmdroid 分支有效**——
 * 高德 MapView 读不了 MBTiles。所以有高德 Key 时下载的包在地图上不会生效，
 * 这一点在页面上直说，别让用户白下几百 MB。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    onBack: () -> Unit,
    vm: OfflineMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val ui by vm.state.collectAsStateWithLifecycle()
    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线地图", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (ui.states.values.any { it.isInstalled }) {
                        TextButton(onClick = { confirmClearAll = true }) {
                            Text("清空", fontSize = 14.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // 顶部说明 + 占用空间
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    "已用 ${formatBytes(ui.usedBytes)} / 上限 2 GB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "提示：离线包（MBTiles）仅供无高德 Key 时的 osmdroid 地图使用；" +
                        "高德分支不支持离线瓦片。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (ui.regions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无可下载的离线区域",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    ui.regions.forEach { (group, items) ->
                        item(key = "group-$group") {
                            Text(
                                group,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(items, key = { it.id }) { region ->
                            val state = ui.states[region.id]
                            OfflineRegionRow(
                                region = region,
                                state = state,
                                onDownload = { vm.download(region) },
                                onDelete = { vm.delete(region) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空离线地图？") },
            text = { Text("将删除全部已下载的离线包，释放占用空间。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    vm.deleteAll()
                    Toast.makeText(context, "已清空离线地图", Toast.LENGTH_SHORT).show()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            }
        )
    }
}

/** 单个区域行：名称、体积、状态、下载/删除按钮。 */
@Composable
private fun OfflineRegionRow(
    region: OfflineRegion,
    state: OfflinePackageState?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val status = state?.status ?: OfflinePackageState.Status.NOT_DOWNLOADED
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(region.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(region.sizeText)
                        append(" · 缩放 ")
                        append(region.minZoom)
                        append("-")
                        append(region.maxZoom)
                        if (region.installedVersionOr(state) != null) {
                            append(" · 版本 ")
                            append(region.installedVersionOr(state))
                        }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status == OfflinePackageState.Status.DOWNLOADING) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state?.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (status == OfflinePackageState.Status.FAILED) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state?.errorMessage ?: "下载失败",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when (status) {
                OfflinePackageState.Status.INSTALLED -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已下载",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除离线包")
                    }
                }
                OfflinePackageState.Status.DOWNLOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "下载离线包")
                    }
                }
            }
        }
    }
}

/** 已安装版本：优先取状态里记录的，没有就取区域的。 */
private fun OfflineRegion.installedVersionOr(state: OfflinePackageState?): String? =
    state?.installedVersion

/** 字节 → 可读体积。 */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
