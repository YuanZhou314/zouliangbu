package com.zouliangbu.footprint.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zouliangbu.footprint.data.remote.ContentApiFactory
import com.zouliangbu.footprint.ui.map.MapPoint
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 线路轨迹全屏预览页：从线路详情点地图进入。
 * 全屏地图 + 轨迹线 + 起终点 + 自动缩放到整条线；数据按 routeId 现拉。
 */
@Composable
fun TrackPreviewScreen(
    routeId: Long,
    onBack: () -> Unit
) {
    var points by remember { mutableStateOf<List<MapPoint>?>(null) }

    LaunchedEffect(routeId) {
        withContext(Dispatchers.IO) {
            val dto = runCatching { ContentApiFactory.service.getTrackPoints(routeId) }
                .getOrNull()?.data.orEmpty()
            points = dto.map { MapPoint(it.lat, it.lng) }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val pts = points
        if (pts == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            MapViewWrapper(
                modifier = Modifier.fillMaxSize(),
                track = pts,
                showEndpoints = true,
                autoZoom = true
            )
        }

        // 左上角返回键（与记录页同款浮层样式，压在地图上）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
    }
}
