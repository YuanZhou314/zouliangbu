package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 标注点（GCJ-02）。 */
@TableName("route_marker")
public class RouteMarkerEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private Integer seq;
    private String name;
    private Double lat;
    private Double lng;
    private Double altitude;
    private Long markerTime;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long v) { this.routeId = v; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer v) { this.seq = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Double getLat() { return lat; }
    public void setLat(Double v) { this.lat = v; }
    public Double getLng() { return lng; }
    public void setLng(Double v) { this.lng = v; }
    public Double getAltitude() { return altitude; }
    public void setAltitude(Double v) { this.altitude = v; }
    public Long getMarkerTime() { return markerTime; }
    public void setMarkerTime(Long v) { this.markerTime = v; }
}
