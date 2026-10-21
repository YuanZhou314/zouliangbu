package com.zouliangbu.footprint.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GearItemDao {

    /** 台账列表（按创建时间倒序）。装备量小（几十件），全量拉回来在 Kotlin 侧算统计。 */
    @Query("SELECT * FROM gear_items ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<GearItem>>

    @Query("SELECT * FROM gear_items WHERE id = :id")
    suspend fun getById(id: Long): GearItem?

    /** 新增和编辑统一走 upsert：id=0 自动生成，id>0 整行覆盖。 */
    @Upsert
    suspend fun upsert(item: GearItem)

    @Query("DELETE FROM gear_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
