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

    /** 记录页的参考线路参数（循迹 b：从线路详情「跟着走」进入，叠加已缓存线路）。 */
    const val ARG_REF_ROUTE_ID = "refRouteId"
    const val RECORD_WITH_REF = "record?$ARG_REF_ROUTE_ID={$ARG_REF_ROUTE_ID}"

    const val HOME = "home"
    const val ROUTES = "routes"
    const val GEAR = "gear"
    const val PROFILE = "profile"
    const val TRACKS = "tracks"
    const val OFFLINE = "offline"
    const val LOGIN = "login"

    const val ARG_SESSION_ID = "sessionId"
    const val TRACK_DETAIL = "track/{$ARG_SESSION_ID}"

    /** 拼一条详情页的实际路由。 */
    fun trackDetail(sessionId: Long): String = "track/$sessionId"

    /** 拼一条「带参考线路的记录页」路由。 */
    fun recordWithReference(routeId: Long): String = "record?$ARG_REF_ROUTE_ID=$routeId"

    // ===== 装备模块（外层全屏页，与 record/detail 同级）=====
    const val GEAR_STATS = "gear/stats"
    const val GEAR_PACKING = "gear/packing"

    const val ARG_GEAR_ID = "gearId"
    const val GEAR_EDIT = "gear/edit?$ARG_GEAR_ID={$ARG_GEAR_ID}"

    /** 拼编辑页路由；gearId = 0 表示新增。 */
    fun gearEdit(gearId: Long): String = "gear/edit?$ARG_GEAR_ID=$gearId"

    // ===== 天气模块（外层全屏页）=====
    const val WEATHER = "weather"

    // ===== 线路地图模式（内层下钻，但隐藏底栏）=====
    const val ROUTE_MAP = "routeMap"

    // ===== 离线地图查看（内层下钻，隐藏底栏）=====
    const val ARG_CITY = "city"
    const val OFFLINE_PREVIEW = "offlinePreview/{$ARG_CITY}"

    /** 拼离线地图查看页路由；城市名是中文，必须 Uri.encode 才能进 URL path。 */
    fun offlinePreview(city: String): String =
        "offlinePreview/" + android.net.Uri.encode(city)
}
