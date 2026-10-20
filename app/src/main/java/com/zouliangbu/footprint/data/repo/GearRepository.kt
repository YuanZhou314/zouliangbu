package com.zouliangbu.footprint.data.repo

import com.zouliangbu.footprint.data.model.GearCategory

/**
 * 装备分类数据源（本期纯静态，不接商城）。
 */
interface GearRepository {
    fun categories(): List<GearCategory>
}
