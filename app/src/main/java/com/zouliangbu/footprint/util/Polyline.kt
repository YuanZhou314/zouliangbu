package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.ui.map.MapPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 轨迹抽稀（Douglas-Peucker 算法，ROADMAP P2.5）。
 *
 * 为什么要抽稀：10 小时徒步能采到 2 万多个点，全部喂给地图渲染会掉帧、
 * 详情页打开变慢。抽稀只影响**渲染**，原始点仍完整落库（统计用原始点）。
 *
 * 为什么用 Douglas-Peucker 而不是"每 N 个取 1"：
 *   - 等间隔抽样在直线段会保留大量冗余点，在急转弯处又会把弯道削平
 *   - DP 保形：形状特征保留，冗余的共线点被去掉
 *
 * 算法是纯几何，与坐标系无关（GCJ-02 / WGS-84 都能用，因为只算平面距离）。
 */
object Polyline {

    /**
     * 距离阈值（米）。点距离"首尾连线"的垂直距离小于该值就认为是冗余点。
     *
     * 取 3.0 m：手机 GPS 在开阔地精度 3–5 m，低于这个尺度的抖动对用户没有意义；
     * 但太小会抽不动（10 万点全保留），太大急转弯会被削平。
     */
    const val DEFAULT_TOLERANCE_M = 3.0

    /** 点数少于该值时不做抽稀（抽了也没意义，还可能把两点变成一点）。 */
    const val MIN_POINTS_FOR_SIMPLIFY = 3

    /**
     * 对 [MapPoint] 列表抽稀。
     *
     * @param toleranceM 垂直距离阈值（米）
     * @return 抽稀后的点序列；**首尾点一定保留**（否则起终点 marker 会跑偏）
     */
    fun simplify(points: List<MapPoint>, toleranceM: Double = DEFAULT_TOLERANCE_M): List<MapPoint> {
        if (points.size < MIN_POINTS_FOR_SIMPLIFY) return points
        val keep = BooleanArray(points.size) { it == 0 || it == points.size - 1 }
        douglasPeucker(points, 0, points.size - 1, toleranceM, keep)
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /**
     * 对 [TrackPoint] 列表抽稀（保留原始对象，含海拔/时间/精度）。
     *
     * 详情页的"轨迹线"用这个版本：折线和起终点 marker 要用**原始点**，
     * 否则 marker 会落在被抽掉的中间点上，位置差几十米。
     */
    fun simplifyPoints(
        points: List<TrackPoint>,
        toleranceM: Double = DEFAULT_TOLERANCE_M
    ): List<TrackPoint> {
        if (points.size < MIN_POINTS_FOR_SIMPLIFY) return points
        val keep = BooleanArray(points.size) { it == 0 || it == points.size - 1 }
        val projected = points.map { MapPoint(it.lat, it.lng) }
        douglasPeucker(projected, 0, points.size - 1, toleranceM, keep)
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /**
     * 递归版 DP。深度最坏是点数（一条完全共线的折线），2 万点会 StackOverflow，
     * 所以这里用**显式栈**的迭代写法。
     */
    private fun douglasPeucker(
        points: List<MapPoint>,
        first: Int,
        last: Int,
        tolerance: Double,
        keep: BooleanArray
    ) {
        if (last <= first + 1) return
        // 显式栈：存 [first, last] 区间
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(first, last))
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            if (end <= start + 1) continue

            var maxDist = -1.0
            var maxIndex = -1
            for (i in start + 1 until end) {
                val d = perpendicularDistance(points[i], points[start], points[end])
                if (d > maxDist) {
                    maxDist = d
                    maxIndex = i
                }
            }
            if (maxIndex > 0 && maxDist > tolerance) {
                keep[maxIndex] = true
                stack.addLast(intArrayOf(start, maxIndex))
                stack.addLast(intArrayOf(maxIndex, end))
            }
        }
    }

    /** 点 [p] 到线段 [a]-[b] 的垂直距离（米）。 */
    private fun perpendicularDistance(p: MapPoint, a: MapPoint, b: MapPoint): Double {
        // 用局部平面近似（等距圆柱投影）。纬度 1° ≈ 111320 m。
        val latScale = 111_320.0
        val lngScale = 111_320.0 * kotlin.math.cos(Math.toRadians(a.lat))

        val px = (p.lng - a.lng) * lngScale
        val py = (p.lat - a.lat) * latScale
        val bx = (b.lng - a.lng) * lngScale
        val by = (b.lat - a.lat) * latScale

        val lenSq = bx * bx + by * by
        if (lenSq < 1e-12) {
            // 退化线段（a 和 b 几乎重合）：退化成点到点距离
            return sqrt(px * px + py * py)
        }
        // 投影长度（带符号），夹到 [0,1] 表示垂足落在线段内
        val t = ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * 抽稀后如果点数没减少（阈值太小 / 轨迹本来就很抖），直接用原序列。
     * 返回值供调用方判断"是否值得走渲染优化路径"。
     */
    fun simplifyOrKeep(
        points: List<MapPoint>,
        toleranceM: Double = DEFAULT_TOLERANCE_M
    ): List<MapPoint> {
        val simplified = simplify(points, toleranceM)
        return if (simplified.size < points.size) simplified else points
    }

    /** 供测试与调试：判断两个值是否在容差内相等。 */
    internal fun nearlyEqual(a: Double, b: Double, epsilon: Double = 1e-6): Boolean =
        abs(a - b) < epsilon
}
