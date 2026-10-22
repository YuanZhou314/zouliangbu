package com.zouliangbu.footprint.ui.tracks

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.AmapKey
import com.zouliangbu.footprint.share.Share
import com.zouliangbu.footprint.share.ShareCardRenderer
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import com.zouliangbu.footprint.util.Pace
import com.zouliangbu.footprint.util.TimeFmt
import com.zouliangbu.footprint.util.TrackSplits
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 轨迹详情回看（全屏，无底栏）。
 *
 * 布局参照主流户外 App（六只脚/两步路）的「地图 + 可拖拽详情卡」：
 *
 *  - 地图铺满整屏做背景，详情卡浮在上面；
 *  - 卡片三档：展开（地图只留顶栏）/ 默认（露出上面约 4 成地图）/ 收起（底部一条，地图全屏）；
 *  - 卡内顺序是「先概览后细节」：时间 → 轨迹点 → 起点坐标 → 标题 → 大字号里程 → 难度 → 六宫格，
 *    分段明细在最后，用户可以往下拉慢慢看；
 *  - 分享/导出这类「输出动作」收进右上角三点菜单，底栏只留社区动作。
 *
 * 拖拽只挂在顶部把手上（内容区留给滚动）。把手 8dp 太细，实际热区做到 24dp。
 */
@Composable
fun TrackDetailScreen(
    sessionId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val factory = remember(sessionId) { TrackDetailViewModel.factory(app, sessionId) }
    val vm: TrackDetailViewModel = viewModel(key = "track-detail-" + sessionId, factory = factory)

    val ui by vm.state.collectAsStateWithLifecycle()
    val session = ui.session
    val title = session?.name ?: session?.let { formatDate(it.startTime) } ?: "轨迹详情"

    var showCompare by remember { mutableStateOf(false) }
    var showContribute by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var contributeError by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    fun shareCard() {
        val s = ui.session
        if (s == null || ui.points.isEmpty()) {
            Toast.makeText(context, "这条记录还没有轨迹点，无法分享", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val card = ShareCardRenderer.render(
                context = context,
                name = s.name,
                startTimeMs = s.startTime,
                distanceMeters = s.totalDistanceMeters.toDouble(),
                activeDurationMs = s.activeDurationMs,
                ascentMeters = ui.ascentMeters,
                trackPoints = ui.points
            )
            Share.shareImage(context, card, "我的行程卡")
        }.onFailure {
            Toast.makeText(context, "生成行程卡失败：" + (it.message ?: "未知错误"), Toast.LENGTH_SHORT).show()
        }
    }

    fun exportGpx() {
        vm.exportGpx { intent ->
            if (intent != null) context.startActivity(intent)
            else Toast.makeText(context, "这条记录还没有轨迹点，无法导出", Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenPx = constraints.maxHeight.toFloat()

        // 顶栏高度：状态栏 + 一行 56dp。地图从它下面开始画，
        // 两者不重叠 —— 否则 bbox 适配得留出一大圈空白才不被顶栏挡住，
        // 留白一大轨迹就被压得很小，看上去像"没全览"。
        val topBarH = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.toPx() }

        // 三档位置（卡片顶边距屏幕顶部的像素）。收起档固定露出 250dp —— 200dp 只够
        // 放下「把手 + 三行概览」，标题和大字号里程会被切掉；250dp 刚好让里程完整露出，
        // 同时仍留着大片地图（约 68% 屏高）。
        val collapsedTop = screenPx - with(density) { 210.dp.toPx() }
        // 默认档让地图占屏高约 1/3（用户对照六只脚提的比例），下面 2/3 归详情卡。
        val mediumTop = screenPx * 0.35f
        val expandedTop = with(density) { 56.dp.toPx() }
        val minTop = expandedTop
        val maxTop = collapsedTop.coerceAtLeast(mediumTop + 1f)

        var sheetTop by remember(screenPx) { mutableFloatStateOf(mediumTop) }

        // 收起档只留「标题 + 大字号里程」，否则三行概览就把首屏吃光，折叠后看不到关键数字。
        // 用 derivedStateOf 而不是直接读 sheetTop：拖拽每帧都会改 sheetTop，
        // 直接读会让整棵内容树每帧重组；这里只在跨过收起线时才翻转一次。
        val compact by remember(screenPx) {
            derivedStateOf { sheetTop >= maxTop - 4f }
        }
        val settle = remember(screenPx) { Animatable(mediumTop) }
        val scope = rememberCoroutineScope()

        // 地图的**实际视口**只到卡片顶边——不能让地图铺满整屏再让卡片盖住下半部分，
        // 否则 newLatLngBounds 会按整屏适配，轨迹正好落在被遮住的那 2/3 里，
        // 用户看到的就是"地图没定位到轨迹"。
        // 只在松手后更新（拖拽中每帧改地图尺寸会让 AMap 反复重排，掉帧）。
        var settledTop by remember(screenPx) { mutableFloatStateOf(mediumTop) }

        val dragState = rememberDraggableState { delta ->
            sheetTop = (sheetTop + delta).coerceIn(minTop, maxTop)
        }

        // 背景用 surface 而不是写死的黑色：卡片顶部两个圆角缺口会露出底色，
        // 以前是黑的，看着像"圆角没做对"。
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // ===== 地图（铺满，卡片浮在上面）=====
            if (ui.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                MapViewWrapper(
                    // 地图从屏幕最顶（状态栏后面）一直画到卡片顶边 —— 这正好是"可见区"，
                    // 所以 bbox 适配天然正确，不需要在引擎里做二次偏移。
                    // 试过「地图铺满整屏 + 用 coveredBottom 补偿」，但 moveCamera 的相机状态是
                    // 异步生效的，算偏移时读到的是旧值，越调越偏（详见 AmapMapEngine 注释）。
                    modifier = Modifier.layout { measurable, constraints ->
                        val h = settledTop.roundToInt().coerceIn(1, constraints.maxHeight)
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth, h)
                        )
                        layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                    },
                    track = ui.points,
                    showEndpoints = true,
                    autoZoom = true,
                    satellite = true,
                    // 换档后可视区变了，重新适配一次 bbox
                    fitKey = settledTop,
                    // 顶部有悬浮圆按钮（44dp）+ 状态栏，留 56dp 不遮挡 marker
                    fitPaddingDp = 56
                )
                if (ui.points.isNotEmpty() && !AmapKey.isConfigured(context)) {
                    Text(
                        "当前未配置地图 Key，仅显示无底图视图，轨迹线不可见",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 72.dp)
                            .fillMaxWidth()
                    )
                }
            }

            // ===== 可拖拽详情卡 =====
            // 用 Surface 而不是 Column + background()：Surface 会把 LocalContentColor
            // 设成 contentColorFor(surface)=onSurface，深色模式下文字才会自动变浅。
            // 用 background() 只画底色，文字仍继承外层的黑色 —— 深色模式下整页字看不见（踩过）。
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 高度跟着 sheetTop 走：高度=屏高-top，卡片底边始终贴屏幕底。
                    // 用 layout 而不是 height(dp)：后者每帧重组整棵子树，18 段分段会明显掉帧。
                    .layout { measurable, constraints ->
                        val h = (screenPx - sheetTop).roundToInt()
                            .coerceIn(0, constraints.maxHeight)
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth, h)
                        )
                        layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                    },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            ) {
            Column {
                // 把手：拖拽热区
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val projected = sheetTop + velocity * 0.10f
                                val target = listOf(minTop, mediumTop, maxTop)
                                    .minByOrNull { abs(it - projected) } ?: mediumTop
                                settledTop = target
                                scope.launch {
                                    settle.snapTo(sheetTop)
                                    settle.animateTo(target, tween(240)) { sheetTop = value }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 8.dp)
                ) {
                    // ---- 概览：时间 / 轨迹点 / 起点 ----
                    val endMs = session?.endTime
                    val startPoint = ui.points.firstOrNull()

                    if (!compact) {
                    MetaRow(
                        icon = Icons.Filled.Schedule,
                        text = session?.let {
                            if (endMs != null && endMs > 0L) formatRange(it.startTime, endMs)
                            else "开始于 " + formatFull(it.startTime)
                        } ?: "—",
                        maxLines = 2
                    )
                    MetaRow(
                        icon = Icons.Filled.Place,
                        text = "全程 " + ui.rawPointCount + " 个轨迹点"
                    )
                    if (startPoint != null) {
                        MetaRow(
                            icon = Icons.Filled.Place,
                            text = "起点 %.5f, %.5f".format(startPoint.lat, startPoint.lng)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    }

                    // ---- 标题 ----
                    Text(
                        title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))

                    // ---- 大字号里程 ----
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            session?.let { "%.2f".format(it.totalDistanceMeters / 1000f) } ?: "—",
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "公里",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        // 难度：本地按 里程+爬升 推出来的，不是用户自填（六只脚那个"简单"是上传者选的）
                        val diff = difficultyOf(
                            (session?.totalDistanceMeters?.toDouble() ?: 0.0) / 1000.0,
                            ui.ascentMeters
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                "难度",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                diff.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                diff.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (!compact) {
                    // ---- 六宫格细节 ----
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GridCell(
                            session?.let { TimeFmt.elapsed(it.activeDurationMs) } ?: "—",
                            "总时间", Modifier.weight(1f)
                        )
                        GridCell(kmh(ui.paceStats.maxSpeedMps), "最快速度(km/h)", Modifier.weight(1f))
                        GridCell(kmh(ui.paceStats.averageSpeedMps), "平均速度(km/h)", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GridCell(
                            if (ui.maxAltitude > 0.0) "%.0f".format(ui.maxAltitude) else "—",
                            "最高海拔(米)", Modifier.weight(1f)
                        )
                        GridCell(
                            if (ui.ascentMeters > 0.0) "%.0f".format(ui.ascentMeters) else "—",
                            "累计爬升(米)", Modifier.weight(1f)
                        )
                        GridCell(
                            if (ui.descentMeters > 0.0) "%.0f".format(ui.descentMeters) else "—",
                            "累计下降(米)", Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    val paused = session?.pausedMs?.takeIf { it > 0L }
                    Text(
                        session?.let {
                            val base = "平均配速 " + paceText(ui.paceStats)
                            if (paused != null) base + " · 含暂停 " + TimeFmt.elapsed(paused)
                            else base
                        } ?: "—",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    val xKm = ui.series.totalKm

                    TrackChart(
                        series = ui.series.speedKmh,
                        title = "全程速度（km/h）",
                        unit = "km/h",
                        color = MaterialTheme.colorScheme.primary,
                        xTotal = xKm,
                        average = ui.series.speedKmh.values.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                        summary = "平均 " + kmh(ui.paceStats.averageSpeedMps) + " · 最高 " +
                            kmh(ui.paceStats.maxSpeedMps) + " km/h（虚线为平均值）",
                        format = { "%.1f".format(it) }
                    )

                    Spacer(Modifier.height(22.dp))

                    TrackChart(
                        series = ui.series.paceSecPerKm,
                        title = "全程配速（分/公里）",
                        unit = "/km",
                        color = MaterialTheme.colorScheme.tertiary,
                        xTotal = xKm,
                        average = ui.series.paceSecPerKm.values.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                        summary = "平均配速 " + paceText(ui.paceStats) + "（数字越小越快）",
                        // 刻度要窄：h:mm'ss 那种 7 字符写法放不进 30dp 的轴槽，统一成「总分钟:秒」
                        format = { paceTick(it.toDouble()) }
                    )

                    Spacer(Modifier.height(22.dp))

                    TrackChart(
                        series = ui.series.altitude,
                        title = "海拔曲线（米）",
                        unit = "m",
                        color = MaterialTheme.colorScheme.secondary,
                        xTotal = xKm,
                        summary = "最高 " + (if (ui.maxAltitude > 0.0) "%.0f".format(ui.maxAltitude) else "—") +
                            " · 最低 " + (if (ui.minAltitude > 0.0) "%.0f".format(ui.minAltitude) else "—") + " m",
                        format = { "%.0f".format(it) }
                    )

                    Spacer(Modifier.height(22.dp))

                    TrackChart(
                        series = ui.series.cumulativeAscent,
                        title = "累计爬升 / 下降（米）",
                        unit = "m",
                        color = MaterialTheme.colorScheme.error,
                        xTotal = xKm,
                        second = ui.series.cumulativeDescent,
                        secondColor = MaterialTheme.colorScheme.primary,
                        secondLabel = "下降",
                        summary = "累计爬升 " + (if (ui.ascentMeters > 0.0) "%.0f".format(ui.ascentMeters) else "—") +
                            " · 累计下降 " + (if (ui.descentMeters > 0.0) "%.0f".format(ui.descentMeters) else "—") + " m",
                        format = { "%.0f".format(it) }
                    )

                    // ---- 分段明细（表格）----
                    if (ui.splits.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        SplitTable(ui.splits)
                    }
                    }
                }

                // ---- 底栏：只放社区动作（分享/导出在右上角三点里）----
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showCompare = true }) {
                        Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("与上次对比", fontSize = 13.sp)
                    }
                    TextButton(onClick = { showContribute = true }) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("贡献到线路库", fontSize = 13.sp)
                    }
                }
            }
            }

            // ===== 悬浮按钮（不再有标题栏：地图直接铺到状态栏，标题在卡片里）=====
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingCircleButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                // 分享 / 导出都收进这里
                Box {
                    FloatingCircleButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("分享行程卡") },
                            onClick = { menuOpen = false; shareCard() }
                        )
                        DropdownMenuItem(
                            text = { Text("导出 GPX") },
                            onClick = { menuOpen = false; exportGpx() }
                        )
                        DropdownMenuItem(
                            text = { Text("与上次对比") },
                            onClick = { menuOpen = false; showCompare = true }
                        )
                        DropdownMenuItem(
                            text = { Text("贡献到线路库") },
                            onClick = { menuOpen = false; showContribute = true }
                        )
                    }
                }
            }
        }
    }

    // ===== 与上次对比（共同路段）=====
    if (showCompare) {
        AlertDialog(
            onDismissRequest = { showCompare = false },
            title = { Text("与上次对比", fontSize = 17.sp) },
            text = {
                if (!ui.compare.checked) {
                    Text("正在比对历史记录…", fontSize = 13.sp)
                } else {
                    TrackCompareCard(compare = ui.compare, modifier = Modifier.padding(0.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompare = false }) { Text("知道了") }
            }
        )
    }

    // ===== 贡献到线路库 =====
    if (showContribute) {
        ContributeTrackDialog(
            defaultName = session?.name ?: (session?.let { formatDate(it.startTime) } ?: ""),
            submitting = submitting,
            error = contributeError,
            onDismiss = {
                showContribute = false
                contributeError = null
            },
            onSubmit = { name, region, intro, nickname ->
                if (ui.rawPointCount < 10) {
                    contributeError = "这条轨迹只有 " + ui.rawPointCount + " 个点，至少需要 10 个点"
                } else {
                    submitting = true
                    contributeError = null
                    vm.contribute(name, region, intro, nickname) { result ->
                        submitting = false
                        result.onSuccess {
                            showContribute = false
                            Toast.makeText(context, "已提交，等管理员确认后上线", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            contributeError = it.message ?: "提交失败，请稍后再试"
                        }
                    }
                }
            }
        )
    }
}

/** 浮在地图上的圆形按钮：半透明 surface 底 + 深色图标，卫星图上也看得清。 */
@Composable
private fun FloatingCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 3.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** 概览里的一行「图标 + 文字」。 */
@Composable
private fun MetaRow(icon: ImageVector, text: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 六宫格里的一格：大数字 + 小标签。 */
@Composable
private fun GridCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 难度档位。图标用 Material 自带的三种表情，避免 emoji 在部分 ROM 上缺字形。 */
private enum class Difficulty(val label: String, val icon: ImageVector) {
    EASY("简单", Icons.Filled.SentimentSatisfied),
    MEDIUM("中等", Icons.Filled.SentimentNeutral),
    HARD("困难", Icons.Filled.SentimentVeryDissatisfied)
}

/**
 * 由「里程 + 累计爬升」推出的难度。
 *
 * 注意这是**本地估算**，不是用户自填：六只脚那种「17km/1000m 爬升」标成「简单」是上传者手选的，
 * 我们这边没有人工难度字段，只能按经验分档。阈值刻意宽松，宁可标轻不标重。
 */
private fun difficultyOf(km: Double, ascentM: Double): Difficulty = when {
    km < 8.0 && ascentM < 300.0 -> Difficulty.EASY
    km < 20.0 && ascentM < 1200.0 -> Difficulty.MEDIUM
    else -> Difficulty.HARD
}

/** 小节标题：统一 15sp 加粗 + 上下留白，让四张图之间有明显层次。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    )
}

/**
 * 分段明细表。
 *
 * 之前是「第 1 km   11:46 · 1000 m · ↑43 m · 11'45」一行串起来，数字对不齐、扫视困难。
 * 改成四列表格：公里 / 用时 / 配速 / 爬升，数字右对齐、隔行浅底色，
 * 一眼能看出哪一段最慢（配速列最大那行）。
 */
@Composable
private fun SplitTable(splits: List<TrackSplits.Split>) {
    val shown = splits.take(30)
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("分段")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
        ) {
            // 表头
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // 四列等宽：之前 0.85/1/1/0.8 会让表头和数据行对不齐，扫视时很难受
                TableHeaderCell("公里", Modifier.weight(1f), TextAlign.Start)
                TableHeaderCell("用时", Modifier.weight(1f), TextAlign.End)
                TableHeaderCell("配速", Modifier.weight(1f), TextAlign.End)
                TableHeaderCell("爬升", Modifier.weight(1f), TextAlign.End)
            }

            shown.forEachIndexed { i, split ->
                if (i > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (i % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (split.partial) (split.index.toString() + " *") else split.index.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        TimeFmt.elapsed(split.durationMs),
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        split.paceSecPerKm?.let { formatPace(it) } ?: "—",
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (split.ascentM >= 1.0) "↑" + "%.0f".format(split.ascentM) else "—",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (splits.size > shown.size) {
                "仅显示前 " + shown.size + " 段，共 " + splits.size + " 段（* 为不足 1 km 的尾段）"
            } else {
                "共 " + splits.size + " 段（* 为不足 1 km 的尾段）"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier
    )
}

/** 米/秒 → km/h 的展示。为 0 或非有限值时给 "—"，不显示 Infinity。 */
private fun kmh(mps: Double): String {
    if (!mps.isFinite() || mps <= 0.0) return "—"
    return "%.1f".format(mps * 3.6)
}

/** 平均配速（分/公里）。速度为 0 时给 "—"。 */
private fun paceText(stats: Pace.Stats): String {
    val secPerKm = stats.averagePaceSecPerKm ?: return "—"
    return formatPace(secPerKm)
}

/** 坐标轴用的紧凑配速刻度：秒/公里 → 总分钟:秒（如 52:18、98:10）。 */
private fun paceTick(secPerKm: Double): String {
    if (!secPerKm.isFinite() || secPerKm <= 0.0) return "—"
    val total = secPerKm.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/** 秒/公里 → m'ss" / h mm'ss"。 */
private fun formatPace(secPerKm: Double): String {
    if (!secPerKm.isFinite() || secPerKm <= 0.0) return "—"
    val total = secPerKm.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d'%02d".format(h, m, sec)
    else "%d'%02d".format(m, sec)
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatFull(epochMs: Long): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(epochMs))

/**
 * 起止时间压成一行。
 *
 * 同一天只写一次日期（"2026.06.19 10:09 - 22:53"），跨天时结束时间省掉年份
 * （"2026.06.19 10:09 - 06.20 09:03"）—— 全写太宽，首行会被截成
 * "2026.06.19 10:09 - 2026."，信息全丢。
 */
private fun formatRange(startMs: Long, endMs: Long): String {
    val dayFmt = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val endDayFmt = SimpleDateFormat("MM.dd", Locale.getDefault())
    val sameDay = dayFmt.format(Date(startMs)) == dayFmt.format(Date(endMs))
    val end = if (sameDay) timeFmt.format(Date(endMs))
    else endDayFmt.format(Date(endMs)) + " " + timeFmt.format(Date(endMs))
    return dayFmt.format(Date(startMs)) + " " + timeFmt.format(Date(startMs)) + " - " + end
}
