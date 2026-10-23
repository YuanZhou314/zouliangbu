package com.zouliangbu.footprint.ui.map

/**
 * 与地图引擎无关的经纬度点（**GCJ-02**，高德坐标系）。
 *
 * 为什么要有这个类型：高德用 `LatLng`、osmdroid 有同名的 `org.osmdroid.util.GeoPoint`，
 * 业务代码（记录页 / 未来的详情页）一旦直接引用厂商类型，换引擎就要改 UI。
 * 转换只发生在各 [MapEngine] 实现内部。
 *
 * 命名不用 `GeoPoint` 是为了避开 osmdroid 的 `org.osmdroid.util.GeoPoint`（同名会撞）。
 */
data class MapPoint(
    val lat: Double,
    val lng: Double
)

/**
 * 可视区的经纬度范围（GCJ-02），[MapEngine.visibleBounds] 的返回值。
 * 供"地图模式视口卡片"之类按当前视野筛选内容的功能用。
 */
data class MapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

/**
 * 引擎无关的点位 marker（"地图模式视口卡片"用）。
 * [id] 由调用方定义，点击回调原样带回；[title] 显示在 marker 信息窗上。
 */
data class MapMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val title: String
)
