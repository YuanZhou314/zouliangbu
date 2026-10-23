package com.zouliangbu.footprint.data.repo

import androidx.room.withTransaction
import com.zouliangbu.footprint.data.db.AppDatabase
import com.zouliangbu.footprint.data.db.RouteCache
import com.zouliangbu.footprint.data.db.RouteCachePoint
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
    private val routeCacheDao = db.routeCacheDao()

    // ===== 记录流程 =====

    suspend fun startSession(startTime: Long): Long =
        sessionDao.insert(TrackSession(startTime = startTime))

    suspend fun appendPoint(point: TrackPoint) {
        pointDao.insert(point)
    }

    /**
     * 批量追加点（ROADMAP P2.6）。
     *
     * 记录过程中用这个而不是逐点 insert：每次 insert 都是一次独立事务 + 一次 fsync，
     * 2 秒一个点、一小时 1800 次 fsync，耗电和卡顿都吃在这里。
     * 一次性 insertAll 走一个事务，1800 次 fsync 变成 1 次。
     */
    suspend fun appendPoints(points: List<TrackPoint>) {
        if (points.isEmpty()) return
        pointDao.insertAll(points)
    }

    suspend fun finalizeSession(id: Long, endTime: Long, distanceMeters: Float, pausedMs: Long) {
        sessionDao.finalize(id, endTime, distanceMeters, pausedMs)
    }

    /** 写入累计爬升 / 下降（v4）。停止后单独调，与 [finalizeSession] 解耦。 */
    suspend fun updateAscent(id: Long, ascentMeters: Double, descentMeters: Double) {
        sessionDao.updateAscent(id, ascentMeters, descentMeters)
    }

    // ===== 读取 =====

    /** 悬挂会话（endTime IS NULL），只在 App 启动时用于恢复，见 SessionRecovery。 */
    suspend fun unfinishedSessions(): List<TrackSession> = sessionDao.unfinished()

    suspend fun getPointsForSession(sessionId: Long): List<TrackPoint> =
        pointDao.getForSession(sessionId)

    suspend fun getSession(id: Long): TrackSession? = sessionDao.getById(id)

    /** 最近 N 条已结束会话（排除自己），「与上次对比」的候选窗口。 */
    suspend fun recentCompletedSessions(excludeId: Long, limit: Int): List<TrackSession> =
        sessionDao.recentCompleted(excludeId, limit)

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
        // 导入的轨迹也要算爬升/下降：列表卡第三项就是它，不算是 0 米（用户反馈）。
        // 文件里没有海拔字段时 GpxImport 全部填 0，算出来自然是 0 —— 这是诚实的，不猜。
        val ascent = com.zouliangbu.footprint.util.Elevation.ascentOf(points)
        val id = sessionDao.insert(
            TrackSession(
                startTime = startTime,
                endTime = endTime,
                totalDistanceMeters = distanceMeters,
                name = name?.takeIf { it.isNotBlank() },
                ascentMeters = ascent.ascentMeters,
                descentMeters = ascent.descentMeters,
                source = TrackSession.SOURCE_IMPORTED
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

    // ===== 线路离线缓存（循迹 a）=====

    /** 取某线路的离线轨迹点（无缓存返回空）。按 seq 升序，坐标 GCJ-02。 */
    suspend fun getCachedRoutePoints(routeId: Long): List<RouteCachePoint> =
        routeCacheDao.getPoints(routeId)

    /** 某线路是否已缓存。 */
    suspend fun hasCachedRoute(routeId: Long): Boolean =
        routeCacheDao.has(routeId) > 0

    /**
     * 保存一条线路的离线轨迹（覆盖式：删旧缓存再插新的）。
     *
     * 缓存元数据与轨迹点放**同一事务**：中途中止不留下「有元数据没点」的脏缓存。
     */
    suspend fun saveRouteCache(routeId: Long, name: String, points: List<RouteCachePoint>) =
        db.withTransaction {
            routeCacheDao.delete(routeId)
            val cacheId = routeCacheDao.insert(
                RouteCache(
                    routeId = routeId,
                    name = name,
                    fetchedAt = System.currentTimeMillis(),
                    pointCount = points.size
                )
            )
            routeCacheDao.insertPoints(points.map { it.copy(cacheId = cacheId) })
        }

    /** 删除某线路的离线缓存。 */
    suspend fun deleteRouteCache(routeId: Long) {
        routeCacheDao.delete(routeId)
    }
}
