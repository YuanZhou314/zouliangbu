package com.zouliangbu.footprint.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.model.FeedItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页城市栏状态。 */
sealed interface CityUiState {
    data object Loading : CityUiState
    data class Ready(val name: String) : CityUiState
    data object NoPermission : CityUiState
    data object Failed : CityUiState
}

/**
 * 首页 ViewModel：动态 feed（mock）+ 城市名（高德定位）。
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as FootprintApp

    val feed: StateFlow<List<FeedItem>> = application.feedRepository
        .observeFeed()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _city = MutableStateFlow<CityUiState>(CityUiState.Loading)
    val city: StateFlow<CityUiState> = _city.asStateFlow()

    private var cityJob: Job? = null

    /**
     * 加载城市：先展示缓存；无权限显示「开启定位」入口（不自动弹窗，
     * 保持"点开始记录才申请权限"的既有体验）；有权限则从定位流取一帧城市。
     */
    fun loadCity(force: Boolean = false) {
        if (!force && _city.value is CityUiState.Ready) return

        application.cityProvider.cachedCity()?.let {
            _city.value = CityUiState.Ready(it)
        }

        if (!application.cityProvider.hasLocationPermission()) {
            if (_city.value !is CityUiState.Ready) _city.value = CityUiState.NoPermission
            return
        }
        if (cityJob?.isActive == true) return

        cityJob = viewModelScope.launch {
            if (_city.value !is CityUiState.Ready) _city.value = CityUiState.Loading
            _city.value = application.cityProvider.fetchCity()
                ?.let { CityUiState.Ready(it) }
                ?: CityUiState.Failed
        }
    }
}
