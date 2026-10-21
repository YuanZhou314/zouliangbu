package com.zouliangbu.footprint.ui.routes

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.zouliangbu.footprint.data.model.RouteItem
import com.zouliangbu.footprint.data.remote.ContentApiFactory
import com.zouliangbu.footprint.data.remote.SubmitRouteRequest
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.model.RouteSection
import com.zouliangbu.footprint.ui.components.CoverPlaceholder
import com.zouliangbu.footprint.ui.components.OfflineDemoBanner
import com.zouliangbu.footprint.ui.components.SectionHeader

/**
 * 线路页：搜索框（本地过滤 mock）+ 横向推荐位 + 分组网格。
 * 卡片本期不可进详情。
 */
@Composable
fun RoutesScreen(
    onOpenDetail: (Long) -> Unit = {},
    vm: RoutesViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var importNote by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 前端域名校验：与服务端 RoutePlatform.detect 保持一致（两步路 / 六只脚）
    val urlValid = importUrl.contains("2bulu.com", ignoreCase = true) ||
            importUrl.contains("foooooot.com", ignoreCase = true)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 标题 + 搜索框
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("线路", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "地图模式",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = false) { /* 暂未实现 */ }
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "导入路线",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showImportDialog = true }
                )
            }
        }
        item(span = { GridItemSpan(2) }) {
            SearchBox(state.query, onQueryChange = vm::onQueryChange)
        }

        // 离线演示数据横幅（问题 2）：回退 Mock 时提示
        item(span = { GridItemSpan(2) }) {
            OfflineDemoBanner()
        }

        // 横向推荐位
        if (state.recommendations.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(state.recommendations, key = { it.id }) { route ->
                        RecommendCard(route, onOpenDetail)
                    }
                }
            }
        }

        // 分组
        state.sections.forEach { section ->
            item(span = { GridItemSpan(2) }) {
                SectionHeader(section.title, subtitle = section.subtitle)
            }
            itemsIndexed(section.routes, key = { _, r -> section.id + "-" + r.id }) { _, route ->
                RouteCard(route, onOpenDetail)
            }
        }

        if (state.isEmpty) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    "没有找到「${state.query.trim()}」相关线路",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showImportDialog = false },
            title = { Text("导入路线", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = {
                            importUrl = it
                            submitError = null
                        },
                        singleLine = true,
                        enabled = !submitting,
                        label = { Text("两步路 / 六只脚链接") },
                        isError = importUrl.isNotBlank() && !urlValid,
                        supportingText = {
                            if (importUrl.isNotBlank() && !urlValid) {
                                Text(
                                    "目前仅支持两步路 / 六只脚链接",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importNote,
                        onValueChange = { importNote = it },
                        singleLine = true,
                        enabled = !submitting,
                        label = { Text("备注（选填）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (submitError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            submitError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = urlValid && !submitting,
                    onClick = {
                        submitting = true
                        submitError = null
                        val uuid = submitterUuidOf(context)
                        // 截断超长输入：链接正常不超过几百字符，防超长串撑爆入库字段
                        val safeUrl = importUrl.trim().take(500)
                        val safeNote = importNote.trim().take(500).ifBlank { null }
                        scope.launch {
                            val resp = runCatching {
                                ContentApiFactory.service.submitRoute(
                                    SubmitRouteRequest(
                                        sourceUrl = safeUrl,
                                        submitterUuid = uuid,
                                        submitterNote = safeNote
                                    )
                                )
                            }
                            submitting = false
                            resp.fold(
                                onSuccess = { api ->
                                    if (api.ok) {
                                        showImportDialog = false
                                        importUrl = ""
                                        importNote = ""
                                        Toast.makeText(
                                            context, "已提交，等待审核", Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        submitError = api.msg.ifBlank { "提交失败，请稍后重试" }
                                    }
                                },
                                onFailure = { e ->
                                    submitError = "网络异常：${e.message ?: "请稍后重试"}"
                                }
                            )
                        }
                    }
                ) {
                    Text(if (submitting) "提交中…" else "提交")
                }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 设备稳定标识：优先 ANDROID_ID，取不到则生成 UUID 存 SharedPreferences 复用（保证同一设备同一标识）。 */
private fun submitterUuidOf(context: Context): String {
    val prefs = context.getSharedPreferences("footprint_device", Context.MODE_PRIVATE)
    prefs.getString("submitter_uuid", null)?.let { return it }
    val androidId = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()
    // 官方确认过的坏值：所有 2.2 及之前模拟器共享的 ANDROID_ID
    val uuid = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
        androidId
    } else {
        java.util.UUID.randomUUID().toString()
    }
    prefs.edit().putString("submitter_uuid", uuid).apply()
    return uuid
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "请输入关键词或线路名称",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** 横向推荐位大卡（封面 + 名称）。 */
@Composable
private fun RecommendCard(route: RouteItem, onOpenDetail: (Long) -> Unit = {}) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { route.id.toLongOrNull()?.let { onOpenDetail(it) } }
    ) {
        CoverPlaceholder(
            seed = route.coverStyle,
            aspectRatio = 1.5f,
            label = route.region,
            coverUrl = route.coverUrl
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                route.name,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${route.distanceKm}km · 爬升${route.ascentMeters}m",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
        }
    }
}

/** 分组内小卡。 */
@Composable
private fun RouteCard(route: RouteItem, onOpenDetail: (Long) -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.clickable { route.id.toLongOrNull()?.let { onOpenDetail(it) } }
    ) {
        Column {
            CoverPlaceholder(
                seed = route.coverStyle,
                aspectRatio = 1.35f,
                label = route.region,
                coverUrl = route.coverUrl
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    route.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${route.distanceKm}km · 爬升${route.ascentMeters}m",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (route.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        route.tags.take(2).forEach { tag ->
                            Text(
                                tag,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
