package com.zouliangbu.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 走两步内容服务端。
 *
 * 内容接口（公开只读）：首页动态 / 线路 / 装备分类 / 全局配置。
 * 后台接口（{@code /api/v1/admin/**}，固定密码换 token）：内容管理、审核、审计、看板。
 * 轨迹数据不上云（本地优先），这里不涉及任何用户轨迹表。
 */
@SpringBootApplication
@MapperScan({"com.zouliangbu.server.content.mapper",
        "com.zouliangbu.server.route.mapper",
        "com.zouliangbu.server.admin.mapper",
        "com.zouliangbu.server.auth.mapper"})
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
