package com.zouliangbu.footprint.ui.gear

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.model.GearCategory
import com.zouliangbu.footprint.ui.theme.ForestGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 装备页：分类宫格（本期不接商城，纯展示不可点）。
 * 无 ViewModel：数据是静态分类列表，将来接商城时再升级。
 */
@Composable
fun GearScreen() {
    val context = LocalContext.current
    // 接 Remote 后 categories() 会走网络（RemoteGearRepository 内部 runBlocking），
    // 必须放 IO 线程，否则主线程 ANR。网络失败时 Remote 回退到本地 Mock，立即返回。
    var categories by remember { mutableStateOf(emptyList<GearCategory>()) }
    LaunchedEffect(Unit) {
        categories = withContext(Dispatchers.IO) {
            (context.applicationContext as FootprintApp).gearRepository.categories()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text("装备", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "先从这里认识你的装备（商城功能开发中）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        itemsIndexed(categories, key = { _, c -> c.id }) { _, category ->
            GearCard(category)
        }
    }
}

@Composable
private fun GearCard(category: GearCategory) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.clickable(enabled = false) { /* 商城暂未实现 */ }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(ForestGreen.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconFor(category.id),
                    contentDescription = category.name,
                    tint = ForestGreen,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(category.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                category.note,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 分类 id → 图标（material-icons-extended；没有 Tent，帐篷用 Cabin）。 */
private fun iconFor(id: String): ImageVector = when (id) {
    "g1" -> Icons.Filled.Backpack
    "g2" -> Icons.Filled.Hiking
    "g3" -> Icons.Filled.Checkroom
    "g4" -> Icons.Filled.Hiking // 登山杖：暂用 Hiking，后续换自定义图标
    "g5" -> Icons.Filled.Cabin
    "g6" -> Icons.Filled.FlashlightOn
    "g7" -> Icons.Filled.LocalDrink
    else -> Icons.Filled.WbSunny
}
