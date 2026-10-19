package com.zouliangbu.footprint.data.repo

import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackPointDao
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.data.db.TrackSessionDao
import kotlinx.coroutines.flow.Flow

/**
 * TrackSession + TrackPoint 的薄封装。
 * 持有 DAO 实例，向上提供 coroutine / Flow 接口。
 */
class TrackRepository(
    private val sessionDao: TrackSessionDao,
    private val pointDao: TrackPointDao
) {

    suspend fun startSession(startTime: Long): Long =
        sessionDao.insert(TrackSession(startTime = startTime))

    suspend fun appendPoint(point: TrackPoint) {
        pointDao.insert(point)
    }

    suspend fun getPointsForSession(sessionId: Long): List<TrackPoint> =
        pointDao.getForSession(sessionId)

    suspend fun getSession(id: Long): TrackSession? = sessionDao.getById(id)

    suspend fun finalizeSession(id: Long, endTime: Long, distanceMeters: Float) {
        sessionDao.finalize(id, endTime, distanceMeters)
    }

    fun observeSessions(): Flow<List<TrackSession>> = sessionDao.observeAll()
}