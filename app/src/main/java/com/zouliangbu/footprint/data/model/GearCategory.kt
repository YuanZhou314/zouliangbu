package com.zouliangbu.footprint.data.model

import kotlinx.serialization.Serializable

/**
 * 装备分类（装备页宫格）。
 * 本期不接商城，仅展示分类；note 是一句话说明。
 */
@Serializable
data class GearCategory(
    val id: String,
    val name: String,
    val note: String
)
