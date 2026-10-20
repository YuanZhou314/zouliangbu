package com.zouliangbu.footprint

import android.app.Application
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.zouliangbu.footprint.data.SessionRecovery
import com.zouliangbu.footprint.data.db.AppDatabase
import com.zouliangbu.footprint.data.mock.MockFeedRepository
import com.zouliangbu.footprint.data.mock.MockGearRepository
import com.zouliangbu.footprint.data.mock.MockRouteRepository
import com.zouliangbu.footprint.data.repo.FeedRepository
import com.zouliangbu.footprint.data.repo.GearRepository
import com.zouliangbu.footprint.data.repo.RouteRepository
import com.zouliangbu.footprint.data.repo.TrackRepository
import com.zouliangbu.footprint.location.CityProvider
import com.zouliangbu.footprint.location.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class FootprintApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: TrackRepository by lazy { TrackRepository(database) }

    /** 全 App 共享的唯一定位提供者：地图蓝点、定位按钮、记录 Service 都从这里订阅。 */
    val locationProvider: LocationProvider by lazy { LocationProvider(this) }

    /** 城市提供者：从共享定位流取城市名（首页「同城」用），带缓存。 */
    val cityProvider: CityProvider by lazy { CityProvider(this, locationProvider) }

    // ====== 内容数据仓库（本期 Mock；接后台时只改这里的实现类，UI 零改动） ======
    val feedRepository: FeedRepository by lazy { MockFeedRepository() }
    val routeRepository: RouteRepository by lazy { MockRouteRepository() }
    val gearRepository: GearRepository by lazy { MockGearRepository() }

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

        // ====== 悬挂会话恢复（P2.2）======
        // 进程可能是上次记录中被系统杀掉后重建的：DB 里会留下 endTime = NULL 的会话，
        // 它既不在历史列表里（列表只查已结束的），也永远不会自己结束。
        // 这里在 IO 线程扫一遍并收尾；结果通过 SessionRecovery.orphaned 交给记录页提示。
        //
        // 为什么放 Application 而不是记录页：用户可能几周都不打开记录页，
        // 但数据必须在他第一次启动 App 时就落定，否则那条轨迹等于丢了。
        appScope.launch {
            runCatching { SessionRecovery.run(repository) }
                .onSuccess { handled ->
                    if (handled.isNotEmpty()) {
                        Log.i(
                            "FootprintApp",
                            "recovered ${handled.size} dangling session(s): " +
                                    handled.joinToString { "id=${it.sessionId} points=${it.pointCount}" }
                        )
                    }
                }
                .onFailure { Log.e("FootprintApp", "SessionRecovery failed", it) }
        }
    }
}