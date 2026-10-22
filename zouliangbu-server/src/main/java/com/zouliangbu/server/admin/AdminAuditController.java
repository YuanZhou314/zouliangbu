package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.admin.entity.AdminAuditEntity;
import com.zouliangbu.server.admin.mapper.AdminAuditMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.zouliangbu.server.admin.AdminSupport.ok;

/**
 * 操作审计查询（只读，不提供删除）。
 *
 * 为什么不给"清空审计"：审计的价值就在于运营自己也删不掉。
 * 数据量大了靠归档表解决，不是靠后台一键清空。
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final AdminAuditMapper auditMapper;

    public AdminAuditController(AdminAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    /**
     * 分页查询。用页码分页而不是 cursor —— 后台要"共 N 条"和跳页。
     *
     * @param action 前缀匹配，如 {@code gear.} 查所有装备分类相关操作
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<AuditRow>>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.normalizeSize(size);

        LambdaQueryWrapper<AdminAuditEntity> qw = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            qw.likeRight(AdminAuditEntity::getAction, action.trim());
        }
        Long total = auditMapper.selectCount(qw);

        // 用 MyBatis-Plus 的 Page 做物理分页，而不是手拼 LIMIT/OFFSET 字符串。
        // MyBatis-Plus 的 last(String) 不接受占位符参数（这是 3.x 的行为），
        // 手拼又违背"SQL 里不出现拼接值"，所以直接用分页插件对象最干净。
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AdminAuditEntity> pg =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(p, s);
        pg.setSearchCount(false);   // 总数上面已单独查过，不重复 count
        qw.orderByDesc(AdminAuditEntity::getId);
        List<AuditRow> items = auditMapper.selectPage(pg, qw).getRecords().stream()
                .map(e -> new AuditRow(e.getId(), e.getOperator(), e.getAction(),
                        e.getTargetType(), e.getTargetId(), e.getDetail(), e.getIp(),
                        e.getCreatedAt() == null ? "" : e.getCreatedAt().toString()))
                .toList();
        return ok(new PageResult<>(items, total == null ? 0 : total, p, s));
    }

    public record AuditRow(Long id, String operator, String action, String targetType,
                           String targetId, String detail, String ip, String createdAt) {}
}
