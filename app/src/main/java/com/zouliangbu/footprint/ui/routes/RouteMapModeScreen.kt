package com.zouliangbu.footprint.ui.routes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.model.RouteItem
import com.zouliangbu.footprint.data.remote.ContentApiFactory
import com.zouliangbu.footprint.ui.map.MapBounds
import com.zouliangbu.footprint.ui.map.MapEngine
import com.zouliangbu.footprint.ui.map.MapMarker
import com.zouliangbu.footprint.ui.map.MapPoint
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** 拖拽过程中的连续范围（地图占屏比例）：拖动时可停在任意位置，松手后才吸附回三档。 */
private const val DRAG_MIN_MAP_FRACTION = 0.02f
private const val DRAG_MAX_MAP_FRACTION = 0.95f

/**
 * 三档吸附位（地图占屏比例）：
 * - top：地图只剩顶部约一条状态栏，列表盖到最上（注意此时地图浮层按钮会被列表盖住）；
 * - middle：上下对半，初始档；
 * - bottom：列表只剩顶部把手区域，地图占满其余部分。
 * public 以便 JVM 单测直接引用。
 */
val MAP_ANCHOR_TOP = 0.04f
val MAP_ANCHOR_MIDDLE = 0.5f
val MAP_ANCHOR_BOTTOM = 0.92f
val MAP_ANCHORS = listOf(MAP_ANCHOR_TOP, MAP_ANCHOR_MIDDLE, MAP_ANCHOR_BOTTOM)

/** 返回 anchors 中离 value 最近的一档（等距时取先出现者）。抽成纯函数便于单测。 */
fun nearestAnchor(value: Float, anchors: List<Float>): Float =
    anchors.minByOrNull { abs(it - value) } ?: value

/**
 * 按关键词过滤线路：name / region / tags 任一**包含**关键词即命中（不区分大小写）。
 * 关键词先 trim，trim 后为空返回原列表（不复制）。纯函数，JVM 单测直接覆盖。
 */
fun filterRoutes(routes: List<RouteItem>, query: String): List<RouteItem> {
    val q = query.trim()
    if (q.isEmpty()) return routes
    return routes.filter { route ->
        route.name.contains(q, ignoreCase = true) ||
            route.region.contains(q, ignoreCase = true) ||
            route.tags.any { it.contains(q, ignoreCase = true) }
    }
}

/** 视口内起点 marker 的筛选边距：可视区每边向外扩 20%，贴边的路线不闪现闪失。 */
const val VIEWPORT_MARKER_MARGIN = 0.20

/** 视口内起点 marker 的数量上限：按离视口中心由近到远截断。 */
const val VIEWPORT_MARKER_LIMIT = 20

/**
 * 从 [routes] 里筛出起点落在 [bounds] 内（四周外扩 [margin] 比例的跨度）的路线，
 * 按与视口中心的距离由近到远排序后最多取 [limit] 条。
 *
 * startLat / startLng 缺失（服务端未更新 / 老缓存）的路线直接跳过——
 * 降级语义：没有坐标就不出 marker，不报错。纯函数，JVM 单测直接覆盖。
 */
fun routesInBounds(
    routes: List<RouteItem>,
    bounds: MapBounds,
    margin: Double,
    limit: Int
): List<RouteItem> {
    if (limit <= 0) return emptyList()
    val latSpan = bounds.north - bounds.south
    val lngSpan = bounds.east - bounds.west
    val n = bounds.north + latSpan * margin
    val s = bounds.south - latSpan * margin
    val e = bounds.east + lngSpan * margin
    val w = bounds.west - lngSpan * margin
    val centerLat = (bounds.north + bounds.south) / 2.0
    val centerLng = (bounds.east + bounds.west) / 2.0
    return routes.mapNotNull { route ->
        val lat = route.startLat ?: return@mapNotNull null
        val lng = route.startLng ?: return@mapNotNull null
        if (lat !in s..n || lng !in w..e) return@mapNotNull null
        // 等距圆柱近似（只用于「谁更近」的排序，不需要真实米数）：经度差乘中心纬度余弦。
        val x = (lng - centerLng) * cos(Math.toRadians(centerLat))
        val y = lat - centerLat
        route to (x * x + y * y)
    }
        .sortedBy { it.second }
        .take(limit)
        .map { it.first }
}

/**
 * 线路「地图模式」全屏页：上半屏地图 + 下半屏线路列表，把手栏（横线 + 搜索框整行）
 * 可上下拖拽调比例，松手后吸附到三档（顶=地图只剩状态栏 / 对半=初始 / 底=列表只剩把手栏）。
 * 搜索框按 name / region / tags 实时过滤列表。
 *
 * 点列表某一行 → 选中该线路、按 routeId 现拉轨迹点画到地图上；地图上的小卡片展示
 * 名称 / 里程 / 爬升，点进去是线路详情。
 */
@Composable
fun RouteMapModeScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: RoutesViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as FootprintApp

    // 列表要覆盖全部线路：推荐位 + 各分组平铺后按 id 去重（推荐位常与分组里的条目重复）。
    val routes = remember(state) {
        (state.recommendations + state.sections.flatMap { it.routes }).distinctBy { it.id }
    }

    var selected by remember { mutableStateOf<RouteItem?>(null) }
    // null = 加载中；空列表 = 拉取失败或该线路没有轨迹数据
    var track by remember { mutableStateOf<List<MapPoint>?>(null) }
    var engine by remember { mutableStateOf<MapEngine?>(null) }

    var mapFraction by remember { mutableFloatStateOf(MAP_ANCHOR_MIDDLE) }
    // 松手吸附：拖拽期间 mapFraction 连续变化，松手后用 Animatable 动画到最近档。
    // settleJob 保存吸附动画，再次拖拽时先取消，避免动画帧和手指互相抢写 mapFraction。
    val settle = remember { Animatable(MAP_ANCHOR_MIDDLE) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // 地图是整屏测量的（见下方 Layout），被列表遮住多少要由 coveredBottomDp 告诉地图引擎，
    // 否则轨迹会被居中到屏幕中间、下半段看不见。只在拖拽**结束**时更新：一次手势只 refit
    // 一次相机，拖动过程中相机不动，画面才稳。
    var coveredBottomDp by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected?.id) {
        val id = selected?.id?.toLongOrNull() ?: return@LaunchedEffect
        track = null
        track = withContext(Dispatchers.IO) {
            runCatching { ContentApiFactory.service.getTrackPoints(id) }
                .getOrNull()?.data.orEmpty()
                .map { MapPoint(it.lat, it.lng) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val density = LocalDensity.current
        val totalHeightPx = remember(maxHeight) { with(density) { maxHeight.toPx() } }

        // 按初始档（对半）给个初值：第一帧一定还没有选中线路，真正 fit 发生在这次赋值之后。
        LaunchedEffect(maxHeight) {
            coveredBottomDp = (maxHeight.value * (1f - MAP_ANCHOR_MIDDLE)).roundToInt()
        }

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                // 图层 1：地图（最底）
                MapViewWrapper(
                    modifier = Modifier.fillMaxSize(),
                    track = track.orEmpty(),
                    showEndpoints = true,
                    // autoZoom=false 才会走引擎的首点跟随（autoZoom=true 时它被显式禁用，
                    // 相机会一直停在 AMap 默认中心北京）。选中线路后的轨迹适配不靠这里的
                    // fitKey（那个机制要求 autoZoom=true），由本页下方单独的 fit 效果接管。
                    autoZoom = false,
                    satellite = false,
                    // 地图按整屏高度测量，SDK 自带的定位按钮会被压到列表下面看不见，改用自绘。
                    showLocateButton = false,
                    coveredBottomDp = coveredBottomDp,
                    onEngineReady = { engine = it }
                )

                // 图层 2：地图浮层（返回键 / 定位 / 底部线路小卡）
                MapOverlayLayer(
                    selected = selected,
                    track = track,
                    onBack = onBack,
                    onOpenDetail = onOpenDetail,
                    onLocate = {
                        app.locationProvider.lastLocation?.let { loc ->
                            engine?.moveTo(MapPoint(loc.latitude, loc.longitude))
                        }
                    }
                )

                // 图层 3：分隔条 + 线路列表（最上层，触摸优先）
                RouteListLayer(
                    routes = routes,
                    selectedId = selected?.id,
                    totalHeightPx = totalHeightPx,
                    onSelect = { selected = it },
                    onDragFraction = { delta ->
                        settleJob?.cancel()
                        mapFraction = (mapFraction + delta).coerceIn(DRAG_MIN_MAP_FRACTION, DRAG_MAX_MAP_FRACTION)
                    },
                    onDragEnd = {
                        val target = nearestAnchor(mapFraction, MAP_ANCHORS)
                        settleJob = scope.launch {
                            if (abs(target - mapFraction) > 0.001f) {
                                settle.snapTo(mapFraction)
                                settle.animateTo(target, tween(240)) { mapFraction = value }
                            }
                            coveredBottomDp = (maxHeight.value * (1f - mapFraction)).roundToInt()
                        }
                    }
                )
            }
        ) { measurables, constraints ->
            val total = constraints.maxHeight
            val mapH = (total * mapFraction).roundToInt().coerceIn(0, total)
            val listH = (total - mapH).coerceAtLeast(0)

            // 地图永远用整屏高度测量：拖拽时它的测量约束不变 → MapView 不重新 measure，
            // 不会反复重建 GL Surface 造成闪屏；多出来的部分被上层列表盖住。
            // 浮层按可见高度测量，内容才能贴着分隔条排。
            val mapPlaceable = measurables[0].measure(constraints.copy(minHeight = total, maxHeight = total))
            val overlayPlaceable = measurables[1].measure(constraints.copy(minHeight = mapH, maxHeight = mapH))
            val listPlaceable = measurables[2].measure(constraints.copy(minHeight = listH, maxHeight = listH))

            layout(constraints.maxWidth, total) {
                mapPlaceable.place(0, 0)
                overlayPlaceable.place(0, 0)
                listPlaceable.place(0, mapH)
            }
        }

        // ===== 选中线路后的轨迹适配（问题 7 后半段）=====
        // 本页 autoZoom=false，MapViewWrapper 内置的 fitKey→zoomToTrack 机制不生效
        //（那个机制要求 autoZoom=true），在这里手动触发：轨迹点拉回、或拖拽落档
        // 改变可视区（coveredBottomDp 变化）时，按当前遮挡高度适配一次相机。
        // 声明在 Layout 之后：与引擎内部的 followTo（按轨迹末点跟相机）同一帧竞跑时，
        // 本效果后启动、moveCamera 后执行，适配结果不会被跟走。
        LaunchedEffect(selected?.id, track, engine, coveredBottomDp) {
            val eng = engine ?: return@LaunchedEffect
            val points = track ?: return@LaunchedEffect
            if (points.isEmpty()) return@LaunchedEffect
            eng.zoomToTrack(
                points,
                with(density) { 32.dp.roundToPx() },
                with(density) { coveredBottomDp.dp.roundToPx() }
            )
        }

        // ===== 视口内路线起点 marker（问题 8a）=====
        // onCameraIdle 是单槽 var，本页是唯一消费者：相机停下（进入页面首点跟随、
        // 手动拖动缩放、选中线路后的 fit）就按可视区刷一次 marker；feed 数据变化时
        // 重挂回调并立即刷一次，保证路线坐标到位后 marker 能出现。
        LaunchedEffect(engine, routes) {
            val eng = engine ?: return@LaunchedEffect
            fun refreshMarkers() {
                val bounds = eng.visibleBounds() ?: return
                val inView = routesInBounds(routes, bounds, VIEWPORT_MARKER_MARGIN, VIEWPORT_MARKER_LIMIT)
                if (inView.isEmpty()) {
                    eng.clearPointMarkers()
                } else {
                    eng.showPointMarkers(
                        inView.mapNotNull { route ->
                            val lat = route.startLat
                            val lng = route.startLng
                            if (lat != null && lng != null) {
                                MapMarker(route.id, lat, lng, route.name)
                            } else {
                                null
                            }
                        }
                    )
                }
            }
            eng.onCameraIdle = { refreshMarkers() }
            // 点 marker 等价于从列表点选：赋值 selected 走既有链路
            //（拉轨迹 → 底部显示线路卡 → 上面的 fit 效果触发 zoomToTrack）。
            eng.onMarkerClick = { id -> routes.firstOrNull { it.id == id }?.let { selected = it } }
            refreshMarkers()
        }
    }
}

/** 地图层之上的浮层：左上角返回键、右下角定位、底部贴边的线路小卡。 */
@Composable
private fun MapOverlayLayer(
    selected: RouteItem?,
    track: List<MapPoint>?,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onLocate: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        FloatingRoundButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(start = 12.dp, top = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
                FloatingRoundButton(onClick = onLocate) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = "定位到我的位置",
                        tint = Color.White
                    )
                }
            }
            if (selected != null) {
                Spacer(Modifier.height(8.dp))
                when {
                    // 拉点中不显示线路信息，避免"选了新线却还显示上一条"的错觉
                    track == null -> TrackLoadingCard()
                    else -> RouteMiniCard(
                        route = selected,
                        noTrack = track.isEmpty(),
                        onClick = { selected.id.toLongOrNull()?.let { onOpenDetail(it) } }
                    )
                }
            }
        }
    }
}

/** 地图底部浮出的线路小卡：名称 + 里程 + 爬升，点击进入详情页。 */
@Composable
private fun RouteMiniCard(route: RouteItem, noTrack: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                route.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricLabel(Icons.Filled.Route, "%.1f km".format(route.distanceKm))
                Spacer(Modifier.width(16.dp))
                MetricLabel(Icons.Filled.Terrain, "爬升 ${route.ascentMeters} m")
            }
            if (noTrack) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "该线路暂无轨迹数据",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 轨迹点拉取中的占位卡（高度接近信息卡，切换时不跳变）。 */
@Composable
private fun TrackLoadingCard() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun MetricLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 40dp 圆形半透明浮层按钮：压在地图上时用固定深色底 + 白色图标，深浅底图都能看清。 */
@Composable
private fun FloatingRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** 下半段：把手栏（拖拽热区 + 搜索框）+ 全部线路列表。 */
@Composable
private fun RouteListLayer(
    routes: List<RouteItem>,
    selectedId: String?,
    totalHeightPx: Float,
    onSelect: (RouteItem) -> Unit,
    onDragFraction: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val visibleRoutes = remember(routes, query) { filterRoutes(routes, query) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 把手栏 = 拖拽热区：拖拽手势挂在整栏（把手横线 + 搜索框整行，约 68dp）上，
        // 而不是只有 20dp 的横线。搜索框自身不挂拖拽手势：BasicTextField 的点击手势
        // 先于父级 pointerInput 收到事件，点按（不过 touch slop）被它消费掉用于聚焦，
        // 只有真实的竖向拖动才会带动列表——不会出现"想点搜索框却拖动了列表"。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(totalHeightPx) {
                    detectVerticalDragGestures(
                        onDragEnd = onDragEnd,
                        onVerticalDrag = { _, dragAmount ->
                            // 整条把手栏都能拖：像素位移换算成占屏比例，边界在调用方 clamp
                            if (totalHeightPx > 0f) onDragFraction(dragAmount / totalHeightPx)
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
            RouteSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visibleRoutes.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "暂无线路" else "未找到匹配的线路",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(visibleRoutes, key = { it.id }) { route ->
                RouteListRow(
                    route = route,
                    selected = route.id == selectedId,
                    onClick = { onSelect(route) }
                )
            }
        }
    }
}

/** 把手栏中的搜索框：深色圆角、surfaceVariant 底、放大镜 icon，输入实时过滤下方列表。 */
@Composable
private fun RouteSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "搜索线路",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清空搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

/** 列表行：选中的行用 primaryContainer + 左侧色条高亮。 */
@Composable
private fun RouteListRow(route: RouteItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    route.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "%.1f km · 爬升 ${route.ascentMeters} m".format(route.distanceKm),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                route.region,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
