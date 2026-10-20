package com.zouliangbu.server.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 线路分组，如「口碑山野线」。 */
@TableName("route_section")
public class RouteSectionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String subtitle;
    private Integer sortWeight;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public Integer getSortWeight() { return sortWeight; }
    public void setSortWeight(Integer sortWeight) { this.sortWeight = sortWeight; }
}
