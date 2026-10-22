package com.zouliangbu.footprint.ui.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.weather.Astronomy
import com.zouliangbu.footprint.weather.Landscape
import com.zouliangbu.footprint.weather.LightPollutionEstimator
import com.zouliangbu.footprint.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Ready(
        val sunTimes: Astronomy.SunTimes,
        val morningGlow: Landscape.Forecast,
        val eveningGlow: Landscape.Forecast,
        val cloudSea: Landscape.Forecast,
        val stargazing: Landscape.Forecast,
        val nowcastText: String?,
        val usingCache: Boolean,
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: WeatherRepository = (app as FootprintApp).weatherRepository

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    fun load(lat: Double, lng: Double, summitElevationM: Double = 1000.0) {
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            try {
                val now = System.currentTimeMillis()
                val st = Astronomy.sunTimes(now, lat, lng)
                val dawnElems = repo.elementsAt(lat, lng, now - 90 * 60_000)
                val duskElems = repo.elementsAt(lat, lng, now - 30 * 60_000)
                val nowElems = repo.elementsAt(lat, lng, now)
                val dayStartMs = (now / 86_400_000L) * 86_400_000L
                val nightMs = st.nightStart?.let { dayStartMs + it.toLong() * 60_000L } ?: (now + 4 * 3600_000)
                val nightElems = repo.elementsAt(lat, lng, nightMs)
                val morning = Landscape.twilightGlow(sunrise = true, lat, lng, now, dawnElems)
                val evening = Landscape.twilightGlow(sunrise = false, lat, lng, now, duskElems)
                val sea = Landscape.cloudSea(lat, lng, now, summitElevationM, nowElems)
                val lp = LightPollutionEstimator.estimate(lat, lng)
                val stars = Landscape.stargazing(nightMs, lat, lng, nightElems, lp)
                val nowText = repo.nowcastText(lat, lng)
                _state.value = WeatherUiState.Ready(st, morning, evening, sea, stars, nowText, false)
            } catch (e: Exception) {
                _state.value = WeatherUiState.Error(e.message ?: "未知错误")
            }
        }
    }
}