package com.zouliangbu.footprint.ui

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
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

    // 转场（重要）：必须用**位移**而不是淡入淡出。
    // 默认的 fade 转场对 Compose 内容有效，但地图是 SurfaceView（GL 渲染），
    // 不吃 View alpha —— 退出详情页时文字先淡掉、地图却原样杵着零点几秒才消失，
    // 看起来就是"递进退出"。滑动位移对所有 View 一视同仁，整页同步进退。
    //
    // 页面隔离（用户反馈 5）：NavHost 转场是新旧两个 destination 并行组合动画，
    // 快速进出时看起来两页叠在一起。enter/popEnter 保留滑动入场，exit/popExit
    // 一律即时移除（None = 旧页当场离场）——返回时旧页瞬间消失，顺带根治
    // "地图延迟退出"。
    NavHost(
        navController = rootNav,
        startDestination = AppRoute.TABS,
        enterTransition = {
            slideInHorizontally(animationSpec = tween(280)) { it }
        },
        exitTransition = { ExitTransition.None },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(280)) { -it / 4 }
        },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(AppRoute.TABS) {
            MainTabsScreen(
                tabNav = tabNav,
                onOpenRecord = {
                    rootNav.navigate(AppRoute.RECORD) { launchSingleTop = true }
                },
                onOpenTrackDetail = openTrackDetail,
                onFollowRoute = { routeId ->
                    rootNav.navigate(AppRoute.recordWithReference(routeId)) { launchSingleTop = true }
                },
                onOpenGearEdit = { gearId ->
                    rootNav.navigate(AppRoute.gearEdit(gearId)) { launchSingleTop = true }
                },
                onOpenGearStats = {
                    rootNav.navigate(AppRoute.GEAR_STATS) { launchSingleTop = true }
                },
                onOpenGearPacking = {
                    rootNav.navigate(AppRoute.GEAR_PACKING) { launchSingleTop = true }
                },
                onOpenWeather = {
                    rootNav.navigate(AppRoute.WEATHER) { launchSingleTop = true }
                }
            )
        }
        composable(AppRoute.RECORD) {
            RecordScreen(
                onBack = { rootNav.popBackStack() },
                onOpenDetail = openTrackDetail
            )
        }
        // 带参考线路的记录页（循迹 b：从线路详情「跟着走」进入）
        composable(
            route = AppRoute.RECORD_WITH_REF,
            arguments = listOf(
                navArgument(AppRoute.ARG_REF_ROUTE_ID) { type = NavType.LongType }
            )
        ) { entry ->
            val refRouteId = entry.arguments?.getLong(AppRoute.ARG_REF_ROUTE_ID) ?: 0L
            RecordScreen(
                onBack = { rootNav.popBackStack() },
                onOpenDetail = openTrackDetail,
                refRouteId = refRouteId
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
        // ===== 天气模块（外层全屏页）=====
        composable(AppRoute.WEATHER) {
            com.zouliangbu.footprint.ui.weather.WeatherScreen(
                onBack = { rootNav.popBackStack() }
            )
        }
    }
}
