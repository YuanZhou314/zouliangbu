package com.zouliangbu.footprint.ui.routes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.data.DeviceId
import com.zouliangbu.footprint.data.remote.ProposalDto
import com.zouliangbu.footprint.data.repo.ProposalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 线路详情页的「社区维护」区。
 *
 * 三件事（对应 docs/product-philosophy.md 的信号层）：
 *  1. 显示当前版本与最后维护者 —— 让贡献被看见，这是开源项目里唯一的货币。
 *  2. 列出待处理的勘误提案，任何走过这条线的人可以「认可 / 反对」。
 *  3. 提供「提一条勘误」入口。
 *
 * 客观勘误只要认票达阈值（默认 2 人，含提案人）且 0 反对，服务端会自动合并成新版本，
 * 所以这里没有「审核通过」按钮 —— 那是管理员的事，App 端只负责发出和确认信号。
 */
@Composable
fun RouteMaintenanceSection(
    routeId: Long,
    version: Int,
    curatorName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ProposalRepository() }

    var proposals by remember { mutableStateOf<List<ProposalDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(routeId, reloadKey) {
        loading = true
        val result = withContext(Dispatchers.IO) { runCatching { repo.listForRoute(routeId) } }
        // 网络失败不吵人：这是附加信息区块，拉不到就静默展示为空
        proposals = result.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(4.dp).height(18.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text("社区维护", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "v" + version + (if (!curatorName.isNullOrBlank()) " · " + curatorName else ""),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        if (loading) {
            Text("正在读取社区提案…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (proposals.isEmpty()) {
            Text(
                "还没有待处理的勘误。走过这条线的人都可以提一条，两个人确认后自动更新。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            proposals.forEach { p ->
                ProposalRow(
                    proposal = p,
                    onVote = { value ->
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                runCatching { repo.vote(p.id, DeviceId.get(context), value) }
                            }
                            res.onSuccess { r ->
                                val msg = when {
                                    r.merged -> "已达成共识，这条线路更新到新版本"
                                    value > 0 -> "已认可（" + r.votesUp + "/" + p.threshold + "）"
                                    else -> "已投反对票，这条提案会等人来处理"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                reloadKey++
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "投票失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        TextButton(onClick = { showEditDialog = true }) {
            Text("提一条勘误", fontSize = 13.sp)
        }
    }

    if (showEditDialog) {
        EditProposalDialog(
            baseVersion = version,
            onDismiss = { showEditDialog = false },
            onSubmit = { field, value, reason, nickname, done ->
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        runCatching {
                            repo.submitEdit(
                                routeId = routeId,
                                fieldKey = field,
                                newValue = value,
                                reason = reason,
                                proposerUuid = DeviceId.get(context),
                                proposerName = nickname,
                                baseVersion = version
                            )
                        }
                    }
                    res.onSuccess {
                        done(null)
                        showEditDialog = false
                        Toast.makeText(context, "已提交。再有一位走过这条线的人认可就会自动更新", Toast.LENGTH_LONG).show()
                        reloadKey++
                    }.onFailure { done(it.message ?: "提交失败") }
                }
            }
        )
    }
}

@Composable
private fun ProposalRow(proposal: ProposalDto, onVote: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                proposal.proposerName.ifBlank { "匿名用户" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "已 " + proposal.votesUp + "/" + proposal.threshold + " 人认可" +
                        if (proposal.votesDown > 0) " · " + proposal.votesDown + " 人反对" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (proposal.reason.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(proposal.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onVote(-1) }) { Text("反对", fontSize = 12.sp) }
            TextButton(onClick = { onVote(1) }) { Text("认可", fontSize = 12.sp) }
        }
    }
}

/** 可提勘误的字段（与服务端 ProposalService 的白名单一致）。 */
private val EDITABLE_FIELDS = listOf(
    "difficulty" to "难度",
    "risk" to "风险",
    "suitable_months" to "适走月份",
    "road_type" to "路况类型",
    "view_type" to "景观类型",
    "traffic_html" to "交通信息",
    "intro_html" to "简介"
)

@Composable
private fun EditProposalDialog(
    baseVersion: Int,
    onDismiss: () -> Unit,
    onSubmit: (field: String, value: String, reason: String, nickname: String, done: (String?) -> Unit) -> Unit
) {
    var field by remember { mutableStateOf(EDITABLE_FIELDS.first()) }
    var value by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("提一条勘误", fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "基于当前版本 v" + baseVersion + "。提交后需要另一位走过这条线的人认可才会生效。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedTextField(
                        value = field.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("要修改的字段") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting
                    )
                    TextButton(
                        onClick = { expanded = true },
                        modifier = Modifier.matchParentSize()
                    ) {}
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        EDITABLE_FIELDS.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.second, fontSize = 13.sp) },
                                onClick = {
                                    field = f
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("正确的值") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("依据（现场看到的 / 什么时候走的）") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting,
                    maxLines = 3
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称（选填，用于署名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submitting = true
                    error = null
                    onSubmit(field.first, value.trim(), reason.trim(), nickname.trim()) { err ->
                        submitting = false
                        error = err
                    }
                },
                enabled = !submitting && value.isNotBlank()
            ) {
                Text(if (submitting) "提交中…" else "提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        }
    )
}