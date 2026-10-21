package com.zouliangbu.server.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.admin.entity.AppConfigEntity;
import com.zouliangbu.server.admin.mapper.AppConfigMapper;
import com.zouliangbu.server.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App 启动时拉取的全局配置（强更、公告）。
 *
 * <b>接口演进硬规则</b>：这个接口会被<b>老版本 App</b> 调用，任何时候都不能删字段、不能改字段语义。
 * 新字段一律给默认值（App 侧可缺省），否则老包反序列化会崩。
 * 这里刻意用扁平结构而不是嵌套对象：嵌套对象缺字段在 kotlinx.serialization 里更容易抛异常。
 *
 * 未开启的功能一律返回安全默认值（不弹窗、不显示公告），避免运营填了一半就生效。
 */
@RestController
@RequestMapping("/api/v1/config")
public class PublicConfigController {

    private final AppConfigMapper configMapper;

    public PublicConfigController(AppConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @GetMapping("/app")
    public ResponseEntity<ApiResponse<AppConfigView>> appConfig() {
        List<AppConfigEntity> rows = configMapper.selectList(new LambdaQueryWrapper<>());
        java.util.Map<String, String> kv = new java.util.HashMap<>();
        for (AppConfigEntity e : rows) {
            kv.put(e.getConfigKey(), e.getConfigValue() == null ? "" : e.getConfigValue());
        }

        boolean forceUpdate = "true".equalsIgnoreCase(kv.getOrDefault("force_update.enabled", "false"));
        int minVersion = parseInt(kv.get("force_update.min_version"), 0);
        String updateUrl = kv.getOrDefault("force_update.url", "");
        // 开关打开但没配置下载地址 → 当成未开启。否则 App 会弹一个点了没反应的窗
        if (forceUpdate && updateUrl.isBlank()) {
            forceUpdate = false;
        }

        boolean noticeOn = "true".equalsIgnoreCase(kv.getOrDefault("notice.enabled", "false"));
        String noticeText = kv.getOrDefault("notice.text", "");
        if (noticeOn && noticeText.isBlank()) {
            noticeOn = false;
        }

        return ResponseEntity.ok(ApiResponse.ok(new AppConfigView(
                forceUpdate, minVersion, forceUpdate ? updateUrl : "",
                forceUpdate ? kv.getOrDefault("force_update.message", "") : "",
                noticeOn, noticeOn ? noticeText : "",
                noticeOn ? kv.getOrDefault("notice.link", "") : "")));
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null || s.isBlank() ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public record AppConfigView(boolean forceUpdate, int minVersion, String updateUrl, String updateMessage,
                                boolean noticeEnabled, String noticeText, String noticeLink) {}
}
