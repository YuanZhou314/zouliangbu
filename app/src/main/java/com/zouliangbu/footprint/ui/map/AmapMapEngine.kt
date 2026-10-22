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
class AmapMapEngine(
    private val aMap: AMap,
    /** 地图 View 的当前尺寸（px），用于把 bbox 适配到「未被遮挡的那一块」。 */
    private val viewSize: () -> Pair<Int, Int> = { 0 to 0 }
) : MapEngine {

    private var polyline: Polyline? = null
    /** 轨迹线底下的深色描边：亮色线压在卫星图/雪地/水泥地上都能看清。 */
    private var polylineShadow: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    private var lastTrack: List<MapPoint> = emptyList()
    private var lastStart: MapPoint? = null
    private var lastEnd: MapPoint? = null

    /**
     * 待执行的缩放请求。
     *
     * 详情页是「先拿数据再画地图」：点集到位时地图往往还没 OnMapLoaded，
     * 这时 animateCamera 会被 AMap 丢掉，而且之后不会自动补——表现为
     * **地图一直停在北京（AMap 默认中心）**，看起来就像没适配轨迹。
     * 所以这里缓存一份，[reapply] 时补一次。
     */
    private var pendingZoom: List<MapPoint>? = null
    private var pendingPaddingPx: Int = 0
    /** 底部被详情卡挡住的高度（px）：适配时要先扣掉，再把相机上移一半。 */
    private var pendingCoveredBottomPx: Int = 0

    /** 相机操作要等一拍才生效，见 applyZoom 里的说明。 */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun setTrack(points: List<MapPoint>) {
        lastTrack = points
        if (points.size < 2) {
            // 单点（刚开始记录）画不出折线，先隐藏；等第二个点到达再显示。
            polyline?.isVisible = false
            return
        }
        val latLngs = points.map { LatLng(it.lat, it.lng) }

        // 先画一条更宽、半透明的深色线做描边，再画亮色主线。
        // 卫星图是深绿地形，纯绿线（原来的 0xFF2E7D32）几乎看不见 —— 换成黄线 + 黑描边。
        val shadow = polylineShadow ?: aMap.addPolyline(
            PolylineOptions()
                .width(TRACK_SHADOW_WIDTH_PX)
                .color(TRACK_SHADOW_COLOR)
                .zIndex(8f)
                .visible(true)
        ).also { polylineShadow = it }
        shadow.points = latLngs
        shadow.isVisible = true

        val line = polyline ?: aMap.addPolyline(
            PolylineOptions()
                .width(TRACK_WIDTH_PX)
                .color(TRACK_COLOR)
                .zIndex(9f)
                .visible(true)
        ).also { polyline = it }

        line.points = latLngs
        line.isVisible = true
        android.util.Log.d(
            "TrackMap",
            "setTrack n=" + latLngs.size + " linePts=" + line.points.size +
                " vis=" + line.isVisible + " z=" + line.zIndex +
                " color=" + Integer.toHexString(line.color) + " width=" + line.width
        )
    }

    override fun clearTrack() {
        lastTrack = emptyList()
        polyline?.isVisible = false
        polylineShadow?.isVisible = false
    }

    override fun setEndpoints(start: MapPoint?, end: MapPoint?) {
        lastStart = start
        lastEnd = end
        startMarker = replaceMarker(startMarker, start, "起点", HUE_GREEN)
        endMarker = replaceMarker(endMarker, end, "终点", HUE_RED)
    }

    override fun zoomToTrack(points: List<MapPoint>, paddingPx: Int, coveredBottomPx: Int) {
        if (points.isEmpty()) return
        pendingZoom = points
        pendingPaddingPx = paddingPx
        pendingCoveredBottomPx = coveredBottomPx
        applyZoom()
    }

    /** 真正执行缩放。地图没加载完时会失败，由 [reapply] 再调一次。 */
    private fun applyZoom() {
        val points = pendingZoom ?: return
        android.util.Log.d(
            "TrackMap",
            "applyZoom n=" + points.size + " padding=" + pendingPaddingPx +
                " before=" + camInfo()
        )
        if (points.size == 1 || isDegenerate(points)) {
            // 单点 / 所有点几乎重合：bbox 面积为 0，newLatLngBounds 会抛异常。
            moveTo(points.first())
            return
        }
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(LatLng(it.lat, it.lng)) }
        val bounds = builder.build()

        val (vw, vh) = viewSize()
        val covered = pendingCoveredBottomPx
        if (covered > 0 && vw > 0 && vh > 0 && covered < vh) {
            // 地图铺满全屏、下面是悬浮的详情卡：bbox 必须只适配「卡片顶部以上」那块，
            // 否则轨迹中心会落在卡片底下。AMap 没有 setPadding（不像 Google Maps），
            // 所以先按「可视区宽高」算 zoom，再把相机整体上移半个卡片高度。
            val visibleH = (vh - covered).coerceAtLeast(vh / 3)
            runCatching {
                // 第一步：按「可视区宽高」算 zoom，bbox 此时居中于整个屏幕（含卡片区域）
                aMap.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, vw, visibleH, pendingPaddingPx)
                )
                // 第二步：等一拍再读相机 —— moveCamera 之后是异步生效的，
                // 立刻读 cameraPosition 还是旧值、读 projection 甚至返回 (0,0)，
                // 照着那个值再 moveCamera 会把地图扔到赤道（真踩过）。
                // 拿到稳定的 zoom 之后，用 Web Mercator 自己算"上移半个卡片高度"折合多少纬度。
                mainHandler.postDelayed({
                    runCatching {
                        val cam = aMap.cameraPosition
                        val target = cam.target ?: return@runCatching
                        val z = cam.zoom
                        if (z <= 0f) return@runCatching
                        val metersPerPx = 156543.03392 *
                            Math.cos(Math.toRadians(target.latitude)) /
                            Math.pow(2.0, z.toDouble())
                        val dLat = (covered / 2.0) * metersPerPx / 111320.0
                        aMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(target.latitude - dLat, target.longitude),
                                z
                            )
                        )
                        android.util.Log.d(
                            "TrackMap",
                            "applyZoom(covered) centered zoom=" + z + " dLat=" + dLat +
                                " from=" + target.latitude
                        )
                    }
                }, CENTER_DELAY_MS)
            }.onFailure {
                android.util.Log.e("TrackMap", "applyZoom(covered) failed: " + it.message)
            }
        } else {
            runCatching {
                aMap.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, pendingPaddingPx)
                )
            }.onFailure {
                android.util.Log.e("TrackMap", "applyZoom failed: " + it.message)
            }
        }
        // 动画有延时，单独打一条"请求后"的日志；最终位置看 onCameraChange 后的下一次调用
        android.util.Log.d(
            "TrackMap",
            "applyZoom target sw=" + builder.build().southwest.latitude + "," +
                builder.build().southwest.longitude + " ne=" +
                builder.build().northeast.latitude + "," + builder.build().northeast.longitude
        )
    }

    /** 相机当前状态，日志用。 */
    private fun camInfo(): String {
        val c = runCatching { aMap.cameraPosition }.getOrNull() ?: return "null"
        val t = c.target ?: return "noTarget"
        return "lat=" + t.latitude + " lng=" + t.longitude + " zoom=" + c.zoom
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
        val target = when (type) {
            MapType.NORMAL -> AMap.MAP_TYPE_NORMAL
            MapType.SATELLITE -> AMap.MAP_TYPE_SATELLITE
        }
        if (aMap.mapType == target) return
        aMap.mapType = target
        // 切卫星图会把已添加的覆盖物清掉（AMap 的行为），而且清理是异步的：
        // 紧接着重画也会被清。所以延迟一拍再 reapply，否则轨迹线直接消失。
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            reapply()
        }, 350L)
    }

    override fun zoomIn() {
        aMap.animateCamera(CameraUpdateFactory.zoomIn())
    }

    override fun zoomOut() {
        aMap.animateCamera(CameraUpdateFactory.zoomOut())
    }

    /** 地图加载完成后重放最后一次的覆盖物设置（见类注释）。 */
    fun reapply() {
        android.util.Log.d("TrackMap", "reapply pendingZoom=" + (pendingZoom?.size ?: -1) + " " + camInfo())
        if (lastTrack.isNotEmpty()) setTrack(lastTrack)
        if (lastStart != null || lastEnd != null) setEndpoints(lastStart, lastEnd)
        // 地图加载完补一次缩放：加载前调的那次被 AMap 丢了（见 pendingZoom 注释）。
        applyZoom()
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
        const val TRACK_WIDTH_PX = 9f

        /** 描边线宽：比主线宽一倍，露出来的部分形成"黑边"。 */
        const val TRACK_SHADOW_WIDTH_PX = 17f

        /** 轨迹线颜色：亮黄。卫星图是深绿地形，绿线等于隐身（踩过一次）。 */
        const val TRACK_COLOR = 0xFFFFD400.toInt()

        /** 描边色：40% 黑。 */
        const val TRACK_SHADOW_COLOR = 0x66000000

        /** moveCamera 生效的等待时间（毫秒）。 */
        const val CENTER_DELAY_MS = 90L

        /** BitmapDescriptorFactory.defaultMarker(hue) 的色相：120=绿，0=红。 */
        const val HUE_GREEN = 120f
        const val HUE_RED = 0f
    }
}
