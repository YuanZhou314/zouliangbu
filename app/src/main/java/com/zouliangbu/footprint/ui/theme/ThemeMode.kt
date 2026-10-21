package com.zouliangbu.footprint.ui.theme

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** 主题模式：跟随系统 / 浅色 / 深色（task-003）。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

/**
 * 主题偏好：SharedPreferences 持久化 + Compose 可观察状态。
 * FootprintApp 启动时 load，MainActivity 读取决定 darkTheme。
 */
object ThemePrefs {
    private const val PREFS = "app_prefs"
    private const val KEY = "theme_mode"

    val mode: MutableState<ThemeMode> = mutableStateOf(ThemeMode.SYSTEM)

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        mode.value = saved?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, value: ThemeMode) {
        mode.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value.name).apply()
    }
}
