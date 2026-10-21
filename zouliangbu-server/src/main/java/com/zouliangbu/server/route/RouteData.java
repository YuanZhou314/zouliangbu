package com.zouliangbu.server.route;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线解析结果通用载体（两步路 / 六只脚共用）。
 *
 * 坐标系：各 Client 负责把源站坐标（均为 WGS-84）转成 GCJ-02 后填充本结构。
 * 字段含义与两步路详情接口对齐，缺数据的平台填 0 / 空串，不填 null。
 */
public final class RouteData {

    private RouteData() {
    }

    /** 单个轨迹点（GCJ-02）。 */
    public static final class TrackPoint {
        public double lat;
        public double lng;
        public double altitude;
        public double speed;
        public long time;
    }

    /** 单个标注点（GCJ-02）。 */
    public static final class Marker {
        public String name;
        public double lat;
        public double lng;
        public double altitude;
        public long time;
    }

    /** 路线详情（已转换为 GCJ-02）。 */
    public static final class ParsedRoute {
        public String name;
        public String region;
        public double totalMileageM;
        public double maxAltitudeM;
        public double cumulativeClimbM;
        public double cumulativeDeclineM;
        public double durationHours;
        public String difficulty;
        public String risk;
        public String suitableMonths;
        public String roadType;
        public String viewType;
        public String introHtml;
        public String contentHtml;
        public String trafficHtml;
        public String sourceUrl;
        public String sourceAuthor;
        public long sourceTrackId;
        public double latStart;
        public double lngStart;
        public double latEnd;
        public double lngEnd;
        public List<TrackPoint> trackPoints = new ArrayList<>();
        public List<Marker> markers = new ArrayList<>();
    }
}
