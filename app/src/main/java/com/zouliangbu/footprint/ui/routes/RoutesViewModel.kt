package com.zouliangbu.footprint.ui.routes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.model.RouteItem
import com.zouliangbu.footprint.data.model.RouteSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 线路页 UI 状态：搜索词 + 过滤后的推荐位/分组。 */
data class RoutesUiState(
    val query: String = "",
    val recommendations: List<RouteItem> = emptyList(),
    val sections: List<RouteSection> = emptyList(),
    /** 有关键词但所有分组都无结果 */
    val isEmpty: Boolean = false
)

/**
 * 线路页 ViewModel：搜索词对 mock 目录做本地过滤（名称/地区）。
 * 将来接后台时，把过滤逻辑换为远程搜索接口，UI 不变。
 */
class RoutesViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as FootprintApp
    private val query = MutableStateFlow("")

    val uiState: StateFlow<RoutesUiState> =
        combine(query, application.routeRepository.observeCatalog()) { q, catalog ->
            val keyword = q.trim()
            val matches: (RouteItem) -> Boolean = {
                it.name.contains(keyword, true) || it.region.contains(keyword, true)
            }
            val filteredSections = catalog.sections.map { section ->
                section.copy(
                    routes = if (keyword.isEmpty()) section.routes else section.routes.filter(matches)
                )
            }.filter { it.routes.isNotEmpty() }.toMutableList()

            // 推荐位线路没有分组归属，搜索时若不单独归组，搜"武功"会搜不到顶部的"武功山反穿线"。
            // 接远程后服务端已返回"搜索结果"分组且搜索态 recommendations 为空，此分支自然不触发。
            if (keyword.isNotEmpty()) {
                val loose = catalog.recommendations.filter(matches)
                if (loose.isNotEmpty()) {
                    filteredSections.add(
                        0,
                        RouteSection(id = "search", title = "搜索结果", subtitle = "", routes = loose)
                    )
                }
            }

            RoutesUiState(
                query = q,
                recommendations = if (keyword.isEmpty()) catalog.recommendations else emptyList(),
                sections = filteredSections,
                isEmpty = keyword.isNotEmpty() && filteredSections.isEmpty()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutesUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}
