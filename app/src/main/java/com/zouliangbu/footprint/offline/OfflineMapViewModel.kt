package com.zouliangbu.footprint.offline

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.offlinemap.OfflineMapManager
import com.amap.api.maps.offlinemap.OfflineMapStatus
import com.zouliangbu.footprint.data.AmapKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 离线地图页状态。
 *
 * 双模式（2026-10-23）：
 *  - **高德官方离线**（有 Key 时的主路径）：[OfflineMapManager] 按城市下载，
 *    下载后高德地图自动使用，真实可下载可离线。这是用户真正用的那条路。
 *  - **MBTiles 离线包**（无 Key 的 osmdroid 兜底）：保留原有下载/校验逻辑。
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

/** 高德官方离线：一行城市数据（从 SDK 的 OfflineMapCity 拍平，便于 Compose 渲染）。 */
data class AmapCityState(
    val name: String,
    val code: String,
    val sizeBytes: Long,
    val state: Int,
    /** 下载进度 0–100。 */
    val progress: Int,
    val version: String?,
    /** 由 SDK 的「已下载列表」判定，比单看 state 可靠（见 [OfflineMapViewModel.refreshAmap]）。 */
    val downloaded: Boolean = false,
    /** 由 SDK 的「下载中列表」判定。 */
    val downloading: Boolean = false
) {
    /**
     * 暂停中。以 state 为准而不是「是否在下载列表里」——暂停后城市仍留在下载队列
     * （SDK 就是这么记账的），只看队列会把暂停项误判成下载中，按钮就永远是「暂停」。
     */
    val isPaused: Boolean
        get() = !downloaded &&
                (state == OfflineMapStatus.PAUSE || state == OfflineMapStatus.STOP)

    val hasNewVersion: Boolean get() = downloaded && state == OfflineMapStatus.NEW_VERSION

    val isFailed: Boolean
        get() = state == OfflineMapStatus.ERROR ||
                state == OfflineMapStatus.EXCEPTION_NETWORK_LOADING ||
                state == OfflineMapStatus.EXCEPTION_AMAP ||
                state == OfflineMapStatus.EXCEPTION_SDCARD ||
                state == OfflineMapStatus.START_DOWNLOAD_FAILD
}

/**
 * 离线地图 ViewModel。
 *
 * 高德模式：[OfflineMapManager] 管下载（内部有持久化，进程活着下载就继续），
 * 这里只做三件事：初始化、把 SDK 的城市列表拍平成 UI 状态、把按钮动作转成 SDK 调用。
 *
 * ## 两个踩过的坑（别改回去）
 * 1. **SDK 调用必须在主线程**：`OfflineMapManager` 构造里直接 `new Handler()`，
 *    在 IO 线程（无 Looper）上创建会崩/静默失败——表现就是「点了下载没反应，
 *    退出再进还在转圈」。所以所有 SDK 调用走 [Dispatchers.Main]。
 * 2. **不能只用 `city.state` 判状态**：`OfflineMapStatus.LOADING == 0`，
 *    而「从未下载」的城市默认值就是 0 —— 照 state 判会让整页城市全部显示
 *    「下载中 + 转圈」。真正的下载状态以 SDK 的
 *    [OfflineMapManager.getDownloadOfflineMapCityList]（已下载）与
 *    [OfflineMapManager.getDownloadingCityList]（下载中）为准。
 */
class OfflineMapViewModel(app: Application) : AndroidViewModel(app) {

    private val tag = "OfflineVM"

    /** 有高德 Key 就走官方离线（真实可下载）；否则走 MBTiles 兜底。 */
    val amapMode: Boolean = AmapKey.isConfigured(app)

    // ===== 高德官方离线 =====

    private var amapManager: OfflineMapManager? = null

    private val _amapCities = MutableStateFlow<List<AmapCityState>>(emptyList())
    val amapCities: StateFlow<List<AmapCityState>> = _amapCities.asStateFlow()

    /** 已下载的城市（置顶展示，与未下载的分开）。 */
    private val _amapDownloaded = MutableStateFlow<List<AmapCityState>>(emptyList())
    val amapDownloaded: StateFlow<List<AmapCityState>> = _amapDownloaded.asStateFlow()

    val amapLoading = MutableStateFlow(true)
    val amapError = MutableStateFlow<String?>(null)

    /**
     * 列表是否已就绪：SDK 的城市列表/本地离线索引是**异步加载**的（见 [initAmap] 注释），
     * 就绪前 UI 显示加载态而不是完整列表，避免先渲染全「未下载」、0.5s 后「已下载」组
     * 突然置顶跳变（用户反馈 1）。判定条件见 [refreshAmap] 与 [initAmap]。
     */
    private val _amapListReady = MutableStateFlow(false)
    val amapListReady: StateFlow<Boolean> = _amapListReady.asStateFlow()

    /** 有任务在下载时轮询刷新进度：SDK 的 onDownload 回调参数含义不明，靠轮询最稳。 */
    private var pollJob: kotlinx.coroutines.Job? = null

    // ===== MBTiles（osmdroid 兜底，逻辑原样保留）=====

    private val manager = OfflinePackageManager(app)

    private val _state = MutableStateFlow(OfflineMapUiState())
    val state: StateFlow<OfflineMapUiState> = _state.asStateFlow()

    init {
        if (amapMode) initAmap() else loadMbtiles()
    }

    private fun initAmap() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val m = OfflineMapManager(
                    getApplication(),
                    object : OfflineMapManager.OfflineMapDownloadListener {
                        // 回调只用来触发刷新；进度与状态统一从城市列表读，
                        // 不依赖回调参数顺序（SDK 的 onDownload 参数含义文档不清）。
                        override fun onDownload(p1: Int, p2: Int, p3: String?) = refreshAmap()
                        override fun onCheckUpdate(hasUpdate: Boolean, errorMsg: String?) = refreshAmap()
                        override fun onRemove(success: Boolean, code: String?, path: String?) = refreshAmap()
                    }
                )
                amapManager = m
                refreshAmap()
            } catch (t: Throwable) {
                Log.e(tag, "OfflineMapManager init failed", t)
                amapError.value = "离线地图初始化失败：" + (t.message ?: "未知错误")
            } finally {
                amapLoading.value = false
            }
        }
        // SDK 的城市列表/本地离线索引是**异步加载**的：构造后立刻读往往拿到
        // 空快照（尤其「已下载」列表要扫本地下载目录），只刷一次的话重进页面
        // 就永远显示空。做有限次延迟重查，任一次「已下载」非空即停；
        // viewModelScope 在 onCleared 取消，不会泄漏。有下载任务时另有轮询兜底。
        viewModelScope.launch(Dispatchers.Main) {
            for (delayMs in longArrayOf(500L, 1_500L, 3_000L)) {
                delay(delayMs)
                if (_amapDownloaded.value.isNotEmpty()) break
                Log.d("OfflineMap", "retry refreshAmap after " + delayMs + "ms")
                refreshAmap()
            }
            // 重查轮跑完（期间「已下载」一直为空）也置就绪：用户本来就没下载任何城市，
            // 不能让页面一直转圈。StateFlow 幂等，若中途已置 true 这里重复赋值无害。
            _amapListReady.value = true
        }
    }

    /**
     * 把 SDK 城市列表拍平成 UI 状态。
     *
     * 「已下载 / 正在下载」以 SDK 的两个专属列表为准，不用城市的 state 字段——
     * 未下载城市的 state 默认值 0 恰好等于 `OfflineMapStatus.LOADING`（见类注释）。
     */
    fun refreshAmap() {
        val m = amapManager ?: return
        val all = runCatching { m.offlineMapCityList }.getOrNull()
        val downloadedList = runCatching { m.downloadOfflineMapCityList }.getOrNull()
        val downloadingList = runCatching { m.downloadingCityList }.getOrNull()
        // 三个列表 size 打进日志：刚构造完全 0、1s 后 downloaded>0 即坐实「SDK 异步加载」根因。
        Log.d(
            "OfflineMap",
            "refresh all=" + (all?.size ?: -1) +
                " downloaded=" + (downloadedList?.size ?: -1) +
                " downloading=" + (downloadingList?.size ?: -1)
        )
        // 城市名列表打进日志（截断前 20 个）：SDK 的 OfflineMapCity.city 命名来自服务器配置，
        // 静态表命中不了时靠这里拿到真实命名（「全国概览图」条目 / 非常规后缀等），用于补表。
        Log.d("OfflineMap", "all cities(first 20)=" + all?.take(20)?.joinToString(",") { it.city ?: "?" })
        Log.d(
            "OfflineMap",
            "downloaded cities(first 20)=" +
                (downloadedList?.take(20)?.joinToString(",") { it.city ?: "?" } ?: "null")
        )
        if (all == null) return
        // 已扫到下载内容即视为列表就绪；StateFlow 幂等，轮询/重查路径重复置 true 无副作用。
        if (!downloadedList.isNullOrEmpty()) _amapListReady.value = true
        val downloadedCodes = downloadedList?.mapNotNull { it.code }?.toHashSet() ?: emptySet()
        val downloadingCodes = downloadingList?.mapNotNull { it.code }?.toHashSet() ?: emptySet()

        val mapped = all
            .filter { !it.city.isNullOrBlank() }
            .map {
                val code = it.code ?: ""
                AmapCityState(
                    name = it.city ?: "",
                    code = code,
                    sizeBytes = it.size,
                    state = it.state,
                    progress = it.getcompleteCode(),
                    version = it.version,
                    downloaded = code in downloadedCodes ||
                            it.state == OfflineMapStatus.SUCCESS,
                    // 暂停/停止不算「在下载」，否则进度条和按钮状态对不上。
                    downloading = code in downloadingCodes &&
                            it.state != OfflineMapStatus.SUCCESS &&
                            it.state != OfflineMapStatus.PAUSE &&
                            it.state != OfflineMapStatus.STOP
                )
            }

        _amapCities.value = mapped.filter { !it.downloaded }.sortedBy { it.name }
        _amapDownloaded.value = mapped.filter { it.downloaded }.sortedBy { it.name }

        // 有任务在跑就开始轮询；没有就停掉，避免白耗电。
        if (downloadingCodes.isNotEmpty()) startPolling() else stopPolling()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                delay(700)
                refreshAmap()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** SDK 调用统一切主线程（OfflineMapManager 内部 Handler 依赖主 Looper）。 */
    private suspend fun onAmap(block: (OfflineMapManager) -> Unit) = withContext(Dispatchers.Main) {
        val m = amapManager
        if (m == null) {
            amapError.value = "离线地图未初始化"
            return@withContext
        }
        runCatching { block(m) }
            .onFailure {
                Log.w(tag, "amap action failed", it)
                // 失败也要落到状态里：否则「点了没反应」连排查线索都没有（删除无反应就是这么坑的）。
                amapError.value = "操作失败：" + (it.message ?: "未知错误")
            }
        refreshAmap()
    }

    fun downloadCity(c: AmapCityState) {
        amapError.value = null
        viewModelScope.launch {
            onAmap { it.downloadByCityCode(c.code) }
            startPolling()
        }
    }

    fun pauseCity(c: AmapCityState) {
        viewModelScope.launch { onAmap { it.pauseByName(c.name) } }
    }

    /** 暂停后继续：SDK 没有单独的 resume，重新发起下载会从断点续传。 */
    fun resumeCity(c: AmapCityState) = downloadCity(c)

    /**
     * 删除一个已下载城市。
     *
     * 注意 `OfflineMapManager.remove(String)` 的参数是城市**名称**（传省份名会删全省），
     * 不是城市编码——传 code 时 SDK 找不到城市，删除静默无效（踩过）。
     */
    fun removeCity(c: AmapCityState) {
        viewModelScope.launch { onAmap { it.remove(c.name) } }
    }

    fun updateCity(c: AmapCityState) {
        viewModelScope.launch { onAmap { it.updateOfflineCityByCode(c.code) } }
    }

    override fun onCleared() {
        stopPolling()
        runCatching { amapManager?.destroy() }
        amapManager = null
        super.onCleared()
    }

    // ===== MBTiles（osmdroid 兜底）=====

    /** 加载 MBTiles 清单并刷新状态。 */
    fun loadMbtiles() {
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

    /** 下载一个 MBTiles 区域。 */
    fun download(region: OfflineRegion) {
        viewModelScope.launch {
            val progressJob = launch {
                manager.states.collect { states ->
                    _state.value = _state.value.copy(states = states)
                }
            }
            manager.download(region) { downloaded, _ ->
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
            _state.value = _state.value.copy(states = manager.states.value)
        }
    }

    /** 删除一个 MBTiles 区域。 */
    fun delete(region: OfflineRegion) {
        viewModelScope.launch {
            manager.delete(region.id)
            _state.value = _state.value.copy(states = manager.states.value)
        }
    }

    /** 清空全部 MBTiles 包。 */
    fun deleteAll() {
        viewModelScope.launch {
            manager.deleteAll()
            _state.value = _state.value.copy(states = manager.states.value)
        }
    }
}
