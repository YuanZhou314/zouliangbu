package com.zouliangbu.footprint.ui.home

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSummary
import com.zouliangbu.footprint.ui.components.MonthHeader
import com.zouliangbu.footprint.ui.components.TrackCard
import com.zouliangbu.footprint.ui.components.TrackSummaryCard
import com.zouliangbu.footprint.ui.components.monthKey
import com.zouliangbu.footprint.ui.theme.ForestGreen
import com.zouliangbu.footprint.ui.theme.TrailOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 首页城市栏状态。 */
sealed interface CityUiState {
    data object Loading : CityUiState
    data class Ready(val name: String) : CityUiState
    data object NoPermission : CityUiState
    data object Failed : CityUiState
}

/**
 * 首页 ViewModel：**本地轨迹时间轴** + 城市名（高德定位）。
 *
 * 数据全部来自本地 Room（`TrackRepository.observeCompleted()`），不发起任何网络请求，
 * 断网时首页照常可用。
 *
 * 城市名走「订阅 CityProvider.cityName」而不是一次性拉取：
 * 启动时的权限弹框是异步的，用户点「允许」后已组合好的首页必须能自动更新。
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as FootprintApp

    val tracks: StateFlow<List<TrackSummary>> = application.repository
        .observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _city = MutableStateFlow<CityUiState>(CityUiState.Loading)
    val city: StateFlow<CityUiState> = _city.asStateFlow()

    // 缩略图缓存：与「我的记录」各自持有，互不影响。
    private val pointsCache = java.util.concurrent.ConcurrentHashMap<Long, List<TrackPoint>>()

    /** 取缩略图用的轨迹点：等距抽 ≤200 个，画折线足够。 */
    suspend fun thumbnailPoints(sessionId: Long): List<TrackPoint> =
        pointsCache[sessionId] ?: withContext(Dispatchers.IO) {
            val all = application.repository.getPointsForSession(sessionId)
            val sampled = if (all.size <= 200) all else {
                val step = all.size.toDouble() / 200
                List(200) { all[(it * step).toInt()] }
            }
            pointsCache[sessionId] = sampled
            sampled
        }

    init {
        observeCity()
    }

    private fun observeCity() {
        // 1) 初始态：缓存值优先，其次按权限决定「定位中 / 开启定位」
        val cached = application.cityProvider.cachedCity()
        _city.value = when {
            cached != null -> CityUiState.Ready(cached)
            application.cityProvider.hasLocationPermission() -> CityUiState.Loading
            else -> CityUiState.NoPermission
        }

        // 2) 城市名一到就刷新（权限授予后 fetchCity 回来即触发）
        viewModelScope.launch {
            application.cityProvider.cityName.collectLatest { name ->
                if (!name.isNullOrBlank()) {
                    _city.value = CityUiState.Ready(name)
                } else if (!application.cityProvider.hasLocationPermission()) {
                    _city.value = CityUiState.NoPermission
                }
            }
        }

        // 3) 有权限就发起定位取城市
        loadCity()
    }

    fun loadCity(force: Boolean = false) {
        if (!force && _city.value is CityUiState.Ready) return

        application.cityProvider.cachedCity()?.let {
            _city.value = CityUiState.Ready(it)
        }

        if (!application.cityProvider.hasLocationPermission()) {
            if (_city.value !is CityUiState.Ready) _city.value = CityUiState.NoPermission
            return
        }
        if (_city.value !is CityUiState.Ready) _city.value = CityUiState.Loading

        viewModelScope.launch {
            // fetchCity 内部带超时；拿到值会写进 cityName，由上面的订阅者统一更新 UI。
            val fetched = application.cityProvider.fetchCity()
            if (fetched == null && _city.value !is CityUiState.Ready) {
                _city.value = CityUiState.Failed
            }
        }
    }
}

/**
 * 首页：城市栏 + 轨迹时间轴（本地数据）。
 *
 * 设计取舍：**首屏只放"我自己的数据"**。
 * 曾经的「同城动态」瀑布流已移除 —— 它的内容必须依赖服务端，断网即空，
 * 与"户外默认没信号"的产品前提冲突（见 docs/product-philosophy.md）。
 */
@Composable
fun HomeScreen(
    onOpenDetail: (Long) -> Unit = {},
    onOpenRecord: () -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()

    // 首页城市栏的定位权限申请：只要 FINE+COARSE，不带后台/通知权限（避免整组被静默忽略）
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) vm.loadCity(force = true)
    }

    LaunchedEffect(Unit) { vm.loadCity() }

    // 本月汇总：tracks 变化才重算，避免每次重组都遍历
    val monthStats = remember(tracks) { statsOf(tracks, monthKey(System.currentTimeMillis())) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "city") {
            CityBar(
                city = city,
                monthCount = monthStats.count,
                onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }

        if (tracks.isEmpty()) {
            item(key = "empty") { HomeEmptyState(onOpenRecord = onOpenRecord) }
            return@LazyColumn
        }

        // 本月汇总：一眼看到"这个月走了多少"
        item(key = "summary") {
            TrackSummaryCard(
                count = monthStats.count,
                totalMs = monthStats.totalMs,
                totalMeters = monthStats.totalMeters
            )
        }

        // 按月倒序分组
        tracks.groupBy { monthKey(it.startTime) }
            .toSortedMap(compareByDescending { it })
            .forEach { (month, list) ->
                item(key = "month-$month") {
                    MonthHeader(
                        month = month,
                        meters = list.sumOf { it.totalDistanceMeters.toDouble() }.toFloat(),
                        ascent = list.sumOf { it.ascentMeters },
                        durationMs = list.sumOf { it.activeDurationMs },
                        count = list.size
                    )
                }
                items(count = list.size, key = { list[it].id }) { idx ->
                    val track = list[idx]
                    TrackCard(
                        track = track,
                        thumbPoints = { vm.thumbnailPoints(track.id) },
                        onClick = { onOpenDetail(track.id) }
                    )
                }
            }

        item(key = "footer") {
            Text(
                "共 ${tracks.size} 条记录 · 数据保存在本机",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class GroupStats(val count: Int, val totalMs: Long, val totalMeters: Float)

private fun statsOf(tracks: List<TrackSummary>, month: String): GroupStats {
    val inMonth = tracks.filter { monthKey(it.startTime) == month }
    return GroupStats(
        count = inMonth.size,
        totalMs = inMonth.sumOf { it.activeDurationMs },
        totalMeters = inMonth.sumOf { it.totalDistanceMeters.toDouble() }.toFloat()
    )
}

/**
 * 城市栏：左边定位状态，右边本月记录条数。
 * 原来的「关注 / 在路上 / 搭子」是纯装饰文字，无点击无逻辑，一并移除。
 */
@Composable
private fun CityBar(city: CityUiState, monthCount: Int, onRequestPermission: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = ForestGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        when (city) {
            is CityUiState.Ready -> Text(
                city.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            CityUiState.Loading -> Text(
                "定位中…",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CityUiState.NoPermission -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onRequestPermission)
            ) {
                Text(
                    "开启定位",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TrailOrange
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "申请定位权限",
                    tint = TrailOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
            CityUiState.Failed -> Text(
                "定位失败",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "本月 $monthCount 条",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 空态：引导用户去记录，而不是看到一片空白。 */
@Composable
private fun HomeEmptyState(onOpenRecord: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("还没有轨迹记录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "点底栏中间的「出发」开始第一次记录，\n轨迹会按时间自动出现在这里。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenRecord) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("去记录")
            }
        }
    }
}
