package com.zouliangbu.footprint.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.data.model.FeedDetail
import com.zouliangbu.footprint.data.remote.ContentApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 动态详情页 UI 状态。 */
sealed interface FeedDetailUiState {
    data object Loading : FeedDetailUiState
    data class Ready(val detail: FeedDetail) : FeedDetailUiState
    /** 网络 404 / 接口不存在 / 异常：内容不存在或已下线。 */
    data object NotFound : FeedDetailUiState
}

class FeedDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<FeedDetailUiState>(FeedDetailUiState.Loading)
    val state: StateFlow<FeedDetailUiState> = _state

    fun load(feedId: String) {
        _state.value = FeedDetailUiState.Loading
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                runCatching {
                    val resp = ContentApiFactory.service.getFeedDetail(feedId)
                    if (resp.ok) resp.data else null
                }.getOrNull()
            }
            _state.value = detail?.let { FeedDetailUiState.Ready(it) } ?: FeedDetailUiState.NotFound
        }
    }
}
