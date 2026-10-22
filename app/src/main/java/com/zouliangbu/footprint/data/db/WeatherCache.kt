package com.zouliangbu.footprint.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 天气缓存表（Room v6）。按经纬度桶（0.05° 量化）缓存最近一次成功拉取的逐小时预报。
 *
 * 设计为整块覆盖：每次刷新用新 JSON 覆盖整行，不存历史快照
 * 景观预报只需要最新一份，存历史反而浪费且无产品价值。
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val bucketKey: String,
    val lat: Double,
    val lng: Double,
    val forecastJson: String,
    val cachedAt: Long,
)
