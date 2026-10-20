package com.zouliangbu.server.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 首页「同城动态」帖子。status=1 上线，0 下线。 */
@TableName("feed_post")
public class FeedPost {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private Integer likeCount;
    private String city;
    private String coverUrl;
    private BigDecimal coverAspectRatio;
    private Integer status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public BigDecimal getCoverAspectRatio() { return coverAspectRatio; }
    public void setCoverAspectRatio(BigDecimal coverAspectRatio) { this.coverAspectRatio = coverAspectRatio; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
