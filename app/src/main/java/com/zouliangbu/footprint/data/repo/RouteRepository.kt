package com.zouliangbu.footprint.data.repo

import com.zouliangbu.footprint.data.model.RouteCatalog
import kotlinx.coroutines.flow.Flow

/**
 * 线路数据源。单个 observeCatalog() 覆盖推荐位 + 分组，
 * 将来接后台可演进为搜索接口（search(keyword)）。
 */
interface RouteRepository {
    fun observeCatalog(): Flow<RouteCatalog>
}
