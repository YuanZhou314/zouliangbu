package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint

/**
 * 分段统计（ROADMAP P3.5）：每公里一段的距离/爬升/用时。
 *
 * 常见用法是「看这一公里用时多久」，徒步者靠它判断哪一段最累。
 *
 * 约定：
 *   - 每**满 1 km** 结算一段；
 *   - 最后不足 1 km 的尾巴单独作为「零头段」（[Split.partial] = true），
 *     它的配速**不折算**（把 300 m 折算成整公里配速会误导）。
 *
 * 爬升是**段内独立**计算的：每段单独做中值滤波 + 阈值累加，
 * 避免上一段末尾的噪声影响下一段起点的参考高度。
 */
object TrackSplits {

    /** 一段的分段统计。 */
    data class Split(
        val index: Int,
        val distanceM: Double,
        val ascentM: Double,
        val durationMs: Long,
        val partial: Boolean
    ) {
        /**
         * 本段配速（秒/公里）。
         *
         * 零头段不折算（partial = true 时返回 null），UI 显示「—」。
         */
        val paceSecPerKm: Double?
            get() {
                if (partial || distanceM <= 0.0 || durationMs <= 0L) return null
                return durationMs / 1000.0 / (distanceM / 1000.0)
            }

        /** 本段平均速度（米/秒）。 */
        val speedMps: Double
            get() = if (durationMs <= 0L) 0.0 else distanceM / (durationMs / 1000.0)
    }

    /**
     * 按公里切段。
     *
     * @param points 轨迹点（按时间升序）
     * @return 各段统计；点数不足 2 时返回空列表
     */
    fun splitsOf(points: List<TrackPoint>): List<Split> {
        if (points.size < 2) return emptyList()
        val out = ArrayList<Split>()

        var segStartIndex = 0
        var segDistance = 0.0
        val segAltitudes = ArrayList<Double>()
        var index = 1

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]

            // 距离增量沿用 TrackMetrics 的同一套规则（精度 30 m / 单段 500 m 不计），
            // 保证分段合计与总距离口径一致。
            TrackMetrics.countedSegment(prev, cur)?.let { segDistance += it }
            segAltitudes.add(cur.altitude)

            if (segDistance >= 1000.0) {
                val ascent = if (segAltitudes.size >= 2) {
                    Elevation.ascentOf(segAltitudes).ascentMeters
                } else {
                    0.0
                }
                val duration = cur.timestamp - points[segStartIndex].timestamp
                out.add(
                    Split(
                        index = index,
                        distanceM = segDistance,
                        ascentM = ascent,
                        durationMs = duration,
                        partial = false
                    )
                )
                index += 1
                segDistance = 0.0
                segAltitudes.clear()
                segStartIndex = i
            }
        }

        // 收尾：剩下不足 1 km 的尾巴。
        if (segDistance > 0.0 || segAltitudes.size >= 2) {
            val ascent = if (segAltitudes.size >= 2) {
                Elevation.ascentOf(segAltitudes).ascentMeters
            } else {
                0.0
            }
            val duration = points.last().timestamp - points[segStartIndex].timestamp
            out.add(
                Split(
                    index = index,
                    distanceM = segDistance,
                    ascentM = ascent,
                    durationMs = duration,
                    partial = true
                )
            )
        }
        return out
    }
}
