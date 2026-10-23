package com.zouliangbu.footprint.plants

/**
 * 行政区命中判定（纯几何，GCJ-02，无 Android 依赖，可 JVM 单测）。
 *
 * 两级过滤：bbox 粗筛（O(1)，挡掉绝大多数不相干城市）→ 射线法环内判定。
 * 边界是抽稀后的外环，边界处会有 ±数百米误差，对"这一带常见植物"的场景无关痛痒。
 */
object PlantGeometry {

    /** bbox = [swLng, swLat, neLng, neLat]。 */
    fun inBbox(lat: Double, lng: Double, bbox: List<Double>): Boolean {
        if (bbox.size < 4) return false
        return lng >= bbox[0] && lat >= bbox[1] && lng <= bbox[2] && lat <= bbox[3]
    }

    /**
     * 射线法：点是否在单个环内。环是 [lng, lat] 点列，闭合与否均可。
     * 经典 even-odd 规则：从点向右引射线，与环的交点数为奇数即在内部。
     */
    fun inRing(lat: Double, lng: Double, ring: List<List<Double>>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].getOrNull(0)
            val yi = ring[i].getOrNull(1)
            val xj = ring[j].getOrNull(0)
            val yj = ring[j].getOrNull(1)
            // 脏数据点（缺经纬度）：跳过该边，按前一个 j 继续比较
            if (xi != null && yi != null && xj != null && yj != null) {
                val crosses = (yi > lat) != (yj > lat) &&
                        lng < (xj - xi) * (lat - yi) / (yj - yi + 1e-12) + xi
                if (crosses) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** 任一外环包含即命中（外环并集；不处理内环洞，行政区边界场景不需要）。 */
    fun inBoundary(lat: Double, lng: Double, rings: List<List<List<Double>>>): Boolean =
        rings.any { inRing(lat, lng, it) }

    /** 完整判定：bbox 预筛 + 环内判定。 */
    fun contains(
        lat: Double,
        lng: Double,
        bbox: List<Double>,
        rings: List<List<List<Double>>>
    ): Boolean = inBbox(lat, lng, bbox) && inBoundary(lat, lng, rings)
}
