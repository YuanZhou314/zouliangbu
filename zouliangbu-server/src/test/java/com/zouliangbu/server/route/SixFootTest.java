package com.zouliangbu.server.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 六只脚解析单测：全部用 resources/sixfoot 下的离线样本（真实页面裁剪 + 真实 trackjson 片段），
 * 不发网络请求，保证 CI 不依赖外网站点可用性。
 */
class SixFootTest {

    private static String fixture(String name) throws Exception {
        try (InputStream in = SixFootTest.class.getResourceAsStream("/sixfoot/" + name)) {
            assertThat(in).as("fixture %s 不存在", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("URL 提取行程 id：PC / 移动端 / 带尾巴")
    void extractTripId() {
        assertThat(SixFoot.extractTripId("https://www.foooooot.com/trip/100142397/")).isEqualTo(100142397L);
        assertThat(SixFoot.extractTripId("https://www.foooooot.com/mobile/trip/100142397/")).isEqualTo(100142397L);
        assertThat(SixFoot.extractTripId("http://foooooot.com/trip/42")).isEqualTo(42L);
        assertThat(SixFoot.extractTripId("https://www.foooooot.com/userindex/574988/")).isNull();
        assertThat(SixFoot.extractTripId("https://www.2bulu.com/mc/route/detail?id=244439")).isNull();
        assertThat(SixFoot.extractTripId(null)).isNull();
    }

    @Test
    @DisplayName("平台识别：六只脚 / 两步路 / 未知域名")
    void detectPlatform() {
        assertThat(RoutePlatform.detect("https://www.foooooot.com/trip/100142397/"))
                .isEqualTo(RoutePlatform.SIXFOOT);
        assertThat(RoutePlatform.detect("https://www.2bulu.com/mc/route/detail?id=244439"))
                .isEqualTo(RoutePlatform.TWOBULU);
        assertThat(RoutePlatform.detect("https://example.com/trip/1")).isNull();
        assertThat(RoutePlatform.detect("")).isNull();
        assertThat(RoutePlatform.SIXFOOT.displayName).isEqualTo("六只脚");
    }

    @Test
    @DisplayName("解析详情页：标题/作者/地区/距离/爬升/海拔/难度/用时")
    void parsePage() throws Exception {
        RouteData.ParsedRoute p = new RouteData.ParsedRoute();
        SixFoot.parsePage(fixture("page.html"), p);

        assertThat(p.name).isEqualTo("苏州中国地图徒步");
        assertThat(p.sourceAuthor).isEqualTo("醉♥客");
        assertThat(p.region).isEqualTo("苏州");
        assertThat(p.totalMileageM).isEqualTo(11600.0);
        assertThat(p.cumulativeClimbM).isEqualTo(647.0);
        assertThat(p.cumulativeDeclineM).isEqualTo(642.0);
        assertThat(p.maxAltitudeM).isEqualTo(119.0);
        assertThat(p.difficulty).isEqualTo("简单");
        assertThat(p.durationHours).isCloseTo(6.9, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("正文剥掉脚印图片，只留文字")
    void contentStripsImages() throws Exception {
        RouteData.ParsedRoute p = new RouteData.ParsedRoute();
        SixFoot.parsePage(fixture("page.html"), p);

        assertThat(p.contentHtml).contains("整个路线现在路迹很明朗");
        assertThat(p.contentHtml).contains("泥地机耕路");
        assertThat(p.contentHtml).doesNotContain("<img");
        assertThat(p.contentHtml).doesNotContain("footprint");
    }

    @Test
    @DisplayName("trackjson 解析：时间/坐标/海拔/速度，坐标转成 GCJ-02")
    void parseTrackJson() throws Exception {
        List<RouteData.TrackPoint> pts = SixFoot.parseTrackJson(fixture("track.json"), new ObjectMapper());

        assertThat(pts).hasSize(4);
        RouteData.TrackPoint first = pts.get(0);
        assertThat(first.time).isEqualTo(1791680597L);
        assertThat(first.altitude).isCloseTo(14.36, org.assertj.core.data.Offset.offset(0.01));
        assertThat(first.speed).isCloseTo(2.52, org.assertj.core.data.Offset.offset(0.01));
        // WGS-84 (31.278765, 120.471607) 在华东，GCJ-02 会整体偏移数百米
        assertThat(first.lat).isNotEqualTo(31.278765);
        assertThat(first.lng).isNotEqualTo(120.471607);
        assertThat(first.lat).isBetween(31.27, 31.29);
        assertThat(first.lng).isBetween(120.46, 120.49);
    }

    @Test
    @DisplayName("trackjson 异常输入不抛异常，返回空或已解析部分")
    void parseTrackJsonRobust() {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(SixFoot.parseTrackJson("", mapper)).isEmpty();
        assertThat(SixFoot.parseTrackJson(null, mapper)).isEmpty();
        assertThat(SixFoot.parseTrackJson("not-json", mapper)).isEmpty();
        assertThat(SixFoot.parseTrackJson("[]", mapper)).isEmpty();
        // 缺字段的点被跳过，不炸
        assertThat(SixFoot.parseTrackJson("[[[1,2]]]", mapper)).isEmpty();
    }

    @Test
    @DisplayName("地区规范：去掉市区/区/县尾巴")
    void normalizeRegion() {
        assertThat(SixFoot.normalizeRegion("江苏 苏州 苏州市区")).isEqualTo("苏州");
        assertThat(SixFoot.normalizeRegion("云南 迪庆 德钦县")).isEqualTo("德钦");
        assertThat(SixFoot.normalizeRegion("北京")).isEqualTo("北京");
        assertThat(SixFoot.normalizeRegion("")).isEmpty();
    }
}
