package com.zouliangbu.footprint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackSessionDao {

    @Insert
    suspend fun insert(session: TrackSession): Long

    @Update
    suspend fun update(session: TrackSession)

    @Query("SELECT * FROM track_sessions WHERE id = :id")
    suspend fun getById(id: Long): TrackSession?

    /**
     * 悬挂会话：`endTime IS NULL` 表示"正在记录"，但进程可能是上次被系统杀掉的残留。
     *
     * 调用时机只有 App 启动（此时本进程还没有 Service 在跑，所以不会有误判）。
     * 按开始时间倒序，正常最多命中 1 条；出现多条说明历史上有过多次异常退出。
     */
    @Query("SELECT * FROM track_sessions WHERE endTime IS NULL ORDER BY startTime DESC")
    suspend fun unfinished(): List<TrackSession>

    @Query("SELECT * FROM track_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<TrackSession>>

    /**
     * 历史列表：只取已结束的会话（endTime IS NOT NULL），带点数，按开始时间倒序。
     *
     * 子查询在 SQLite 里走 `track_points.sessionId` 索引（TrackPoint 已声明该索引），
     * 不至于退化成全表扫。空轨迹（点数 0）也会返回——导入失败或采点异常的记录
     * 需要能被看见并删除，而不是静默消失。
     */
    @Query(
        """
        SELECT s.id AS id,
               s.startTime AS startTime,
               s.endTime AS endTime,
               s.totalDistanceMeters AS totalDistanceMeters,
               s.name AS name,
               s.pausedMs AS pausedMs,
               s.ascentMeters AS ascentMeters,
               s.descentMeters AS descentMeters,
               (SELECT COUNT(*) FROM track_points p WHERE p.sessionId = s.id) AS pointCount
        FROM track_sessions s
        WHERE s.endTime IS NOT NULL
        ORDER BY s.startTime DESC
        """
    )
    fun observeCompleted(): Flow<List<TrackSummary>>

    /** 写入会话的最终距离、结束时间和累计暂停时长。 */
    @Query(
        "UPDATE track_sessions SET endTime = :endTime, totalDistanceMeters = :distance, " +
                "pausedMs = :pausedMs WHERE id = :id"
    )
    suspend fun finalize(id: Long, endTime: Long, distance: Float, pausedMs: Long)

    /**
     * 写入累计爬升 / 下降（v4，ROADMAP P3.1）。
     *
     * 单独一个方法而不是塞进 [finalize]：爬升是**停止后**才算得出的
     * （要对整条轨迹做中值滤波），而 [finalize] 是停止瞬间就该写完的。
     * 分开写，停止瞬间不必等滤波算完，UI 的摘要卡立刻能出。
     */
    @Query(
        "UPDATE track_sessions SET ascentMeters = :ascent, descentMeters = :descent WHERE id = :id"
    )
    suspend fun updateAscent(id: Long, ascent: Double, descent: Double)

    /**
     * 最近 N 条已结束会话（排除自己），用于「与上次对比」找可比对象。
     *
     * 不做 SQL 层的"同一条线"判断——那需要几何匹配，SQLite 做不了；
     * 这里只按时间取一个候选窗口，具体比对交给 SegmentMatch（纯 Kotlin）。
     */
    @Query("SELECT * FROM track_sessions WHERE endTime IS NOT NULL AND id != :excludeId " +
            "ORDER BY startTime DESC LIMIT :limit")
    suspend fun recentCompleted(excludeId: Long, limit: Int): List<TrackSession>

    @Query("DELETE FROM track_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
