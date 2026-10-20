package com.zouliangbu.footprint.data.model

/**
 * 首页「同城动态」卡片。
 *
 * coverUrl 预留：将来接网络图片时由 Remote 实现填入；
 * 本期恒为 null，UI 用 coverStyle 映射渐变占位色块。
 */
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
