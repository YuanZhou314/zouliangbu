package com.zouliangbu.footprint.ui.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.data.db.GearItem

/**
 * 装备新增 / 编辑表单（gearId = 0 表示新增）。
 * 字段：名称（必填）/ 品牌型号 / 分类 / 状态 / 重量 g / 价格（元）/ 购买日期 / 备注。
 * 价格以分入库（避免浮点），UI 用元输入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearEditScreen(
    gearId: Long,
    onBack: () -> Unit,
    vm: GearViewModel = viewModel()
) {
    val isEdit = gearId > 0L

    var name by remember { mutableStateOf("") }
    var brandModel by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(vm.categories.firstOrNull()?.id ?: "g1") }
    var transferred by remember { mutableStateOf(false) }
    var weightText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(!isEdit) }
    var showNameError by remember { mutableStateOf(false) }
    // 编辑时保留库里的 createdAt：@Upsert 是整行覆盖，丢了它排序就乱了
    var createdAt by remember { mutableStateOf(0L) }

    LaunchedEffect(gearId) {
        if (isEdit) vm.getItem(gearId)?.let { g ->
            name = g.name
            brandModel = g.brandModel
            categoryId = g.categoryId
            transferred = g.status == GearItem.STATUS_TRANSFERRED
            if (g.weightGrams > 0) weightText = g.weightGrams.toString()
            g.priceCents?.let { priceText = (it / 100.0).toString().trimEnd('0').trimEnd('.') }
            g.purchaseDate?.let { purchaseDate = it }
            g.note?.let { note = it }
            createdAt = g.createdAt
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) "编辑装备" else "添加装备",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = {
                            vm.delete(gearId)
                            onBack()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; showNameError = false },
                label = { Text("名称 *") },
                isError = showNameError,
                supportingText = if (showNameError) {
                    { Text("请填写名称") }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = brandModel,
                onValueChange = { brandModel = it },
                label = { Text("品牌型号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("分类", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.categories.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            FilterChip(
                                selected = categoryId == c.id,
                                onClick = { categoryId = c.id },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }
            }

            Text("状态", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !transferred,
                    onClick = { transferred = false },
                    label = { Text("可用") }
                )
                FilterChip(
                    selected = transferred,
                    onClick = { transferred = true },
                    label = { Text("已转让") }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("重量（克）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) },
                    label = { Text("价格（元）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = purchaseDate,
                onValueChange = { purchaseDate = it },
                label = { Text("购买日期（如 2025-03-01，可不填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (name.isBlank()) {
                        showNameError = true
                        return@Button
                    }
                    vm.save(
                        GearItem(
                            id = gearId,
                            name = name.trim(),
                            brandModel = brandModel.trim(),
                            categoryId = categoryId,
                            status = if (transferred) GearItem.STATUS_TRANSFERRED
                            else GearItem.STATUS_AVAILABLE,
                            weightGrams = weightText.toIntOrNull() ?: 0,
                            priceCents = priceText.toDoubleOrNull()
                                ?.let { Math.round(it * 100) },
                            purchaseDate = purchaseDate.trim().takeIf { it.isNotEmpty() },
                            note = note.trim().takeIf { it.isNotEmpty() },
                            createdAt = createdAt
                        )
                    )
                    onBack()
                },
                enabled = loaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEdit) "保存修改" else "添加")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
