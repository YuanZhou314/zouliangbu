package com.zouliangbu.footprint.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.service.RecordingTrackService
import com.zouliangbu.footprint.ui.map.MapViewWrapper
import com.zouliangbu.footprint.ui.theme.OnDarkMuted
import com.zouliangbu.footprint.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 出发页（全屏记录页，无底栏）。
 *
 * 由原 MainScreen 整体迁移而来，能力不变：全屏地图 + 权限两步申请 + 记录浮层
 * + 开始/停止 + 停止后摘要卡。新增：左上角返回键（onBack 只退导航栈，
 * 记录 Service / 通知不受影响；RecordingTrackState 是进程内单例，
 * 重新进入本页浮层数据自动恢复）。
 *
 * 权限分两步：拆成两个 launcher，因为一个 launcher 不能在自己的 callback 里再 launch 自身
 * （Kotlin 的 val 必须在初始化后才能引用）。Android 10+ 强制后台定位必须单独请求。
 */
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: RecordViewModel = viewModel()
) {
    val context = LocalContext.current
    val snapshot by vm.recording.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    // 实时轨迹：Service 每落一个点推一次，ViewModel 追加后这里跟着重组，地图折线就"长"出来。
    val track by vm.track.collectAsStateWithLifecycle()
    // 上次被系统杀掉留下的悬挂会话：App 启动时已自动收尾保存，这里只提示一次。
    val recovered by vm.orphaned.collectAsStateWithLifecycle()
    val isRecording = snapshot != null
    val isPaused = snapshot?.paused == true

    // 摘要对话框：service stop 后展示
    var summaryFor by remember { mutableStateOf<Long?>(null) }

    fun proceedToStart() {
        vm.startSession { sid ->
            context.startForegroundService(
                RecordingTrackService.startIntent(context, sid)
            )
        }
    }

    // 第二步：后台定位（Android 10+）。不论结果都允许开始前台记录——后台权限仅影响息屏后能否继续采集。
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        proceedToStart()
    }

    // 第一步：前台定位 + 通知（Android 13+）。拿到 FINE 后再决定是否触发第二步。
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!fineGranted) {
            return@rememberLauncherForActivityResult
        }
        val bgPerms = backgroundPermissions()
        val bgAlreadyGranted = bgPerms.isEmpty() ||
                ContextCompat.checkSelfPermission(context, bgPerms[0]) ==
                PackageManager.PERMISSION_GRANTED
        if (bgAlreadyGranted) {
            proceedToStart()
        } else {
            backgroundPermissionLauncher.launch(bgPerms)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 记录中实时画线（P1.2）：不画起终点、不自动缩放——相机要跟着当前位置走。
        MapViewWrapper(
            modifier = Modifier.fillMaxSize(),
            track = track
        )

        // 左上角返回键（地图画到状态栏后面，需要 statusBarsPadding）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        // 记录中浮层：顶部居中
        if (snapshot != null) {
            val s = snapshot!!
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp, start = 64.dp, end = 64.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC263238))
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("距离", color = OnDarkMuted, fontSize = 12.sp)
                    Text(
                        TimeFmt.distance(s.distanceMeters),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "时长 " + TimeFmt.elapsed(s.elapsedMs),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        // 暂停时给一个明显标识：否则用户看到时长不走会以为是卡死。
                        if (s.paused) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "已暂停",
                                color = Color(0xFF1B1B1B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFFC107))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 底部控制栏：唯一一个大的开始/停止按钮
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRecording) {
                    Button(
                        onClick = {
                            // 第一步：检查前台定位权限；若已授权直接进入第二步
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted) {
                                // 第一步已有权，跳到第二步
                                val bgPerms = backgroundPermissions()
                                val bgGranted = bgPerms.isEmpty() ||
                                        ContextCompat.checkSelfPermission(context, bgPerms[0]) ==
                                        PackageManager.PERMISSION_GRANTED
                                if (bgGranted) {
                                    proceedToStart()
                                } else {
                                    backgroundPermissionLauncher.launch(bgPerms)
                                }
                            } else {
                                foregroundPermissionLauncher.launch(foregroundPermissions())
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("开始记录", fontSize = 18.sp)
                    }
                } else {
                    // 暂停 / 继续（P2.4）：暂停期间不采点、不计距离、不计时长，
                    // 恢复后同一条会话继续写，轨迹不会被拆成两条。
                    Button(
                        onClick = {
                            val intent = if (isPaused) {
                                RecordingTrackService.resumeIntent(context)
                            } else {
                                RecordingTrackService.pauseIntent(context)
                            }
                            // 服务已在前台运行，startService 即可（不需要 startForegroundService）。
                            context.startService(intent)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF37474F),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPaused) "继续" else "暂停", fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            val sid = snapshot?.sessionId
                            val tooShort = vm.isTooShort(snapshot)
                            context.startService(RecordingTrackService.stopIntent(context))
                            if (tooShort && sid != null) {
                                // 用户要求：太短的记录直接不计入，只提醒、不给选择
                                summaryFor = null
                                vm.discardShortRecord(sid)
                            } else {
                                summaryFor = sid
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("停止记录", fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // 上次记录被系统杀掉：已自动收尾保存，这里只提示一次（P2.2）。
    // 选"继续记录"是不可行的——进程死后 GPS 采集已断，硬接上会在轨迹里留一个跨公里的空洞。
    recovered.firstOrNull()?.let { item ->
        AlertDialog(
            onDismissRequest = { vm.dismissRecovery() },
            title = { Text("上次记录异常中断") },
            text = {
                Column {
                    Text(
                        "检测到一条未正常结束的记录（${TimeFmt.distance(item.distanceMeters)} · " +
                                "${item.pointCount} 个点），已自动保存到「我的记录」。"
                    )
                    if (recovered.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "共 ${recovered.size} 条，其余同样已保存。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.dismissRecovery()
                        onOpenDetail(item.sessionId)
                    }
                ) { Text("查看轨迹") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissRecovery() }) { Text("知道了") }
            }
        )
    }

    // 短记录提示：太短的记录已自动丢弃，不给用户选择（用户明确要求）
    val shortDiscarded by vm.shortDiscarded.collectAsStateWithLifecycle()
    if (shortDiscarded) {
        AlertDialog(
            onDismissRequest = { vm.consumeShortDiscarded() },
            title = { Text("记录太短") },
            text = { Text("本次记录时长或距离太短，未保存。下次出去徒步再试试吧！") },
            confirmButton = {
                TextButton(onClick = { vm.consumeShortDiscarded() }) { Text("知道了") }
            }
        )
    }

    // 摘要对话框：service 结束后从 sessions 里拿最终数据
    summaryFor?.let { sid ->        val session: TrackSession? = remember(sid, sessions) {
            sessions.firstOrNull { it.id == sid }
        }
        if (session != null) {
            AlertDialog(
                onDismissRequest = { summaryFor = null },
                title = { Text("本次记录") },
                text = {
                    Column {
                        Text("距离：${TimeFmt.distance(session.totalDistanceMeters)}")
                        // 用 activeDurationMs（已扣暂停）：暂停期间不该算进运动时长。
                        // endTime 还没落库时（刚点停止、Room 还没推新值）按当前时间估算，
                        // 否则摘要卡会先闪一下 00:00 再跳正。
                        val durationMs = if (session.endTime != null) {
                            session.activeDurationMs
                        } else {
                            (System.currentTimeMillis() - session.startTime - session.pausedMs)
                                .coerceAtLeast(0L)
                        }
                        Text(
                            "时长：${TimeFmt.elapsed(durationMs)}" +
                                    if (session.pausedMs > 0) {
                                        "（暂停 ${TimeFmt.elapsed(session.pausedMs)}）"
                                    } else ""
                        )
                        Text(
                            "开始：${
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    .format(Date(session.startTime))
                            }"
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已保存到本地数据库，可导出 GPX 分享或导入其他轨迹软件",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Row {
                        // 停止后直接进详情回看（P1.8）：比"关闭摘要再去我的里翻"少两步。
                        TextButton(
                            onClick = {
                                summaryFor = null
                                onOpenDetail(sid)
                            }
                        ) { Text("查看轨迹") }
                        // 导出 GPX：写 cacheDir → FileProvider 授权 → 系统分享面板。
                        // 坐标在 GpxWriter 里转成 WGS-84，外部软件打开才不会偏几百米。
                        TextButton(
                            onClick = {
                                vm.exportGpx(session) { intent ->
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "这条记录还没有轨迹点，无法导出",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        ) { Text("导出 GPX") }
                    }
                },
                // 「关闭」放 dismissButton：三个按钮挤在 Confirm 行里，在窄屏上会被裁掉。
                dismissButton = {
                    TextButton(onClick = { summaryFor = null }) { Text("关闭") }
                }
            )
        }
    }
}

/**
 * 权限分两步请求：
 *
 * - **前台权限**：ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION + （Android 13+）POST_NOTIFICATIONS。
 * - **后台权限**（仅 Android 10+）：ACCESS_BACKGROUND_LOCATION。
 *
 * Android 10+ 强制要求后台定位必须**单独**请求（不能跟其他 dangerous 权限同组），否则系统静默忽略；
 * Android 13+ 的 POST_NOTIFICATIONS 同理。两步走确保每个对话框都能弹出来。
 */
private fun foregroundPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun backgroundPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }
