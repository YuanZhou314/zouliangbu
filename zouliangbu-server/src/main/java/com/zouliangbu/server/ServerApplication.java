package com.zouliangbu.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 走两步内容服务端。
 *
 * 本期只提供三个只读接口：首页动态 / 线路 / 装备分类。
 * 轨迹数据不上云（本地优先），这里不涉及任何轨迹相关表。
 */
@SpringBootApplication
@MapperScan("com.zouliangbu.server.content.mapper")
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
