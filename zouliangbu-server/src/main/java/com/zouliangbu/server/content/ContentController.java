package com.zouliangbu.server.content;

import com.zouliangbu.server.common.ApiResponse;
import com.zouliangbu.server.content.dto.ContentDtos.GearCategory;
import com.zouliangbu.server.content.dto.ContentDtos.RouteCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内容接口 v1（全部只读，无鉴权——内容是公开数据，本期不需要账号体系）。
 *
 * GET /api/v1/routes?keyword=武功&city=
 * GET /api/v1/gear/categories
 */
@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private final ContentQueryService service;

    public ContentController(ContentQueryService service) {
        this.service = service;
    }

    @GetMapping("/routes")
    public ApiResponse<RouteCatalog> routes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city) {
        return ApiResponse.ok(service.catalog(keyword, city));
    }

    @GetMapping("/gear/categories")
    public ApiResponse<List<GearCategory>> gearCategories() {
        return ApiResponse.ok(service.gearCategories());
    }

    /** 健康检查：部署脚本与负载均衡用它判断存活。 */
    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }
}
