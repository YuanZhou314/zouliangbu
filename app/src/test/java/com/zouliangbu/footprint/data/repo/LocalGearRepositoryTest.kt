package com.zouliangbu.footprint.data.repo

import com.zouliangbu.footprint.data.db.GearItem
import com.zouliangbu.footprint.data.db.GearItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LocalGearRepository 的 save() 逻辑（新增补 createdAt / 编辑保留）。
 * DAO 用内存假实现，不碰 Room/Context，纯 JVM。
 */
class LocalGearRepositoryTest {

    /** 内存假 DAO：记录 upsert 调用并模拟 id 自增。 */
    private class FakeGearItemDao : GearItemDao {
        val upserts = mutableListOf<GearItem>()
        val deletedIds = mutableListOf<Long>()
        private val store = MutableStateFlow<List<GearItem>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<GearItem>> = store
        override suspend fun getById(id: Long): GearItem? = store.value.firstOrNull { it.id == id }
        override suspend fun upsert(item: GearItem) {
            upserts.add(item)
            val saved = if (item.id == 0L) item.copy(id = nextId++) else item
            store.value = store.value.filterNot { it.id == saved.id } + saved
        }
        override suspend fun deleteById(id: Long) {
            deletedIds.add(id)
            store.value = store.value.filterNot { it.id == id }
        }
    }

    @Test
    fun `新增时写入 createdAt`() = runBlocking {
        val dao = FakeGearItemDao()
        val repo = LocalGearRepository(dao)
        repo.save(GearItem(name = "头灯", categoryId = "g6"))
        val saved = dao.upserts.single()
        assertTrue("新增应补 createdAt", saved.createdAt > 0L)
        assertEquals(0L, saved.id) // id 仍由 DAO 侧生成
    }

    @Test
    fun `编辑时保留原 createdAt`() = runBlocking {
        val dao = FakeGearItemDao()
        val repo = LocalGearRepository(dao)
        val original = GearItem(id = 5L, name = "炉头", categoryId = "g6", createdAt = 123456789L)
        repo.save(original)
        assertEquals(123456789L, dao.upserts.single().createdAt)
    }

    @Test
    fun `编辑改字段不影响 createdAt`() = runBlocking {
        val dao = FakeGearItemDao()
        val repo = LocalGearRepository(dao)
        repo.save(GearItem(id = 5L, name = "炉头", categoryId = "g6", note = "改装", createdAt = 42L))
        val saved = dao.upserts.single()
        assertEquals(42L, saved.createdAt)
        assertEquals("改装", saved.note)
    }

    @Test
    fun `delete 委托 DAO`() = runBlocking {
        val dao = FakeGearItemDao()
        val repo = LocalGearRepository(dao)
        repo.delete(9L)
        assertEquals(listOf(9L), dao.deletedIds)
    }

    @Test
    fun `categories 返回静态分类列表`() {
        val repo = LocalGearRepository(FakeGearItemDao())
        val cats = repo.categories()
        assertTrue("至少 8 个分类", cats.size >= 8)
        assertTrue("id 应以 g 开头", cats.all { it.id.startsWith("g") })
    }
}
