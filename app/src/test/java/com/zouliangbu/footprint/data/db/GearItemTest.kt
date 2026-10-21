package com.zouliangbu.footprint.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** GearItem entity 的纯逻辑：默认值、状态常量、copy 语义。 */
class GearItemTest {

    @Test
    fun `status 常量取值正确`() {
        assertEquals(0, GearItem.STATUS_AVAILABLE)
        assertEquals(1, GearItem.STATUS_TRANSFERRED)
    }

    @Test
    fun `默认字段符合契约`() {
        val item = GearItem(name = "帐篷", categoryId = "g5")
        assertEquals(0L, item.id)
        assertEquals("", item.brandModel)
        assertEquals(GearItem.STATUS_AVAILABLE, item.status)
        assertEquals(0, item.weightGrams)
        assertNull(item.priceCents)
        assertNull(item.purchaseDate)
        assertNull(item.note)
        assertEquals(0L, item.createdAt)
    }

    @Test
    fun `priceCents 存分为 Long 不受浮点影响`() {
        val item = GearItem(name = "睡袋", categoryId = "g5", priceCents = 1599L)
        assertEquals(1599L, item.priceCents)
    }

    @Test
    fun `copy 修改 status 保留其余字段`() {
        val item = GearItem(
            id = 7L, name = "背包", categoryId = "g1",
            brandModel = "ULA Circuit", weightGrams = 1100, priceCents = 238000L, createdAt = 42L
        )
        val transferred = item.copy(status = GearItem.STATUS_TRANSFERRED)
        assertEquals(GearItem.STATUS_TRANSFERRED, transferred.status)
        assertEquals(item.id, transferred.id)
        assertEquals(item.name, transferred.name)
        assertEquals(item.brandModel, transferred.brandModel)
        assertEquals(item.weightGrams, transferred.weightGrams)
        assertEquals(item.priceCents, transferred.priceCents)
        assertEquals(item.createdAt, transferred.createdAt)
    }

    @Test
    fun `相等性按数据类语义`() {
        val a = GearItem(id = 1, name = "x", categoryId = "g1")
        val b = GearItem(id = 1, name = "x", categoryId = "g1")
        assertEquals(a, b)
    }
}
