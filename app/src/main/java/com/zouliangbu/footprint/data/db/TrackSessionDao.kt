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

    @Query("SELECT * FROM track_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<TrackSession>>

    /** 写入会话的最终距离和结束时间。 */
    @Query(
        "UPDATE track_sessions SET endTime = :endTime, totalDistanceMeters = :distance " +
                "WHERE id = :id"
    )
    suspend fun finalize(id: Long, endTime: Long, distance: Float)
}