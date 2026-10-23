package com.zouliangbu.footprint.offline

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.ui.map.MapPoint
import com.zouliangbu.footprint.ui.map.MapViewWrapper

/**
 * 已下载离线地图的查看页（点「已下载」列表里的城市进）。
 *
 * 本身就是一张普通高德地图：离线包下载完成后由 SDK 自动接管瓦片来源，
 * 无需任何额外开关。页面底部把这件事说清楚，并给出验证方法（关网再看）。
 */
@Composable
fun OfflineMapPreviewScreen(
    cityName: String,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // 初始相机：落到已下载城市的中心（静态表，见 OfflineRegionCatalog.cityCenter），
        // 不传的话地图会先停在北京、再被首点跟随拉到当前位置——看别的城市就完全跑偏。
        // 省级区域用更小的 zoom 才能看全。表外城市（如「全国概览图」条目）回落中国几何
        // 中心 + zoom 4 全国小图，initialCamera 永不为 null（首点跟随因此永远禁用）。
        val cityCenter = OfflineRegionCatalog.cityCenter(cityName)
        // 表未命中时回落中国几何中心 + 全国小图（zoom 4）：绝不能让 initialCamera 保持 null，
        // 否则 AmapMapView 的首点跟随会把相机拉到本机位置（用户反馈 2 的根因）。
        // 最坏表现从「定位到本机」变成「全国概览」，可接受；真实命名靠日志拿到后补表治本。
        val camera = cityCenter ?: MapPoint(35.8617, 104.1954)
        val zoom = when {
            cityCenter == null -> 4f
            cityName.endsWith("省") || cityName.endsWith("自治区") -> 7f
            else -> 10f
        }
        LaunchedEffect(cityName) {
            if (cityCenter == null) {
                Log.w(
                    "OfflineMap",
                    "no static center for city \"$cityName\", fallback to China overview (zoom $zoom)"
                )
            }
            Log.d("OfflineMap", "preview camera city=$cityName center=$camera zoom=$zoom")
        }
        MapViewWrapper(
            modifier = Modifier.fillMaxSize(),
            satellite = false,
            showLocateButton = true,
            initialCamera = camera,
            initialZoom = zoom
        )

        // 左上角返回键：与记录页同款（40dp 圆形半透明底），地图上必须保证可读。
        Box(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
                )
                .size(40.dp)
                .background(Color(0x99000000), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    "已下载：$cityName",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "这张地图已使用本地离线包，关掉网络（飞行模式）仍能正常加载底图。" +
                        "可以现在开关一次网络验证。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
