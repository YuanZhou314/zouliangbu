package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 轨迹点（GCJ-02）。 */
@TableName("route_track_point")
public class RouteTrackPointEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private Integer seq;
    private Double lat;
    private Double lng;
    private Double altitude;
    private Double speed;
    private Long trackTime;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long v) { this.routeId = v; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer v) { this.seq = v; }
    public Double getLat() { return lat; }
    public void setLat(Double v) { this.lat = v; }
    public Double getLng() { return lng; }
    public void setLng(Double v) { this.lng = v; }
    public Double getAltitude() { return altitude; }
    public void setAltitude(Double v) { this.altitude = v; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double v) { this.speed = v; }
    public Long getTrackTime() { return trackTime; }
    public void setTrackTime(Long v) { this.trackTime = v; }
}
