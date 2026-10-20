package com.zouliangbu.footprint.offline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 离线地图页状态。
 *
 * @property regions  按大区分组、组内按名称排序的区域清单
 * @property states   各区域的安装状态（key = region id）
 * @property loading  清单加载中
 */
data class OfflineMapUiState(
    val regions: List<Pair<String, List<OfflineRegion>>> = emptyList(),
    val states: Map<String, OfflinePackageState> = emptyMap(),
    val loading: Boolean = true
) {
    /** 已安装包的总体积（字节）。 */
    val usedBytes: Long
        get() = states.values.filter { it.isInstalled }.sumOf { it.region.sizeBytes }
}

/**
 * 离线地图 ViewModel。
 *
 * 职责：读清单、管下载、报告状态。
 * 下载用 [viewModelScope] 的协程——用户离开页面时协程被取消，
 * 下载随之中断（已下载部分留在 .part 文件里，下次重下）。
 */
class OfflineMapViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = OfflinePackageManager(app)

    private val _state = MutableStateFlow(OfflineMapUiState())
    val state: StateFlow<OfflineMapUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** 加载清单并刷新状态。 */
    fun load() {
        viewModelScope.launch {
            val regions = withContext(Dispatchers.IO) {
                OfflineRegionCatalog.builtIn(getApplication())
            }
            withContext(Dispatchers.IO) { manager.refresh(regions) }
            _state.value = OfflineMapUiState(
                regions = OfflineRegionCatalog.grouped(regions),
                states = manager.states.value,
                loading = false
            )
        }
    }

    /** 下载一个区域。 */
    fun download(region: OfflineRegion) {
        viewModelScope.launch {
            // 下载期间持续把进度推给 UI。
            val progressJob = launch {
                manager.states.collect { states ->
                    _state.value = _state.value.copy(states = states)
                }
            }
            manager.download(region) { downloaded, total ->
                _state.value = _state.value.copy(
                    states = _state.value.states.toMutableMap().apply {
                        val old = this[region.id]
                        if (old != null) {
                            this[region.id] = old.copy(downloadedBytes = downloaded)
                        }
                    }
                )
            }
            progressJob.cancel()
            // 下载结束后刷新一次，把最终状态（INSTALLED / FAILED）落到 UI。
            _state.value = _state.value.copy(states = manager.states.value)
        }
    }

    /** 删除一个区域。 */
    fun delete(region: OfflineRegion) {
        viewModelScope.launch {
            manager.delete(region.id)
            _state.value = _state.value.copy(states = manager.states.value)
        }
    }

    /** 清空全部离线包。 */
    fun deleteAll() {
        viewModelScope.launch {
            manager.deleteAll()
            _state.value = _state.value.copy(states = manager.states.value)
        }
    }
}
