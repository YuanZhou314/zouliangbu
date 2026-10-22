package com.zouliangbu.footprint.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.ui.theme.ForestGreen
import com.zouliangbu.footprint.ui.theme.TrailOrange
import com.zouliangbu.footprint.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 我的页（简单版）：头像/昵称/社交数字占位 + 「我的记录」本地真实统计 + 户外足迹入口。
 * 「我的记录」卡片与「户外足迹 → 轨迹」都通向历史列表（[onOpenTracks]）。
 */
@Composable
fun ProfileScreen(
    onOpenTracks: () -> Unit,
    onOpenOffline: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onOpenWeather: () -> Unit = {},
    vm: ProfileViewModel = viewModel()
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    // 登录态来自 AuthSession（进程启动时从 SharedPreferences 恢复），UI 只读不写。
    val user by com.zouliangbu.footprint.data.remote.AuthSession.user.collectAsStateWithLifecycle()
    var showLogout by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ===== 用户信息头：未登录点进登录页；已登录显示用户名 + 退出 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp)
                .clickable(enabled = user == null, onClick = onOpenLogin),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "头像",
                    tint = ForestGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user?.username ?: "徒步者",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (user != null) "已登录" else "点击登录 / 注册",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (user != null) {
                Text(
                    "退出",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = { showLogout = true })
                )
            }
        }

        // ===== 社交数字占位 =====
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell("0", "互相关注")
                StatCell("0", "队伍")
                StatCell("0", "关注")
                StatCell("0", "粉丝")
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== 我的记录（本地 Room 真实数据） =====
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.clickable(onClick = onOpenTracks)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Hiking,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("我的记录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "全部 >",
                        fontSize = 12.sp,
                        color = ForestGreen
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCell("${stats.count}", "记录次数")
                    StatCell(TimeFmt.distance(stats.totalMeters.toFloat()), "累计距离")
                    StatCell(
                        stats.lastStartTime?.let {
                            SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(it))
                        } ?: "—",
                        "最近记录"
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== 外观（task-003：自动/手动浅深色切换） =====
        val themeMode by com.zouliangbu.footprint.ui.theme.ThemePrefs.mode
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Interests,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("外观", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    com.zouliangbu.footprint.ui.theme.ThemeMode.entries.forEach { m ->
                        val selected = themeMode == m
                        Text(
                            m.label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { com.zouliangbu.footprint.ui.theme.ThemePrefs.set(context, m) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===== 户外足迹（入口占位） =====
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("户外足迹", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EntryCell(Icons.Filled.Map, "轨迹", null, onClick = onOpenTracks)
                    EntryCell(Icons.Filled.Download, "离线地图", null, onClick = onOpenOffline)
                    EntryCell(Icons.Filled.Interests, "兴趣点", "开发中")
                    EntryCell(Icons.Filled.Map, "景观天气", null, onClick = onOpenWeather)
                    EntryCell(Icons.Filled.CloudOff, "云端照片", "开发中")
                    EntryCell(Icons.Filled.Person, "帖子", "开发中")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        Text(
            "排行榜 / 福利等更多功能开发中",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    if (showLogout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录？") },
            text = { Text("退出后本地记录仍在，但换设备需要重新登录才能同步身份。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.zouliangbu.footprint.data.remote.AuthSession.clear(context)
                    showLogout = false
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLogout = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryCell(
    icon: ImageVector,
    label: String,
    note: String? = "开发中",
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick)
            else Modifier.clickable(enabled = false) { }
        )
    ) {
        Icon(icon, contentDescription = label, tint = TrailOrange, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (note != null) {
            Text(note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
