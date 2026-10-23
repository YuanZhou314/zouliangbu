package com.zouliangbu.footprint.weather

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 蓝调时刻（太阳 -6°~-4°）纯函数测试。
 *
 * 物理约束（不依赖权威表，靠高度角单调性自洽）：
 *   早晨太阳高度角递增：dawn(-6°) < blueRise(-4°) < sunrise(-0.833°)
 *   傍晚递减：sunset(-0.833°) > blueFall(-4°) > dusk(-6°)
 * 蓝调窗口长度随纬度/季节变化，但正常纬度下不会超过 1 小时。
 */
class LandscapeBlueHourTest {

    private fun utcMillis(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, m - 1, d, 12, 0, 0)
        }.timeInMillis

    private val bjLat = 39.9
    private val bjLng = 116.4

    @Test
    fun `晨蓝调夹在民用晨光与日出之间`() {
        val st = Astronomy.sunTimes(utcMillis(2026, 6, 21), bjLat, bjLng)
        val b = Landscape.blueHour(st)
        val dawn = st.dawn!!
        val rise = b.morningEnd!!
        assertTrue("晨蓝调开始(-6°)应早于结束(-4°)", dawn < rise)
        assertTrue("晨蓝调结束应早于日出", rise < st.sunrise!!)
        // 深圳北京纬度下 -6°→-4° 大约几分钟到半小时，不应超过 60 分钟
        assertTrue("晨蓝调窗口应小于 60 分钟，实际 ${rise - dawn}", rise - dawn in 0.0..60.0)
    }

    @Test
    fun `暮蓝调夹在日落与民用昏影终之间`() {
        val st = Astronomy.sunTimes(utcMillis(2026, 6, 21), bjLat, bjLng)
        val b = Landscape.blueHour(st)
        val fall = b.eveningStart!!
        val dusk = b.eveningEnd!!
        assertTrue("日落应早于暮蓝调开始", st.sunset!! < fall)
        assertTrue("暮蓝调开始(-4°)应早于结束(-6°)", fall < dusk)
        assertTrue("暮蓝调窗口应小于 60 分钟，实际 ${dusk - fall}", dusk - fall in 0.0..60.0)
    }

    @Test
    fun `高纬冬季蓝调窗口显著变长但仍单调`() {
        // 北纬 60 度冬至，太阳贴着地平线爬，-6°~-4° 走得很慢
        val st = Astronomy.sunTimes(utcMillis(2026, 12, 21), 60.0, 24.9)
        val b = Landscape.blueHour(st)
        val ms = b.morningStart
        val me = b.morningEnd
        if (ms != null && me != null) {
            assertTrue("晨蓝调仍应单调", ms < me)
            assertTrue("窗口不应超过 2 小时", me - ms < 120.0)
        }
        // 高纬冬季两段至少一段存在
        assertTrue(
            "高纬冬季应有蓝调",
            b.morningStart != null || b.eveningStart != null,
        )
    }

    @Test
    fun `极昼当天不产生蓝调`() {
        val st = Astronomy.sunTimes(utcMillis(2026, 6, 21), 78.0, 15.0)
        val b = Landscape.blueHour(st)
        assertNull("极昼无晨蓝调", b.morningStart)
        assertNull("极昼无暮蓝调", b.eveningStart)
    }
}
