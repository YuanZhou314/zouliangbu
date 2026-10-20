package com.zouliangbu.server.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 线路。sectionId 为 null 表示只出现在推荐位；isRecommend=1 进横向推荐。 */
@TableName("route")
public class RouteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String region;
    private BigDecimal distanceKm;
    private Integer ascentMeters;
    private String coverUrl;
    /** 逗号分隔的标签，读取时 split（避免为 2~3 个标签建关联表） */
    private String tags;
    private Long sectionId;
    private Boolean isRecommend;
    private Integer sortWeight;
    private Integer status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public Integer getAscentMeters() { return ascentMeters; }
    public void setAscentMeters(Integer ascentMeters) { this.ascentMeters = ascentMeters; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }

    public Boolean getIsRecommend() { return isRecommend; }
    public void setIsRecommend(Boolean isRecommend) { this.isRecommend = isRecommend; }

    public Integer getSortWeight() { return sortWeight; }
    public void setSortWeight(Integer sortWeight) { this.sortWeight = sortWeight; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
