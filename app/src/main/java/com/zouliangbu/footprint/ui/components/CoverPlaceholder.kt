package com.zouliangbu.footprint.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zouliangbu.footprint.R
import com.zouliangbu.footprint.data.remote.ContentApiFactory

/**
 * 封面组件。
 *
 * coverUrl 非空时用 Coil 加载网络图（加载中/失败回退 [R.drawable.ic_cover_placeholder]）；
 * 为空时直接显示默认占位图（浅底 + 山形图案），可选标签（城市/地区）叠加。调用方零改动。
 */

/**
 * 把服务端返回的 coverUrl 解析成可加载的绝对地址：
 * - null → null（走默认占位图）
 * - 已是 http(s) 绝对地址 → 原样
 * - 以 / 开头的相对路径（如 /uploads/2026-10/x.jpg）→ 拼 [ContentApiFactory.BASE_URL]
 */
fun resolveCoverUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return if (raw.startsWith("http://") || raw.startsWith("https://")) {
        raw
    } else {
        ContentApiFactory.BASE_URL.trimEnd('/') + (if (raw.startsWith("/")) raw else "/$raw")
    }
}

// seed 参数保留以兼容现有调用点（占位图统一后不再区分色板）
@Suppress("UNUSED_PARAMETER")
@Composable
fun CoverPlaceholder(
    seed: Int,
    aspectRatio: Float,
    label: String? = null,
    modifier: Modifier = Modifier,
    coverUrl: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.6f, 1.6f))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEAF1E7))
    ) {
        val url = resolveCoverUrl(coverUrl)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // 加载中/失败都露出默认占位图
                error = painterResource(R.drawable.ic_cover_placeholder),
                modifier = Modifier.matchParentSize()
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_cover_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
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
