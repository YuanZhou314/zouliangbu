package com.zouliangbu.footprint.data.repo

import com.zouliangbu.footprint.data.db.GearItem
import com.zouliangbu.footprint.data.db.GearItemDao
import com.zouliangbu.footprint.data.mock.MockData
import com.zouliangbu.footprint.data.model.GearCategory
import kotlinx.coroutines.flow.Flow

/**
 * 装备台账的本地（Room）实现。
 *
 * 接后台只换实现类、UI 零改动：分类沿用静态 [MockData.gearCategories]
 * （分类是产品定义，不是用户数据，不进库），装备条目读写本地库。
 * 将来接商城/云同步时换实现即可，UI 不动。
 */
class LocalGearRepository(private val dao: GearItemDao) : GearRepository {

    override fun categories(): List<GearCategory> = MockData.gearCategories

    fun observeItems(): Flow<List<GearItem>> = dao.observeAll()

    suspend fun getItem(id: Long): GearItem? = dao.getById(id)

    /** 新增时补 createdAt（编辑时保留原值）。 */
    suspend fun save(item: GearItem) {
        dao.upsert(
            if (item.id == 0L) item.copy(createdAt = System.currentTimeMillis()) else item
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
