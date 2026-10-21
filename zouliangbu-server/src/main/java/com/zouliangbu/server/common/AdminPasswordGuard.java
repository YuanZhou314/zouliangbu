package com.zouliangbu.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * admin 密码安全护栏：启动时检查是否仍在用默认/占位密码。
 *
 * - prod profile + 弱密码 → 抛异常终止启动（fail-fast）。
 *   放 ApplicationReadyEvent 而不是构造期：保证端口与 Flyway 都已就绪，报错信息完整可见，
 *   且不会在测试切片（无该 Bean 依赖链）里制造启动噪音。
 * - 仅 dev/test/local profile → log.warn 醒目警告，方便本地/CI 继续用默认密码跑通。
 *   其他任何情况（含 prod 与漏配 profile 的默认空 profile）一律 fail-fast——默认拒绝。
 *
 * 换密码方式（Spring 宽松绑定）：配置项 admin.password ←→ 环境变量 ADMIN__PASSWORD。
 */
@Component
public class AdminPasswordGuard {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordGuard.class);

    /** 允许使用默认密码的 profile（仅本地/测试），其余一律按生产处理。 */
    private static final java.util.Set<String> LEISURE_PROFILES =
            java.util.Set.of("dev", "test", "local");

    private final String adminPassword;
    private final Environment env;

    public AdminPasswordGuard(@Value("${admin.password:admin}") String adminPassword,
                              Environment env) {
        this.adminPassword = adminPassword;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        boolean weak = adminPassword == null || adminPassword.isBlank()
                || "admin".equals(adminPassword)
                || "CHANGE_ME".equals(adminPassword);
        if (!weak) {
            return;
        }
        // 默认拒绝：只有显式声明 dev/test/local 才降级为警告。
        // 反过来写（只认 prod）会让「生产忘配 profile」这条最常见的失误绕过整个护栏。
        boolean nonProd = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> LEISURE_PROFILES.contains(p));
        boolean isProd = !nonProd;
        if (isProd) {
            throw new IllegalStateException(
                    "生产环境(admin.password)仍在使用默认/占位密码，拒绝启动。"
                            + "请用环境变量 ADMIN__PASSWORD 设置强密码后重启。");
        }
        log.warn("┌──────────────────────────────────────────────────────────┐");
        log.warn("│  ⚠ 安全警告：admin 后台密码是默认值/占位值（admin 或 CHANGE_ME）");
        log.warn("│  仅限本地/测试环境使用。生产环境必须用环境变量覆盖：");
        log.warn("│      ADMIN__PASSWORD=<强密码>   （Spring 宽松绑定 admin.password）");
        log.warn("└──────────────────────────────────────────────────────────┘");
    }
}
