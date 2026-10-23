package com.zouliangbu.footprint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * 线路离线缓存 DAO（循迹 a）。
 *
 * 写用 [upsert] + [insertPoints]；读用 [getPoints]（按 seq 排序）。
 * 删除缓存靠外键 CASCADE 级联删点，DAO 层只需删父行。
 */
@Dao
interface RouteCacheDao {

    /** 按 routeId 取缓存元数据（无则 null）。 */
    @Query("SELECT * FROM route_cache WHERE routeId = :routeId")
    suspend fun get(routeId: Long): RouteCache?

    /** 按 routeId 取全部轨迹点（按 seq 升序）。 */
    @Query(
        "SELECT p.* FROM route_cache_point p " +
                "JOIN route_cache c ON p.cacheId = c.id " +
                "WHERE c.routeId = :routeId ORDER BY p.seq ASC"
    )
    suspend fun getPoints(routeId: Long): List<RouteCachePoint>

    /** 插入一行缓存元数据，返回自增 id。 */
    @Insert
    suspend fun insert(cache: RouteCache): Long

    @Insert
    suspend fun insertPoints(points: List<RouteCachePoint>)

    /** 删除某线路的离线缓存（级联删点）。 */
    @Query("DELETE FROM route_cache WHERE routeId = :routeId")
    suspend fun delete(routeId: Long)

    /** 是否已缓存该线路。 */
    @Query("SELECT COUNT(*) FROM route_cache WHERE routeId = :routeId")
    suspend fun has(routeId: Long): Int
}
