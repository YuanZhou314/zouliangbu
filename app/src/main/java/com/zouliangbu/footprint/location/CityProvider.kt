package com.zouliangbu.footprint.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 城市提供者：从共享定位流取一帧带城市名的定位，取完即退订（不常驻）。
 *
 * 设计约束：全 App 只有一个 AMapLocationClient（见 LocationProvider），
 * 这里不新建第二个 client，只订阅 LocationProvider 的共享流。
 * LocationProvider 已开 isNeedAddress=true，AMapLocation.city 才有值。
 *
 * 城市 = city 字段，为空时降级用 province；统一去掉「市」后缀（"深圳市"→"深圳"）。
 * 拿到的城市写 SharedPreferences 缓存，下次冷启动立即有值。
 */
class CityProvider(
    context: Context,
    private val locationProvider: LocationProvider
) {
    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences("city_cache", Context.MODE_PRIVATE)
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /**
     * 城市名的可观察最新值（null = 还没拿到）。
     *
     * 为什么要有它：权限弹框是异步的，用户点「允许」时首页可能早已组合完毕并停在
     * 「开启定位」态。只靠一次性的 fetchCity() 返回值，UI 状态不会跟着更新。
     * 这里把 fetchCity 的结果一并发布出去，首页订阅即可自动刷新。
     */
    val cityName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        // 冷启动先用缓存值占位，避免首屏「定位中…」闪一下
        cityName.value = prefs.getString(KEY_LAST_CITY, null)
    }

    /** 预热：启动时后台拉一次城市名写缓存并发布。 */
    fun refresh() {
        if (!hasLocationPermission()) return
        scope.launch {
            runCatching { fetchCity() }
        }
    }

    fun cachedCity(): String? = prefs.getString(KEY_LAST_CITY, null)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * 订阅共享定位流，拿到第一帧城市非空的定位后立即返回并退订。
     * 超时（默认 10s）或上游异常返回 null（不抛异常，调用方决定降级文案）。
     * 注意：上游是 SharedFlow，用 try/catch（.catch 操作符对 SharedFlow 已弃用且无效果）；
     * CancellationException 必须原样抛出，否则会破坏协程取消语义。
     */
    suspend fun fetchCity(timeoutMs: Long = 10_000L): String? {
        val city = withTimeoutOrNull(timeoutMs) {
            try {
                locationProvider.location
                    .firstOrNull { !it.city.isNullOrBlank() || !it.province.isNullOrBlank() }
                    ?.let { loc ->
                        (loc.city?.takeIf { it.isNotBlank() } ?: loc.province)
                            ?.removeSuffix("市")
                    }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "location flow failed", t)
                null
            }
        }
        if (!city.isNullOrBlank()) {
            prefs.edit().putString(KEY_LAST_CITY, city).apply()
            cityName.value = city
        }
        return city
    }

    private companion object {
        const val TAG = "CityProvider"
        const val KEY_LAST_CITY = "last_city"
    }
}
