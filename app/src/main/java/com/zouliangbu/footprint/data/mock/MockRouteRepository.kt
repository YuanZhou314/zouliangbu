package com.zouliangbu.footprint.data.mock

import com.zouliangbu.footprint.data.model.RouteCatalog
import com.zouliangbu.footprint.data.repo.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Mock 线路数据源。将来替换为 RemoteRouteRepository(Retrofit)。 */
class MockRouteRepository : RouteRepository {
    override fun observeCatalog(): Flow<RouteCatalog> = flowOf(MockData.routeCatalog)
}
