package com.zouliangbu.footprint

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.zouliangbu.footprint.data.db.AppDatabase
import com.zouliangbu.footprint.data.repo.TrackRepository
import org.osmdroid.config.Configuration
import java.io.File

class FootprintApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: TrackRepository by lazy {
        TrackRepository(database.sessionDao(), database.pointDao())
    }

    override fun onCreate() {
        super.onCreate()
        // ====== 隐私合规（必调，否则 AMapLocationClient 构造直接抛异常崩溃）======
        // 自 2023 年起，国内合规要求 SDK 调用方必须先告知并获得用户对隐私政策的同意。
        // 注意：高德 SDK 的隐私状态在两个类上**分别**保存——只调一个不够：
        //   - MapsInitializer → 高德地图 SDK
        //   - AMapLocationClient → 高德定位 SDK（这个不调就崩溃）
        // 这里在 SDK 任何接口前一次性确认同意；后续 UI 上若做隐私弹窗，按用户实际选择再调用。
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        // osmdroid 全局初始化（无高德 Key 时的过渡地图）。
        // 必须在任何 MapView 实例化之前调用；幂等——重复调用以最后一次为准。
        Configuration.getInstance().apply {
            load(this@FootprintApp, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
            userAgentValue = packageName
            // 瓦片缓存写到 app 私有目录，避开 WRITE_EXTERNAL_STORAGE 权限（minSdk 26 仍可能命中）。
            osmdroidTileCache = File(filesDir, "osmdroid").apply { mkdirs() }
        }
    }
}