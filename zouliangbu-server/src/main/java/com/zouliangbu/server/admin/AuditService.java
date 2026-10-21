package com.zouliangbu.server.admin;

import com.zouliangbu.server.admin.entity.AdminAuditEntity;
import com.zouliangbu.server.admin.mapper.AdminAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 写操作审计。
 *
 * 为什么不用拦截器统一记：拦截器拿不到"业务上到底改了什么"（比如 PUT 只传了两个字段），
 * 记出来的全是 URL + 原始 body，看不出意图。所以由 Controller 在成功落库后显式调用，
 * 顺便记录人类可读的摘要。
 *
 * <b>审计失败不允许影响主流程</b>：写日志本身挂了不该让管理员那次保存回滚，
 * 因此整体 try/catch 并只打 WARN。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** 正文类字段截断长度：一条线路正文几十 KB，全存进去审计表很快就撑爆。 */
    private static final int DETAIL_LIMIT = 200;

    private final AdminAuditMapper mapper;

    public AuditService(AdminAuditMapper mapper) {
        this.mapper = mapper;
    }

    public void record(String action, String targetType, Object targetId, String detail) {
        try {
            AdminAuditEntity e = new AdminAuditEntity();
            e.setOperator(AdminContext.current());
            e.setAction(action);
            e.setTargetType(targetType);
            e.setTargetId(targetId == null ? null : String.valueOf(targetId));
            e.setDetail(truncate(detail));
            e.setIp(currentIp());
            e.setCreatedAt(LocalDateTime.now());
            mapper.insert(e);
        } catch (RuntimeException ex) {
            log.warn("audit failed: action={}, target={}/{}", action, targetType, targetId, ex);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= DETAIL_LIMIT ? s : s.substring(0, DETAIL_LIMIT) + "…";
    }

    /** 取客户端 IP。本机/内网直连时 X-Forwarded-For 不存在，回落到 remoteAddr 即可。 */
    private static String currentIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            var req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
            }
            return req.getRemoteAddr();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
