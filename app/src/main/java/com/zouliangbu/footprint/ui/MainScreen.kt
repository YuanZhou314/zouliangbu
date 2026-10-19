package com.zouliangbu.footprint.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.zouliangbu.footprint.ui.map.AmapMapView
import com.zouliangbu.footprint.ui.theme.OnDarkMuted
import com.zouliangbu.footprint.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val snapshot by vm.recording.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val isRecording = snapshot != null

    // 摘要对话框：service stop 后展示
    var summaryFor by remember { mutableStateOf<Long?>(null) }
    var pendingStart by remember { mutableStateOf(false) }

    // 权限 launcher：先请求权限，拿到后再创建会话 + 启动 service
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted && pendingStart) {
            pendingStart = false
            vm.startSession { sid ->
                context.startForegroundService(
                    RecordingTrackService.startIntent(context, sid)
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmapMapView(Modifier.fillMaxSize())

        // 记录中浮层：顶部居中
        if (snapshot != null) {
            val s = snapshot!!
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC263238))
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("距离", color = OnDarkMuted, fontSize = 12.sp)
                    Text(
                        TimeFmt.distance(s.distanceMeters),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("时长", color = OnDarkMuted, fontSize = 12.sp)
                    Text(
                        TimeFmt.elapsed(s.elapsedMs),
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }
        }

        // 底部控制栏：唯一一个大的开始/停止按钮
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRecording) {
                    Button(
                        onClick = {
                            // 检查权限；若已授权则直接开始，否则让 launcher 处理
                            val alreadyGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (alreadyGranted) {
                                vm.startSession { sid ->
                                    context.startForegroundService(
                                        RecordingTrackService.startIntent(context, sid)
                                    )
                                }
                            } else {
                                pendingStart = true
                                permissionLauncher.launch(requiredPermissions())
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
                    Button(
                        onClick = {
                            val sid = snapshot?.sessionId
                            context.startService(RecordingTrackService.stopIntent(context))
                            summaryFor = sid
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("停止记录", fontSize = 18.sp)
                    }
                }
            }
        }
    }

    // 摘要对话框：service 结束后从 sessions 里拿最终数据
    summaryFor?.let { sid ->
        val session: TrackSession? = remember(sid, sessions) {
            sessions.firstOrNull { it.id == sid }
        }
        if (session != null) {
            AlertDialog(
                onDismissRequest = { summaryFor = null },
                title = { Text("本次记录") },
                text = {
                    Column {
                        Text("距离：${TimeFmt.distance(session.totalDistanceMeters)}")
                        val durationMs = (session.endTime ?: System.currentTimeMillis()) - session.startTime
                        Text("时长：${TimeFmt.elapsed(durationMs)}")
                        Text(
                                "开始：${
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        .format(Date(session.startTime))
                                }"
                            )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "（点 ${sid} 已保存到本地数据库）",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { summaryFor = null }) { Text("关闭") }
                }
            )
        }
    }

    // 兜底：如果服务进程被杀后重启，recording 状态可能 stale；这里不做额外恢复处理。
    LaunchedEffect(Unit) { /* placeholder */ }
}

/**
 * 按平台 API 选择要请求的权限集合。
 * - ACCESS_FINE_LOCATION：所有版本都需要。
 * - ACCESS_BACKGROUND_LOCATION：Android 10 (API 29) 强制要求单独请求。
 * - POST_NOTIFICATIONS：Android 13 (API 33) 起通知运行时权限。
 */
private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()