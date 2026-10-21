package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.content.entity.RouteSectionEntity;
import com.zouliangbu.server.content.mapper.RouteSectionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.zouliangbu.server.admin.AdminSupport.badRequest;
import static com.zouliangbu.server.admin.AdminSupport.notFound;
import static com.zouliangbu.server.admin.AdminSupport.ok;
import static com.zouliangbu.server.admin.AdminSupport.parseId;
import static com.zouliangbu.server.admin.AdminSupport.trimToNull;

/**
 * 线路分组管理（如「口碑山野线」「新手友好线」）。
 *
 * <b>subtitle 返回空串不能返回 null</b>：App 侧 RouteSection.subtitle 是非空 String，
 * kotlinx.serialization 遇 null 会抛异常，整个路线页白屏。
 *
 * 另外：命名与排序会直接影响 App 分组卡片的顺序（公开接口按 sort_weight ASC 排），
 * 现在 Mock 用的是"新手 → 口碑 → 进阶"的直觉顺序，服务端 sort_weight 越小越靠前，
 * 运营调数字时要和 App 预期对得上。
 */
@RestController
@RequestMapping("/api/v1/admin/route-sections")
public class AdminRouteSectionController {

    private final RouteSectionMapper sectionMapper;
    private final AuditService audit;

    public AdminRouteSectionController(RouteSectionMapper sectionMapper, AuditService audit) {
        this.sectionMapper = sectionMapper;
        this.audit = audit;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SectionRow>>> list() {
        List<RouteSectionEntity> rows = sectionMapper.selectList(
                new LambdaQueryWrapper<RouteSectionEntity>().orderByAsc(RouteSectionEntity::getSortWeight));
        return ok(rows.stream().map(AdminRouteSectionController::toRow).toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody SectionUpsert body) {
        if (body == null || trimToNull(body.title()) == null) {
            return badRequest("标题不能为空");
        }
        if (body.title().trim().length() > 40) {
            return badRequest("标题最多 40 字");
        }
        RouteSectionEntity e = new RouteSectionEntity();
        e.setTitle(body.title().trim());
        e.setSubtitle(trimToNull(body.subtitle()));
        e.setSortWeight(body.sortWeight() == null ? 100 : body.sortWeight());
        sectionMapper.insert(e);
        audit.record("section.create", "route_section", e.getId(), "新增分组：" + e.getTitle());
        return ok(e.getId());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable String id, @RequestBody SectionUpsert body) {
        RouteSectionEntity e = load(id);
        if (e == null) {
            return notFound("分组不存在");
        }
        if (body == null || trimToNull(body.title()) == null) {
            return badRequest("标题不能为空");
        }
        e.setTitle(body.title().trim());
        e.setSubtitle(trimToNull(body.subtitle()));
        if (body.sortWeight() != null) {
            e.setSortWeight(body.sortWeight());
        }
        sectionMapper.updateById(e);
        audit.record("section.update", "route_section", e.getId(), "编辑分组：" + e.getTitle());
        return ok(null);
    }

    private RouteSectionEntity load(String rawId) {
        Long id = parseId(rawId);
        return id == null ? null : sectionMapper.selectById(id);
    }

    private static SectionRow toRow(RouteSectionEntity e) {
        return new SectionRow(e.getId(), e.getTitle(),
                e.getSubtitle() == null ? "" : e.getSubtitle(),   // 空串而非 null，见类注释
                e.getSortWeight() == null ? 0 : e.getSortWeight());
    }

    public record SectionRow(Long id, String title, String subtitle, int sortWeight) {}

    public record SectionUpsert(String title, String subtitle, Integer sortWeight) {}
}
