package com.zouliangbu.footprint.ui.map

import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions

/**
 * [MapEngine] 的高德实现。
 *
 * 关键点（踩过一次就别踩第二次）：
 *   - **Polyline 只创建一次**，之后用 `polyline.points = 新点集`（底层 `setPoints`）换点，
 *     不要每个点都 `addPolyline`——AMap 内部会重建纹理且旧覆盖物不会自动回收。
 *   - 地图未加载完时添加覆盖物可能不渲染，所以缓存最后一次的 track / endpoints，
 *     由调用方在地图 `OnMapLoadedListener` 里调 [reapply] 重放一次。
 *   - `newLatLngBounds` 对"零面积 bbox"（所有点重合，或单点）会抛异常，先判跨度再决定用不用。
 *
 * 所有坐标都是 GCJ-02，直接喂 LatLng，不需要转换。
 */
class AmapMapEngine(private val aMap: AMap) : MapEngine {

    private var polyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    private var lastTrack: List<MapPoint> = emptyList()
    private var lastStart: MapPoint? = null
    private var lastEnd: MapPoint? = null

    override fun setTrack(points: List<MapPoint>) {
        lastTrack = points
        if (points.size < 2) {
            // 单点（刚开始记录）画不出折线，先隐藏；等第二个点到达再显示。
            polyline?.isVisible = false
            return
        }
        val line = polyline ?: aMap.addPolyline(
            PolylineOptions()
                .width(TRACK_WIDTH_PX)
                .color(TRACK_COLOR)
                .zIndex(9f)
                .visible(true)
        ).also { polyline = it }

        line.points = points.map { LatLng(it.lat, it.lng) }
        line.isVisible = true
    }

    override fun clearTrack() {
        lastTrack = emptyList()
        polyline?.isVisible = false
    }

    override fun setEndpoints(start: MapPoint?, end: MapPoint?) {
        lastStart = start
        lastEnd = end
        startMarker = replaceMarker(startMarker, start, "起点", HUE_GREEN)
        endMarker = replaceMarker(endMarker, end, "终点", HUE_RED)
    }

    override fun zoomToTrack(points: List<MapPoint>, paddingPx: Int) {
        if (points.isEmpty()) return
        if (points.size == 1 || isDegenerate(points)) {
            // 单点 / 所有点几乎重合：bbox 面积为 0，newLatLngBounds 会抛异常。
            moveTo(points.first())
            return
        }
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(LatLng(it.lat, it.lng)) }
        aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx))
    }

    override fun moveTo(point: MapPoint, zoom: Float, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lng), zoom)
        if (animate) aMap.animateCamera(update) else aMap.moveCamera(update)
    }

    override fun followTo(point: MapPoint) {
        // 跟随只平移不缩放：zoom <= 0 说明地图还没就绪，退回默认 16 级。
        val zoom = aMap.cameraPosition.zoom.takeIf { it > 0f } ?: 16f
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lng), zoom))
    }

    override fun setMapType(type: MapType) {
        aMap.mapType = when (type) {
            MapType.NORMAL -> AMap.MAP_TYPE_NORMAL
            MapType.SATELLITE -> AMap.MAP_TYPE_SATELLITE
        }
    }

    override fun zoomIn() {
        aMap.animateCamera(CameraUpdateFactory.zoomIn())
    }

    override fun zoomOut() {
        aMap.animateCamera(CameraUpdateFactory.zoomOut())
    }

    /** 地图加载完成后重放最后一次的覆盖物设置（见类注释）。 */
    fun reapply() {
        if (lastTrack.isNotEmpty()) setTrack(lastTrack)
        if (lastStart != null || lastEnd != null) setEndpoints(lastStart, lastEnd)
    }

    private fun replaceMarker(
        existing: Marker?,
        point: MapPoint?,
        title: String,
        hue: Float
    ): Marker? {
        existing?.remove()
        if (point == null) return null
        return aMap.addMarker(
            MarkerOptions()
                .position(LatLng(point.lat, point.lng))
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
                .anchor(0.5f, 0.5f)
        )
    }

    /** 纬度跨度与经度跨度都小于 ~1e-5 度（约 1 m）时认为退化成一点。 */
    private fun isDegenerate(points: List<MapPoint>): Boolean {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        points.forEach {
            if (it.lat < minLat) minLat = it.lat
            if (it.lat > maxLat) maxLat = it.lat
            if (it.lng < minLng) minLng = it.lng
            if (it.lng > maxLng) maxLng = it.lng
        }
        return (maxLat - minLat) < 1e-5 && (maxLng - minLng) < 1e-5
    }

    private companion object {
        /** 线宽（像素，AMap 的 width 单位是屏幕像素，不随缩放变化）。 */
        const val TRACK_WIDTH_PX = 10f

        /** 轨迹线颜色：品牌绿 ForestGreen（0xFF2E7D32），ARGB 全不透明。 */
        const val TRACK_COLOR = 0xFF2E7D32.toInt()

        /** BitmapDescriptorFactory.defaultMarker(hue) 的色相：120=绿，0=红。 */
        const val HUE_GREEN = 120f
        const val HUE_RED = 0f
    }
}
