package com.zouliangbu.footprint.data.model

import kotlinx.serialization.Serializable

/**
 * 首页「同城动态」帖子（列表卡片）。字段与 ContentDtos.FeedItem 一致。
 *
 * coverStyle/coverUrl 预留：coverUrl 由 Remote 实现填入（相对路径，需拼 BASE_URL），
 * UI 用 CoverPlaceholder(coverUrl=…) 展示，null 时回退渐变占位色块。
 */
@Serializable
data class FeedItem(
    val id: String,
    val title: String,
    val author: String,
    val likeCount: Int,
    val city: String,
    /** 0..N，UI 映射到渐变调色板（CoverPlaceholder） */
    val coverStyle: Int,
    /** 宽/高 比，制造瀑布流两列错落（0.8 ~ 1.4） */
    val coverAspectRatio: Float = 1f,
    val coverUrl: String? = null
)
