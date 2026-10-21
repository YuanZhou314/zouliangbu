package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 线路版本快照：每次合并/导入存一份完整状态，回滚 = 把旧快照写回。 */
@TableName("route_detail_revision")
public class RouteDetailRevisionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private Integer version;
    private String snapshotJson;
    private String proposerName;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long v) { this.routeId = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String v) { this.snapshotJson = v; }
    public String getProposerName() { return proposerName; }
    public void setProposerName(String v) { this.proposerName = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
