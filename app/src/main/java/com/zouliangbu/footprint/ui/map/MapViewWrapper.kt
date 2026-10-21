package com.zouliangbu.footprint.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zouliangbu.footprint.data.AmapKey

/**
 * 地图统一入口。调用方只引用本 Composable，由它按运行时状态路由：
 *
 *   - [AmapKey.isConfigured] == true  → [AmapMapView]（高德 MapView，需要有效 Key）
 *   - [AmapKey.isConfigured] == false → [OsmMapView]（osmdroid + 极图瓦片，无需 Key）
 *
 * Key 下发后只需替换 res/values/strings.xml 中的 amap_api_key 占位符再 build 一次，业务代码无需改动。
 *
 * 轨迹渲染参数（[track] / [showEndpoints] / [autoZoom]）**只在有 Key 的高德分支生效**：
 * ROADMAP D1 明确 osmdroid 仅作兜底、不做功能对等（不实现 polyline / 详情），
 * 所以无 Key 时地图上只有蓝点没有线——这是既定取舍，不是 bug。
 */
@Composable
fun MapViewWrapper(
    modifier: Modifier = Modifier,
    track: List<MapPoint> = emptyList(),
    showEndpoints: Boolean = false,
    autoZoom: Boolean = false,
    onEngineReady: ((MapEngine) -> Unit)? = null
) {
    val context = LocalContext.current
    if (AmapKey.isConfigured(context)) {
        AmapMapView(
            modifier = modifier,
            track = track,
            showEndpoints = showEndpoints,
            autoZoom = autoZoom,
            onEngineReady = onEngineReady
        )
    } else {
        OsmMapView(modifier)
    }
}
