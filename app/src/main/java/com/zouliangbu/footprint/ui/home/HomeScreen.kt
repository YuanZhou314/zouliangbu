package com.zouliangbu.footprint.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.model.FeedItem
import com.zouliangbu.footprint.ui.components.CoverPlaceholder
import com.zouliangbu.footprint.ui.components.OfflineDemoBanner
import com.zouliangbu.footprint.ui.theme.ForestGreen
import com.zouliangbu.footprint.ui.theme.TrailOrange

/**
 * 首页：顶部城市栏（同城）+ 运营 Banner 占位 + 「同城动态」两列瀑布流。
 *
 * 动态卡片可点进 FeedDetailScreen（feed/{id}）。
 */
@Composable
fun HomeScreen(
    onOpenDetail: (String) -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()

    // 首页城市栏的定位权限申请：只要 FINE+COARSE，不带后台/通知权限（避免整组被静默忽略）
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) vm.loadCity(force = true)
    }

    LaunchedEffect(Unit) { vm.loadCity() }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp
    ) {
        // 顶部：城市栏 + Banner（横跨两列）
        item(span = StaggeredGridItemSpan.FullLine) {
            CityBar(city) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        // 离线演示数据横幅（问题 2）：回退 Mock 时提示
        item(span = StaggeredGridItemSpan.FullLine) {
            OfflineDemoBanner()
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            HomeBanner()
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Text(
                "同城动态",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
            )
        }

        if (feed.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    "暂无动态",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        items(feed, key = { it.id }) { item ->
            FeedCard(item, onOpenDetail = { onOpenDetail(item.id) })
        }

        if (feed.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
            Text(
                "— 更多内容，敬请期待 —",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CityBar(city: CityUiState, onRequestPermission: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = ForestGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        when (city) {
            is CityUiState.Ready -> Text(
                city.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            CityUiState.Loading -> Text(
                "定位中…",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CityUiState.NoPermission -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onRequestPermission)
            ) {
                Text(
                    "开启定位",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TrailOrange
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "申请定位权限",
                    tint = TrailOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
            CityUiState.Failed -> Text(
                "定位失败",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        Text("关注", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text("在路上", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text("搭子", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeBanner() {
    // 运营 Banner 占位：渐变 + 文案。将来接入运营配置接口后替换。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.6f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(ForestGreen, Color(0xFF66BB6A))))
            .clickable(enabled = false) { /* 活动页暂未实现 */ },
        contentAlignment = Alignment.CenterStart
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "2026 户外特工 & 走两步",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
            Text(
                "秋天，去山里走走",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(56.dp)
        )
    }
}

@Composable
private fun FeedCard(item: FeedItem, onOpenDetail: () -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.clickable(onClick = onOpenDetail)
    ) {
        Column {
            CoverPlaceholder(
                seed = item.coverStyle,
                aspectRatio = item.coverAspectRatio,
                label = item.city,
                coverUrl = item.coverUrl
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 头像占位（首字母圆形）
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB0BEC5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.author.take(1),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.author,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "${item.likeCount}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
