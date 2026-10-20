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
 */
@Entity(tableName = "track_sessions")
data class TrackSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val name: String? = null
)
