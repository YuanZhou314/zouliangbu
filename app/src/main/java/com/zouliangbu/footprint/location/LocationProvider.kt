package com.zouliangbu.footprint.location

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * AMap 高精度定位的共享 Flow 封装（GCJ-02 输出）。
 *
 * 设计目标：**全 App 只跑一个 AMapLocationClient**。
 * 地图"我的位置"蓝点、自绘定位按钮、轨迹记录 Service 都订阅同一个共享流，
 * 避免多个 AMapLocationClient 同时请求互相抢定位资源。
 *
 * 通过 `shareIn` 把冷流（callbackFlow）转成共享热流：上游只在最后一个订阅者
 * 退订后 5s 才停止，记录中 Service 一直订阅，上游不会断。
 *
 * 错误码非 0（无定位、权限拒绝等）的点会被丢弃，Flow 不发射。
 */
class LocationProvider(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    /** 最近一次成功定位（自绘定位按钮 / 首点跟随用）。线程安全。 */
    @Volatile
    var lastLocation: AMapLocation? = null
        private set

    /**
     * 共享定位流：地图蓝点 + 记录 Service + 城市名共用同一个上游 AMapLocationClient。
     *
     * started = Eagerly：App 一启动就开始定位（用户明确要求「打开 App 就自动定位」）。
     * 之前是 WhileSubscribed(5s)，没有界面订阅时上游根本不启动 → 「给了权限也不定位」。
     * 全 App 只有一个 client，常驻运行不会额外增加功耗（本来就 2s 一次）。
     */
    val location: SharedFlow<AMapLocation> by lazy {
        locationUpdatesInternal()
            .onEach { loc ->
                lastLocation = loc
                if (!firstFixLogged) {
                    firstFixLogged = true
                    Log.i(
                        "LocationProvider",
                        "first fix: ${loc.latitude},${loc.longitude} city=${loc.city}"
                    )
                }
            }
            .shareIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                replay = 1 // 新订阅者（地图 activate）立即拿到最近一次定位
            )
    }

    /**
     * 预热定位（App 启动 / 授权成功后调用）：触碰一次 location 让 lazy 求值，
     * Eagerly 即开始定位；常驻订阅保证 lastLocation 持续更新（自绘定位按钮要用）。
     * 幂等。
     */
    fun start() {
        if (warmStarted) return
        warmStarted = true
        warmJob = scope.launch {
            runCatching { location.collect { } }
        }
        Log.i("LocationProvider", "start() -> location flow running")
    }

    @Volatile private var warmStarted = false
    @Volatile private var firstFixLogged = false
    private var warmJob: Job? = null

    @SuppressLint("MissingPermission")
    private fun locationUpdatesInternal(): Flow<AMapLocation> = callbackFlow {
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
            interval = 2_000L
            isGpsFirst = true
            isOnceLocation = false
            // 首页「同城」需要城市名：开启逆地理编码后 AMapLocation.city 才有值。
            // 坐标输出仍是 GCJ-02，不受影响；记录 Service 只读 lat/lng/accuracy，
            // 多余字段无副作用。无网时 city 为空但坐标仍正常回调。
            isNeedAddress = true
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
