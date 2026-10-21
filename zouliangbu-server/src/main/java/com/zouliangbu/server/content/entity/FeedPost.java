package com.zouliangbu.server.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 首页「同城动态」帖子。status=1 上线，0 下线。
 *
 * content / coverAuthor 是 V4 加的后台管理字段：公开接口目前只吐列表卡片需要的字段，
 * 详情正文留给 App 后续接入时再用，所以它们只在后台接口里出现。
 */
@TableName("feed_post")
public class FeedPost {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private Integer likeCount;
    private String city;
    private String coverUrl;
    private String coverAuthor;
    private BigDecimal coverAspectRatio;
    private String content;
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

    public String getCoverAuthor() { return coverAuthor; }
    public void setCoverAuthor(String coverAuthor) { this.coverAuthor = coverAuthor; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
