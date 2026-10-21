package com.zouliangbu.footprint.ui.gear

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.GearItem
import com.zouliangbu.footprint.ui.theme.ForestGreen
import kotlin.math.min

/**
 * 装备统计页：汇总卡 + 分类占比环形图（Compose Canvas 手画，无新依赖）。
 * 重量 / 费用两种口径切换；已转让条目默认排除，可切换包含。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearStatsScreen(
    onBack: () -> Unit,
    vm: GearViewModel = viewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val categories = vm.categories

    var byWeight by remember { mutableStateOf(true) } // true 重量 / false 费用
    var includeTransferred by remember { mutableStateOf(false) }

    val counted = remember(items, includeTransferred) {
        items.filter { includeTransferred || it.status == GearItem.STATUS_AVAILABLE }
    }

    // 每个分类的占比值：重量求和，或费用求和（无价格的按 0 算）
    val slices = remember(counted, byWeight) {
        categories.map { c ->
            val list = counted.filter { it.categoryId == c.id }
            StatSlice(
                c.name,
                if (byWeight) list.sumOf { it.weightGrams.toDouble() }
                else list.sumOf { (it.priceCents ?: 0L).toDouble() } / 100.0
            )
        }.filter { it.value > 0.0 }
    }
    val total = slices.sumOf { it.value }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("装备统计", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 汇总卡
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCell("${counted.size}", "件装备")
                    StatCell(fmtWeight(counted.sumOf { it.weightGrams }), "总重量")
                    val priceSum = counted.sumOf { it.priceCents ?: 0L }
                    StatCell(if (priceSum > 0) fmtPrice(priceSum) else "—", "总花费")
                }
            }

            // 口径切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = byWeight,
                    onClick = { byWeight = true },
                    label = { Text("按重量") }
                )
                FilterChip(
                    selected = !byWeight,
                    onClick = { byWeight = false },
                    label = { Text("按费用") }
                )
                FilterChip(
                    selected = includeTransferred,
                    onClick = { includeTransferred = !includeTransferred },
                    label = { Text("含已转让") }
                )
            }

            // 环形图
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    slices = slices,
                    centerLabel = if (byWeight) fmtWeight(total.toInt()) else fmtPrice(total.toLong()),
                    modifier = Modifier.size(200.dp)
                )
            }

            // 图例
            if (slices.isEmpty()) {
                Text(
                    "还没有数据，先去添加几件装备吧",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                slices.forEachIndexed { index, slice ->
                    val color = sliceColor(index)
                    val percent = if (total > 0) slice.value / total * 100 else 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(10.dp).background(color, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(slice.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (byWeight) fmtWeight(slice.value.toInt()) else fmtPrice(slice.value.toLong()),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            String.format(java.util.Locale.CHINA, "%.0f%%", percent),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 分类占比配色（绿系为主，与主题呼应；固定顺序保证图例和弧一致）。 */
private fun sliceColor(index: Int): Color = when (index % 8) {
    0 -> ForestGreen
    1 -> Color(0xFF6FA46F)
    2 -> Color(0xFFA5C88A)
    3 -> Color(0xFFD3C36A)
    4 -> Color(0xFFD69B62)
    5 -> Color(0xFFC4715A)
    6 -> Color(0xFF8A7CA8)
    else -> Color(0xFF6B8BA4)
}

/** 环形图的一段：分类名 + 占比值（重量克数或费用元数）。 */
private data class StatSlice(val name: String, val value: Double)

/**
 * 环形图：Canvas 逐段画弧。slices 为空时画一个浅灰底环占位。
 */
@Composable
private fun DonutChart(
    slices: List<StatSlice>,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val totalValue = slices.sumOf { it.value }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 28.dp.toPx()
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            if (totalValue <= 0.0) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                return@Canvas
            }

            var startAngle = -90f // 从 12 点方向开始
            slices.forEachIndexed { index, slice ->
                val sweep = (slice.value / totalValue * 360.0).toFloat()
                drawArc(
                    color = sliceColor(index),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                if (slices.isNotEmpty()) "共 ${slices.size} 类" else "暂无数据",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
