package com.zouliangbu.footprint.ui.tabs

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zouliangbu.footprint.data.RecordingTrackState
import com.zouliangbu.footprint.ui.auth.LoginScreen
import com.zouliangbu.footprint.ui.components.AppBottomBar
import com.zouliangbu.footprint.ui.gear.GearScreen
import com.zouliangbu.footprint.ui.home.HomeScreen
import com.zouliangbu.footprint.ui.navigation.AppRoute
import com.zouliangbu.footprint.ui.navigation.BottomTabs
import com.zouliangbu.footprint.offline.OfflineMapPreviewScreen
import com.zouliangbu.footprint.offline.OfflineMapScreen
import com.zouliangbu.footprint.ui.routes.RouteMapModeScreen
import com.zouliangbu.footprint.ui.routes.RouteDetailScreen
import com.zouliangbu.footprint.ui.profile.ProfileScreen
import com.zouliangbu.footprint.ui.record.RecordScreen
import com.zouliangbu.footprint.ui.routes.RoutesScreen
import com.zouliangbu.footprint.ui.routes.TrackPreviewScreen
import com.zouliangbu.footprint.ui.tracks.TrackListScreen
import kotlinx.coroutines.flow.map

/**
 * 主框架：Scaffold（含底栏）+ 内层 NavHost（四个 tab）。
 *
 * 只在外层 destination「tabs」中组合；「record」是外层 NavHost 的独立 destination，
 * 不经过本 Scaffold，所以天然全屏、无底栏。
 */
@Composable
fun MainTabsScreen(
    tabNav: NavHostController,
    onOpenRecord: () -> Unit,
    onOpenTrackDetail: (Long) -> Unit,
    /** 循迹 b：从线路详情「跟着走」跳到记录页并叠加参考线。 */
    onFollowRoute: (Long) -> Unit = {},
    onOpenGearEdit: (Long) -> Unit = {},
    onOpenGearStats: () -> Unit = {},
    onOpenGearPacking: () -> Unit = {},
    onOpenWeather: () -> Unit = {}
) {
    // 只关心"是否在记录"：RecordingTrackState 每 2 秒发一次 Snapshot，
    // 必须 map + distinctUntilChanged，否则整个主框架（含当前 tab 页）跟着每 2 秒重组。
    val isRecording by remember {
        RecordingTrackState.current.map { it != null }
    }.collectAsStateWithLifecycle(initialValue = false)

    val entry by tabNav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    // 只有四个主 tab 才显示底栏：二级/三级页（我的记录、离线地图、线路详情、
    // 轨迹预览、登录、地图模式）全屏显示，底部不该杵着一条 tab 栏。
    val showBottomBar = currentRoute in setOf(
        AppRoute.HOME, AppRoute.ROUTES, AppRoute.GEAR, AppRoute.PROFILE
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    tabs = BottomTabs,
                    selectedRoute = currentRoute,
                    isRecording = isRecording,
                    onTabClick = { route -> switchTab(tabNav, route) },
                    onRecordClick = onOpenRecord
                )
            }
        }
    ) { inner ->
        // 页面隔离（用户反馈 5）：NavHost 转场是新旧两个 destination **并行**组合动画，
        // 快速切页时看起来两页叠在一起，SurfaceView 地图页（GL 渲染不吃 View alpha）
        // 尤其糟。默认全部无动画（None = 旧页立即移除、新页立即上场，snap 即切），
        // 彻底消除 tab 间与普通页之间的叠加；带地图的覆盖页单独覆盖 enter/popEnter
        // 保留滑动入场（见下方各 composable），exit/popExit 仍走这里的即时移除。
        NavHost(
            navController = tabNav,
            startDestination = AppRoute.HOME,
            modifier = Modifier.padding(inner),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(AppRoute.HOME) {
                // 首页 = 本地轨迹时间轴：点卡片直接进轨迹详情（外层全屏页）
                HomeScreen(
                    onOpenDetail = { sessionId -> onOpenTrackDetail(sessionId) },
                    onOpenRecord = onOpenRecord
                )
            }
            composable(AppRoute.ROUTES) {
                RoutesScreen(
                    onOpenDetail = { tabNav.navigate("route/$it") },
                    onOpenMapMode = { tabNav.navigate(AppRoute.ROUTE_MAP) }
                )
            }
            composable(AppRoute.GEAR) {
                GearScreen(
                    onOpenStats = onOpenGearStats,
                    onOpenPacking = onOpenGearPacking,
                    onOpenEdit = onOpenGearEdit
                )
            }
            composable(AppRoute.PROFILE) {
                // 「我的记录」是下钻页，不进 tab 栈：navigate 入栈，返回回到「我的」。
                ProfileScreen(
                    onOpenTracks = { tabNav.navigate(AppRoute.TRACKS) },
                    onOpenOffline = { tabNav.navigate(AppRoute.OFFLINE) },
                    onOpenLogin = { tabNav.navigate(AppRoute.LOGIN) },
                    onOpenWeather = onOpenWeather
                )
            }
            // 登录 / 注册（2026-10-21）：内层 destination，保留底栏，登录成功即返回。
            composable(AppRoute.LOGIN) {
                LoginScreen(
                    onBack = { tabNav.popBackStack() },
                    onLoggedIn = { tabNav.popBackStack() }
                )
            }
            composable(AppRoute.TRACKS) {
                TrackListScreen(
                    onBack = { tabNav.popBackStack() },
                    onOpenDetail = onOpenTrackDetail
                )
            }
            // 离线地图管理（ROADMAP P4.2）
            composable(AppRoute.OFFLINE) {
                OfflineMapScreen(
                    onBack = { tabNav.popBackStack() },
                    onOpenPreview = { city ->
                        tabNav.navigate(AppRoute.offlinePreview(city))
                    }
                )
            }
            // 已下载城市的离线地图查看页
            composable(
                AppRoute.OFFLINE_PREVIEW,
                enterTransition = { mapPageEnter() },
                popEnterTransition = { mapPagePopEnter() }
            ) { entry ->
                val city = entry.arguments?.getString(AppRoute.ARG_CITY) ?: ""
                OfflineMapPreviewScreen(cityName = city, onBack = { tabNav.popBackStack() })
            }
            // 线路地图模式：上半屏地图 + 下半屏列表
            composable(
                AppRoute.ROUTE_MAP,
                enterTransition = { mapPageEnter() },
                popEnterTransition = { mapPagePopEnter() }
            ) {
                RouteMapModeScreen(
                    onBack = { tabNav.popBackStack() },
                    onOpenDetail = { tabNav.navigate("route/$it") }
                )
            }
            // 线路详情（ROADMAP P6 S5）：点地图进轨迹预览
            composable(
                "route/{routeId}",
                enterTransition = { mapPageEnter() },
                popEnterTransition = { mapPagePopEnter() }
            ) { entry ->
                val id = entry.arguments?.getString("routeId")?.toLongOrNull() ?: 0L
                RouteDetailScreen(
                    routeId = id,
                    onBack = { tabNav.popBackStack() },
                    onOpenTrackPreview = { tabNav.navigate("routeTrack/$id") },
                    onFollowRoute = onFollowRoute
                )
            }
            // 轨迹预览（task-003）：全屏大图轨迹
            composable(
                "routeTrack/{routeId}",
                enterTransition = { mapPageEnter() },
                popEnterTransition = { mapPagePopEnter() }
            ) { entry ->
                val id = entry.arguments?.getString("routeId")?.toLongOrNull() ?: 0L
                TrackPreviewScreen(routeId = id, onBack = { tabNav.popBackStack() })
            }
        }
    }
}

/**
 * 标准 tab 切换：saveState/restoreState 保证每个 tab 的滚动位置与状态不丢；
 * launchSingleTop 防止重复入栈；popUpTo 到起始目的地防止栈无限增长。
 */
private fun switchTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 带地图覆盖页共用的入场转场（exit/popExit 不覆盖，走 NavHost 默认的即时移除）：
 * 正向进入从右滑入保留方向感；返回时下层从左滑回。仍用位移而不是淡入淡出——
 * SurfaceView 不吃 View alpha，fade 会出现"文字先消失、地图杵着"的递进残影。
 */
private fun mapPageEnter(): EnterTransition =
    slideInHorizontally(animationSpec = tween(280)) { it }

private fun mapPagePopEnter(): EnterTransition =
    slideInHorizontally(animationSpec = tween(280)) { -it / 4 }
