package com.zouliangbu.footprint.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.data.remote.AuthClient
import com.zouliangbu.footprint.data.remote.AuthException
import com.zouliangbu.footprint.data.remote.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录/注册页 ViewModel：一个页面两种模式（登录 ↔ 注册），共用表单状态。
 * 成功后写 AuthSession（内存 + 磁盘），我的页通过 AuthSession.user 观察到。
 */
class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val client = AuthClient()

    val user: StateFlow<AuthSession.User?> = AuthSession.user

    data class UiState(
        val mode: Mode = Mode.LOGIN,
        val username: String = "",
        val password: String = "",
        val email: String = "",
        val loading: Boolean = false,
        val error: String? = null
    )

    enum class Mode { LOGIN, REGISTER }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun onUsername(v: String) { _ui.value = _ui.value.copy(username = v, error = null) }
    fun onPassword(v: String) { _ui.value = _ui.value.copy(password = v, error = null) }
    fun onEmail(v: String) { _ui.value = _ui.value.copy(email = v, error = null) }
    fun switchMode(mode: Mode) { _ui.value = _ui.value.copy(mode = mode, error = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _ui.value
        if (s.loading) return
        // 提交前先本地校验，省一次注定失败的网络往返
        val localError = when {
            s.username.length < 3 -> "用户名至少 3 个字符（字母/数字/下划线）"
            !s.username.matches(Regex("[A-Za-z0-9_]+")) -> "用户名只能包含字母、数字、下划线"
            s.password.length < 6 -> "密码至少 6 位"
            s.mode == Mode.REGISTER && s.email.isNotBlank() &&
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email).matches() -> "邮箱格式不正确"
            else -> null
        }
        if (localError != null) {
            _ui.value = s.copy(error = localError)
            return
        }
        _ui.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val u = if (s.mode == Mode.LOGIN) {
                    client.login(s.username.trim(), s.password)
                } else {
                    client.register(s.username.trim(), s.password, s.email.trim().ifBlank { null })
                }
                AuthSession.save(getApplication(), u)
                onSuccess()
            } catch (e: AuthException) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = "请求失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun logout() = AuthSession.clear(getApplication())
}
