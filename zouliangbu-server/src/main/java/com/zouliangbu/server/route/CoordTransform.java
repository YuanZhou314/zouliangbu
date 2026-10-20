package com.zouliangbu.server.route;

/**
 * GCJ-02（火星坐标）与 WGS-84（GPS 原始坐标）转换。
 *
 * 与 App 侧 util/CoordTransform.kt 算法完全一致（逐行移植，保持精度对齐）。
 * 用途：两步路抓回的轨迹是 WGS-84，走两步 App 全链路是 GCJ-02，必须转换。
 */
public final class CoordTransform {

    private static final double SEMI_MAJOR_AXIS = 6378245.0;
    private static final double ECCENTRICITY_SQ = 0.00669342162296594323;
    private static final double PI = Math.PI;

    private CoordTransform() {
    }

    /** 粗略国境判定：超出该矩形一律认为在境外，不做偏移。 */
    public static boolean isOutOfChina(double lat, double lng) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    /** WGS-84 → GCJ-02。 */
    public static double[] wgs84ToGcj02(double lat, double lng) {
        if (isOutOfChina(lat, lng)) {
            return new double[]{lat, lng};
        }

        double dLatRaw = transformLat(lng - 105.0, lat - 35.0);
        double dLngRaw = transformLng(lng - 105.0, lat - 35.0);

        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - ECCENTRICITY_SQ * magic * magic;
        double sqrtMagic = Math.sqrt(magic);

        double dLat = (dLatRaw * 180.0)
                / ((SEMI_MAJOR_AXIS * (1 - ECCENTRICITY_SQ)) / (magic * sqrtMagic) * PI);
        double dLng = (dLngRaw * 180.0)
                / (SEMI_MAJOR_AXIS / sqrtMagic * Math.cos(radLat) * PI);

        return new double[]{lat + dLat, lng + dLng};
    }

    /**
     * GCJ-02 → WGS-84（不动点迭代逼近，与 App 侧 gcj02ToWgs84 一致）。
     */
    public static double[] gcj02ToWgs84(double lat, double lng) {
        return gcj02ToWgs84(lat, lng, 3);
    }

    private static double[] gcj02ToWgs84(double lat, double lng, int iterations) {
        if (isOutOfChina(lat, lng)) {
            return new double[]{lat, lng};
        }
        double wgsLat = lat;
        double wgsLng = lng;
        for (int i = 0; i < iterations; i++) {
            double[] gcj = wgs84ToGcj02(wgsLat, wgsLng);
            wgsLat += lat - gcj[0];
            wgsLng += lng - gcj[1];
        }
        return new double[]{wgsLat, wgsLng};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
