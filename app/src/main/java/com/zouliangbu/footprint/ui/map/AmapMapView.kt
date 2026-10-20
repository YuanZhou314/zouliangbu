package com.zouliangbu.footprint.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.location.AMapLocation
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.AmapKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全屏高德地图 Composable。
 *
 * - 没有有效 API Key 时显示提示占位（编译/安装不受影响）。
 * - 有 Key 时把 AMap 的 MapView 包进 AndroidView，按 Lifecycle 转发
 *   onCreate / onResume / onPause / onDestroy（AMap 10.x MapView 只有这 4 个生命周期方法）。
 *
 * 定位策略（重要）：**地图不自己发起 AMap 定位**。
 * 地图的"我的位置"通过 [AmapLocationSource] 桥接 App 共享的 [LocationProvider] 定位流，
 * 与轨迹记录 Service 共用同一个上游 AMapLocationClient，避免多个定位客户端互相抢资源。
 * 内置"定位到当前位置"按钮关闭，改由 Compose 自绘（右下角），位置完全可控。
 */
@Composable
fun AmapMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!AmapKey.isConfigured(context)) {
        MapKeyPlaceholder(modifier)
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { createMapView(context) }
    val provider = remember { (context.applicationContext as FootprintApp).locationProvider }

    // 地图侧定位源：桥接共享 LocationProvider。Compose 负责订阅/退订，随生命周期。
    val source = remember { AmapLocationSource(provider) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    mapView.map.run {
                        setLocationSource(source)
                        setMyLocationEnabled(true)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.map.setMyLocationEnabled(false)
                    mapView.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // 首点跟随：拿到第一个有效定位后把相机移到真实位置，避免初始停在默认中心
        //（AMap 默认中心不在国内用户位置，WiFi 下点定位才会跳变）。
        val followJob = scope.launch {
            source.firstFix { loc ->
                mapView.map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(loc.latitude, loc.longitude), 16f
                    )
                )
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            followJob.cancel()
            source.destroy()
            scope.cancel()
            mapView.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // 自绘定位按钮（右下角，位置可控，替代高德内置按钮——内置按钮位置由 SDK 定死）。
        // 点击把相机移回最近一次定位点。
        val recenterClick: () -> Unit = {
            provider.lastLocation?.let { loc ->
                mapView.map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(loc.latitude, loc.longitude), 16f
                    )
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp, end = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFFFFFFF), RoundedCornerShape(28.dp))
                .clickable(onClick = recenterClick)
                .padding(14.dp)
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = "定位到我的位置",
                tint = Color(0xFF4285F4)
            )
        }
    }
}

/**
 * 把 [LocationProvider] 的定位流桥接给高德地图的 "我的位置" 蓝点。
 * AMap 的 LocationSource 是拉模型（地图在需要时调用 activate），这里转成订阅共享流。
 */
private class AmapLocationSource(
    private val provider: com.zouliangbu.footprint.location.LocationProvider
) : LocationSource {
    private var listener: LocationSource.OnLocationChangedListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: kotlinx.coroutines.Job? = null

    override fun activate(listener: LocationSource.OnLocationChangedListener?) {
        this.listener = listener
        job?.cancel()
        job = scope.launch {
            provider.location.collect { loc ->
                // AMapLocation 可以直接喂给地图蓝点（已经是 GCJ-02）。
                listener?.onLocationChanged(loc)
            }
        }
    }

    /**
     * 取定位流的第一帧定位点并回调（首点跟随相机用）。
     * 不依赖 activate 是否已调用；共享流 replay=1，新订阅者立即可拿到最近一次定位。
     */
    fun firstFix(callback: (AMapLocation) -> Unit) {
        scope.launch {
            provider.location.first().let { callback(it) }
        }
    }

    override fun deactivate() {
        job?.cancel()
        job = null
        listener = null
    }

    fun destroy() {
        deactivate()
        scope.cancel()
    }
}

private fun createMapView(context: Context): MapView {
    val mapView = MapView(context)
    mapView.onCreate(null)
    val aMap = mapView.map
    aMap.uiSettings.apply {
        // 内置定位按钮关闭：改用 Compose 自绘，位置可控。
        setMyLocationButtonEnabled(false)
        setZoomControlsEnabled(false)
        setCompassEnabled(true)
        setScaleControlsEnabled(true)
    }
    // 蓝点样式：普通蓝点 + 精度圈（不旋转跟随，保持简洁）
    aMap.myLocationStyle = MyLocationStyle().apply {
        myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
        interval(2_000L)
    }
    // 不在这里 moveCamera 到固定位置：初始默认位置由 AMap 决定（国内默认北京附近）。
    // 首点跟随在 AmapMapView 的定位订阅里处理。
    return mapView
}

@Composable
private fun MapKeyPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFB0BEC5)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "高德 AMap Key 未配置\n\n" +
                    "1. 访问 lbs.amap.com 注册开发者账号\n" +
                    "2. 创建 Android 应用 → 填入包名 com.zouliangbu.footprint\n" +
                    "   和 debug.keystore 的 SHA1\n" +
                    "3. 把高德下发的 Key 填到\n" +
                    "   res/values/strings.xml 的 amap_api_key\n\n" +
                    "当前显示是占位视图，App 已编译安装成功。",
            color = Color(0xFF263238),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp)
        )
    }
}
