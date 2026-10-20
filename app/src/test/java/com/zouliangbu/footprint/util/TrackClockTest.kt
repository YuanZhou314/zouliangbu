package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackSession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时长计算（P2.4 暂停）与活动时长的回归测试。
 *
 * 关键性质：**暂停期间时长不走**、**多次暂停累加**、**永远不会出现负数**
 * （负数会直接显示成 "-1:-5" 这种东西，用户只会认为 App 坏了）。
 */
class TrackClockTest {

    @Test
    fun `没有暂停时时长就是流逝时间`() {
        assertEquals(60_000L, TrackClock.activeElapsedMs(160_000L, 100_000L, 0L))
    }

    @Test
    fun `已结束的暂停从时长里扣掉`() {
        // 总共走了 60s，其中暂停 20s
        assertEquals(40_000L, TrackClock.activeElapsedMs(160_000L, 100_000L, 20_000L))
    }

    @Test
    fun `暂停中时长冻结`() {
        val start = 100_000L
        val now = 160_000L
        // 20s 时开始暂停，之后再也没恢复：活动时长应停在 20s，而不是继续增长
        val pausedAt = 120_000L
        assertEquals(20_000L, TrackClock.activeElapsedMs(now, start, 0L, pausedAt))
        // 即使又过了 10 分钟，依然 20s
        assertEquals(20_000L, TrackClock.activeElapsedMs(now + 600_000L, start, 0L, pausedAt))
    }

    @Test
    fun `多次暂停累加`() {
        // 100s 里暂停两次各 20s + 10s，还剩 70s。当前处于第三次暂停中又过了 5s → 65s
        val value = TrackClock.activeElapsedMs(
            nowElapsed = 200_000L,
            startedElapsed = 100_000L,
            pausedMs = 30_000L,
            pausedAtElapsed = 195_000L
        )
        assertEquals(65_000L, value)
    }

    @Test
    fun `时钟回退或数值异常时不会返回负数`() {
        assertEquals(0L, TrackClock.activeElapsedMs(100_000L, 100_000L, 0L))
        // 暂停时长比总时长还长（数据脏了）也不返回负数
        assertEquals(0L, TrackClock.activeElapsedMs(110_000L, 100_000L, 50_000L))
    }

    @Test
    fun `会话活动时长扣掉暂停`() {
        val session = TrackSession(
            startTime = 1_000_000L,
            endTime = 1_600_000L,   // 墙上时钟经过 10 分钟
            pausedMs = 240_000L     // 其中暂停 4 分钟
        )
        assertEquals(360_000L, session.activeDurationMs)
    }

    @Test
    fun `记录中的会话活动时长兜底为 0 而不是负数`() {
        val running = TrackSession(startTime = 1_000_000L, endTime = null)
        assertEquals(0L, running.activeDurationMs)
    }
}
