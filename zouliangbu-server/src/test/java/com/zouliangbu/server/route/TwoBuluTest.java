package com.zouliangbu.server.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwoBuluTest {

    @Test
    @DisplayName("从链接提取路线 id")
    void extractRouteId() {
        assertThat(TwoBulu.extractRouteId("https://www.2bulu.com/mc/route/detail?id=244439"))
                .isEqualTo(244439L);
        assertThat(TwoBulu.extractRouteId("https://www.2bulu.com/mc/route/detail?id=12345&x=1"))
                .isEqualTo(12345L);
        assertThat(TwoBulu.extractRouteId("https://example.com/")).isNull();
        assertThat(TwoBulu.extractRouteId(null)).isNull();
        assertThat(TwoBulu.extractRouteId("")).isNull();
    }

    @Test
    @DisplayName("XOR 0x4B 解密：加密字节 ^ 0x4B 恢复原字节")
    void xorDecrypt() {
        byte[] plain = "{\"errCode\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] encrypted = new byte[plain.length];
        for (int i = 0; i < plain.length; i++) {
            encrypted[i] = (byte) (plain[i] ^ 0x4B);
        }
        String decrypted = TwoBulu.decryptTrackResponse(encrypted);
        assertThat(decrypted).isEqualTo("{\"errCode\":0}");
    }

    @Test
    @DisplayName("空输入返回空串")
    void xorEmpty() {
        assertThat(TwoBulu.decryptTrackResponse(new byte[0])).isEmpty();
        assertThat(TwoBulu.decryptTrackResponse(null)).isEmpty();
    }

    @Test
    @DisplayName("WGS84→GCJ02 偏移量级合理（深圳约 300~600m）")
    void coordTransformOffset() {
        // 深圳某点
        double[] gcj = CoordTransform.wgs84ToGcj02(22.6578, 114.5026);
        double latDiff = Math.abs(gcj[0] - 22.6578);
        double lngDiff = Math.abs(gcj[1] - 114.5026);
        // 纬度偏移约 0.003~0.005，经度约 0.004~0.007
        assertThat(latDiff).isBetween(0.001, 0.01);
        assertThat(lngDiff).isBetween(0.001, 0.01);
    }

    @Test
    @DisplayName("境外坐标不变（isOutOfChina）")
    void coordTransformOutOfChina() {
        double[] result = CoordTransform.wgs84ToGcj02(48.8566, 2.3522); // 巴黎
        assertThat(result[0]).isEqualTo(48.8566);
        assertThat(result[1]).isEqualTo(2.3522);
    }

    @Test
    @DisplayName("GCJ02→WGS84 迭代收敛（往返残差 < 1m）")
    void coordTransformRoundtrip() {
        double lat = 22.6578;
        double lng = 114.5026;
        double[] gcj = CoordTransform.wgs84ToGcj02(lat, lng);
        double[] wgs = CoordTransform.gcj02ToWgs84(gcj[0], gcj[1]);
        assertThat(Math.abs(wgs[0] - lat)).isLessThan(0.0001); // ~10m
        assertThat(Math.abs(wgs[1] - lng)).isLessThan(0.0001);
    }
}
