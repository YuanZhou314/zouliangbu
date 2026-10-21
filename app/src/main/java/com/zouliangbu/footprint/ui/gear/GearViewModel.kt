package com.zouliangbu.footprint.ui.gear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.db.GearItem
import com.zouliangbu.footprint.data.model.GearCategory
import com.zouliangbu.footprint.data.repo.LocalGearRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 装备台账 ViewModel：条目列表来自 Room 的 Flow，分类来自静态 MockData。
 *
 * 统计（总重量 / 总费用 / 分类占比）不在 ViewModel 里预聚合——
 * 数据量小（几十件），Compose 侧 remember 计算即可，减少一层状态同步。
 */
class GearViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: LocalGearRepository =
        (app as FootprintApp).gearRepository as LocalGearRepository

    val items: StateFlow<List<GearItem>> = repo.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: List<GearCategory> = repo.categories()

    suspend fun getItem(id: Long): GearItem? = repo.getItem(id)

    fun save(item: GearItem) {
        viewModelScope.launch { repo.save(item) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
