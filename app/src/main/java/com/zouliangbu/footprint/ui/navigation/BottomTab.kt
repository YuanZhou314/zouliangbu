package com.zouliangbu.footprint.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底栏 tab 元数据。
 *
 * 注意：中间的「出发」大圆按钮不在此列表——它由 AppBottomBar 单独绘制，
 * 语义上不是 tab（点击是跳转全屏记录页，不切换选中态）。
 */
data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val BottomTabs = listOf(
    BottomTab(AppRoute.HOME, "首页", Icons.Filled.Home),
    BottomTab(AppRoute.ROUTES, "线路", Icons.Filled.Route),
    // 中间位置由凸起圆钮占据（Spacer 让位）
    BottomTab(AppRoute.GEAR, "装备", Icons.Filled.Backpack),
    BottomTab(AppRoute.PROFILE, "我的", Icons.Filled.Person)
)
