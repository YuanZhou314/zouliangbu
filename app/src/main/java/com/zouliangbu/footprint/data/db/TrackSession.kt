package com.zouliangbu.footprint.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一次户外记录会话：从用户按下「开始记录」到「停止记录」。
 *
 * @property id               自增主键
 * @property startTime        开始时间（epoch millis）
 * @property endTime          结束时间（epoch millis）；为 null 时表示「正在记录」
 * @property totalDistanceMeters 最终累计距离（米），停止时由 service 写入
 * @property name             轨迹名。自己记录的会话为 null（UI 按开始时间兜底展示），
 *                            导入的 GPX 取文件里的 `<trk><name>`。v2 新增，可空。
 * @property pausedMs         累计暂停时长（毫秒）。v3 新增，默认 0。
 *                            **活动时长 = endTime - startTime - pausedMs**，
 *                            UI 展示时长/配速一律用这个公式，不要直接用 endTime - startTime。
 * @property ascentMeters     累计爬升（米）。v4 新增，默认 0。
 *                            由 [com.zouliangbu.footprint.util.Elevation] 算出，
 *                            老记录为 0（没算过，不猜）。
 * @property descentMeters    累计下降（米）。v4 新增，默认 0。
 * @property source           会话来源：recorded=自己记录 / imported=外部导入。
 *                            v8 新增，默认 recorded。「我的记录」统计只算 recorded——
 *                            导入别人的轨迹不是自己的运动量；跟着线路自己走出来的
 *                            轨迹走的是记录流程，自然是 recorded，无需特判。
 */
@Entity(tableName = "track_sessions")
data class TrackSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val name: String? = null,
    val pausedMs: Long = 0L,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val source: String = SOURCE_RECORDED
) {
    /** 活动时长（扣暂停）。endTime 为 null（记录中）时按 startTime 兜底返回 0。 */
    val activeDurationMs: Long
        get() = ((endTime ?: startTime) - startTime - pausedMs).coerceAtLeast(0L)

    companion object {
        const val SOURCE_RECORDED = "recorded"
        const val SOURCE_IMPORTED = "imported"
    }
}
