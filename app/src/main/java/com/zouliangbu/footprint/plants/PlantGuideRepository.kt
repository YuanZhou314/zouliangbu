package com.zouliangbu.footprint.plants

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 常见植物图鉴仓库（纯本地 assets，离线可用）。
 *
 * 匹配链路：轨迹点（GCJ-02）→ index.json bbox 预筛 → 命中城市的区域文件做边界精确判定。
 * bbox 预筛在前是因为区域文件 20KB+，不能每个候选都解析一遍。
 *
 * 没有数据覆盖的行政区返回 null —— UI 整块不显示，不弹提示（这是个静默增强模块，
 * 断网/无覆盖都不能成为打扰用户的理由）。
 */
class PlantGuideRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedIndex: List<PlantRegionIndexEntry>? = null

    /** 给定 GCJ-02 坐标，返回命中行政区的图鉴；未命中返回 null。 */
    suspend fun regionFor(lat: Double, lng: Double): PlantRegion? = withContext(Dispatchers.IO) {
        val index = cachedIndex ?: loadIndex().also { cachedIndex = it }
        for (entry in index) {
            if (!PlantGeometry.inBbox(lat, lng, entry.bbox)) continue
            // bbox 相邻城市可能重叠（如深圳/东莞），逐个做环判定，第一个命中的赢
            val region = runCatching { loadRegion(entry.adcode) }.getOrNull() ?: continue
            if (PlantGeometry.inBoundary(lat, lng, region.boundary)) {
                return@withContext region
            }
        }
        null
    }

    private fun loadIndex(): List<PlantRegionIndexEntry> = runCatching {
        context.assets.open(INDEX_PATH).bufferedReader().use { reader ->
            json.decodeFromString<PlantRegionIndex>(reader.readText())
        }.regions
    }.getOrDefault(emptyList())

    private fun loadRegion(adcode: String): PlantRegion =
        context.assets.open("$DIR/$adcode.json").bufferedReader().use { reader ->
            json.decodeFromString<PlantRegion>(reader.readText())
        }

    private companion object {
        const val DIR = "plants"
        const val INDEX_PATH = "plants/index.json"
    }
}
