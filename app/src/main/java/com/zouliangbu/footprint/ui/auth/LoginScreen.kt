package com.zouliangbu.footprint.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zouliangbu.footprint.ui.theme.ForestGreen

/**
 * 登录 / 注册页（外层全屏）。注册暂不支持邮箱验证、忘记密码（服务端没做邮箱服务），
 * 用户名一旦注册不可改——提示里写清楚。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    vm: LoginViewModel = viewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ui.mode == LoginViewModel.Mode.LOGIN) "登录" else "注册") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Filled.Hiking,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(56.dp)
            )
            Text("走两步", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = ui.mode == LoginViewModel.Mode.LOGIN,
                    onClick = { vm.switchMode(LoginViewModel.Mode.LOGIN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("登录") }
                SegmentedButton(
                    selected = ui.mode == LoginViewModel.Mode.REGISTER,
                    onClick = { vm.switchMode(LoginViewModel.Mode.REGISTER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("注册") }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = ui.username,
                onValueChange = vm::onUsername,
                label = { Text("用户名") },
                supportingText = { Text("3-30 位字母、数字或下划线，注册后不可修改") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::onPassword,
                label = { Text("密码") },
                supportingText = { Text("至少 6 位") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (ui.mode == LoginViewModel.Mode.REGISTER) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ui.email,
                    onValueChange = vm::onEmail,
                    label = { Text("邮箱（选填）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ui.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.submit(onSuccess = onLoggedIn) },
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (ui.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (ui.mode == LoginViewModel.Mode.LOGIN) "登录" else "创建账号")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "注册即表示同意：数据经服务器传输与存储。\n轨迹数据始终保存在本机，不上云。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
