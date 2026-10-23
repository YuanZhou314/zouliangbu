package com.zouliangbu.footprint.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 线路轨迹离线缓存（循迹 a，ROADMAP 循迹缺口一）。
 *
 * 线路详情页「下载离线」后落库，进山无信号时也能看参考线 / 跟着走。
 * 与 [RouteCachePoint] 一对一：一条线路缓存一份轨迹点。
 *
 * @property routeId      服务端线路 id（`route_detail.id`），唯一。
 * @property name         线路名（展示 / 列表用）。
 * @property fetchedAt    抓取时间（epoch millis），用于「是否过期」判断与排序。
 * @property pointCount   轨迹点数（冗余，省一次 COUNT）。
 */
@Entity(
    tableName = "route_cache",
    indices = [Index("routeId", unique = true)]
)
data class RouteCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val name: String,
    val fetchedAt: Long,
    val pointCount: Int
)

/**
 * 线路离线缓存的一条轨迹点（GCJ-02，与服务端返回一致，无需转换）。
 */
@Entity(
    tableName = "route_cache_point",
    foreignKeys = [ForeignKey(
        entity = RouteCache::class,
        parentColumns = ["id"],
        childColumns = ["cacheId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cacheId")]
)
data class RouteCachePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cacheId: Long,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val altitude: Double?
)
