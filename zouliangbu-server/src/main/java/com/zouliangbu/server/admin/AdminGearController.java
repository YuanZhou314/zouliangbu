package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.content.entity.GearCategoryEntity;
import com.zouliangbu.server.content.mapper.GearCategoryMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.zouliangbu.server.admin.AdminSupport.badRequest;
import static com.zouliangbu.server.admin.AdminSupport.notFound;
import static com.zouliangbu.server.admin.AdminSupport.ok;
import static com.zouliangbu.server.admin.AdminSupport.parseId;
import static com.zouliangbu.server.admin.AdminSupport.trimToNull;

/**
 * 装备分类管理。
 *
 * 硬约束：{@code code} 是与 App 侧图标映射的键（g1..g8，见 App 的 GearIcon 映射），
 * 一旦改了就画错图标甚至画不出来 → <b>code 不允许修改</b>，只允许改名/备注/排序。
 * 这也解释了为什么这里没有 delete：删掉一个 code，App 端那个分类的图标就空了。
 */
@RestController
@RequestMapping("/api/v1/admin/gear")
public class AdminGearController {

    private final GearCategoryMapper gearMapper;
    private final AuditService audit;

    public AdminGearController(GearCategoryMapper gearMapper, AuditService audit) {
        this.gearMapper = gearMapper;
        this.audit = audit;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GearCategoryEntity>>> list() {
        List<GearCategoryEntity> rows = gearMapper.selectList(
                new LambdaQueryWrapper<GearCategoryEntity>().orderByAsc(GearCategoryEntity::getSortWeight));
        return ok(rows);
    }

    /** 单条详情。装备表很小，编辑页其实用不上，但保持 REST 完整，也方便排查。 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GearCategoryEntity>> detail(@PathVariable String id) {
        GearCategoryEntity e = load(id);
        return e == null ? notFound("装备分类不存在") : ok(e);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody GearUpsert body) {
        String err = validate(body, true);
        if (err != null) {
            return badRequest(err);
        }
        Long dup = gearMapper.selectCount(
                new LambdaQueryWrapper<GearCategoryEntity>().eq(GearCategoryEntity::getCode, body.code().trim()));
        if (dup != null && dup > 0) {
            return badRequest("code 已存在：" + body.code().trim());
        }
        GearCategoryEntity e = new GearCategoryEntity();
        e.setCode(body.code().trim());
        e.setName(body.name().trim());
        e.setNote(trimToNull(body.note()));
        e.setIconKey(trimToNull(body.iconKey()));
        e.setSortWeight(body.sortWeight() == null ? 100 : body.sortWeight());
        gearMapper.insert(e);
        audit.record("gear.create", "gear", e.getId(), "新增装备分类：" + e.getCode() + " / " + e.getName());
        return ok(e.getId());
    }

    /** 编辑：code 不可改，传了也忽略（防止误改导致 App 图标错位）。 */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable String id, @RequestBody GearUpsert body) {
        GearCategoryEntity e = load(id);
        if (e == null) {
            return notFound("装备分类不存在");
        }
        String err = validate(body, false);
        if (err != null) {
            return badRequest(err);
        }
        e.setName(body.name().trim());
        e.setNote(trimToNull(body.note()));
        e.setIconKey(trimToNull(body.iconKey()));
        if (body.sortWeight() != null) {
            e.setSortWeight(body.sortWeight());
        }
        gearMapper.updateById(e);
        audit.record("gear.update", "gear", e.getId(), "编辑装备分类：" + e.getCode() + " / " + e.getName());
        return ok(null);
    }

    private GearCategoryEntity load(String rawId) {
        Long id = parseId(rawId);
        return id == null ? null : gearMapper.selectById(id);
    }

    private static String validate(GearUpsert b, boolean requireCode) {
        if (b == null) {
            return "请求体不能为空";
        }
        if (requireCode && trimToNull(b.code()) == null) {
            return "code 不能为空";
        }
        if (requireCode && b.code().trim().length() > 16) {
            return "code 最多 16 字符";
        }
        if (trimToNull(b.name()) == null) {
            return "名称不能为空";
        }
        if (b.name().trim().length() > 32) {
            return "名称最多 32 字";
        }
        return null;
    }

    /** code 只在新增时有意义；编辑接口收到 code 也不采纳。 */
    public record GearUpsert(String code, String name, String note, String iconKey, Integer sortWeight) {}
}
