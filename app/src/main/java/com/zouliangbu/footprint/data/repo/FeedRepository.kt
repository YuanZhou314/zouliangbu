package com.zouliangbu.footprint.data.repo

import com.zouliangbu.footprint.data.model.FeedItem
import kotlinx.coroutines.flow.Flow

/**
 * 首页动态数据源。
 * 本期为 Mock 实现；将来接后台时新增 RemoteFeedRepository（Retrofit），
 * 只在 FootprintApp 里换一行，UI / ViewModel 零改动。
 */
interface FeedRepository {
    fun observeFeed(): Flow<List<FeedItem>>
}
