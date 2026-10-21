package com.zouliangbu.footprint.data.model

import kotlinx.serialization.Serializable

/**
 * 首页动态详情页。字段与服务端 ContentDtos.FeedDetail 一一对应。
 *
 * 契约：id/title/author/likeCount/city/coverStyle 服务端必须返回（无默认值）；
 * content 允许空串但不为 null；coverAuthor / createdAt / coverUrl 可为 null。
 */
@Serializable
data class FeedDetail(
    val id: String,
    val title: String,
    val author: String,
    val likeCount: Int,
    val city: String,
    val coverStyle: Int,
    val coverUrl: String? = null,
    val coverAspectRatio: Float = 1f,
    val coverAuthor: String? = null,
    val content: String = "",
    val createdAt: String? = null
)
