package com.zouliangbu.footprint.data.db

/**
 * 历史列表用的会话投影：比 [TrackSession] 多一个点数。
 *
 * 为什么不让列表每项再去查一次点数：LazyColumn 逐项查库会出现 N+1 查询，
 * 列表滚动时每帧都在碰磁盘。这里用一条带子查询的 SQL 一次算完。
 *
 * 不是 @Entity——Room 会把它当普通 POJO 按列名映射，所以 SQL 里必须显式 `AS` 别名。
 *
 * @property pausedMs 累计暂停时长，展示时长时要从 (endTime - startTime) 里扣掉。
 * @property ascentMeters 累计爬升（米），v4 起有值。
 * @property descentMeters 累计下降（米），v4 起有值。
 */
data class TrackSummary(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val totalDistanceMeters: Float,
    val name: String?,
    val pausedMs: Long,
    val ascentMeters: Double,
    val descentMeters: Double,
    val pointCount: Int,
    /**
     * 最高海拔（米），由 track_points.altitude 取 MAX 得出（与详情页 maxAltitude 同语义）。
     * 0 = 无高程数据（空轨迹或点没有海拔），UI 沿用详情页约定显示 "—"。
     */
    val maxAltitudeMeters: Double = 0.0,
    /** v8：recorded=自己记录 / imported=导入。列表上给导入项打标。 */
    val source: String = TrackSession.SOURCE_RECORDED
) {
    /** 是否外部导入（不是自己的运动量）。 */
    val isImported: Boolean get() = source == TrackSession.SOURCE_IMPORTED
    /** 活动时长（扣暂停）。 */
    val activeDurationMs: Long
        get() = ((endTime ?: startTime) - startTime - pausedMs).coerceAtLeast(0L)
}
