package com.zouliangbu.footprint.data.repo

import androidx.room.withTransaction
import com.zouliangbu.footprint.data.db.AppDatabase
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.data.db.TrackSummary
import kotlinx.coroutines.flow.Flow

/**
 * TrackSession + TrackPoint 的薄封装。
 *
 * 持 [AppDatabase] 而不是两个 DAO：导入 GPX 要在一个事务里先插会话、再批量插点，
 * 跨 DAO 的 `@Transaction` 做不到，只能靠 `db.withTransaction {}`。
 */
class TrackRepository(private val db: AppDatabase) {

    private val sessionDao = db.sessionDao()
    private val pointDao = db.pointDao()

    // ===== 记录流程 =====

    suspend fun startSession(startTime: Long): Long =
        sessionDao.insert(TrackSession(startTime = startTime))

    suspend fun appendPoint(point: TrackPoint) {
        pointDao.insert(point)
    }

    suspend fun finalizeSession(id: Long, endTime: Long, distanceMeters: Float, pausedMs: Long) {
        sessionDao.finalize(id, endTime, distanceMeters, pausedMs)
    }

    // ===== 读取 =====

    /** 悬挂会话（endTime IS NULL），只在 App 启动时用于恢复，见 SessionRecovery。 */
    suspend fun unfinishedSessions(): List<TrackSession> = sessionDao.unfinished()

    suspend fun getPointsForSession(sessionId: Long): List<TrackPoint> =
        pointDao.getForSession(sessionId)

    suspend fun getSession(id: Long): TrackSession? = sessionDao.getById(id)

    suspend fun countPoints(sessionId: Long): Int = pointDao.count(sessionId)

    fun observeSessions(): Flow<List<TrackSession>> = sessionDao.observeAll()

    /** 历史列表用：已结束会话 + 点数。 */
    fun observeCompleted(): Flow<List<TrackSummary>> = sessionDao.observeCompleted()

    // ===== 导入 / 删除 =====

    /**
     * 导入一条外部轨迹。
     *
     * 会话与点放在**同一事务**里：否则中途中止会留下一个「点数为 0 的已完成会话」，
     * 列表里看着像记录失败，用户还得手动删。
     *
     * @param points 各点的 `sessionId` 由本方法回填，调用方传 0 即可。
     * @return 新会话 id
     */
    suspend fun importTrack(
        name: String?,
        points: List<TrackPoint>,
        startTime: Long,
        endTime: Long,
        distanceMeters: Float
    ): Long = db.withTransaction {
        val id = sessionDao.insert(
            TrackSession(
                startTime = startTime,
                endTime = endTime,
                totalDistanceMeters = distanceMeters,
                name = name?.takeIf { it.isNotBlank() }
            )
        )
        pointDao.insertAll(points.map { it.copy(sessionId = id) })
        id
    }

    /** 删会话，连带删点（外键 CASCADE 也会做，但显式删不依赖外键开关）。 */
    suspend fun deleteSession(id: Long) = db.withTransaction {
        pointDao.deleteForSession(id)
        sessionDao.deleteById(id)
    }
}
