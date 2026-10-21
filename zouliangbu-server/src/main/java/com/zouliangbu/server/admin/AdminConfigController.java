package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.admin.entity.AppConfigEntity;
import com.zouliangbu.server.admin.mapper.AppConfigMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.zouliangbu.server.admin.AdminSupport.badRequest;
import static com.zouliangbu.server.admin.AdminSupport.ok;

/**
 * 配置中心：强更开关 / 公告条。
 *
 * 后台按"键值对列表"编辑，不暴露自由新增删除 —— 能加什么键是由 App 代码决定的，
 * 后台凭空加一个 {@code foo=1}，App 根本不会读，只会让运维误以为生效了。
 * 新增开关的正确流程：App 加读取代码 → 这里加 V5 迁移脚本插默认值 → 后台就有了。
 */
@RestController
@RequestMapping("/api/v1/admin/config")
public class AdminConfigController {

    /** 允许在后台展示和修改的键。白名单之外的键即使库里存在也不返回，防止误改内部配置。 */
    private static final List<String> VISIBLE_KEYS = List.of(
            "force_update.enabled", "force_update.min_version", "force_update.url", "force_update.message",
            "notice.enabled", "notice.text", "notice.link");

    private final AppConfigMapper configMapper;
    private final AuditService audit;

    public AdminConfigController(AppConfigMapper configMapper, AuditService audit) {
        this.configMapper = configMapper;
        this.audit = audit;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConfigRow>>> list() {
        List<AppConfigEntity> rows = configMapper.selectList(
                new LambdaQueryWrapper<AppConfigEntity>().in(AppConfigEntity::getConfigKey, VISIBLE_KEYS));
        // 白名单顺序输出，而不是数据库返回顺序（IN 查询不保证顺序）
        Map<String, AppConfigEntity> byKey = new LinkedHashMap<>();
        rows.forEach(r -> byKey.put(r.getConfigKey(), r));
        List<ConfigRow> out = VISIBLE_KEYS.stream()
                .map(k -> {
                    AppConfigEntity e = byKey.get(k);
                    return new ConfigRow(k,
                            e == null || e.getConfigValue() == null ? "" : e.getConfigValue(),
                            e == null || e.getNote() == null ? "" : e.getNote(),
                            e == null || e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
                })
                .toList();
        return ok(out);
    }

    /** 批量保存：{ "notice.text": "xxx", "notice.enabled": "true" }。 */
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> save(@RequestBody Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            return badRequest("没有要保存的配置项");
        }
        for (String k : body.keySet()) {
            if (!VISIBLE_KEYS.contains(k)) {
                return badRequest("不支持的配置键：" + k);
            }
        }
        StringBuilder changed = new StringBuilder();
        for (Map.Entry<String, String> en : body.entrySet()) {
            AppConfigEntity e = configMapper.selectById(en.getKey());
            if (e == null) {
                e = new AppConfigEntity();
                e.setConfigKey(en.getKey());
                e.setConfigValue(en.getValue());
                e.setUpdatedAt(LocalDateTime.now());
                configMapper.insert(e);
            } else {
                if (en.getValue() != null && !en.getValue().equals(e.getConfigValue())) {
                    changed.append(en.getKey()).append(": ")
                            .append(e.getConfigValue()).append(" → ").append(en.getValue()).append("; ");
                }
                e.setConfigValue(en.getValue());
                e.setUpdatedAt(LocalDateTime.now());
                configMapper.updateById(e);
            }
        }
        audit.record("config.save", "app_config", null,
                changed.length() == 0 ? "配置无变化" : changed.toString());
        return ok(null);
    }

    public record ConfigRow(String key, String value, String note, String updatedAt) {}
}
