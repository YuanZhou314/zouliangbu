package com.zouliangbu.footprint.data.repo

import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.data.remote.ProposalApi
import com.zouliangbu.footprint.data.remote.ProposalDto
import com.zouliangbu.footprint.data.remote.SubmitProposalRequest
import com.zouliangbu.footprint.data.remote.VoteRequest
import com.zouliangbu.footprint.data.remote.VoteResultDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 社区共维护：提案的提交与查询（服务端 /api/v1/proposals）。
 *
 * 只做「转发 + 组包」，不做本地缓存：提案是**服务端的状态**，缓存一份只会让人以为
 * 自己投过的票丢了。网络失败就把异常交给 UI 提示"提交失败，稍后再试"。
 *
 * 三种提案（对应 docs/product-philosophy.md 的四类提交）：
 *  - [submitEdit]   勘误：改已有线路的一个字段，发出去后由走过这条线的人认票。
 *  - [submitUpload] 贡献：把自己记录的整条轨迹交上去，管理员确认后进线路库。
 *  - [submitImport] 导入：只贴一个外部链接，服务端抓取（合规审核在管理端）。
 */
class ProposalRepository {

    private val api get() = ProposalApi.service

    /** 某条线路的待处理提案（默认只拉 PENDING，已合并/驳回的不占视线）。 */
    suspend fun listForRoute(routeId: Long, status: String? = "PENDING"): List<ProposalDto> {
        val resp = api.list(status = status, routeId = routeId, limit = 50)
        if (!resp.ok) throw IllegalStateException(resp.msg.ifBlank { "拉取提案失败" })
        return resp.data.orEmpty()
    }

    /**
     * 提一条勘误。
     *
     * @param fieldKey 服务端白名单里的字段名：difficulty / risk / suitable_months /
     *                 road_type / view_type / traffic_html / intro_html，或 route.name / route.region
     * @param baseVersion 提交时看到的线路版本，服务端靠它检测冲突（别人先改了 → CONFLICT）
     */
    suspend fun submitEdit(
        routeId: Long,
        fieldKey: String,
        newValue: String,
        reason: String,
        proposerUuid: String,
        proposerName: String?,
        baseVersion: Int?
    ): ProposalDto {
        val payload = buildJsonObject {
            put("fields", buildJsonObject { put(fieldKey, newValue) })
        }.toString()
        return submit(
            SubmitProposalRequest(
                type = "EDIT",
                category = "OBJECTIVE",
                targetRouteId = routeId,
                payloadJson = payload,
                baseVersion = baseVersion,
                reason = reason.takeIf { it.isNotBlank() },
                proposerUuid = proposerUuid,
                proposerName = proposerName?.takeIf { it.isNotBlank() }
            )
        )
    }

    /**
     * 把自己记录的轨迹贡献到线路库（UPLOAD）。
     *
     * 服务端规则层会校验：点数 10~20000、坐标在国内范围、时间戳递增。
     * 不通过直接 400，所以这里的错误信息要原样展示给用户（他们得知道是哪条不合格）。
     */
    suspend fun submitUpload(
        session: TrackSession,
        points: List<TrackPoint>,
        name: String,
        region: String,
        intro: String,
        proposerUuid: String,
        proposerName: String?
    ): ProposalDto {
        val payload = buildJsonObject {
            put("name", name)
            put("region", region)
            put("distanceKm", (session.totalDistanceMeters / 1000.0))
            put("ascentMeters", session.ascentMeters.toInt())
            put("intro", intro)
            put("points", buildJsonArray {
                points.forEach { p ->
                    add(buildJsonObject {
                        put("lat", p.lat)
                        put("lng", p.lng)
                        put("alt", p.altitude)
                        put("t", p.timestamp)
                    })
                }
            })
        }.toString()
        return submit(
            SubmitProposalRequest(
                type = "UPLOAD",
                category = "OBJECTIVE",
                payloadJson = payload,
                reason = intro.takeIf { it.isNotBlank() },
                proposerUuid = proposerUuid,
                proposerName = proposerName?.takeIf { it.isNotBlank() }
            )
        )
    }

    /** 认票 / 反对（value = 1 / -1）。返回服务端最新的票数与状态。 */
    suspend fun vote(proposalId: Long, voterKey: String, value: Int): VoteResultDto {
        val resp = api.vote(proposalId, VoteRequest(voterKey = voterKey, value = value))
        if (!resp.ok) throw IllegalStateException(resp.msg.ifBlank { "投票失败" })
        return resp.data ?: VoteResultDto()
    }

    private suspend fun submit(req: SubmitProposalRequest): ProposalDto {
        val resp = api.submit(req)
        if (!resp.ok) throw IllegalStateException(resp.msg.ifBlank { "提交失败" })
        return resp.data ?: throw IllegalStateException("服务端没有返回提案")
    }

    /** 供 UI 判断"这条轨迹能不能贡献"（点数太少服务端会拒，提前提示更友好）。 */
    fun canContribute(points: List<TrackPoint>): Boolean = points.size >= 10
}
