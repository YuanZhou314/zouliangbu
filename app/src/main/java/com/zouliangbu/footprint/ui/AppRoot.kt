package com.zouliangbu.footprint.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zouliangbu.footprint.ui.navigation.AppRoute
import com.zouliangbu.footprint.ui.record.RecordScreen
import com.zouliangbu.footprint.ui.tabs.MainTabsScreen

/**
 * App 根导航（外层）：
 *   tabs   → 主框架（底栏 + 四个内容 tab）
 *   record → 全屏记录页（原 MainScreen 能力，无底栏，返回回主框架）
 *
 * 两个 NavController 都提在根部：进入 record 时内层 NavHost 虽离开组合，
 * 返回后 tab 栈与页面状态靠 saveState/restoreState 恢复。
 */
@Composable
fun AppRoot() {
    val rootNav = rememberNavController()
    val tabNav = rememberNavController()

    NavHost(navController = rootNav, startDestination = AppRoute.TABS) {
        composable(AppRoute.TABS) {
            MainTabsScreen(
                tabNav = tabNav,
                onOpenRecord = {
                    rootNav.navigate(AppRoute.RECORD) { launchSingleTop = true }
                }
            )
        }
        composable(AppRoute.RECORD) {
            RecordScreen(onBack = { rootNav.popBackStack() })
        }
    }
}
