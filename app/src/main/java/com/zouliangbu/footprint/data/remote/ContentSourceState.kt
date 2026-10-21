package com.zouliangbu.footprint.data.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 内容数据源状态（问题 2）：任一 Remote 仓库因网络失败/空数据回退到 Mock 时置 true，
 * 网络成功时置 false。UI（HomeScreen / RoutesScreen）据此显示「离线演示数据」横幅。
 */
object ContentSourceState {
    var isOfflineFallback by mutableStateOf(false)
        private set

    /** Remote 仓库网络成功拿到线上数据时调用。 */
    fun onNetworkSuccess() {
        isOfflineFallback = false
    }

    /** Remote 仓库回退 Mock 时调用。 */
    fun onOfflineFallback() {
        isOfflineFallback = true
    }
}
