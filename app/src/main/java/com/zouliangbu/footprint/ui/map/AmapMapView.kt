package com.zouliangbu.footprint.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.MyLocationStyle
import com.zouliangbu.footprint.data.AmapKey

/**
 * 全屏高德地图 Composable。
 *
 * - 没有有效 API Key 时显示提示占位（编译/安装不受影响）。
 * - 有 Key 时把 AMap 的 MapView 包进 AndroidView，按 Lifecycle 转发
 *   onCreate / onResume / onPause / onDestroy（AMap 10.x MapView 只有这 4 个生命周期方法）。
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

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

private fun createMapView(context: Context): MapView {
    val mapView = MapView(context)
    mapView.onCreate(null)
    val aMap = mapView.map
    aMap.uiSettings.apply {
        setMyLocationButtonEnabled(true)
        setZoomControlsEnabled(false)
        setCompassEnabled(true)
        setScaleControlsEnabled(true)
    }
    aMap.myLocationStyle = MyLocationStyle().apply {
        interval(2000L)
        myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
    }
    aMap.setMyLocationEnabled(true)
    aMap.moveCamera(CameraUpdateFactory.zoomTo(16f))
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