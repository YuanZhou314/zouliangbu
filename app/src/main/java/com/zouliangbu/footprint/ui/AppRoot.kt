package com.zouliangbu.footprint.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zouliangbu.footprint.ui.navigation.AppRoute
import com.zouliangbu.footprint.ui.gear.GearEditScreen
import com.zouliangbu.footprint.ui.gear.GearPackingScreen
import com.zouliangbu.footprint.ui.gear.GearStatsScreen
import com.zouliangbu.footprint.ui.record.RecordScreen
import com.zouliangbu.footprint.ui.tabs.MainTabsScreen
import com.zouliangbu.footprint.ui.tracks.TrackDetailScreen

/**
 * App 根导航（外层）：
 *   tabs         → 主框架（底栏 + 五个内层 destination）
 *   record       → 全屏记录页（无底栏）
 *   track/{id}   → 全屏轨迹详情（无底栏）
 *
 * 详情页挂在**外层**是有意的：记录页停止后的摘要卡、历史列表都能跳进来，
 * 且不被底栏挤占地图高度。两个入口共用同一条路由。
 *
 * 两个 NavController 都提在根部：进入 record/detail 时内层 NavHost 虽离开组合，
 * 返回后 tab 栈与页面状态靠 saveState/restoreState 恢复。
 */
@Composable
fun AppRoot() {
    val rootNav = rememberNavController()
    val tabNav = rememberNavController()

    // 详情入口：记录页与历史列表都要用，抽成一个 lambda 避免两处写重复的导航参数。
    val openTrackDetail: (Long) -> Unit = { sessionId ->
        rootNav.navigate(AppRoute.trackDetail(sessionId)) { launchSingleTop = true }
    }

    NavHost(navController = rootNav, startDestination = AppRoute.TABS) {
        composable(AppRoute.TABS) {
            MainTabsScreen(
                tabNav = tabNav,
                onOpenRecord = {
                    rootNav.navigate(AppRoute.RECORD) { launchSingleTop = true }
                },
                onOpenTrackDetail = openTrackDetail,
                onOpenGearEdit = { gearId ->
                    rootNav.navigate(AppRoute.gearEdit(gearId)) { launchSingleTop = true }
                },
                onOpenGearStats = {
                    rootNav.navigate(AppRoute.GEAR_STATS) { launchSingleTop = true }
                },
                onOpenGearPacking = {
                    rootNav.navigate(AppRoute.GEAR_PACKING) { launchSingleTop = true }
                }
            )
        }
        composable(AppRoute.RECORD) {
            RecordScreen(
                onBack = { rootNav.popBackStack() },
                onOpenDetail = openTrackDetail
            )
        }
        composable(
            route = AppRoute.TRACK_DETAIL,
            arguments = listOf(
                navArgument(AppRoute.ARG_SESSION_ID) { type = NavType.LongType }
            )
        ) { entry ->
            val id = entry.arguments?.getLong(AppRoute.ARG_SESSION_ID) ?: 0L
            TrackDetailScreen(sessionId = id, onBack = { rootNav.popBackStack() })
        }
        // ===== 装备模块（外层全屏页）=====
        composable(
            route = AppRoute.GEAR_EDIT,
            arguments = listOf(
                navArgument(AppRoute.ARG_GEAR_ID) {
                    type = NavType.LongType
                    defaultValue = 0L // gear/edit 不带参数 = 新增
                }
            )
        ) { entry ->
            val gearId = entry.arguments?.getLong(AppRoute.ARG_GEAR_ID) ?: 0L
            GearEditScreen(gearId = gearId, onBack = { rootNav.popBackStack() })
        }
        composable(AppRoute.GEAR_STATS) {
            GearStatsScreen(onBack = { rootNav.popBackStack() })
        }
        composable(AppRoute.GEAR_PACKING) {
            GearPackingScreen(onBack = { rootNav.popBackStack() })
        }
    }
}
