package com.zouliangbu.footprint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 封面占位组件。
 *
 * coverUrl 为空时画渐变色块 + Terrain 图标水印 + 可选标签（城市/地区）；
 * 将来接入网络图片后，在 coverUrl != null 分支替换为图片组件即可，调用方不变。
 */
private val CoverPalettes = listOf(
    listOf(Color(0xFF2E7D32), Color(0xFF81C784)), // 山林绿
    listOf(Color(0xFF00695C), Color(0xFF4DB6AC)), // 竹海青
    listOf(Color(0xFF1565C0), Color(0xFF64B5F6)), // 天空蓝
    listOf(Color(0xFFE65100), Color(0xFFFFB74D)), // 落日橙
    listOf(Color(0xFF4E342E), Color(0xFFA1887F))  // 岩石灰
)

@Composable
fun CoverPlaceholder(
    seed: Int,
    aspectRatio: Float,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = CoverPalettes[Math.floorMod(seed, CoverPalettes.size)]
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.6f, 1.6f))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(colors))
    ) {
        Icon(
            Icons.Filled.Terrain,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 10.dp)
                .size(72.dp)
        )
        if (!label.isNullOrBlank()) {
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0x66000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
