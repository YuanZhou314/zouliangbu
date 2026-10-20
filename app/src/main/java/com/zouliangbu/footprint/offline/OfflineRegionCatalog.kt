package com.zouliangbu.footprint.offline

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 离线地图区域清单（ROADMAP P4.1）。
 *
 * 清单来源优先级：
 *   1. 远程清单（`https://.../offline/regions.json`）——真实场景用这个；
 *   2. 本地内置清单（`assets/offline/regions.json`）——离线兜底，也是开发期的默认来源。
 *
 * 清单里的 `url` 是 MBTiles 文件的下载地址。**清单本身不打包瓦片**，
 * 所以首次使用必须联网下清单；下不到就用内置清单（可能过期，但至少能下包）。
 *
 * 纯逻辑部分（解析）刻意不依赖 Android 类，便于 JVM 单测；
 * 只有读 assets 那一层碰 Context。
 */
object OfflineRegionCatalog {

    private const val TAG = "OfflineCatalog"

    /** 内置清单文件名（放在 assets 下）。 */
    const val BUILT_IN_ASSET = "offline/regions.json"

    /**
     * 解析清单 JSON。
     *
     * 格式：
     * ```json
     * {
     *   "version": "2026-10-20",
     *   "regions": [
     *     {
     *       "id": "zj",
     *       "name": "浙江省",
     *       "group": "华东",
     *       "url": "https://example.com/offline/zj.mbtiles",
     *       "size": 134217728,
     *       "sha256": "abc...",
     *       "version": "2026-10-01",
     *       "bounds": { "minLat": 27.0, "minLng": 118.0, "maxLat": 31.2, "maxLng": 123.0 },
     *       "minZoom": 6, "maxZoom": 14
     *     }
     *   ]
     * }
     * ```
     *
     * 解析失败不抛异常：返回空列表，让调用方回退到内置清单。
     */
    fun parse(json: String): List<OfflineRegion> {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("regions") ?: JSONArray()
            val out = ArrayList<OfflineRegion>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val url = o.optString("url").takeIf { it.isNotBlank() } ?: continue
                val b = o.optJSONObject("bounds")
                out.add(
                    OfflineRegion(
                        id = id,
                        name = o.optString("name", id),
                        group = o.optString("group", "其他"),
                        downloadUrl = url,
                        sizeBytes = o.optLong("size", 0L),
                        sha256 = o.optString("sha256", ""),
                        version = o.optString("version", "0"),
                        bounds = if (b != null) {
                            GeoBounds(
                                minLat = b.optDouble("minLat", 0.0),
                                minLng = b.optDouble("minLng", 0.0),
                                maxLat = b.optDouble("maxLat", 0.0),
                                maxLng = b.optDouble("maxLng", 0.0)
                            )
                        } else {
                            GeoBounds.EMPTY
                        },
                        minZoom = o.optInt("minZoom", 6),
                        maxZoom = o.optInt("maxZoom", 14)
                    )
                )
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "parse regions failed", t)
            emptyList()
        }
    }

    /** 读内置清单。 */
    fun builtIn(context: Context): List<OfflineRegion> {
        return try {
            context.assets.open(BUILT_IN_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
                .let { parse(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "read built-in catalog failed", t)
            emptyList()
        }
    }

    /** 按大区分组，组内按名称排序。UI 直接按这个顺序渲染。 */
    fun grouped(regions: List<OfflineRegion>): List<Pair<String, List<OfflineRegion>>> =
        regions.groupBy { it.group }
            .toSortedMap()
            .map { (group, items) -> group to items.sortedBy { it.name } }
}
