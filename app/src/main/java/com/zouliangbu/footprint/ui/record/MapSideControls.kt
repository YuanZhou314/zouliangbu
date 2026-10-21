package com.zouliangbu.footprint.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.ui.map.MapType

/**
 * 记录页右侧地图控制列（仿两步路右排按钮）。
 *
 * 本期只做两个：**图层切换（矢量/卫星）** 与 **缩放 +/−**。
 * 其余（搜索/工具/热力图/队伍/守护）评估结论是本期不做，见 ROADMAP。
 *
 * 图层切换用一个弹出小卡（点「叠加」图标展开两个选项），避免常驻占屏。
 * 缩放按钮做成两个竖排圆钮，放在图层按钮下方。
 *
 * @param mapType        当前底图类型（受控状态，由 [onMapTypeChange] 回调更新）。
 * @param onMapTypeChange 用户点选新底图类型。
 * @param onZoomIn       放大一级。
 * @param onZoomOut      缩小一级。
 */
@Composable
fun MapSideControls(
    mapType: MapType,
    onMapTypeChange: (MapType) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var layerMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 图层切换：点击展开一个小面板，选完自动收起。
        Box(contentAlignment = Alignment.TopEnd) {
            SideCircleButton(
                icon = Icons.Default.Layers,
                contentDescription = "切换图层",
                onClick = { layerMenuExpanded = !layerMenuExpanded }
            )

            if (layerMenuExpanded) {
                Column(
                    modifier = Modifier
                        .padding(end = 52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xF2FFFFFF))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    LayerOption(
                        icon = Icons.Default.Map,
                        label = "标准",
                        selected = mapType == MapType.NORMAL
                    ) {
                        onMapTypeChange(MapType.NORMAL)
                        layerMenuExpanded = false
                    }
                    LayerOption(
                        icon = Icons.Default.Satellite,
                        label = "卫星",
                        selected = mapType == MapType.SATELLITE
                    ) {
                        onMapTypeChange(MapType.SATELLITE)
                        layerMenuExpanded = false
                    }
                }
            }
        }

        // 缩放 +/−（两步路是 ±17.5 数字，我们只做两个圆钮，级别数字不显示——
        // 要读相机状态回调，收益低）。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SideCircleButton(
                icon = Icons.Default.ZoomIn,
                contentDescription = "放大",
                onClick = onZoomIn,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            Box(
                Modifier
                    .size(width = 56.dp, height = 1.dp)
                    .background(Color(0x22000000))
            )
            SideCircleButton(
                icon = Icons.Default.ZoomOut,
                contentDescription = "缩小",
                onClick = onZoomOut,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
        }
    }
}

@Composable
private fun LayerOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    RowishChip(
        icon = icon,
        label = label,
        selected = selected,
        onClick = onClick
    )
}

/** 图层选项小条目（图标 + 文字，选中态染绿）。 */
@Composable
private fun RowishChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFF2E7D32) else Color.Transparent
    val fg = if (selected) Color.White else Color(0xFF263238)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Text(label, color = fg, fontSize = 13.sp)
        }
    }
}

/** 圆形白底图标按钮（叠在地图上）。 */
@Composable
private fun SideCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(Color.White, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color(0xFF424242),
            modifier = Modifier.size(24.dp)
        )
    }
}
