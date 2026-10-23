package com.zouliangbu.footprint.ui.map

import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
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
    /** 参考线（循迹 b）：虚线、青色，压在实时轨迹下方。 */
    private var referenceLine: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    private var lastTrack: List<MapPoint> = emptyList()
    private var lastReference: List<MapPoint> = emptyList()
    private var lastStart: MapPoint? = null
    private var lastEnd: MapPoint? = null

    // ===== 视口卡片能力（task-001 加，当前无业务消费）=====
    override var onCameraIdle: (() -> Unit)? = null
    override var onMarkerClick: ((String) -> Unit)? = null

    /** [showPointMarkers] 添加的 AMap marker → 业务 id。按引用直查，点击回调带回 id。 */
    private val pointMarkers = mutableMapOf<Marker, String>()
    /** 最后一次的点位请求：切底图会清覆盖物，reapply 时重放（与 track 同一套约定）。 */
    private var lastPointMarkers: List<MapMarker> = emptyList()

    init {
        // 相机移动结束 → onCameraIdle；marker 点击只认自己 add 的点位 marker，
        // 起终点 marker 的点击走默认行为（弹信息窗）。
        aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) = Unit
            override fun onCameraChangeFinish(position: CameraPosition?) {
                onCameraIdle?.invoke()
            }
        })
        aMap.setOnMarkerClickListener(object : AMap.OnMarkerClickListener {
            override fun onMarkerClick(marker: Marker?): Boolean {
                val m = marker ?: return false
                // marker 引用直查业务 id：不能用「实际加成功的列表」与「请求列表」
                // 按下标平行对位——addMarker 任一条失败长度就错位，会回调错 id（审查发现）。
                pointMarkers[m]?.let {
                    onMarkerClick?.invoke(it)
                    return true
                }
                return false
            }
        })
    }

    /**
     * 待补执行的缩放请求。
     *
     * 详情页是「先拿数据再画地图」：点集到位时地图往往还没 OnMapLoaded，
     * 这时 moveCamera 会被 AMap 丢掉，而且之后不会自动补——表现为
     * **地图一直停在北京（AMap 默认中心）**，看起来就像没适配轨迹。
     *
     * 语义（重要，别改回去）：**只暂存因 viewSize 为 (0,0) 而没能生效的请求**，
     * 由 [reapply]（OnMapLoaded / setMapType）补一次；请求一旦实际生效、或被判为
     * 无效（可视区过小 / zoom 被钳到下限），立即清空——保证 reapply 永远不会
     * 重放已生效或无效的参数（此前坏参数会被反复重放，把相机越打越偏）。
     */
    private var pendingZoom: List<MapPoint>? = null
    private var pendingPaddingPx: Int = 0
    /** 底部被详情卡挡住的高度（px）：适配时要先扣掉，再把相机上移一半。 */
    private var pendingCoveredBottomPx: Int = 0

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

    override fun setReferenceTrack(points: List<MapPoint>) {
        lastReference = points
        if (points.size < 2) {
            referenceLine?.isVisible = false
            return
        }
        val latLngs = points.map { LatLng(it.lat, it.lng) }
        val line = referenceLine ?: aMap.addPolyline(
            PolylineOptions()
                .width(REFERENCE_WIDTH_PX)
                .color(REFERENCE_COLOR)
                .zIndex(5f)
                .setDottedLine(true)
        ).also { referenceLine = it }
        line.points = latLngs
        line.isVisible = true
    }

    override fun setEndpoints(start: MapPoint?, end: MapPoint?) {
        lastStart = start
        lastEnd = end
        startMarker = replaceMarker(startMarker, start, "起点", HUE_GREEN)
        endMarker = replaceMarker(endMarker, end, "终点", HUE_RED)
    }

    override fun zoomToTrack(points: List<MapPoint>, paddingPx: Int, coveredBottomPx: Int) {
        applyZoom(points, paddingPx, coveredBottomPx)
    }

    /**
     * 真正执行缩放（同步算完、一次 moveCamera，幂等，重放不会偏）。
     *
     * ## 为什么不再用 `newLatLngBounds` + 事后补偿
     * 详情页是「地图铺满全屏、详情卡浮在下半屏」：bbox 必须只适配卡片上方那块可视区。
     * AMap 没有 `setPadding`，老做法是「先 newLatLngBounds 适配，再 postDelayed 读相机
     * 后把中心上移半个卡片高度」——但 moveCamera 是异步生效的，中途还可能被
     * setMapType 触发的 [reapply] 打断，两步之间一错就整体偏掉，表现就是
     * **进详情页看不到轨迹，得下拉一次才出现**。
     *
     * ## 两个必须跳过的坏档（踩过三次）
     * 卡片拖到展开档时 covered≈整屏，可视区只剩一条缝：
     *   1. 可视区高度 < [MIN_AVAIL_H_PX]：算出的 zoom 会被钳到极小档位，
     *      而南移补偿 dLat 仍按那个档位的米/像素（≈20km/px）放大 →
     *      相机被甩到数千公里外，轨迹跑到可视区上方。宁可不动相机。
     *   2. 算出的 zoom 在下限（< [ZOOM_FLOOR]）以下：同样是「bbox 远超可视区
     *      能表达的范围」，补偿必然离谱，同样跳过。
     * 两种情况都清掉 pending，保证后续 reapply 不会重放坏参数。
     */
    private fun applyZoom(points: List<MapPoint>, paddingPx: Int, coveredBottomPx: Int) {
        if (points.isEmpty()) return
        if (points.size == 1 || isDegenerate(points)) {
            // 单点 / 所有点几乎重合：bbox 面积为 0，newLatLngBounds 会抛异常。
            moveTo(points.first())
            pendingZoom = null
            return
        }

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

        val (vw, vh) = viewSize()
        if (vw <= 0 || vh <= 0) {
            // 还没布局：只有这种情况暂存请求，等 reapply（OnMapLoaded / setMapType）补。
            // 此时硬算会拿 0 宽高算出荒谬的 zoom。
            pendingZoom = points
            pendingPaddingPx = paddingPx
            pendingCoveredBottomPx = coveredBottomPx
            android.util.Log.d("TrackMap", "applyZoom deferred: view not laid out n=" + points.size)
            return
        }

        val covered = coveredBottomPx.coerceIn(0, vh - 1)
        val pad = paddingPx.coerceAtLeast(0)
        // 可视区 = 整屏去掉底部被卡片盖住的那块，再扣掉四周留白。
        val rawAvailH = vh - covered - 2 * pad
        if (rawAvailH < MIN_AVAIL_H_PX) {
            // 展开档（卡片几乎盖满全屏）：跳过本次 fit，相机保持不动，清 pending。
            pendingZoom = null
            android.util.Log.d(
                "TrackMap",
                "applyZoom skipped: availH=" + rawAvailH + " < " + MIN_AVAIL_H_PX + ", camera untouched"
            )
            return
        }
        val availW = (vw - 2 * pad).coerceAtLeast(60)
        val availH = rawAvailH

        val centerLat = (minLat + maxLat) / 2.0
        val centerLng = (minLng + maxLng) / 2.0
        val mPerDegLat = 111320.0
        val cosLat = Math.cos(Math.toRadians(centerLat)).coerceAtLeast(0.01)
        val mPerDegLng = 111320.0 * cosLat

        // 让 bbox 刚好塞进可视区所需的「米/像素」，取宽高两个方向的较大者。
        val spanLngM = (maxLng - minLng) * mPerDegLng
        val spanLatM = (maxLat - minLat) * mPerDegLat
        val needMpp = maxOf(spanLngM / availW, spanLatM / availH).coerceAtLeast(0.01)

        // mpp = 156543.03392 * cos(lat) / 2^z  →  z = log2(156543.03392 * cos(lat) / mpp)
        val zoomRaw = Math.log(156543.03392 * cosLat / needMpp) / Math.log(2.0)
        if (zoomRaw < ZOOM_FLOOR) {
            // zoom 会被钳到下限以下：dLat 补偿会按极粗的 mpp 把中心推到千里之外，跳过。
            pendingZoom = null
            android.util.Log.d(
                "TrackMap",
                "applyZoom skipped: zoomRaw=" + zoomRaw + " < " + ZOOM_FLOOR + ", camera untouched"
            )
            return
        }
        val zoom = zoomRaw.coerceAtMost(18.0)

        // 相机目标点渲染在屏幕正中（vh/2），但我们希望 bbox 中心出现在可视区正中
        // （(vh-covered)/2），即比屏幕中心高 covered/2 像素 —— 所以相机中心要南移
        // covered/2 像素对应的纬度。dLat 与 zoom 用同一个 mpp（钳位后已定格）。
        val mppAtZoom = 156543.03392 * cosLat / Math.pow(2.0, zoom)
        val dLat = (covered / 2.0) * mppAtZoom / mPerDegLat
        val target = LatLng(centerLat - dLat, centerLng)

        // 到这里请求已判定有效：清 pending，reapply 不会重放本次参数。
        pendingZoom = null
        runCatching {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, zoom.toFloat()))
        }.onFailure {
            android.util.Log.e("TrackMap", "applyZoom failed: " + it.message)
        }
        android.util.Log.d(
            "TrackMap",
            "applyZoom n=" + points.size + " vw=" + vw + " vh=" + vh +
                " covered=" + covered + " zoom=" + zoom +
                " target=" + target.latitude + "," + target.longitude +
                " bboxCenter=" + centerLat + "," + centerLng
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
        if (lastReference.isNotEmpty()) setReferenceTrack(lastReference)
        if (lastStart != null || lastEnd != null) setEndpoints(lastStart, lastEnd)
        if (lastPointMarkers.isNotEmpty()) showPointMarkers(lastPointMarkers)
        // 地图加载完补一次缩放：只重放因 view 未布局而挂起的请求（见 pendingZoom 注释）。
        pendingZoom?.let { applyZoom(it, pendingPaddingPx, pendingCoveredBottomPx) }
    }

    // ===== 视口卡片能力（task-001 加，当前无业务消费）=====

    override fun visibleBounds(): MapBounds? {
        return runCatching {
            val bounds = aMap.projection.visibleRegion.latLngBounds
            MapBounds(
                north = bounds.northeast.latitude,
                south = bounds.southwest.latitude,
                east = bounds.northeast.longitude,
                west = bounds.southwest.longitude
            )
        }.getOrNull()
    }

    override fun showPointMarkers(markers: List<MapMarker>) {
        lastPointMarkers = markers
        pointMarkers.keys.forEach { it.remove() }
        pointMarkers.clear()
        markers.forEach { m ->
            runCatching {
                aMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(m.lat, m.lng))
                        .title(m.title)
                        .zIndex(6f)
                )
            }.getOrNull()?.let { pointMarkers[it] = m.id }
        }
    }

    override fun clearPointMarkers() {
        lastPointMarkers = emptyList()
        pointMarkers.keys.forEach { it.remove() }
        pointMarkers.clear()
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

        /** 参考线宽（px）。 */
        const val REFERENCE_WIDTH_PX = 7f

        /** 参考线颜色：青色（与亮黄实时轨迹、绿底卫星图都区分开）。 */
        const val REFERENCE_COLOR = 0xFF00BCD4.toInt()

        /** BitmapDescriptorFactory.defaultMarker(hue) 的色相：120=绿，0=红。 */
        const val HUE_GREEN = 120f
        const val HUE_RED = 0f

        /**
         * 可视区高度低于此值（px，约 1/10 屏）就跳过 bbox 适配：卡片展开档
         * 可视区只剩一条缝，硬算会把相机甩出数千公里（踩过三次的"轨迹在上方"）。
         */
        const val MIN_AVAIL_H_PX = 120

        /** bbox 适配的 zoom 下限：低于它说明可视区表达不了 bbox，直接跳过不 fit。 */
        const val ZOOM_FLOOR = 4.0
    }
}
