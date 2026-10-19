package com.zouliangbu.footprint.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Distance {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Haversine 距离（米）。
     *
     * 适用坐标系：WGS-84 或 GCJ-02（两者都是地理坐标系，Haversine 用经纬度即可，
     * 偏差 < 0.5%，对本应用（户外徒步距离累计）完全可接受）。
     */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }
}