package com.zouliangbu.footprint.data

import com.zouliangbu.footprint.data.repo.TrackRepository
import com.zouliangbu.footprint.util.TrackMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 悬挂会话恢复（ROADMAP P2.2）。
 *
 * **问题**：`RecordingTrackState` 是进程内单例。记录到一半进程被系统杀掉后，
 * Service 没了、通知没了、内存状态没了，但 DB 里那条会话的 `endTime` 还是 NULL——
 * 于是它既不在历史列表里（列表只查已结束会话），也永远不会自己结束，等于数据静默丢失。
 *
 * **做法**（App 启动时跑一次，此时本进程必然没有 Service 在跑，不会误判）：
 *   - 0 个点的悬挂会话 → 直接删。没有任何可挽回的信息，留着只会污染列表。
 *   - 有点的 → 用**最后一个点的时间**当结束时间、按统一规则重算距离，收尾保存。
 *
 * **为什么不做"继续记录"**：进程死后 GPS 采集已经断开，继续写会让轨迹在时间上
 * 出现一个无法解释的空洞（地图上表现为一条跨越几公里的直线）。宁可保存成一条
 * 完整的历史记录，让用户重新开始一次。恢复提示里会明确告诉他发生了什么。
 */
object SessionRecovery {

    /** 被自动收尾的悬挂会话，UI 用来提示「上次记录异常中断，已保存」。 */
    data class Orphaned(
        val sessionId: Long,
        val name: String?,
        val pointCount: Int,
        val distanceMeters: Float,
        val endTime: Long
    )

    private val _orphaned = MutableStateFlow<List<Orphaned>>(emptyList())
    val orphaned: StateFlow<List<Orphaned>> = _orphaned

    /**
     * 本进程启动的墙上时钟。
     *
     * 用来排除"本进程自己刚创建的会话"：恢复是在 Application.onCreate 里异步跑的，
     * 如果用户手速极快，可能在它查库之前就点了「开始记录」，那条新会话同样是
     * `endTime IS NULL`——把它当悬挂会话收尾，会把正在记录的数据提前结束掉。
     * 本进程创建的会话 startTime 必然 >= 这个值，跳过即可。
     */
    private val processStartWallMs = System.currentTimeMillis()

    /**
     * 扫描并收尾所有悬挂会话。幂等：第二次跑已经查不到 `endTime IS NULL` 的行。
     *
     * @return 本次被收尾的会话（可能为空）
     */
    suspend fun run(repo: TrackRepository): List<Orphaned> {
        // 双保险：有活着的记录状态说明某个 Service 正在写，绝不能在它脚下改数据。
        if (RecordingTrackState.current.value != null) return emptyList()

        val handled = mutableListOf<Orphaned>()
        for (session in repo.unfinishedSessions()) {
            // 本进程刚建的会话，不是残留。
            if (session.startTime >= processStartWallMs) continue
            val points = repo.getPointsForSession(session.id)
            if (points.isEmpty()) {
                repo.deleteSession(session.id)
                continue
            }
            // 结束时间取最后一个点的时间，而不是"现在"：被杀之后可能过了两天才打开 App，
            // 用 now 会让这条轨迹的时长变成 48 小时。
            val endTime = points.maxOf { it.timestamp }.coerceAtLeast(session.startTime)
            val distance = TrackMetrics.distanceMeters(points)
            repo.finalizeSession(session.id, endTime, distance, session.pausedMs)
            handled += Orphaned(
                sessionId = session.id,
                name = session.name,
                pointCount = points.size,
                distanceMeters = distance,
                endTime = endTime
            )
        }
        if (handled.isNotEmpty()) _orphaned.value = handled
        return handled
    }

    /** UI 展示完提示后清空，避免下次进记录页又弹一遍。 */
    fun consume() { _orphaned.value = emptyList() }
}
