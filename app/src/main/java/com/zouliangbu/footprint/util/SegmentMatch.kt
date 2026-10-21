package com.zouliangbu.footprint.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 两条轨迹的「共同路段」匹配与对比（记录层做深的核心功能）。
 *
 * 场景：同一条线跑了多次，想知道「这次比上次快了多少、少爬多少」。
 *
 * 做法（纯几何，无地图服务依赖，JVM 可测）：
 *  1. 把轨迹 A 展开成折线，每个 B 点找 A 折线上最近的点（点到线段投影）。
 *  2. 命中容差内的点连成「命中段」，允许中间跳过 2 个未命中点（抗 GPS 噪声）。
 *  3. 命中段在 A 上覆盖的弧长 = 共同路段长度；太短则判定为「不是同一条路」返回 null。
 *  4. 只在共同路段上对比距离/时长/爬升/均速。
 *
 * 坐标系：调用方保证两条轨迹同一坐标系（App 内全链路 GCJ-02）。
 * 距离用局部等距投影（以 A 首点为原点），短距离（<50km）误差可忽略。
 */
object SegmentMatch {

    /** 轨迹采样点（时间/海拔可空）。 */
    data class Sample(
        val lat: Double,
        val lng: Double,
        val timestamp: Long? = null,
        val altitude: Double? = null
    )

    /** 共同路段（索引闭区间，A/B 各一段）。 */
    data class Overlap(
        val aStart: Int,
        val aEnd: Int,
        val bStart: Int,
        val bEnd: Int,
        val meters: Double
    )

    /** 共同路段上的对比结果。 */
    data class Comparison(
        val overlap: Overlap,
        val aDistanceMeters: Double,
        val bDistanceMeters: Double,
        val aDurationMs: Long?,
        val bDurationMs: Long?,
        val aAscentMeters: Double,
        val bAscentMeters: Double,
        val aAvgSpeedKmh: Double?,
        val bAvgSpeedKmh: Double?
    ) {
        /** b 比 a 慢多少（正 = 更慢）。距离比 = bDistance / aDistance。 */
        val distanceRatio: Double get() = if (aDistanceMeters <= 0) 1.0 else bDistanceMeters / aDistanceMeters

        /** b 比 a 慢多少（正 = 更慢）。时长比 = bDuration / aDuration。 */
        val timeRatio: Double? get() {
            val a = aDurationMs ?: return null
            val b = bDurationMs ?: return null
            if (a <= 0) return null
            return b.toDouble() / a.toDouble()
        }

        /** 爬升差（b - a，正 = b 爬得更多）。 */
        val ascentDeltaMeters: Double get() = bAscentMeters - aAscentMeters
    }

    private const val M_PER_DEG_LAT = 111_320.0

    /** 默认命中容差（米）：与轨迹记录的距离规则同量级（精度 30m）。 */
    const val DEFAULT_TOLERANCE_M = 30.0

    /** 共同路段占较短一条的比例下限，低于此值判定为「不是同一条路」。 */
    const val MIN_OVERLAP_RATIO = 0.3

    /** 共同路段绝对长度下限（米），过滤掉路口级别的偶然重合。 */
    const val MIN_OVERLAP_METERS = 200.0

    /** 命中段内允许连续未命中的点数（抗 GPS 噪声/隧道失锁）。 */
    private const val MAX_GAP = 2

    /** 浮点比较容差。 */
    private const val EPS = 1e-6

    /**
     * 对比两条轨迹的共同路段。
     *
     * @return 不是同一条路（重合太短）时返回 null
     */
    fun compare(
        a: List<Sample>,
        b: List<Sample>,
        toleranceMeters: Double = DEFAULT_TOLERANCE_M
    ): Comparison? {
        val overlap = findOverlap(a, b, toleranceMeters) ?: return null

        // aStart / aEnd 都是顶点索引（闭区间），直接切片即可
        val aSlice = a.subList(overlap.aStart, overlap.aEnd + 1)
        val bSlice = b.subList(overlap.bStart, overlap.bEnd + 1)

        val aDur = durationOf(aSlice)
        val bDur = durationOf(bSlice)

        return Comparison(
            overlap = overlap,
            aDistanceMeters = polylineLength(aSlice),
            bDistanceMeters = polylineLength(bSlice),
            aDurationMs = aDur,
            bDurationMs = bDur,
            aAscentMeters = ascentOf(aSlice),
            bAscentMeters = ascentOf(bSlice),
            aAvgSpeedKmh = avgSpeedKmh(overlap.meters, aDur),
            bAvgSpeedKmh = avgSpeedKmh(overlap.meters, bDur)
        )
    }

    /** 找出两条轨迹的最长共同路段。 */
    fun findOverlap(
        a: List<Sample>,
        b: List<Sample>,
        toleranceMeters: Double = DEFAULT_TOLERANCE_M
    ): Overlap? {
        if (a.size < 2 || b.size < 2) return null

        val aLocal = a.map { toLocal(it, a[0]) }
        val bLocal = b.map { toLocal(it, a[0]) }

        // A 折线各段长度 + 累计弧长
        val segLen = DoubleArray(a.size - 1)
        val cum = DoubleArray(a.size)
        for (i in 1 until a.size) {
            segLen[i - 1] = dist(aLocal[i - 1], aLocal[i])
            cum[i] = cum[i - 1] + segLen[i - 1]
        }

        // 每个 B 点投影到 A 折线：命中则记录「所在段序号 + 段内参数 t」。
        // 只记段序号不够：B 的端点常正好落在 A 的顶点上，那时 t=0/1 两种归属都成立，
        // 取到哪一段完全取决于遍历顺序，会随机多算/少算一整段。
        val hitSeg = IntArray(b.size) { -1 }
        val hitT = DoubleArray(b.size)
        for (j in b.indices) {
            var best = Double.MAX_VALUE
            var bestSeg = -1
            var bestT = 0.0
            for (i in 0 until a.size - 1) {
                val p1 = aLocal[i]
                val p2 = aLocal[i + 1]
                val vx = p2.x - p1.x
                val vy = p2.y - p1.y
                val len2 = vx * vx + vy * vy
                val t = if (len2 <= 1e-9) 0.0
                else {
                    val raw = ((bLocal[j].x - p1.x) * vx + (bLocal[j].y - p1.y) * vy) / len2
                    raw.coerceIn(0.0, 1.0)
                }
                val cx = p1.x + t * vx
                val cy = p1.y + t * vy
                val d = sqrt((bLocal[j].x - cx) * (bLocal[j].x - cx) + (bLocal[j].y - cy) * (bLocal[j].y - cy))
                if (d < best) {
                    best = d
                    bestSeg = i
                    bestT = t
                }
            }
            if (best <= toleranceMeters) {
                hitSeg[j] = bestSeg
                hitT[j] = bestT
            }
        }

        // 找最长命中段（允许 MAX_GAP 个连续未命中）
        var bestRun = 0 until 0
        var curStart = -1
        var curEnd = -1
        var gap = 0
        for (j in b.indices) {
            if (hitSeg[j] >= 0) {
                if (curStart < 0) {
                    curStart = j
                } else if (gap > MAX_GAP) {
                    // 断开：结算上一段
                    if (curEnd - curStart > bestRun.last - bestRun.first) bestRun = curStart..curEnd
                    curStart = j
                }
                curEnd = j
                gap = 0
            } else if (curStart >= 0) {
                gap++
                if (gap > MAX_GAP) {
                    if (curEnd - curStart > bestRun.last - bestRun.first) bestRun = curStart..curEnd
                    curStart = -1
                    curEnd = -1
                    gap = 0
                }
            }
        }
        if (curStart >= 0 && curEnd - curStart > bestRun.last - bestRun.first) bestRun = curStart..curEnd
        if (bestRun.isEmpty() || bestRun.first < 0) return null

        val segStart = hitSeg[bestRun.first]
        val segEnd = hitSeg[bestRun.last]
        if (segStart < 0 || segEnd < segStart) return null

        // 换算成顶点索引：t≈1 表示正好落在段的终点，起点后移一个顶点，
        // 否则「B 从 A 的第 5 个点开始」会被算成从第 4 个点开始（白多 100 m）。
        val aStart = if (hitT[bestRun.first] >= 1.0 - EPS) segStart + 1 else segStart
        val aEnd = if (hitT[bestRun.last] >= 1.0 - EPS) segEnd + 1 else segEnd
        if (aEnd <= aStart) return null

        val meters = cum[aEnd] - cum[aStart]

        // 重合度：占较短一条的比例 + 绝对长度下限
        val totalA = cum.last()
        val totalB = polylineLength(b)
        val shorter = minOf(totalA, totalB)
        if (meters < MIN_OVERLAP_METERS) return null
        if (shorter > 0 && meters / shorter < MIN_OVERLAP_RATIO) return null

        return Overlap(aStart, aEnd, bestRun.first, bestRun.last, meters)
    }

    // ===== 内部工具 =====

    private data class P(val x: Double, val y: Double)

    private fun toLocal(s: Sample, origin: Sample): P {
        val lat0 = Math.toRadians(origin.lat)
        val mLng = M_PER_DEG_LAT * cos(lat0)
        return P((s.lng - origin.lng) * mLng, (s.lat - origin.lat) * M_PER_DEG_LAT)
    }

    private fun dist(p: P, q: P): Double {
        val dx = p.x - q.x
        val dy = p.y - q.y
        return sqrt(dx * dx + dy * dy)
    }

    /** 折线总长（米）。 */
    fun polylineLength(points: List<Sample>): Double {
        if (points.size < 2) return 0.0
        val origin = points[0]
        var total = 0.0
        for (i in 1 until points.size) {
            total += dist(toLocal(points[i - 1], origin), toLocal(points[i], origin))
        }
        return total
    }

    /** 时长（毫秒）：首尾时间戳之差，缺时间戳返回 null。 */
    fun durationOf(points: List<Sample>): Long? {
        if (points.size < 2) return null
        val first = points.first().timestamp ?: return null
        val last = points.last().timestamp ?: return null
        val d = last - first
        return if (d >= 0) d else null
    }

    /** 累计爬升（米）：只累计超过阈值的上升段，与记录层口径一致（3m 阈值）。 */
    fun ascentOf(points: List<Sample>, thresholdMeters: Double = 3.0): Double {
        var total = 0.0
        var last: Double? = null
        for (p in points) {
            val alt = p.altitude ?: continue
            val prev = last
            last = alt
            if (prev == null) continue
            val d = alt - prev
            if (d > thresholdMeters) total += d
        }
        return total
    }

    private fun avgSpeedKmh(meters: Double, durationMs: Long?): Double? {
        val d = durationMs ?: return null
        if (d <= 0) return null
        return meters / (d / 1000.0) * 3.6
    }

    /** 两条轨迹是否可判定为「同一条路」。 */
    fun isSameRoute(a: List<Sample>, b: List<Sample>, toleranceMeters: Double = DEFAULT_TOLERANCE_M): Boolean =
        findOverlap(a, b, toleranceMeters) != null

    /** 供测试与调试：两点的近似距离（米）。 */
    fun distanceMeters(a: Sample, b: Sample): Double =
        dist(toLocal(b, a), toLocal(a, a))

    internal fun approxEqual(a: Double, b: Double, eps: Double = 1e-6): Boolean = abs(a - b) < eps
}
