package com.zouliangbu.footprint.plants

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 植物图鉴纯逻辑单测：几何命中判定 + assets JSON 结构解析。
 * 不碰 Android 类，普通 JVM 单测直接跑。
 */
class PlantGuideLogicTest {

    // ---------- PlantGeometry ----------

    /** 简单方框：经度 114.0-115.0，纬度 22.0-23.0。 */
    private val squareRing = listOf(
        listOf(114.0, 22.0), listOf(115.0, 22.0),
        listOf(115.0, 23.0), listOf(114.0, 23.0), listOf(114.0, 22.0)
    )
    private val squareBbox = listOf(114.0, 22.0, 115.0, 23.0)

    @Test
    fun `点在方框环内`() {
        assertTrue(PlantGeometry.inRing(22.5, 114.5, squareRing))
    }

    @Test
    fun `点在环外`() {
        assertFalse(PlantGeometry.inRing(22.5, 115.5, squareRing))
        assertFalse(PlantGeometry.inRing(24.0, 114.5, squareRing))
        assertFalse(PlantGeometry.inRing(21.0, 100.0, squareRing))
    }

    @Test
    fun `bbox 预筛`() {
        assertTrue(PlantGeometry.inBbox(22.5, 114.5, squareBbox))
        assertFalse(PlantGeometry.inBbox(22.5, 116.0, squareBbox))
        // 边界上算命中（>= / <=），避免贴着行政区界的轨迹漏判
        assertTrue(PlantGeometry.inBbox(22.0, 114.0, squareBbox))
        assertFalse(PlantGeometry.inBbox(22.5, 114.5, emptyList()))
    }

    @Test
    fun `完整判定 bbox 不中则不算命中`() {
        // bbox 外的点直接拒绝，即便环判定可能误判
        assertFalse(PlantGeometry.contains(40.0, 116.0, squareBbox, listOf(squareRing)))
        assertTrue(PlantGeometry.contains(22.5, 114.5, squareBbox, listOf(squareRing)))
    }

    @Test
    fun `多外环任一命中即可`() {
        val ringA = listOf(
            listOf(114.0, 22.0), listOf(115.0, 22.0),
            listOf(115.0, 23.0), listOf(114.0, 23.0)
        )
        val ringB = listOf(
            listOf(116.0, 30.0), listOf(117.0, 30.0),
            listOf(117.0, 31.0), listOf(116.0, 31.0)
        )
        assertTrue(PlantGeometry.inBoundary(30.5, 116.5, listOf(ringA, ringB)))
        assertTrue(PlantGeometry.inBoundary(22.5, 114.5, listOf(ringA, ringB)))
        assertFalse(PlantGeometry.inBoundary(25.0, 115.5, listOf(ringA, ringB)))
    }

    @Test
    fun `非闭合环与病态点不崩溃`() {
        val openRing = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0), listOf(0.0, 1.0))
        assertTrue(PlantGeometry.inRing(0.5, 0.5, openRing))
        assertFalse(PlantGeometry.inRing(2.0, 2.0, openRing))
        // 含脏数据点（缺经纬度）时跳过该点而不是抛异常
        val dirtyRing = listOf(listOf(0.0, 0.0), emptyList(), listOf(1.0, 1.0), listOf(0.0, 1.0))
        PlantGeometry.inRing(0.5, 0.5, dirtyRing) // 不抛异常即通过
    }

    // ---------- 模型解析（assets JSON 结构冒烟） ----------

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `区域 JSON 解析 - 忽略未知字段`() {
        val raw = """
        {
          "version": 1, "adcode": "440300", "regionName": "深圳市",
          "generatedAt": "2026-10-23T21:00:00", "topN": 40,
          "sources": {"gbif": {"totalPlantRecords": 57843}, "inaturalist": {"totalSpecies": 5481}},
          "bbox": [113.75, 22.39, 114.63, 22.86],
          "boundary": [[[113.75, 22.39], [114.63, 22.39], [114.63, 22.86]]],
          "species": [
            {"scientificName": "Lantana camara", "nameCn": "马缨丹", "score": 2.86,
             "gbifCount": 569, "inatCount": 2083,
             "photo": {"url": "https://example/x.jpg", "license": "cc0",
                        "attribution": "(c) someone", "source": "inaturalist"}},
            {"scientificName": "Ficus microcarpa", "nameCn": null, "score": 2.15,
             "gbifCount": 286, "inatCount": 1317, "photo": null}
          ]
        }
        """.trimIndent()
        val region = json.decodeFromString<PlantRegion>(raw)
        assertEquals("深圳市", region.regionName)
        assertEquals(2, region.species.size)
        assertEquals("马缨丹", region.species[0].nameCn)
        assertEquals("cc0", region.species[0].photo?.license)
        assertNull(region.species[1].nameCn)
        assertEquals(3, region.boundary[0].size)
    }

    @Test
    fun `索引 JSON 解析`() {
        val raw = """
        {"version": 1, "regions": [
          {"adcode": "440300", "regionName": "深圳市", "bbox": [113.7, 22.4, 114.6, 22.9]},
          {"adcode": "110000", "regionName": "北京市", "bbox": [115.4, 39.4, 117.5, 41.1]}
        ]}
        """.trimIndent()
        val index = json.decodeFromString<PlantRegionIndex>(raw)
        assertEquals(2, index.regions.size)
        assertEquals("110000", index.regions[1].adcode)
    }
}
