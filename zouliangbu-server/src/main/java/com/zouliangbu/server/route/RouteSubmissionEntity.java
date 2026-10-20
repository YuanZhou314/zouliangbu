package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 用户提交的路线链接 + 审批状态。 */
@TableName("route_submission")
public class RouteSubmissionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceUrl;
    private String sourceRouteId;
    private String submitterUuid;
    private String submitterNote;
    private String status;
    private String rejectReason;
    private Long routeId;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public String getSourceRouteId() { return sourceRouteId; }
    public void setSourceRouteId(String v) { this.sourceRouteId = v; }
    public String getSubmitterUuid() { return submitterUuid; }
    public void setSubmitterUuid(String v) { this.submitterUuid = v; }
    public String getSubmitterNote() { return submitterNote; }
    public void setSubmitterNote(String v) { this.submitterNote = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String v) { this.rejectReason = v; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long v) { this.routeId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime v) { this.processedAt = v; }
}
