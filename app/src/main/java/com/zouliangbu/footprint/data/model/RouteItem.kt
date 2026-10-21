package com.zouliangbu.footprint.data.model

import kotlinx.serialization.Serializable

/**
 * 线路条目（线路页分组网格 / 推荐位共用）。字段与 ContentDtos.RouteItem 一致。
 */
@Serializable
data class RouteItem(
    val id: String,
    val name: String,
    val region: String,
    val distanceKm: Double,
    val ascentMeters: Int,
    /** 0..N，UI 映射到渐变调色板（CoverPlaceholder） */
    val coverStyle: Int,
    val tags: List<String> = emptyList(),
    val coverUrl: String? = null
)

/** 线路分组，如「口碑山野线」「新手友好线」。 */
@Serializable
data class RouteSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val routes: List<RouteItem>
)

/** 线路页整体数据：横向推荐位 + 分组列表。 */
@Serializable
data class RouteCatalog(
    val recommendations: List<RouteItem> = emptyList(),
    val sections: List<RouteSection> = emptyList()
)
