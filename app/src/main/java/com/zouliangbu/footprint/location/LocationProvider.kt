package com.zouliangbu.footprint.location

import android.annotation.SuppressLint
import android.content.Context
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

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2000L): Flow<AMapLocation> = callbackFlow {
        val client = AMapLocationClient(appContext)
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