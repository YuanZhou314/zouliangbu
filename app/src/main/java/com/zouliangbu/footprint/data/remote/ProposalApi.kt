package com.zouliangbu.footprint.data.remote

import kotlinx.serialization.Serializable

/**
 * 社区共维护提案接口（服务端 ProposalController / AdminProposalController）。
 *
 * 契约与服务端 ProposalController.ProposalRow 逐字段一致（只增不改）。
 */
@Serializable
data class ProposalDto(
    val id: Long,
    val type: String = "",
    val category: String = "",
    val targetRouteId: Long? = null,
    val sourceUrl: String = "",
    val reason: String = "",
    val proposerName: String = "",
    val status: String = "",
    val votesUp: Int = 0,
    val votesDown: Int = 0,
    val threshold: Int = 2,
    val routeId: Long? = null,
    val mergedVersion: Int? = null,
    val rejectReason: String = "",
    val baseVersion: Int? = null,
    val createdAt: String = ""
)

/** 提交提案（三型共用）。 */
@Serializable
data class SubmitProposalRequest(
    val type: String,
    val category: String = "OBJECTIVE",
    val targetRouteId: Long? = null,
    val sourceUrl: String? = null,
    val payloadJson: String? = null,
    val baseVersion: Int? = null,
    val reason: String? = null,
    val proposerUuid: String,
    val proposerName: String? = null
)

@Serializable
data class VoteRequest(val voterKey: String, val value: Int)

@Serializable
data class VoteResultDto(
    val proposalId: Long = 0,
    val status: String = "",
    val votesUp: Int = 0,
    val votesDown: Int = 0,
    val merged: Boolean = false,
    val mergedVersion: Int? = null
)

/** 提案接口（复用 ContentApiFactory 的 Retrofit 实例，baseUrl 一致）。 */
interface ProposalService {
    @retrofit2.http.POST("api/v1/proposals")
    suspend fun submit(@retrofit2.http.Body req: SubmitProposalRequest): ApiResponse<ProposalDto>

    @retrofit2.http.GET("api/v1/proposals")
    suspend fun list(
        @retrofit2.http.Query("status") status: String? = null,
        @retrofit2.http.Query("routeId") routeId: Long? = null,
        @retrofit2.http.Query("limit") limit: Int = 50
    ): ApiResponse<List<ProposalDto>>

    @retrofit2.http.POST("api/v1/proposals/{id}/vote")
    suspend fun vote(
        @retrofit2.http.Path("id") id: Long,
        @retrofit2.http.Body req: VoteRequest
    ): ApiResponse<VoteResultDto>
}

/** 提案接口单例（懒加载，避免在没有网络配置时提前建 Retrofit）。 */
object ProposalApi {
    val service: ProposalService by lazy {
        ContentApiFactory.retrofit.create(ProposalService::class.java)
    }
}
