package com.zouliangbu.footprint.ui.tracks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「贡献到线路库」对话框。
 *
 * 只收四个字段，全部可空或预填：贡献的是**轨迹本身**，不是游记。
 * 名称必填（服务端建线路要用），地区可空（服务端兜底"未知"）。
 */
@Composable
fun ContributeTrackDialog(
    defaultName: String,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, region: String, intro: String, nickname: String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var region by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("贡献到线路库", fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "把你的这条轨迹交给公共线路库，管理员确认后其他人也能走。\n" +
                            "会署你的昵称（可不填）。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("线路名称（必填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("地区（如 萍乡）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = intro,
                    onValueChange = { intro = it },
                    label = { Text("一句话说明（路况/补给）") },
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
                    Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), region.trim(), intro.trim(), nickname.trim()) },
                enabled = !submitting && name.isNotBlank()
            ) {
                Text(if (submitting) "提交中…" else "提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
        }
    )
}
