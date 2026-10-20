package com.zouliangbu.footprint.ui.tabs

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
import com.zouliangbu.footprint.ui.components.AppBottomBar
import com.zouliangbu.footprint.ui.gear.GearScreen
import com.zouliangbu.footprint.ui.home.HomeScreen
import com.zouliangbu.footprint.ui.navigation.AppRoute
import com.zouliangbu.footprint.ui.navigation.BottomTabs
import com.zouliangbu.footprint.offline.OfflineMapScreen
import com.zouliangbu.footprint.ui.routes.RouteDetailScreen
import com.zouliangbu.footprint.ui.profile.ProfileScreen
import com.zouliangbu.footprint.ui.record.RecordScreen
import com.zouliangbu.footprint.ui.routes.RoutesScreen
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
    onOpenTrackDetail: (Long) -> Unit
) {
    // 只关心"是否在记录"：RecordingTrackState 每 2 秒发一次 Snapshot，
    // 必须 map + distinctUntilChanged，否则整个主框架（含当前 tab 页）跟着每 2 秒重组。
    val isRecording by remember {
        RecordingTrackState.current.map { it != null }
    }.collectAsStateWithLifecycle(initialValue = false)

    val entry by tabNav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        bottomBar = {
            AppBottomBar(
                tabs = BottomTabs,
                selectedRoute = currentRoute,
                isRecording = isRecording,
                onTabClick = { route -> switchTab(tabNav, route) },
                onRecordClick = onOpenRecord
            )
        }
    ) { inner ->
        NavHost(
            navController = tabNav,
            startDestination = AppRoute.HOME,
            modifier = Modifier.padding(inner)
        ) {
            composable(AppRoute.HOME) { HomeScreen() }
            composable(AppRoute.ROUTES) { RoutesScreen(onOpenDetail = { tabNav.navigate("route/$it") }) }
            composable(AppRoute.GEAR) { GearScreen() }
            composable(AppRoute.PROFILE) {
                // 「我的记录」是下钻页，不进 tab 栈：navigate 入栈，返回回到「我的」。
                ProfileScreen(
                    onOpenTracks = { tabNav.navigate(AppRoute.TRACKS) },
                    onOpenOffline = { tabNav.navigate(AppRoute.OFFLINE) }
                )
            }
            composable(AppRoute.TRACKS) {
                TrackListScreen(
                    onBack = { tabNav.popBackStack() },
                    onOpenDetail = onOpenTrackDetail
                )
            }
            // 离线地图管理（ROADMAP P4.2）：放在内层，属于「我的」下钻，保留底栏。
            composable(AppRoute.OFFLINE) {
                OfflineMapScreen(onBack = { tabNav.popBackStack() })
            }
            // 线路详情（ROADMAP P6 S5）
            composable("route/{routeId}") { entry ->
                val id = entry.arguments?.getString("routeId")?.toLongOrNull() ?: 0L
                RouteDetailScreen(routeId = id, onBack = { tabNav.popBackStack() })
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
