package com.zouliangbu.footprint.plants

import kotlinx.serialization.Serializable

/**
 * 常见植物图鉴的数据模型。
 *
 * 数据源与生成方式见 `.workbuddy/scratch/plant-api-probe/precompute_plants.py`：
 * DataV 开放边界 + GBIF/iNaturalist 观测聚合，预计算成静态 JSON 打进 assets，
 * 运行期零网络依赖（图片除外）。坐标全部是 GCJ-02，与轨迹点同系，直接比对。
 */

/** index.json 的一条行政区索引：bbox 预筛用，边界明细在各自区域文件里按需加载。 */
@Serializable
data class PlantRegionIndexEntry(
    val adcode: String,
    val regionName: String,
    /** [swLng, swLat, neLng, neLat]，GCJ-02。 */
    val bbox: List<Double> = emptyList()
)

@Serializable
data class PlantRegionIndex(
    val version: Int = 1,
    val regions: List<PlantRegionIndexEntry> = emptyList()
)

/** 单张植物照片。license 只会是 cc0 / cc-by / cc-by-nc（生成端已过滤保留版权的图）。 */
@Serializable
data class PlantPhoto(
    val url: String = "",
    val license: String = "",
    val attribution: String = "",
    val source: String = "",
    val observationUrl: String? = null
)

/** 一个物种条目。nameCn 为 null 表示生成端没拿到可信中文名，UI 回退显示学名。 */
@Serializable
data class PlantSpecies(
    val scientificName: String,
    val nameCn: String? = null,
    val score: Double = 0.0,
    val gbifCount: Int = 0,
    val inatCount: Int? = null,
    val photo: PlantPhoto? = null
)

/** 一个行政区的完整图鉴（plants/{adcode}.json）。 */
@Serializable
data class PlantRegion(
    val version: Int = 1,
    val adcode: String = "",
    val regionName: String = "",
    val topN: Int = 0,
    val species: List<PlantSpecies> = emptyList(),
    /**
     * GCJ-02 外环列表 `[[[lng,lat],...],...]`。
     * 只有抽稀后的外环（弃洞），边界处匹配误差 ±数百米——对"查这一带常见植物"足够。
     */
    val boundary: List<List<List<Double>>> = emptyList(),
    /** [swLng, swLat, neLng, neLat]，GCJ-02。 */
    val bbox: List<Double> = emptyList()
)
