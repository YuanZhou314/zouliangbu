package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 社区贡献提案（所有"用户能往库里加东西"的统一入口）。
 *
 * type：
 *  - IMPORT：外部平台链接导入（事实来源是第三方 → 需人工合规审核）
 *  - UPLOAD：贡献者自己走出来的轨迹（事实来源是提交者 → 规则层校验即可）
 *  - EDIT：对已有线路资料的勘误（靠投票信号收敛，客观修正可自动合并）
 *
 * status：
 *  PENDING 待处理 / APPROVED 已批准 / REJECTED 已驳回 / MERGED 已合并（进了新版本） / CONFLICT 版本冲突
 */
@TableName("content_proposal")
public class ContentProposalEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String category;
    private Long targetRouteId;
    private String sourceUrl;
    private String payloadJson;
    private Integer baseVersion;
    private String reason;
    private String proposerUuid;
    private String proposerName;
    private Long proposerUserId;
    private String status;
    private Integer votesUp;
    private Integer votesDown;
    private Integer threshold;
    private Long routeId;
    private Integer mergedVersion;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public Long getTargetRouteId() { return targetRouteId; }
    public void setTargetRouteId(Long v) { this.targetRouteId = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public Integer getBaseVersion() { return baseVersion; }
    public void setBaseVersion(Integer v) { this.baseVersion = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getProposerUuid() { return proposerUuid; }
    public void setProposerUuid(String v) { this.proposerUuid = v; }
    public String getProposerName() { return proposerName; }
    public void setProposerName(String v) { this.proposerName = v; }
    public Long getProposerUserId() { return proposerUserId; }
    public void setProposerUserId(Long v) { this.proposerUserId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getVotesUp() { return votesUp; }
    public void setVotesUp(Integer v) { this.votesUp = v; }
    public Integer getVotesDown() { return votesDown; }
    public void setVotesDown(Integer v) { this.votesDown = v; }
    public Integer getThreshold() { return threshold; }
    public void setThreshold(Integer v) { this.threshold = v; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long v) { this.routeId = v; }
    public Integer getMergedVersion() { return mergedVersion; }
    public void setMergedVersion(Integer v) { this.mergedVersion = v; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String v) { this.rejectReason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime v) { this.processedAt = v; }
}
