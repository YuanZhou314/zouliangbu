package com.zouliangbu.footprint.data.mock

import com.zouliangbu.footprint.data.model.FeedItem
import com.zouliangbu.footprint.data.repo.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Mock 动态数据源。将来替换为 RemoteFeedRepository(Retrofit)。 */
class MockFeedRepository : FeedRepository {
    override fun observeFeed(): Flow<List<FeedItem>> = flowOf(MockData.feed)
}
