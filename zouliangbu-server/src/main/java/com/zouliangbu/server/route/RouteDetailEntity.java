package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 线路详情扩展（正文 + 交通 + 分类 + 来源）。 */
@TableName("route_detail")
public class RouteDetailEntity {
    @TableId(value = "route_id", type = IdType.INPUT)
    private Long routeId;
    private String introHtml;
    private String contentHtml;
    private String trafficHtml;
    private String difficulty;
    private String risk;
    private String suitableMonths;
    private String roadType;
    private String viewType;
    private String durationName;
    private Long trackSourceId;
    private String sourceUrl;
    private String sourceName;
    private String sourceAuthor;
    private LocalDateTime parsedAt;
    /** 版本号：每次合并 +1，回滚也 +1（保持单调，冲突检测靠它）。 */
    private Integer version;
    /** 最后一次维护者（提案人昵称 / 管理员）。 */
    private String curatorName;
    private LocalDateTime updatedAt;

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public String getIntroHtml() { return introHtml; }
    public void setIntroHtml(String v) { this.introHtml = v; }
    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String v) { this.contentHtml = v; }
    public String getTrafficHtml() { return trafficHtml; }
    public void setTrafficHtml(String v) { this.trafficHtml = v; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String v) { this.difficulty = v; }
    public String getRisk() { return risk; }
    public void setRisk(String v) { this.risk = v; }
    public String getSuitableMonths() { return suitableMonths; }
    public void setSuitableMonths(String v) { this.suitableMonths = v; }
    public String getRoadType() { return roadType; }
    public void setRoadType(String v) { this.roadType = v; }
    public String getViewType() { return viewType; }
    public void setViewType(String v) { this.viewType = v; }
    public String getDurationName() { return durationName; }
    public void setDurationName(String v) { this.durationName = v; }
    public Long getTrackSourceId() { return trackSourceId; }
    public void setTrackSourceId(Long v) { this.trackSourceId = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String v) { this.sourceName = v; }
    public String getSourceAuthor() { return sourceAuthor; }
    public void setSourceAuthor(String v) { this.sourceAuthor = v; }
    public LocalDateTime getParsedAt() { return parsedAt; }
    public void setParsedAt(LocalDateTime v) { this.parsedAt = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
    public String getCuratorName() { return curatorName; }
    public void setCuratorName(String v) { this.curatorName = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
