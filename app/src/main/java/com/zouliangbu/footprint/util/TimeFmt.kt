package com.zouliangbu.footprint.util

import java.util.concurrent.TimeUnit

object TimeFmt {

    /**
     * 把毫秒格式化为 mm:ss（不足一小时）或 h:mm:ss（一小时以上）。
     */
    fun elapsed(elapsedMs: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(elapsedMs.coerceAtLeast(0L))
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
               else "%02d:%02d".format(m, s)
    }

    /** 米 / 公里 自动切换的展示。 */
    fun distance(meters: Float): String =
        if (meters >= 1000f) "%.2f km".format(meters / 1000f)
        else "%d m".format(meters.toInt())
}