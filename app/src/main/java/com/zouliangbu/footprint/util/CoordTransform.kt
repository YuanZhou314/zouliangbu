package com.zouliangbu.footprint.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 一个经纬度点。坐标系由调用方约定（本包内出现的都是 WGS-84 或 GCJ-02 之一）。 */
data class GeoCoord(val lat: Double, val lng: Double)

/**
 * GCJ-02（火星坐标） ↔ WGS-84（GPS 原始坐标）转换。
 *
 * 为什么必须有它：App 里所有定位与地图都是 GCJ-02，而 GPX/KML 的行业惯例是 WGS-84。
 * 不转换直接导出，位置会偏 300–800 m（我国境内偏移量级），在户外场景下等于错一条山脊。
 *
 * 算法说明：
 *   - 正向（WGS-84 → GCJ-02）是公开的加偏算法，基于克拉索夫斯基椭球参数；
 *   - 反向（GCJ-02 → WGS-84）**没有解析解**，用不动点迭代逼近：
 *     `wgs += gcj - forward(wgs)`，偏移量平滑、收敛快，默认 3 轮残差已 < 0.1 m。
 *   - 境外不做偏移（GCJ-02 只在我国境内生效），否则会把国外轨迹带偏。
 *
 * 精度预期：与主流在线转换工具比对，境内偏差 < 1 m。
 */
object CoordTransform {

    /** 克拉索夫斯基椭球长半轴（GCJ-02 算法固定用这套参数）。 */
    private const val SEMI_MAJOR_AXIS = 6378245.0

    /** 第一偏心率平方。 */
    private const val ECCENTRICITY_SQ = 0.00669342162296594323

    private const val PI = Math.PI

    /** 粗略国境判定：超出该矩形一律认为在境外，不做偏移。 */
    fun isOutOfChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    /** WGS-84 → GCJ-02。 */
    fun wgs84ToGcj02(lat: Double, lng: Double): GeoCoord {
        if (isOutOfChina(lat, lng)) return GeoCoord(lat, lng)

        val dLatRaw = transformLat(lng - 105.0, lat - 35.0)
        val dLngRaw = transformLng(lng - 105.0, lat - 35.0)

        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - ECCENTRICITY_SQ * magic * magic
        val sqrtMagic = sqrt(magic)

        val dLat = (dLatRaw * 180.0) /
                ((SEMI_MAJOR_AXIS * (1 - ECCENTRICITY_SQ)) / (magic * sqrtMagic) * PI)
        val dLng = (dLngRaw * 180.0) / (SEMI_MAJOR_AXIS / sqrtMagic * cos(radLat) * PI)

        return GeoCoord(lat + dLat, lng + dLng)
    }

    /**
     * GCJ-02 → WGS-84（迭代逼近）。
     *
     * @param iterations 迭代轮数。2 轮约 0.5 m，3 轮 < 0.1 m；再往上收益可忽略。
     */
    fun gcj02ToWgs84(lat: Double, lng: Double, iterations: Int = 3): GeoCoord {
        if (isOutOfChina(lat, lng)) return GeoCoord(lat, lng)

        var wgsLat = lat
        var wgsLng = lng
        repeat(iterations) {
            val gcj = wgs84ToGcj02(wgsLat, wgsLng)
            wgsLat += lat - gcj.lat
            wgsLng += lng - gcj.lng
        }
        return GeoCoord(wgsLat, wgsLng)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
