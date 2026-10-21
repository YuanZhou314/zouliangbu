package com.zouliangbu.footprint

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.zouliangbu.footprint.ui.AppRoot
import com.zouliangbu.footprint.ui.theme.FootprintTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by com.zouliangbu.footprint.ui.theme.ThemePrefs.mode
            FootprintTheme(
                darkTheme = when (mode) {
                    com.zouliangbu.footprint.ui.theme.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    com.zouliangbu.footprint.ui.theme.ThemeMode.LIGHT -> false
                    com.zouliangbu.footprint.ui.theme.ThemeMode.DARK -> true
                }
            ) {
                // ===== 启动即请求定位权限（task-003 反馈）=====
                // Android 规定运行时权限必须用户手动同意，无法完全静默；这里把
                // 申请提前到 App 首帧：第一次打开弹一次对话框，同意后 App 内
                // 地图蓝点 / 同城 / 记录页全都自动就位，不再等用户点定位按钮。
                // 只申请前台定位（FINE/COARSE）+ 通知；后台定位仍由记录页单独
                // 申请（Android 10+ 要求二者分开，同组请求会被系统静默忽略）。
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { }

                LaunchedEffect(Unit) {
                    val wanted = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    val missing = wanted.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                                PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                AppRoot()
            }
        }
    }
}
