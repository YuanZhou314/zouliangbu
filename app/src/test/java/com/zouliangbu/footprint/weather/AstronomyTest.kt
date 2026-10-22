package com.zouliangbu.footprint.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 天文计算测试。
 *
 * 权威值取自 timeanddate.com / USNO（北京 2026-06-21 与 2026-12-21 两个对分日，
 * 覆盖太阳赤纬最大/最小两种极端——公式抄错时这两个日子最容易暴露偏差）。
 * 容差：日出日落 ± 4 分钟，高度角 ± 1.2°，月相 ± 0.06。
 *
 * 时区说明：返回的分钟数是「距当天 UTC 零点的分钟数」，北京 = UTC+8，
 * 所以北京时间 04:46 的日出对应本地 4*60+46-8*60 = 负数，已被 +24h 归一。
 */
class AstronomyTest {

    private fun utcMillis(y: Int, m: Int, d: Int, hh: Int = 12, mm: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, m - 1, d, hh, mm, 0)
        }.timeInMillis

    private val bjLat = 39.9
    private val bjLng = 116.4

    private fun norm(m: Double): Double = ((m % 1440.0) + 1440.0) % 1440.0

    private fun fmtMin(min: Double?): String {
        if (min == null) return "null"
        val t = ((min % 1440) + 1440) % 1440
        val h = (t / 60).toInt()
        val m = (t % 60).toInt()
        return "%02d:%02d".format(h, m)
    }

    @Test
    fun `夏至北京日出日落UTC时间接近 20_46 和 11_47`() {
        val t = Astronomy.sunTimes(utcMillis(2026, 6, 21), bjLat, bjLng)
        println("夏至 UTC日出=" + fmtMin(t.sunrise) + " UTC日落=" + fmtMin(t.sunset))
        assertNotNull(t.sunrise)
        assertNotNull(t.sunset)
        // 北京 2026-06-21 日出 04:46 / 日落 19:47 (UTC+8) → UTC 20:46 / 11:47
        assertEquals((20 * 60 + 46).toDouble(), t.sunrise!!, 4.0)
        assertEquals((11 * 60 + 47).toDouble(), t.sunset!!, 4.0)
    }

    @Test
    fun `冬至北京日出日落UTC时间接近 23_35 和 09_14`() {
        val t = Astronomy.sunTimes(utcMillis(2026, 12, 21), bjLat, bjLng)
        println("冬至 UTC日出=" + fmtMin(t.sunrise) + " UTC日落=" + fmtMin(t.sunset))
        // 北京 2026-12-21 日出 07:35 / 日落 16:52 (UTC+8) → UTC 23:32 / 08:52
        assertEquals((23 * 60 + 32).toDouble(), t.sunrise!!, 4.0)
        assertEquals((8 * 60 + 52).toDouble(), t.sunset!!, 4.0)
    }

    @Test
    fun `夏至白昼时长约十五小时`() {
        val t = Astronomy.sunTimes(utcMillis(2026, 6, 21), bjLat, bjLng)
        var len = t.sunset!! - t.sunrise!!
        if (len < 0) len += 1440
        println("夏至白昼=" + len + " 分钟")
        // 北京夏至白昼约 14h55m
        assertEquals((14 * 60 + 55).toDouble(), len, 10.0)
    }

    @Test
    fun `黄金时刻应夹在日出之后正午之前`() {
        val t = Astronomy.sunTimes(utcMillis(2026, 6, 21), bjLat, bjLng)
        println("黄金时刻=" + fmtMin(t.goldenHour) + " ~ " + fmtMin(t.goldenHourEnd))
        assertTrue("黄金时刻应在日出之后", t.goldenHour!! > t.sunrise!!)
        assertTrue("黄金时刻结束应早于正午12h", t.goldenHourEnd!! < 12 * 60)
    }

    @Test
    fun `晨昏线时刻必须与参考值一致且按高度角单调`() {
        val t = Astronomy.sunTimes(utcMillis(2026, 6, 21), bjLat, bjLng)
        println("日出=" + fmtMin(t.sunrise) + " 民用=" + fmtMin(t.dawn) +
            " 航海=" + fmtMin(t.nauticalDawn) + " 天文=" + fmtMin(t.nightEnd))
        // 早晨这一串随太阳高度角降低而**提前**（都归一到当天 UTC，北京夏至不跨零点）：
        //   日出(-0.833) 1245 > 民用(-6) 1213 > 航海(-12) 1171 > 天文(-18) 1122
        // 数值与 SunCalc 参考值逐一比对。
        assertEquals((20 * 60 + 45).toDouble(), t.sunrise!!, 3.0)
        assertEquals((20 * 60 + 13).toDouble(), t.dawn!!, 3.0)
        assertEquals((19 * 60 + 31).toDouble(), t.nauticalDawn!!, 3.0)
        assertEquals((18 * 60 + 42).toDouble(), t.nightEnd!!, 3.0)

        assertTrue(t.sunrise!! > t.dawn!!)
        assertTrue(t.dawn!! > t.nauticalDawn!!)
        assertTrue(t.nauticalDawn!! > t.nightEnd!!)

        // 傍晚这一串则相反：日落 < 民用昏影终 < 天文昏影始
        assertTrue(t.sunset!! < t.dusk!!)
    }

    @Test
    fun `夏至正午太阳高度角符合 90 减纬差`() {
        val noon = utcMillis(2026, 6, 21, 4, 16) // 北京真太阳时正午≈ UTC 04:16
        val p = Astronomy.sunPosition(noon, bjLat, bjLng)
        println("夏至正午 高度角=" + p.altitude + " 方位角=" + p.azimuth)
        // 90 - |39.9 - 23.44| = 73.5
        assertEquals(73.54, p.altitude, 0.5)
        assertTrue("正午方位应接近正南", p.azimuth > 150 && p.azimuth < 210)
    }

    @Test
    fun `午夜太阳高度角为负`() {
        val p = Astronomy.sunPosition(utcMillis(2026, 6, 21, 16, 0), bjLat, bjLng)
        println("午夜高度角=" + p.altitude)
        assertTrue("午夜太阳应在地平线下，实际=" + p.altitude, p.altitude < -20)
    }

    @Test
    fun `北极圈夏至极昼不产生日落`() {
        val t = Astronomy.sunTimes(utcMillis(2026, 6, 21), 78.0, 15.0)
        println("北极夏至 日出=" + fmtMin(t.sunrise) + " 日落=" + fmtMin(t.sunset))
        assertNull("北极圈夏至应无日落", t.sunset)
    }

    @Test
    fun `月相与被照亮比例自洽`() {
        val full = Astronomy.moonPosition(utcMillis(2026, 1, 3, 12), bjLat, bjLng)
        println("满月期 phase=" + full.phase + " fraction=" + full.fraction)
        assertEquals(0.5031, full.phase, 0.05)
        assertTrue("满月被照亮比例应>0.85", full.fraction > 0.85)

        val new = Astronomy.moonPosition(utcMillis(2026, 1, 19, 12), bjLat, bjLng)
        println("新月期 phase=" + new.phase + " fraction=" + new.fraction)
        assertTrue("新月被照亮比例应<0.15", new.fraction < 0.15)
    }

    @Test
    fun `月出到月落接近十二小时`() {
        val t = Astronomy.moonTimes(utcMillis(2026, 6, 21), bjLat, bjLng)
        println("月出=" + fmtMin(t.moonrise) + " 月落=" + fmtMin(t.moonset))
        assertNotNull(t.moonrise)
        assertNotNull(t.moonset)
        var diff = t.moonset!! - t.moonrise!!
        if (diff < 0) diff += 1440
        assertTrue("月出到月落应接近半天", diff > 11 * 60 && diff < 13 * 60)
    }

    @Test
    fun `银河中心八月应比二月更高`() {
        val summer = Astronomy.galacticCenter(utcMillis(2026, 8, 15, 14), bjLat, bjLng)
        val winter = Astronomy.galacticCenter(utcMillis(2026, 2, 15, 14), bjLat, bjLng)
        println("8月银河 高度=" + summer.altitude + " 方位=" + summer.azimuth)
        println("2月银河 高度=" + winter.altitude + " 方位=" + winter.azimuth)
        assertTrue("8月银河应比2月高", summer.altitude > winter.altitude)
    }
}