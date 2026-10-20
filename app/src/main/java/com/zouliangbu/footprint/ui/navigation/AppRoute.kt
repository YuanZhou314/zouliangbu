package com.zouliangbu.footprint.ui.navigation

/**
 * 导航 route 常量。
 *
 * 双层 NavHost 结构：
 *   - 外层：TABS（主框架，含底栏） / RECORD（全屏记录页，无底栏）
 *   - 内层（TABS 内）：HOME / ROUTES / GEAR / PROFILE 四个 tab
 */
object AppRoute {
    const val TABS = "tabs"
    const val RECORD = "record"

    const val HOME = "home"
    const val ROUTES = "routes"
    const val GEAR = "gear"
    const val PROFILE = "profile"
}
