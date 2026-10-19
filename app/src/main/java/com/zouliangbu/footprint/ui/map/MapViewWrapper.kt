package com.zouliangbu.footprint.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zouliangbu.footprint.data.AmapKey

/**
 * 地图统一入口。调用方只引用本 Composable，由它按运行时状态路由：
 *
 *   - [AmapKey.isConfigured] == true  → [AmapMapView]（高德 MapView，需要有效 Key）
 *   - [AmapKey.isConfigured] == false → [OsmMapView]（osmdroid + OSM 瓦片，无需 Key）
 *
 * Key 下发后只需替换 res/values/strings.xml 中的 amap_api_key 占位符再 build 一次，业务代码无需改动。
 */
@Composable
fun MapViewWrapper(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (AmapKey.isConfigured(context)) {
        AmapMapView(modifier)
    } else {
        OsmMapView(modifier)
    }
}