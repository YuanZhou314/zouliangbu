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
