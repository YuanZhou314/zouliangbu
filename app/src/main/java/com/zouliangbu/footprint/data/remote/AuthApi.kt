package com.zouliangbu.footprint.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 账号 API + 本地会话存储（2026-10-21，用户名/邮箱+密码登录）。
 *
 * token 只存 SharedPreferences（明文即可接受：token 30 天过期，泄露风险与危害都有限；
 * 上 EncryptedSharedPreferences 反而引入 androidx.security 的维护负担）。
 * token 目前只保存不上送——服务端还没有受保护接口，带上它只是为后续 S7 做准备。
 */
object AuthSession {

    data class User(val userId: Long, val username: String, val token: String)

    private const val PREFS = "auth_session"
    private const val KEY_UID = "uid"
    private const val KEY_NAME = "username"
    private const val KEY_TOKEN = "token"

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    /** 进程启动时由 FootprintApp 调一次，从磁盘恢复会话。 */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        _user.value = User(prefs.getLong(KEY_UID, 0), prefs.getString(KEY_NAME, "") ?: "", token)
    }

    fun save(context: Context, user: User) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UID, user.userId)
            .putString(KEY_NAME, user.username)
            .putString(KEY_TOKEN, user.token)
            .apply()
        _user.value = user
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _user.value = null
    }
}

// ===== Retrofit 接口与 DTO（字段名与服务端 AuthController 一致）=====

interface AuthServiceApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body req: AuthReq): ApiResponse<AuthUser>

    @POST("api/v1/auth/login")
    suspend fun login(@Body req: AuthReq): ApiResponse<AuthUser>
}

@kotlinx.serialization.Serializable
data class AuthReq(
    val username: String,
    val password: String,
    val email: String? = null
)

@kotlinx.serialization.Serializable
data class AuthUser(
    val userId: Long,
    val username: String,
    val token: String
)

/**
 * 注册/登录的薄封装：网络放 IO，业务失败抛 [AuthException]（msg 是可直接展示的人话），
 * 网络失败照常上抛，由 ViewModel 统一兜底文案。
 */
class AuthException(val code: Int, message: String) : Exception(message)

class AuthClient {

    private val api: AuthServiceApi by lazy {
        ContentApiFactory.retrofit.create(AuthServiceApi::class.java)
    }

    suspend fun register(username: String, password: String, email: String?): AuthSession.User =
        withContext(Dispatchers.IO) {
            val resp = runCatching { api.register(AuthReq(username, password, email)) }
                .getOrElse { throw AuthException(-1, "网络不可用，请检查连接后重试") }
            if (!resp.ok || resp.data == null) throw AuthException(resp.code, resp.msg)
            AuthSession.User(resp.data.userId, resp.data.username, resp.data.token)
        }

    suspend fun login(username: String, password: String): AuthSession.User =
        withContext(Dispatchers.IO) {
            val resp = runCatching { api.login(AuthReq(username, password)) }
                .getOrElse { throw AuthException(-1, "网络不可用，请检查连接后重试") }
            if (!resp.ok || resp.data == null) throw AuthException(resp.code, resp.msg)
            AuthSession.User(resp.data.userId, resp.data.username, resp.data.token)
        }
}
