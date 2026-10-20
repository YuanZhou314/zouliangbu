package com.zouliangbu.footprint.data.remote

import com.zouliangbu.footprint.data.model.FeedItem
import com.zouliangbu.footprint.data.model.GearCategory
import com.zouliangbu.footprint.data.model.RouteCatalog
import com.zouliangbu.footprint.data.repo.FeedRepository
import com.zouliangbu.footprint.data.repo.GearRepository
import com.zouliangbu.footprint.data.repo.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
// kotlinx.coroutines.withContext 未使用
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * Retrofit + kotlinx-serialization 的 HTTP 客户端（ROADMAP P6 S4）。
 *
 * 与服务端 ContentController 的三个接口一一对应：
 *   - GET /api/v1/feed
 *   - GET /api/v1/routes
 *   - GET /api/v1/gear/categories
 *
 * 设计要点：
 *   1. **kotlinx-serialization 而不是 Gson**——Kotlin 官方、编译期校验。
 *   2. **错误处理统一走 ApiResponse<T>{code,msg,data}**——业务失败与 HTTP 失败分开。
 *   3. **网络失败时上抛异常**，由调用方（RemoteXxxRepository）捕获后回退 Mock。
 */
object ContentApiFactory {

    /** 本地开发默认 10.0.2.2（模拟器访问宿主机）。真机调试要改成实际 IP。 */
    const val BASE_URL = "http://10.0.2.2:8080"

    /** 单例 Retrofit。 */
    val service: ContentService by lazy { build() }

    private fun build(): ContentService {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val converterFactory = json.asConverterFactory("application/json".toMediaType())
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(converterFactory)
            .build()
            .create(ContentService::class.java)
    }
}

/** Retrofit service 接口。 */
interface ContentService {
    @GET("api/v1/feed")
    suspend fun feed(): ApiResponse<FeedPage>

    @GET("api/v1/routes")
    suspend fun routes(): ApiResponse<RouteCatalog>

    @GET("api/v1/gear/categories")
    suspend fun gearCategories(): ApiResponse<List<GearCategory>>

    @retrofit2.http.POST("api/v1/routes/submit")
    suspend fun submitRoute(@retrofit2.http.Body req: SubmitRouteRequest): ApiResponse<Long>

    @GET("api/v1/routes/submissions/{id}")
    suspend fun getSubmission(@retrofit2.http.Path("id") id: Long): ApiResponse<SubmissionStatus>

    @GET("api/v1/routes/{id}")
    suspend fun getRoute(@retrofit2.http.Path("id") id: Long): ApiResponse<RouteDetail>

    @GET("api/v1/routes/{id}/track")
    suspend fun getTrackPoints(@retrofit2.http.Path("id") id: Long): ApiResponse<List<TrackPointDto>>

    @GET("api/v1/routes/{id}/markers")
    suspend fun getMarkers(@retrofit2.http.Path("id") id: Long): ApiResponse<List<MarkerDto>>
}

/**
 * 服务端 /feed 的分页响应体：items + nextCursor（cursor 分页）。
 * 字段名与服务端 ContentDtos.FeedPage 一致。
 */
@kotlinx.serialization.Serializable
data class FeedPage(
    val items: List<FeedItem> = emptyList(),
    val nextCursor: String? = null
)

/**
 * 服务端统一响应体 `{code, msg, data}`。
 *
 * code = 0 表示成功，其它为 ErrorCode。
 */
@kotlinx.serialization.Serializable
data class ApiResponse<T>(
    val code: Int,
    val msg: String = "",
    val data: T? = null
) {
    val ok: Boolean get() = code == 0
}

/** 提交路线链接请求体。 */
@kotlinx.serialization.Serializable
data class SubmitRouteRequest(
    val sourceUrl: String,
    val submitterUuid: String,
    val submitterNote: String? = null
)

/** 提交状态响应体。 */
@kotlinx.serialization.Serializable
data class SubmissionStatus(
    val id: Long,
    val status: String,
    val rejectReason: String? = null,
    val routeId: Long? = null
)

/** 路线详情（含正文）。 */
@kotlinx.serialization.Serializable
data class RouteDetail(
    val id: Long,
    val name: String? = null,
    val region: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val sourceAuthor: String? = null,
    val introHtml: String? = null,
    val contentHtml: String? = null,
    val trafficHtml: String? = null,
    val difficulty: String? = null,
    val risk: String? = null,
    val suitableMonths: String? = null,
    val roadType: String? = null,
    val viewType: String? = null,
    val distanceKm: Double = 0.0,
    val ascentMeters: Int = 0,
    val durationName: String? = null,
    val trackPointCount: Int = 0,
    val markerCount: Int = 0
)

/** 轨迹点 DTO。 */
@kotlinx.serialization.Serializable
data class TrackPointDto(
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val altitude: Double? = null,
    val speed: Double? = null,
    val time: Long? = null
)

/** 标注点 DTO。 */
@kotlinx.serialization.Serializable
data class MarkerDto(
    val seq: Int,
    val name: String? = null,
    val lat: Double,
    val lng: Double,
    val altitude: Double? = null,
    val time: Long? = null
)

/** RemoteFeedRepository：网络失败回退 Mock，UI 无感。 */
class RemoteFeedRepository(
    private val fallback: FeedRepository
) : FeedRepository {
    override fun observeFeed(): Flow<List<FeedItem>> = flow {
        val page = runCatching { ContentApiFactory.service.feed().data }
            .getOrNull()
        val items = page?.items ?: emptyList()
        if (items.isNotEmpty()) {
            emit(items)
        } else {
            fallback.observeFeed().collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)
}

/** RemoteRouteRepository：同上。 */
class RemoteRouteRepository(
    private val fallback: RouteRepository
) : RouteRepository {
    override fun observeCatalog(): Flow<RouteCatalog> = flow {
        val catalog = runCatching { ContentApiFactory.service.routes().data }
            .getOrNull()
        // 服务端直接返回 RouteCatalog{recommendations, sections}，字段与 App model 一致。
        if (catalog != null && catalog.recommendations.isNotEmpty()) {
            emit(catalog)
        } else {
            fallback.observeCatalog().collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)
}

/** RemoteGearRepository：同上。 */
class RemoteGearRepository(
    private val fallback: GearRepository
) : GearRepository {
    override fun categories(): List<GearCategory> = runCatching {
        kotlinx.coroutines.runBlocking { ContentApiFactory.service.gearCategories().data ?: emptyList() }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: fallback.categories()
}
