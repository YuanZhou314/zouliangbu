package com.zouliangbu.footprint.ui.gear

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.GearItem
import com.zouliangbu.footprint.ui.theme.ForestGreen

/**
 * 打包清单页：从装备库勾选本次要带的装备，底部常驻总重量（含 / 不含消耗品）。
 *
 * 单清单，勾选状态与「消耗品」标记都存 SharedPreferences（数据小、结构扁平，
 * 不值得为它再建表）；装备本体仍在 Room。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearPackingScreen(
    onBack: () -> Unit,
    vm: GearViewModel = viewModel()
) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val categories = vm.categories

    // 只从「可用」装备里挑：已转让的不在家，打不了包
    val packable = remember(items) {
        items.filter { it.status == GearItem.STATUS_AVAILABLE }
    }

    val prefs = remember { context.getSharedPreferences("gear_packing", Context.MODE_PRIVATE) }
    var checkedIds by remember { mutableStateOf(prefs.getStringSet(KEY_CHECKED, emptySet())!!.toSet()) }
    var consumableIds by remember { mutableStateOf(prefs.getStringSet(KEY_CONSUMABLE, emptySet())!!.toSet()) }
    var saved by remember { mutableStateOf(true) } // 是否已保存（有改动置 false）

    fun toggle(set: Set<String>, id: Long): Set<String> =
        if (id.toString() in set) set - id.toString() else set + id.toString()

    val picked = packable.filter { it.id.toString() in checkedIds }
    val weightAll = picked.sumOf { it.weightGrams }
    val weightNoConsumable =
        picked.filter { it.id.toString() !in consumableIds }.sumOf { it.weightGrams }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("打包清单", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Card(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "总重 ${fmtWeight(weightAll)}（含消耗品）",
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "基础 ${fmtWeight(weightNoConsumable)}（不含消耗品）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                prefs.edit()
                                    .putStringSet(KEY_CHECKED, checkedIds)
                                    .putStringSet(KEY_CONSUMABLE, consumableIds)
                                    .apply()
                                saved = true
                            },
                            enabled = !saved
                        ) { Text("保存清单") }
                    }
                }
            }
        }
    ) { inner ->
        if (packable.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("没有可打包的装备", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "先在装备页添加几件可用装备",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(packable, key = { it.id }) { item ->
                val checked = item.id.toString() in checkedIds
                val consumable = item.id.toString() in consumableIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            checkedIds = toggle(checkedIds, item.id)
                            saved = false
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            fontSize = 15.sp,
                            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal
                        )
                        Text(
                            categories.firstOrNull { it.id == item.categoryId }?.name ?: "未分类",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        fmtWeight(item.weightGrams),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    // 「耗」标记：点一下把它标为消耗品，总重会分「含/不含」两行
                    TextButton(
                        onClick = {
                            consumableIds = toggle(consumableIds, item.id)
                            saved = false
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "耗",
                            fontSize = 12.sp,
                            color = if (consumable) ForestGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private const val KEY_CHECKED = "checked_ids"
private const val KEY_CONSUMABLE = "consumable_ids"
