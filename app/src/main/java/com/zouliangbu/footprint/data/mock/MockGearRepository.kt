package com.zouliangbu.footprint.data.mock

import com.zouliangbu.footprint.data.model.GearCategory
import com.zouliangbu.footprint.data.repo.GearRepository

/** Mock 装备分类数据源。将来替换为 RemoteGearRepository(Retrofit)。 */
class MockGearRepository : GearRepository {
    override fun categories(): List<GearCategory> = MockData.gearCategories
}
