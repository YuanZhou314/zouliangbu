package com.zouliangbu.server.admin;

import java.util.List;

/**
 * 后台统一分页形状。
 *
 * 与 App 侧的 cursor 分页刻意不同：App 是"无限下滑"，只需要下一页指针；
 * 后台要跳页、要总数、要"共 N 条"的文案，页码分页才对。
 * 两边各用各的，别为了统一把任何一边做拧。
 *
 * @param items 当前页数据
 * @param total 满足筛选条件的总条数
 * @param page  当前页码，从 1 开始
 * @param size  每页条数
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    /** 归一化页码：page<1 → 1；size 夹在 1..200（后台单页最多 200，防止有人手改 URL 拉全表）。 */
    public static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 200);
    }
}
