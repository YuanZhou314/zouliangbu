package com.zouliangbu.footprint.offline

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
    /** 查看已下载城市的离线地图。 */
    onOpenPreview: (String) -> Unit = {},
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
        if (vm.amapMode) {
            AmapOfflineList(vm, onOpenPreview, Modifier.fillMaxSize().padding(inner))
        } else {
            MbtilesOfflineList(vm, Modifier.fillMaxSize().padding(inner))
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

// ===== 高德官方离线（有 Key 时的主路径：城市级下载，下载后地图自动使用）=====

@Composable
private fun AmapOfflineList(
    vm: OfflineMapViewModel,
    onOpenPreview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cities by vm.amapCities.collectAsStateWithLifecycle()
    val downloaded by vm.amapDownloaded.collectAsStateWithLifecycle()
    val loading by vm.amapLoading.collectAsStateWithLifecycle()
    // 列表就绪门控：SDK 异步加载完成前不渲染列表，避免「已下载」组 0.5s 后突然
    // 置顶导致整页跳变（用户反馈 1）。
    val listReady by vm.amapListReady.collectAsStateWithLifecycle()
    val error by vm.amapError.collectAsStateWithLifecycle()

    Column(modifier) {
        Text(
            "高德官方离线地图：按城市下载，下载完成后地图在无网络时也能正常使用。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )
        error?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        if (loading || !listReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                // 已下载置顶，并与未下载隔开——用户要能一眼看到"哪些已经能用"。
                if (downloaded.isNotEmpty()) {
                    item(key = "group-downloaded") {
                        GroupHeader("已下载 · ${downloaded.size} 个城市")
                    }
                    items(downloaded, key = { "d-" + it.code }) { c ->
                        AmapCityRow(c, vm, onClick = { onOpenPreview(c.name) })
                    }
                    item(key = "group-available") { GroupHeader("可下载的城市") }
                }
                items(cities, key = { it.code }) { c ->
                    AmapCityRow(c, vm, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun AmapCityRow(c: AmapCityState, vm: OfflineMapViewModel, onClick: (() -> Unit)?) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 3.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Card(
        modifier = rowModifier,
        colors = CardDefaults.cardColors(
            containerColor = if (c.downloaded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                val statusText = when {
                    c.downloading -> "下载中 ${c.progress}%"
                    c.isPaused -> "已暂停 ${c.progress}%"
                    c.hasNewVersion -> "有新版本，可更新"
                    c.isFailed -> "下载失败，点击重试"
                    c.downloaded -> "已下载" + (c.version?.let { " · $it" } ?: "") + " · 点击查看"
                    else -> "%.0f MB".format(c.sizeBytes / 1024.0 / 1024.0)
                }
                Text(
                    statusText,
                    fontSize = 12.sp,
                    color = when {
                        c.isFailed -> MaterialTheme.colorScheme.error
                        c.downloading || c.downloaded -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (c.downloading || c.isPaused) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (c.progress.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            AmapCityAction(c, vm)
        }
    }
}

@Composable
private fun AmapCityAction(c: AmapCityState, vm: OfflineMapViewModel) {
    when {
        c.downloading -> {
            IconButton(onClick = { vm.pauseCity(c) }) {
                Icon(Icons.Filled.Pause, contentDescription = "暂停下载")
            }
        }
        c.isPaused -> {
            IconButton(onClick = { vm.resumeCity(c) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "继续下载")
            }
            IconButton(onClick = { vm.removeCity(c) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除离线数据")
            }
        }
        c.downloaded -> {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已下载",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            IconButton(onClick = { vm.removeCity(c) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除离线数据")
            }
        }
        c.hasNewVersion -> {
            TextButton(onClick = { vm.updateCity(c) }) { Text("更新", fontSize = 13.sp) }
        }
        else -> {
            IconButton(onClick = { vm.downloadCity(c) }) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "下载离线地图")
            }
        }
    }
}

// ===== MBTiles（osmdroid 兜底分支，无高德 Key 时显示）=====

@Composable
private fun MbtilesOfflineList(vm: OfflineMapViewModel, modifier: Modifier = Modifier) {
    val ui by vm.state.collectAsStateWithLifecycle()

    Column(modifier) {
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
