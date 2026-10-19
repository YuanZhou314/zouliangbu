package com.zouliangbu.footprint.location

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * AMap 高精度定位的 Flow 封装。
 *
 * 输出是 AMapLocation（已经做了 GCJ-02 偏移），调用方直接用 lat/lng 即可，
 * 距离累加不会出现 WGS-84 ↔ GCJ-02 混用导致的系统偏差。
 *
 * 错误码非 0（无定位、权限拒绝等）的点会被丢弃，Flow 不发射。
 */
class LocationProvider(context: Context) {

    private val appContext: Context = context.applicationContext

    init {
        // 防御：即使 FootprintApp.onCreate 跑过，这里再确认一次隐私状态。
        // START_STICKY 重启 Service 时，应用进程可能被复用但 Service 路径可能走
        // 不到 Application.onCreate —— 这里 init 在 LocationProvider 构造时触发，
        // 保证 AMapLocationClient 第一次构造前隐私 flag 已就位。
        runCatching {
            AMapLocationClient.updatePrivacyShow(appContext, true, true)
            AMapLocationClient.updatePrivacyAgree(appContext, true)
        }.onFailure { Log.w("LocationProvider", "privacy update failed", it) }
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2000L): Flow<AMapLocation> = callbackFlow {
        val client = try {
            AMapLocationClient(appContext)
        } catch (t: Throwable) {
            // 隐私未同意 / Key 缺失 / 内部异常——把原始 cause 写 logcat，Flow 关闭即可。
            // 不要让 Service 进程崩（START_STICKY 触发后会进入死循环）。
            Log.e("LocationProvider", "AMapLocationClient 构造失败", t)
            close(t)
            return@callbackFlow
        }

        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = intervalMs
            isGpsFirst = true
            isOnceLocation = false
            isNeedAddress = false
        }
        client.setLocationOption(option)

        val listener = AMapLocationListener { loc ->
            if (loc != null && loc.errorCode == 0) {
                trySend(loc)
            } else if (loc != null) {
                Log.w("LocationProvider", "AMap error ${loc.errorCode}: ${loc.errorInfo}")
            }
        }
        client.setLocationListener(listener)
        client.startLocation()

        awaitClose {
            client.stopLocation()
            client.onDestroy()
        }
    }
}