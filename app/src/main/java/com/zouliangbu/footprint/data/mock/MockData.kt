package com.zouliangbu.footprint.data.mock

import com.zouliangbu.footprint.data.model.GearCategory
import com.zouliangbu.footprint.data.model.RouteCatalog
import com.zouliangbu.footprint.data.model.RouteItem
import com.zouliangbu.footprint.data.model.RouteSection

/**
 * 全部中文 mock 文案集中在此（将来删文件即完成迁移）。
 * 山名/线路为国内真实知名徒步地，封面用色块占位，不依赖图片资源。
 */
object MockData {

    private val wugongshan = RouteItem("r1", "武功山反穿线", "萍乡", 13.5, 1200, 0, listOf("户外毕业线", "露营"))
    private val huizhou = RouteItem("r2", "惠州大南山", "惠州", 13.6, 1050, 1, listOf("草甸", "一日往返"))
    private val pengcheng = RouteItem("r3", "鹏城十峰·梧桐山", "深圳", 9.8, 940, 2, listOf("夜爬", "看日出"))
    private val huizhane = RouteItem("r4", "徽杭古道", "宣城", 18.0, 680, 3, listOf("古道", "新手友好"))
    private val yubeng = RouteItem("r5", "雨崩 inner 环线", "迪庆", 26.0, 1600, 4, listOf("高原", "多日"))
    private val moGan = RouteItem("r6", "莫干山蒋公道", "湖州", 11.2, 520, 1, listOf("亲子", "竹林"))
    private val haya = RouteItem("r7", "哈巴雪山", "香格里拉", 21.0, 2900, 2, listOf("雪山", "进阶"))
    private val chuanxi = RouteItem("r8", "长穿毕", "阿坝", 46.0, 2100, 0, listOf("重装", "经典"))
    private val wutai = RouteItem("r9", "大五台顺穿", "忻州", 52.0, 2300, 3, listOf("朝台", "多日"))
    private val xiaowutai = RouteItem("r10", "小五台北东线", "张家口", 28.0, 1750, 4, listOf("华北K2", "进阶"))
    private val moganshan2 = RouteItem("r11", "莫干山岭上环线", "湖州", 7.6, 430, 1, listOf("周末", "轻装"))
    private val guilin = RouteItem("r12", "漓江徒步精华段", "桂林", 15.5, 350, 2, listOf("山水", "新手友好"))

    val routeCatalog = RouteCatalog(
        recommendations = listOf(wugongshan, yubeng, chuanxi),
        sections = listOf(
            RouteSection(
                "s1", "口碑山野线", "走过的人都说好",
                listOf(pengcheng, huizhou, moGan, huizhane)
            ),
            RouteSection(
                "s2", "新手友好线", "难度低、补给全，第一次徒步选这里",
                listOf(moganshan2, guilin, huizhane)
            ),
            RouteSection(
                "s3", "进阶挑战线", "多日重装 / 高海拔，量力而行",
                listOf(yubeng, haya, wutai, xiaowutai, chuanxi, wugongshan)
            )
        )
    )

    val gearCategories: List<GearCategory> = listOf(
        GearCategory("g1", "背包", "一日 / 多日背负"),
        GearCategory("g2", "登山鞋", "越野 / 重装"),
        GearCategory("g3", "冲锋衣", "防风防雨"),
        GearCategory("g4", "登山杖", "省膝盖 30%"),
        GearCategory("g5", "帐篷睡袋", "露营过夜"),
        GearCategory("g6", "头灯照明", "夜爬必备"),
        GearCategory("g7", "水具滤水", "户外补水"),
        GearCategory("g8", "防晒配件", "帽子 / 冰袖")
    )
}
