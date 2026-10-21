package com.zouliangbu.footprint.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.model.FeedDetail
import com.zouliangbu.footprint.ui.components.CoverPlaceholder

/**
 * 动态详情页（问题 5）：顶部封面 → 标题 → 作者/城市/点赞/时间行 → 正文（去 HTML 标签）。
 * 404 / 加载失败显示「内容不存在或已下线」+ 返回按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(
    feedId: String,
    onBack: () -> Unit,
    vm: FeedDetailViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(feedId) { vm.load(feedId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("动态详情", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { inner ->
        when (val s = state) {
            FeedDetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            FeedDetailUiState.NotFound -> Box(
                Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "内容不存在或已下线",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("返回") }
                }
            }

            is FeedDetailUiState.Ready -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
            ) {
                CoverPlaceholder(
                    seed = s.detail.coverStyle,
                    aspectRatio = s.detail.coverAspectRatio,
                    label = s.detail.city,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    coverUrl = s.detail.coverUrl
                )
                Text(
                    s.detail.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                MetaRow(s.detail)
                ContentSection(s.detail.content)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MetaRow(d: FeedDetail) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(d.author, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(14.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            d.city,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(13.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            "${d.likeCount}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        d.createdAt?.takeIf { it.isNotBlank() }?.let { time ->
            Spacer(Modifier.weight(1f))
            Text(
                formatDate(time),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** createdAt 原样太长时取 yyyy-MM-dd 前缀，否则原样返回。 */
private fun formatDate(raw: String): String =
    if (raw.length >= 10 && raw[4] == '-' && raw[7] == '-') raw.take(10) else raw

/** 正文：复用 RouteDetailScreen 的去 HTML 标签思路。 */
@Composable
private fun ContentSection(html: String) {
    val text = html
        .replace(Regex("<p>"), "")
        .replace(Regex("</p>"), "\n")
        .replace(Regex("<br[^>]*>"), "\n")
        .replace(Regex("<li>"), "\u2022 ")
        .replace(Regex("</li>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("&nbsp;"), " ")
        .replace(Regex("&amp;"), "&")
        .replace(Regex("&lt;"), "<")
        .replace(Regex("&gt;"), ">")
        .trim()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (text.isBlank()) {
            Text(
                "暂无正文",
                fontSize = 13.sp,
                color = Color(0xFF999999)
            )
        } else {
            Text(text, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFF333333))
        }
    }
}
