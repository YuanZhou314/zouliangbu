package com.zouliangbu.server.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * AdminPasswordGuard 纯单元测试：不起 Spring 容器，直接 new + MockEnvironment。
 *
 * 契约：弱密码 + prod profile → check() 抛 IllegalStateException；
 *       弱密码 + 非 prod（dev/test）→ 不抛，仅 warn。
 */
class AdminPasswordGuardTest {

    private AdminPasswordGuard guard(String password, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new AdminPasswordGuard(password, env);
    }

    @Test
    @DisplayName("prod + 默认密码 admin → 抛异常终止启动")
    void prodWithWeakPasswordFails() {
        assertThatCode(() -> guard("admin", "prod").check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN__PASSWORD");
    }

    @Test
    @DisplayName("dev + 默认密码 admin → 不抛异常，仅警告")
    void devWithWeakPasswordDoesNotThrow() {
        assertThatCode(() -> guard("admin", "dev").check()).doesNotThrowAnyException();
    }
}
