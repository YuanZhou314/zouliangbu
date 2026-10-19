package com.zouliangbu.footprint.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * osmdroid 地图（极图 GeoQ 瓦片）。无高德 Key 时的过渡实现。
 *
 * - 与 [AmapMapView] 同一接口签名：仅接受 modifier。轨迹叠加（polyline）留待后续接入 TrackRepository
 *   数据时再做，目前业务层还没把轨迹点喂到地图上。
 * - 瓦片源：极图（GeoQ）`map.geoq.cn`，国内 CDN，公开匿名访问，无需 token。
 *   **不**用 OSM 官方 `tile.openstreetmap.org`——后者在国内被墙或极慢，会导致空白。
 *   极图瓦片坐标系是 GCJ-02，OSM MapView 渲染逻辑本身无影响（Web Mercator 投影相同）。
 * - 我的位置：osmdroid 自带的 [GpsMyLocationProvider] 从 Android LocationManager 拿 WGS-84 坐标。
 *   与极图瓦片的 GCJ-02 坐标系会有 50–500 m 偏移——这是过渡方案的已知限制，等高德 Key 下发后
 *   切回 AmapMapView 就一致了；定位真实数据进来后才会看出偏移，目前 marker 显示的是 overlay 默认图。
 * - 生命周期：ON_RESUME / ON_PAUSE 走 LifecycleEventObserver；MapView.onDetach 在 onDispose 里调用。
 */
@Composable
fun OsmMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { createMapView(context) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

/** 极图（GeoQ）公开瓦片源，覆盖 OnlineTileSourceBase 默认的 {z}/{x}/{y} 模板，匹配极图的 {z}/{y}/{x}。 */
private val ChinaGeoqTiles = object : OnlineTileSourceBase(
    "GeoQ-China-Online-Community",
    0, 18, 256, ".png",
    arrayOf<String>()  // URL 由 getTileURLString 自定义拼装
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "https://map.geoq.cn/ArcGIS/rest/services/ChinaOnlineCommunity/MapServer/tile/$z/$y/$x"
    }
}

private fun createMapView(context: Context): MapView {
    val mapView = MapView(context).apply {
        setTileSource(ChinaGeoqTiles)
        setMultiTouchControls(true)
        // 默认中心（北京天安门）；首次定位回调触发后会被覆盖。
        controller.setZoom(16.0)
        controller.setCenter(GeoPoint(39.9087, 116.3975))
    }

    val myLocationOverlay = MyLocationNewOverlay(
        GpsMyLocationProvider(context),
        mapView
    ).apply {
        enableMyLocation()
        // 首次定位后地图自动平移到「我的位置」，避免一直显示默认中心。
        enableFollowLocation()
    }
    mapView.overlays.add(myLocationOverlay)

    mapView.invalidate()
    return mapView
}