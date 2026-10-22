package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint
import kotlin.math.abs

/**
 * 把轨迹切成等距桶，生成图表用的序列（速度 / 配速 / 海拔 / 累计爬升下降）。
 *
 * 为什么按**距离**切而不是按点数切：GPS 采样间隔并不均匀（等红灯时点密、跑起来时点疏），
 * 按点数切会让慢速段的曲线被拉长，图看起来"忽快忽慢"其实是采样密度造成的假象。
 *
 * 几个必须处理的边界：
 *  - 距离规则与 [TrackMetrics] 一致（精度 > 30 m 或单段 > 500 m 不计），否则漂移点会造出
 *    一个"瞬间 200 km/h"的尖峰，把整张图的纵轴压扁；
 *  - 静止桶（位移 < [MIN_MOVE_M]）不出速度点，否则满屏 0；
 *  - 时间戳倒退（导入文件里偶发）直接跳过该段，不产生负时长。
 */
object TrackSeries {

    /** 每个桶的距离（米）。100 m 在 18 km 轨迹上约 180 个点，折线够细腻又不至于太重。 */
    const val BUCKET_METERS = 100.0

    /** 桶内位移小于该值视为静止，不计速度（与 Pace 的口径一致）。 */
    const val MIN_MOVE_M = 10.0

    /** 一条曲线。values 按距离均匀排列，min/max 用于画纵轴。 */
    data class Series(
        val values: List<Float>,
        val min: Float = 0f,
        val max: Float = 0f
    ) {
        /** 少于 2 个点画不出线，UI 侧直接显示"数据不足"。 */
        val drawable: Boolean get() = values.size >= 2 && max > min
    }

    data class Bundle(
        val speedKmh: Series = Series(emptyList()),
        val paceSecPerKm: Series = Series(emptyList()),
        val altitude: Series = Series(emptyList()),
        /** 累计爬升（米），从 0 单调递增。 */
        val cumulativeAscent: Series = Series(emptyList()),
        /** 累计下降（米），从 0 单调递增。 */
        val cumulativeDescent: Series = Series(emptyList()),
        /** 横轴总长（公里）。 */
        val totalKm: Float = 0f
    )

    fun build(points: List<TrackPoint>, bucketMeters: Double = BUCKET_METERS): Bundle {
        if (points.size < 2 || bucketMeters <= 0.0) return Bundle()

        val speed = mutableListOf<Float>()
        val pace = mutableListOf<Float>()
        val alt = mutableListOf<Float>()
        val asc = mutableListOf<Float>()
        val desc = mutableListOf<Float>()

        var sinceBucket = 0.0      // 当前桶已累计的距离
        var bucketMs = 0L          // 当前桶的时长
        var bucketAltSum = 0.0
        var bucketAltCount = 0
        var cumAscent = 0.0
        var cumDescent = 0.0
        var lastAlt: Double? = null
        var totalMeters = 0.0

        fun flushBucket() {
            if (bucketAltCount > 0) alt.add((bucketAltSum / bucketAltCount).toFloat())
            if (sinceBucket >= MIN_MOVE_M && bucketMs > 0L) {
                val mps = sinceBucket / (bucketMs / 1000.0)
                if (mps.isFinite() && mps >= 0.0) {
                    speed.add((mps * 3.6).toFloat())
                    // 配速只在真正在走的时候有意义；< 0.5 km/h 相当于站着，跳过
                    if (mps * 3.6 >= 0.5) {
                        pace.add((1000.0 / mps).toFloat())
                    }
                }
            }
            asc.add(cumAscent.toFloat())
            desc.add(cumDescent.toFloat())
            sinceBucket = 0.0
            bucketMs = 0L
            bucketAltSum = 0.0
            bucketAltCount = 0
        }

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            // 距离规则与 TrackMetrics 保持一致
            val seg = TrackMetrics.countedSegment(prev, cur)
            val dt = cur.timestamp - prev.timestamp

            // 海拔：每个点都累计，抖动由 [Elevation.ASCENT_THRESHOLD_M] 过滤
            val a = cur.altitude
            if (a > 0.0) {
                lastAlt?.let { last ->
                    val d = a - last
                    if (abs(d) >= Elevation.ASCENT_THRESHOLD_M) {
                        if (d > 0) cumAscent += d else cumDescent += -d
                        lastAlt = a
                    }
                } ?: run { lastAlt = a }
                bucketAltSum += a
                bucketAltCount++
            }

            if (seg == null || dt <= 0L) {
                // 不可信的段：不算距离，但桶内的海拔仍然保留（否则会丢点）
                continue
            }

            var remaining = seg.toDouble()
            totalMeters += seg
            // 一段可能跨多个桶（点稀疏时），按剩余的桶容量逐段切分
            while (remaining > 0.0) {
                val capacity = bucketMeters - sinceBucket
                if (remaining >= capacity) {
                    // 按距离比例分摊时间：桶内速度才不会被"最后一个大段"带偏
                    val frac = if (seg > 0f) (capacity / seg).coerceIn(0.0, 1.0) else 1.0
                    sinceBucket = bucketMeters
                    bucketMs += (dt * frac).toLong()
                    remaining -= capacity
                    flushBucket()
                } else {
                    sinceBucket += remaining
                    val frac = if (seg > 0f) (remaining / seg).coerceIn(0.0, 1.0) else 1.0
                    bucketMs += (dt * frac).toLong()
                    remaining = 0.0
                }
            }
        }
        // 收尾：不足一桶的尾巴也要画出来。
        // 注意最后几个点可能刚好落在桶边界上（sinceBucket == 0），此时尾巴距离为 0
        // 但**累计值还在涨**——不补一笔的话曲线末端会少掉最后一截爬升/下降。
        if (sinceBucket > 0.0 || bucketAltCount > 0) {
            flushBucket()
        } else if (asc.isNotEmpty() &&
            (asc.last() != cumAscent.toFloat() || desc.last() != cumDescent.toFloat())
        ) {
            asc.add(cumAscent.toFloat())
            desc.add(cumDescent.toFloat())
            alt.add(alt.last())
        }

        return Bundle(
            speedKmh = seriesOf(speed),
            paceSecPerKm = seriesOf(pace),
            altitude = seriesOf(alt),
            cumulativeAscent = seriesOf(asc),
            cumulativeDescent = seriesOf(desc),
            totalKm = (totalMeters / 1000.0).toFloat()
        )
    }

    private fun seriesOf(values: List<Float>): Series {
        if (values.isEmpty()) return Series(emptyList())
        // 纵轴用 1%/99% 分位而不是绝对极值：单个 GPS 尖峰不该决定整张图的比例
        val sorted = values.sorted()
        val lo = sorted[(sorted.size * 0.01).toInt().coerceIn(0, sorted.size - 1)]
        val hi = sorted[(sorted.size * 0.99).toInt().coerceIn(0, sorted.size - 1)]
        return Series(values, lo, if (hi > lo) hi else lo + 1f)
    }
}
