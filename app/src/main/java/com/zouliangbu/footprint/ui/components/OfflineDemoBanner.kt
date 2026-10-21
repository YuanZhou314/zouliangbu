package com.zouliangbu.footprint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.data.remote.ContentSourceState

/**
 * 网络不可用横幅：Remote 仓库拉不到线上数据时提示（内容此时为空，不再回退假数据）。
 * 只在 [ContentSourceState.isOfflineFallback] 为 true 时显示。
 */
@Composable
fun OfflineDemoBanner(modifier: Modifier = Modifier) {
    if (!ContentSourceState.isOfflineFallback) return
    Text(
        "网络不可用，内容暂时加载不了",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
