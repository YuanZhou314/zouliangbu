package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import org.springframework.http.ResponseEntity;

/**
 * 后台 Controller 的共用小工具。
 *
 * 之前试过用泛型基类 {@code CrudControllerBase<E,R>} 收掉 7 个模块的 CRUD，
 * 结果是每个模块的 list() 筛选参数都不一样、DTO 也不一样，基类要么加一堆钩子
 * 要么被逐个覆盖 —— 抽象反而比直写更难读。所以只留真正通用的两件小事：
 * 统一响应包装 + 字符串 id 解析。
 *
 * 分页 / 软删除 / 审计都由各模块显式写：它们本来就该一眼看出来做了什么。
 */
public final class AdminSupport {

    /** 统一成功包装，省掉每个方法手写 ResponseEntity.ok(ApiResponse.ok(...)) 的括号噪音。 */
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** 业务参数错误（HTTP 400）。 */
    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String msg) {
        return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, msg);
    }

    /** 目标不存在（HTTP 404）。 */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(String msg) {
        return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, msg);
    }

    /**
     * 字符串 id → 主键。解析失败返回 null 而不是抛异常：
     * 后台 URL 是手敲的，{@code /gear/abc} 应该回 404，不该是 500。
     */
    public static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 空串归一成 null：前端清空表单字段传的是 ""，不能让空串当成有值写进库。 */
    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private AdminSupport() {
    }
}
