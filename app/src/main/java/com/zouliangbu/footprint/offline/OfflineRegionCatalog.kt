package com.zouliangbu.footprint.offline

import android.content.Context
import android.util.Log
import com.zouliangbu.footprint.ui.map.MapPoint
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

    /**
     * 高德城市名 → GCJ-02 城市中心点（**静态表**，给已下载地图预览页做初始相机）。
     *
     * 高德的 `OfflineMapCity` 不给中心坐标，工程里也没有引入 search SDK
     * （GeocodeSearch 兜底做不了），所以只配一张表：键名与高德离线城市列表的
     * 命名习惯一致（带「市/省/自治区」后缀）。当前覆盖：
     *   - 34 个省级行政区（直辖市/省/自治区/特别行政区，省级条目一律用省会中心点）；
     *   - 常用出游城市（深圳/广州/厦门/青岛/三亚/丽江 等，App 主场景在深圳）。
     * 坐标为 GCJ-02 近似值（城市中心用途，百米级偏差无感知）。
     * 表外城市预览页回落到 AMap 默认位置并打日志提示。
     */
    private val CITY_CENTERS: Map<String, Pair<Double, Double>> = mapOf(
        // ---- 直辖市 ----
        "北京市" to (39.9042 to 116.4074),
        "天津市" to (39.1256 to 117.2005),
        "上海市" to (31.2304 to 121.4737),
        "重庆市" to (29.5630 to 106.5516),
        // ---- 省（用省会中心点）----
        "河北省" to (38.0428 to 114.5149),      // 石家庄
        "山西省" to (37.8706 to 112.5489),      // 太原
        "辽宁省" to (41.8057 to 123.4315),      // 沈阳
        "吉林省" to (43.8171 to 125.3235),      // 长春
        "黑龙江省" to (45.8038 to 126.5349),    // 哈尔滨
        "江苏省" to (32.0603 to 118.7969),      // 南京
        "浙江省" to (30.2741 to 120.1551),      // 杭州
        "安徽省" to (31.8206 to 117.2272),      // 合肥
        "福建省" to (26.0745 to 119.2965),      // 福州
        "江西省" to (28.6829 to 115.8579),      // 南昌
        "山东省" to (36.6512 to 117.1201),      // 济南
        "河南省" to (34.7472 to 113.6249),      // 郑州
        "湖北省" to (30.5928 to 114.3055),      // 武汉
        "湖南省" to (28.2282 to 112.9388),      // 长沙
        "广东省" to (23.1291 to 113.2644),      // 广州
        "海南省" to (20.0444 to 110.1999),      // 海口
        "四川省" to (30.5728 to 104.0668),      // 成都
        "贵州省" to (26.6470 to 106.6302),      // 贵阳
        "云南省" to (25.0453 to 102.7097),      // 昆明
        "陕西省" to (34.3416 to 108.9398),      // 西安
        "甘肃省" to (36.0611 to 103.8343),      // 兰州
        "青海省" to (36.6171 to 101.7782),      // 西宁
        "台湾省" to (25.0330 to 121.5654),      // 台北
        // ---- 自治区（用首府中心点）----
        "内蒙古自治区" to (40.8414 to 111.7519),    // 呼和浩特
        "广西壮族自治区" to (22.8170 to 108.3665),   // 南宁
        "西藏自治区" to (29.6520 to 91.1721),        // 拉萨
        "宁夏回族自治区" to (38.4872 to 106.2309),   // 银川
        "新疆维吾尔自治区" to (43.8256 to 87.6168),  // 乌鲁木齐
        // 自治区简称键：全称带民族名（维吾尔/回族/壮族），简称「新疆/西藏/广西/宁夏」
        // 无法靠补后缀命中（"新疆"+"自治区"="新疆自治区"≠全称），只能显式建键。
        "内蒙古" to (40.8414 to 111.7519),
        "广西" to (22.8170 to 108.3665),
        "西藏" to (29.6520 to 91.1721),
        "宁夏" to (38.4872 to 106.2309),
        "新疆" to (43.8256 to 87.6168),
        // ---- 特别行政区 ----
        "香港特别行政区" to (22.3193 to 114.1694),
        "澳门特别行政区" to (22.1987 to 113.5439),
        // ---- 省会/首府城市（单独下载整座城时也能命中）----
        "石家庄市" to (38.0428 to 114.5149),
        "太原市" to (37.8706 to 112.5489),
        "呼和浩特市" to (40.8414 to 111.7519),
        "沈阳市" to (41.8057 to 123.4315),
        "长春市" to (43.8171 to 125.3235),
        "哈尔滨市" to (45.8038 to 126.5349),
        "南京市" to (32.0603 to 118.7969),
        "杭州市" to (30.2741 to 120.1551),
        "合肥市" to (31.8206 to 117.2272),
        "福州市" to (26.0745 to 119.2965),
        "南昌市" to (28.6829 to 115.8579),
        "济南市" to (36.6512 to 117.1201),
        "郑州市" to (34.7472 to 113.6249),
        "武汉市" to (30.5928 to 114.3055),
        "长沙市" to (28.2282 to 112.9388),
        "广州市" to (23.1291 to 113.2644),
        "南宁市" to (22.8170 to 108.3665),
        "海口市" to (20.0444 to 110.1999),
        "成都市" to (30.5728 to 104.0668),
        "贵阳市" to (26.6470 to 106.6302),
        "昆明市" to (25.0453 to 102.7097),
        "拉萨市" to (29.6520 to 91.1721),
        "西安市" to (34.3416 to 108.9398),
        "兰州市" to (36.0611 to 103.8343),
        "西宁市" to (36.6171 to 101.7782),
        "银川市" to (38.4872 to 106.2309),
        "乌鲁木齐市" to (43.8256 to 87.6168),
        // ---- 主要出游城市（App 主场景深圳 + 常见目的地）----
        "深圳市" to (22.5431 to 114.0579),
        "东莞市" to (23.0207 to 113.7518),
        "惠州市" to (23.1115 to 114.4161),
        "珠海市" to (22.2707 to 113.5767),
        "苏州市" to (31.2990 to 120.5853),
        "厦门市" to (24.4798 to 118.0894),
        "青岛市" to (36.0671 to 120.3826),
        "大连市" to (38.9140 to 121.6147),
        "宁波市" to (29.8683 to 121.5440),
        "温州市" to (27.9938 to 120.6994),
        "桂林市" to (25.2742 to 110.2902),
        "三亚市" to (18.2528 to 109.5119),
        "丽江市" to (26.8721 to 100.2300),
        "张家界市" to (29.1170 to 110.4790)
    )

    /**
     * 查城市中心点（GCJ-02）。先精确匹配，再兼容用户端不带「市/省/自治区」后缀的
     * 简称（"广东"、"拉萨" 走补后缀；"新疆/西藏/广西/宁夏/内蒙古" 走表内简称键——
     * 全称带民族名，补后缀命不中，见 CITY_CENTERS 内注释）。
     * 表外城市返回 null（调用方自行决定回落行为）。
     */
    fun cityCenter(name: String): MapPoint? {
        val key = name.trim()
        if (key.isEmpty()) return null
        CITY_CENTERS[key]?.let { (lat, lng) -> return MapPoint(lat, lng) }
        // 简称兜底：依「市 → 省 → 自治区」顺序补后缀再查。
        //（先精确后补后缀，避免「长沙市」这类本名被误扩成不存在的键。）
        for (suffix in listOf("市", "省", "自治区")) {
            if (key.endsWith(suffix)) continue
            CITY_CENTERS[key + suffix]?.let { (lat, lng) -> return MapPoint(lat, lng) }
        }
        return null
    }
}
