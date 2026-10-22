package com.zouliangbu.server.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气（QWeather）客户端。凭据仅保存在服务端，App 不持有。
 *
 * 认证：JWT（Ed25519 / EdDSA）。控制台上传公钥，服务端持有私钥并对每次请求签发
 * 短时效 JWT（15 分钟），放在 Authorization: Bearer 头。
 * 配置项（zlb.env / application-*.yml）：
 *   zlb.weather.qweather-project-id   项目 ID（JWT sub）
 *   zlb.weather.qweather-credential-id 凭据 ID（JWT kid）
 *   zlb.weather.qweather-private-key   Ed25519 私钥 PEM（一行内 \\n 转义或直接多行）
 *   zlb.weather.qweather-host          API 主机，默认 https://api.qweather.com/v7
 */
@Component
public class QWeatherClient {

    private static final Logger log = LoggerFactory.getLogger(QWeatherClient.class);

    @Value("${zlb.weather.qweather-host:https://api.qweather.com/v7}")
    private String baseUrl;

    @Value("${zlb.weather.qweather-project-id:}")
    private String projectId;

    @Value("${zlb.weather.qweather-credential-id:}")
    private String credentialId;

    @Value("${zlb.weather.qweather-dev-id:}")
    private String devId;

    @Value("${zlb.weather.qweather-private-key:}")
    private String privateKeyPem;

    private final HttpClient httpClient;

    /** 缓存的 JWT 与签发时刻（避免每次请求都签名）。 */
    private volatile String cachedToken;
    private volatile Instant tokenIssuedAt = Instant.EPOCH;

    public QWeatherClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean available() {
        return notBlank(projectId) && notBlank(credentialId) && notBlank(privateKeyPem) && notBlank(devId);
    }

    /** 签发（或复用）JWT。有效期 15 分钟，提前 2 分钟刷新。 */
    private synchronized String token() throws Exception {
        Instant now = Instant.now();
        if (cachedToken != null && tokenIssuedAt.plusSeconds(13 * 60).isAfter(now)) {
            return cachedToken;
        }
        byte[] der = pemToDer(privateKeyPem);
        PrivateKey key = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(der));

        String header = b64url(("{\"alg\":\"EdDSA\",\"kid\":\"" + credentialId + "\"}"));
        String payload = b64url(("{\"iss\":\"" + devId + "\",\"sub\":\"" + projectId + "\",\"iat\":" + (now.getEpochSecond() - 30)
                + ",\"exp\":" + now.plusSeconds(900).getEpochSecond() + "}"));
        String signingInput = header + "." + payload;

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(key);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String token = signingInput + "." + b64url(sig.sign());
        cachedToken = token;
        tokenIssuedAt = now;
        return token;
    }

    public String fetchWeather(double lat, double lng) throws Exception {
        if (!available()) {
            throw new RuntimeException("和风 JWT 凭据未配置完整（dev-id/project/credential/private-key）");
        }
        String loc = String.format(java.util.Locale.ROOT, "%.2f,%.2f", lng, lat);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"location\":\"").append(loc).append("\"");
        try {
            String now = get(baseUrl + "/weather/now?location=" + loc);
            sb.append(",\"now\":").append(now);
        } catch (Exception e) {
            log.warn("和风实况失败：{}", e.getMessage());
            sb.append(",\"now\":null");
        }
        try {
            String h = get(baseUrl + "/weather/24h?location=" + loc);
            sb.append(",\"hourly\":").append(h);
        } catch (Exception e) {
            log.warn("和风 24h 失败：{}", e.getMessage());
            sb.append(",\"hourly\":null");
        }
        try {
            String a = get(baseUrl + "/alarm/now?location=" + loc);
            sb.append(",\"alarm\":").append(a);
        } catch (Exception e) {
            sb.append(",\"alarm\":null");
        }
        sb.append("}");
        return sb.toString();
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("Authorization", "Bearer " + token())
                .GET()
                .build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("和风 HTTP " + resp.statusCode());
        }
        return decodeBody(resp);
    }

    private String decodeBody(HttpResponse<byte[]> resp) throws IOException {
        String enc = resp.headers().firstValue("Content-Encoding").orElse("");
        byte[] body = resp.body();
        if (enc.contains("gzip")) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(body))) {
                body = gz.readAllBytes();
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /** 剥 PEM 头尾与空白，取 Base64 主体 DER 字节。 */
    private static byte[] pemToDer(String pem) {
        String body = pem.replace("\\n", "\n").replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getMimeDecoder().decode(body);
    }

    private static String b64url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public void fillFromCache(ConcurrentHashMap<String, ?> cache, String key) { }
}
