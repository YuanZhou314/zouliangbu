package com.zouliangbu.footprint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackPointDao {

    @Insert
    suspend fun insert(point: TrackPoint): Long

    /**
     * 批量写点。GPX 导入一次性落几千点时必须用这个——
     * 逐点 insert 是逐次事务 + 逐次 fsync，几千点能卡到怀疑人生。
     * （记录过程的批量缓冲另说，见 ROADMAP P2.6。）
     */
    @Insert
    suspend fun insertAll(points: List<TrackPoint>)

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getForSession(sessionId: Long): List<TrackPoint>

    @Query("SELECT COUNT(*) FROM track_points WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("DELETE FROM track_points WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
