package com.zouliangbu.footprint.ui.navigation

/**
 * 导航 route 常量。
 *
 * 双层 NavHost 结构：
 *   - 外层：TABS（主框架，含底栏） / RECORD（全屏记录页，无底栏） / TRACK_DETAIL（全屏详情，无底栏）
 *   - 内层（TABS 内）：HOME / ROUTES / GEAR / PROFILE / TRACKS 五个 destination
 *
 * 详情页放在**外层**：它要占满屏幕看地图，不能被底栏和 Scaffold padding 挤压。
 * 列表页放在**内层**：属于「我的」的下钻，保留底栏，用户可以随时切走。
 */
object AppRoute {
    const val TABS = "tabs"
    const val RECORD = "record"

    const val HOME = "home"
    const val ROUTES = "routes"
    const val GEAR = "gear"
    const val PROFILE = "profile"
    const val TRACKS = "tracks"

    const val ARG_SESSION_ID = "sessionId"
    const val TRACK_DETAIL = "track/{$ARG_SESSION_ID}"

    /** 拼一条详情页的实际路由。 */
    fun trackDetail(sessionId: Long): String = "track/$sessionId"
}
