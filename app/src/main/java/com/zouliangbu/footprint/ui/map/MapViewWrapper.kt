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
    /** 参考线（循迹 b）：记录页叠加已缓存线路，虚线显示在实时轨迹下方。 */
    referenceTrack: List<MapPoint> = emptyList(),
    showEndpoints: Boolean = false,
    autoZoom: Boolean = false,
    /** 变化时重新做一次 bbox 适配（如底部卡片换档导致地图可视区变了）。 */
    fitKey: Any? = null,
    /** bbox 适配时四周留白（dp）。 */
    fitPaddingDp: Int = 32,
    /** 底部被悬浮卡片挡住的高度（dp）：适配时扣掉，把轨迹推到可见区中间。 */
    coveredBottomDp: Int = 0,
    /** 卫星图（轨迹回看用）。 */
    satellite: Boolean = false,
    /** 是否显示右下角自绘定位按钮（详情页传 false，自己画）。 */
    showLocateButton: Boolean = true,
    /**
     * 初始相机位置：非空时地图初始化后立即定位到该点，并**禁用首点跟随**（本参数优先）。
     * 预览类页面（离线地图查看等）用它把相机先放到目标城市，否则会定位到当前位置。
     * 只在高德分支生效；osmdroid 兜底分支忽略（既定取舍）。
     */
    initialCamera: MapPoint? = null,
    /** 初始相机缩放级别（配合 [initialCamera] 使用）。 */
    initialZoom: Float = 14f,
    onEngineReady: ((MapEngine) -> Unit)? = null
) {
    val context = LocalContext.current
    if (AmapKey.isConfigured(context)) {
        AmapMapView(
            modifier = modifier,
            track = track,
            referenceTrack = referenceTrack,
            showEndpoints = showEndpoints,
            autoZoom = autoZoom,
            fitKey = fitKey,
            fitPaddingDp = fitPaddingDp,
            coveredBottomDp = coveredBottomDp,
            satellite = satellite,
            showLocateButton = showLocateButton,
            initialCamera = initialCamera,
            initialZoom = initialZoom,
            onEngineReady = onEngineReady
        )
    } else {
        OsmMapView(modifier)
    }
}
