package com.zouliangbu.footprint.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条 GPS 定位采样点，属于某个 TrackSession。
 *
 * @property sessionId  所属会话的外键
 * @property lat        纬度（GCJ-02 坐标系，因 AMap 输出就是 GCJ-02）
 * @property lng        经度（GCJ-02）
 * @property altitude   海拔（米）
 * @property timestamp  采样时刻（epoch millis）
 * @property accuracy   水平精度（米）。超过 30 米的点会被 service 丢弃。
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = TrackSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val lat: Double,
    val lng: Double,
    val altitude: Double,
    val timestamp: Long,
    val accuracy: Float
)