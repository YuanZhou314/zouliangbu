package com.zouliangbu.footprint.ui.gear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.GearItem
import com.zouliangbu.footprint.ui.theme.ForestGreen
import java.util.Locale

/**
 * 装备页：单页三段式台账。
 * 顶栏（标题 + 统计入口 + 添加）→ 横滑分类筛选 chips → 汇总行 + 装备卡片列表；
 * 右下角「打包清单」FAB。条目点击进编辑页。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GearScreen(
    onOpenStats: () -> Unit,
    onOpenPacking: () -> Unit,
    onOpenEdit: (Long) -> Unit,
    vm: GearViewModel = viewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val categories = vm.categories
    var filterId by remember { mutableStateOf<String?>(null) } // null = 全部

    val visible = remember(items, filterId) {
        if (filterId == null) items else items.filter { it.categoryId == filterId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("装备", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.Insights, contentDescription = "统计")
                    }
                    IconButton(onClick = { onOpenEdit(0L) }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加装备")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPacking,
                containerColor = ForestGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("打包清单")
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 分类筛选 chips（横滑）
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterId == null,
                        onClick = { filterId = null },
                        label = { Text("全部") }
                    )
                    categories.forEach { c ->
                        FilterChip(
                            selected = filterId == c.id,
                            onClick = { filterId = if (filterId == c.id) null else c.id },
                            label = { Text(c.name) }
                        )
                    }
                }
            }
            // 汇总行
            item {
                val available = visible.filter { it.status == GearItem.STATUS_AVAILABLE }
                Text(
                    "${available.size} 件可用 · " +
                            "总重 ${fmtWeight(available.sumOf { it.weightGrams })}" +
                            (available.sumOf { it.priceCents ?: 0L }
                                .takeIf { it > 0 }?.let { " · ${fmtPrice(it)}" } ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (visible.isEmpty()) {
                item { EmptyHint() }
            }
            items(visible, key = { it.id }) { item ->
                GearCard(
                    item = item,
                    categoryName = categories.firstOrNull { it.id == item.categoryId }?.name ?: "未分类",
                    onClick = { onOpenEdit(item.id) }
                )
            }
        }
    }
}

@Composable
private fun GearCard(item: GearItem, categoryName: String, onClick: () -> Unit) {
    val transferred = item.status == GearItem.STATUS_TRANSFERRED
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        ForestGreen.copy(alpha = if (transferred) 0.05f else 0.10f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconFor(item.categoryId),
                    contentDescription = categoryName,
                    tint = ForestGreen.copy(alpha = if (transferred) 0.4f else 1f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (transferred) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildList {
                        add(categoryName)
                        if (item.brandModel.isNotBlank()) add(item.brandModel)
                        if (item.weightGrams > 0) add(fmtWeight(item.weightGrams))
                        item.priceCents?.takeIf { it > 0 }?.let { add(fmtPrice(it)) }
                    }.joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (transferred) {
                Text(
                    "已转让",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("还没有登记装备", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "点右上角 + 把你的背包、鞋、帐篷都录进来",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 分类 id → 图标（material-icons-extended；没有 Tent，帐篷用 Cabin）。 */
internal fun iconFor(id: String): ImageVector = when (id) {
    "g1" -> Icons.Filled.Backpack
    "g2" -> Icons.Filled.Hiking
    "g3" -> Icons.Filled.Checkroom
    "g4" -> Icons.Filled.Hiking // 登山杖：暂用 Hiking，后续换自定义图标
    "g5" -> Icons.Filled.Cabin
    "g6" -> Icons.Filled.FlashlightOn
    "g7" -> Icons.Filled.LocalDrink
    else -> Icons.Filled.WbSunny
}

/** 克 → 人话（≥1kg 显示 kg，两位小数）。 */
internal fun fmtWeight(grams: Int): String =
    if (grams >= 1000) String.format(Locale.CHINA, "%.2f kg", grams / 1000.0)
    else "${grams}g"

/** 分 → 人话（两位小数，整数不带小数）。 */
internal fun fmtPrice(cents: Long): String {
    val yuan = cents / 100.0
    return if (cents % 100 == 0L) "¥${cents / 100}" else String.format(Locale.CHINA, "¥%.2f", yuan)
}
