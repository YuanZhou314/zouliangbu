package com.zouliangbu.server.content;

import com.zouliangbu.server.common.ApiResponse;
import com.zouliangbu.server.content.dto.ContentDtos.FeedDetail;
import com.zouliangbu.server.content.dto.ContentDtos.FeedPage;
import com.zouliangbu.server.content.dto.ContentDtos.GearCategory;
import com.zouliangbu.server.content.dto.ContentDtos.RouteCatalog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内容接口 v1（全部只读，无鉴权——内容是公开数据，本期不需要账号体系）。
 *
 * GET /api/v1/feed?city=深圳&cursor=&size=20
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

    @GetMapping("/feed")
    public ApiResponse<FeedPage> feed(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(service.feed(city, cursor, size));
    }

    /** 动态详情：不存在或未上线 → 404（code 1004）。 */
    @GetMapping("/feed/{id}")
    public ResponseEntity<ApiResponse<FeedDetail>> feedDetail(@PathVariable long id) {
        FeedDetail detail = service.feedDetail(id);
        if (detail == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "内容不存在");
        }
        return ResponseEntity.ok(ApiResponse.ok(detail));
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
