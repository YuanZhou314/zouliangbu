package com.zouliangbu.footprint.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一件装备的台账记录（v5 新增）。
 *
 * @property id           自增主键
 * @property name         名称，必填
 * @property brandModel   品牌型号，可空串
 * @property categoryId   分类 id（g1–g8，沿用 [com.zouliangbu.footprint.data.mock.MockData.gearCategories]）
 * @property status       0 可用 / 1 已转让；已转让条目列表置灰、统计默认排除
 * @property weightGrams  重量（克）
 * @property priceCents   价格（分），可空——很多老装备根本不记得多少钱
 * @property purchaseDate 购买日期（yyyy-MM-dd 文本），可空
 * @property note         备注，可空
 * @property createdAt    创建时间（epoch millis），插入时由仓库写入
 */
@Entity(tableName = "gear_items")
data class GearItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brandModel: String = "",
    val categoryId: String,
    val status: Int = 0,
    val weightGrams: Int = 0,
    val priceCents: Long? = null,
    val purchaseDate: String? = null,
    val note: String? = null,
    val createdAt: Long = 0
) {
    companion object {
        const val STATUS_AVAILABLE = 0
        const val STATUS_TRANSFERRED = 1
    }
}
