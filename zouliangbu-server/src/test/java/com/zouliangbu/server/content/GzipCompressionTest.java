package com.zouliangbu.server.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gzip 压缩集成测试。
 *
 * 说明：MockMvc 走 DispatcherServlet，不经过 Tomcat connector，
 * server.compression 的开关在 connector 层，MockMvc 永远测不到；
 * spring-boot-starter-webmvc-test 也不带 TestRestTemplate。
 * 所以这里起真实端口（RANDOM_PORT），用 JDK HttpClient 发带
 * Accept-Encoding: gzip 的请求，验证 Content-Encoding 响应头。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GzipCompressionTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("响应体超过 min-response-size 时开启 gzip")
    void largeResponsesAreGzipped() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                // 选 /admin/edit.html：约 13KB，远超 min-response-size=1024，mime text/html 在压缩列表内。
                // 公开 JSON 接口里最大的 /api/v1/routes 实测仅约 0.9KB，达不到阈值不触发压缩属正常行为。
                .uri(URI.create("http://localhost:" + port + "/admin/edit.html"))
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        // 选 /api/v1/routes：完整目录（推荐位 + 3 分组 + 12 条线）远超 min-response-size=1024；
        // gear/categories 只有 8 条分类，响应体可能不足阈值，不触发压缩属正常行为。
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(resp.body().length).isGreaterThan(1024);

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.headers().firstValue("Content-Encoding"))
                .as("JSON 响应体超过 min-response-size=1024 时应启用 gzip")
                .isPresent()
                .hasValueSatisfying(v -> assertThat(v.toLowerCase()).isEqualTo("gzip"));
    }
}
