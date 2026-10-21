package com.zouliangbu.server.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把本地上传目录映射成静态资源：{@code /uploads/**} → 磁盘目录。
 *
 * 注意 {@code file:} 前缀末尾那一笔斜杠不能少 —— 少了 {@code file:/a/b} 会被当成"文件"，
 * Spring 实际按 {@code file:/a/b/xxx} 解析没问题，但写成 {@code file:/a/b} 在部分版本上会 404，
 * 所以统一以分隔符结尾。
 */
@Configuration
public class UploadWebConfig implements WebMvcConfigurer {

    private final String dir;
    private final String publicPrefix;

    public UploadWebConfig(@Value("${admin.upload.dir:./uploads}") String dir,
                           @Value("${admin.upload.public-prefix:/uploads}") String publicPrefix) {
        this.dir = dir;
        this.publicPrefix = publicPrefix;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path abs = Paths.get(dir).toAbsolutePath().normalize();
        String location = abs.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(publicPrefix + "/**")
                .addResourceLocations(location);
    }
}
