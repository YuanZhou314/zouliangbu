package com.zouliangbu.footprint

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.zouliangbu.footprint.ui.AppRoot
import com.zouliangbu.footprint.ui.theme.FootprintTheme

class MainActivity : ComponentActivity() {

    /**
     * 启动即申请定位权限（用户反馈：打开 App 就该自动定位，不要等点按钮）。
     *
     * 关键：launcher 必须在 Activity.onCreate 阶段注册（STARTED 之前），
     * 放在 Composable 的 LaunchedEffect 里时序不稳（首帧组合可能晚于 RESUMED，
     * 部分机型对话框不弹）。这里直接注册并在 onCreate 里立刻 launch。
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        Log.i("MainActivity", "permission result: $grants")
        onLocationPermissionSettled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestLocationIfNeeded()

        setContent {
            val mode by com.zouliangbu.footprint.ui.theme.ThemePrefs.mode
            FootprintTheme(
                darkTheme = when (mode) {
                    com.zouliangbu.footprint.ui.theme.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    com.zouliangbu.footprint.ui.theme.ThemeMode.LIGHT -> false
                    com.zouliangbu.footprint.ui.theme.ThemeMode.DARK -> true
                }
            ) {
                AppRoot()
            }
        }
    }

    /** 已授权 → 直接开定位；未授权 → 弹一次系统对话框（不含后台定位，Android 10+ 必须单独请求）。 */
    private fun requestLocationIfNeeded() {
        if (hasLocationPermission()) {
            onLocationPermissionSettled()
            return
        }
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Log.i("MainActivity", "launch permission request: $wanted")
        permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun onLocationPermissionSettled() {
        if (!hasLocationPermission()) {
            Log.w("MainActivity", "location permission not granted, skip auto-locate")
            return
        }
        // 系统定位总开关关着时，SDK 只会一直回调错误码，这里给个提示入口。
        val providerOn = runCatching {
            Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE) !=
                    Settings.Secure.LOCATION_MODE_OFF
        }.getOrDefault(true)
        Log.i("MainActivity", "auto-locate start (systemLocationOn=$providerOn)")

        val app = application as FootprintApp
        app.locationProvider.start()
        app.cityProvider.refresh()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
