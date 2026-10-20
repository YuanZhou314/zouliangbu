package com.zouliangbu.footprint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.ui.navigation.BottomTab
import com.zouliangbu.footprint.ui.theme.ForestGreen

/**
 * 底部导航栏：4 个 tab 槽 + 中间凸起的绿色「出发」大圆钮（两步路同款）。
 *
 * 结构（关键，勿改）：
 *   Surface(圆角背景+阴影)
 *     └ Box(高 84dp = 60dp 底栏 + 24dp 凸起，navigationBarsPadding 在此层)
 *        ├ Row(底部对齐，高 60dp)：tab[0] tab[1] Spacer(让位) tab[2] tab[3]
 *        └ 圆钮(Box 64dp，TopCenter) —— 必须完全在父 Box 边界内：
 *          Compose 命中测试受父边界限制，负 offset 出界的上半部分会点不到。
 *
 * [isRecording] = true 时圆钮文字变「记录中」并显示红点，再次点击回到记录页。
 */
@Composable
fun AppBottomBar(
    tabs: List<BottomTab>,
    selectedRoute: String?,
    isRecording: Boolean,
    onTabClick: (String) -> Unit,
    onRecordClick: () -> Unit
) {
    require(tabs.size == 4) { "AppBottomBar expects exactly 4 tabs (center slot is the record button)" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // 背景铺到系统导航栏后面，内容整体上移（放在 Box 上而不是 Surface 上）
                .navigationBarsPadding()
                .height(84.dp) // 60dp 底栏 + 24dp 凸起，凸起部分计入高度保证可点击
        ) {
            // 底层 60dp 导航条
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                TabSlot(tabs[0], selectedRoute == tabs[0].route) { onTabClick(tabs[0].route) }
                TabSlot(tabs[1], selectedRoute == tabs[1].route) { onTabClick(tabs[1].route) }
                Spacer(Modifier.weight(1f)) // 中间给圆钮让位
                TabSlot(tabs[2], selectedRoute == tabs[2].route) { onTabClick(tabs[2].route) }
                TabSlot(tabs[3], selectedRoute == tabs[3].route) { onTabClick(tabs[3].route) }
            }

            // 中间凸起圆钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(64.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(ForestGreen)
                    .clickable(
                        role = Role.Button,
                        onClick = onRecordClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Hiking,
                        contentDescription = if (isRecording) "记录中，回到记录页" else "出发，开始记录",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        if (isRecording) "记录中" else "出发",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 记录中红点（圆钮右上角）
            if (isRecording) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = 24.dp, y = 2.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabSlot(tab: BottomTab, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) ForestGreen else MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                interactionSource = interaction,
                indication = null,
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(tab.label, fontSize = 10.sp, color = tint)
    }
}
