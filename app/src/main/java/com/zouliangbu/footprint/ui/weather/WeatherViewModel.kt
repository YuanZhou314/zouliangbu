package com.zouliangbu.footprint.ui.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.weather.Astronomy
import com.zouliangbu.footprint.weather.DailyForecast
import com.zouliangbu.footprint.weather.HourlyPoint
import com.zouliangbu.footprint.weather.Landscape
import com.zouliangbu.footprint.weather.LightPollutionEstimator
import com.zouliangbu.footprint.weather.QWeatherNow
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
        /** 蓝调时刻（太阳 -6°~-4°），晨 / 暮各一段，纯天文推导。 */
        val blueHour: Landscape.BlueHour,
        val cloudSea: Landscape.Forecast,
        val stargazing: Landscape.Forecast,
        /** 未来 3 天逐日预报（已跳过今天）；旧缓存无 daily 块时为空。 */
        val daily: List<DailyForecast>,
        /** 从现在起的逐小时序列（≤36 个整点），逐小时多维表用。 */
        val hourly: List<HourlyPoint>,
        /** 城市名（CityProvider 缓存），拿不到时 UI 显示「当前位置」。 */
        val locationName: String?,
        /** 和风实况原文，UI 用它出天气现象 / 风向。 */
        val now: QWeatherNow?,
        /** 当前气温（℃）：和风 temp 优先，缺失时用 Open-Meteo 逐小时预报兜底。 */
        val currentTempC: Double?,
        val currentHumidity: Double?,
        /** 当前风速（m/s）：和风给的是 km/h，已换算。 */
        val currentWindMs: Double?,
        /** 本页数据 assembled 的时刻；UI 用它算「现在」在日照弧线上的位置并显示更新时间。 */
        val updatedAtMs: Long,
        /** 两个数据源都没取到（走了缓存或离线降级），UI 据此打「离线数据」标。 */
        val usingCache: Boolean,
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: WeatherRepository = (app as FootprintApp).weatherRepository
    private val cityProvider = (app as FootprintApp).cityProvider

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
                val blue = Landscape.blueHour(st)
                val sea = Landscape.cloudSea(lat, lng, now, summitElevationM, nowElems)
                val lp = LightPollutionEstimator.estimate(lat, lng)
                val stars = Landscape.stargazing(nightMs, lat, lng, nightElems, lp)
                // 逐日 / 逐小时序列与景观要素共用同一份缓存（30min TTL），
                // 只会触发一次网络；daily 跳过 index 0（今天）取未来 3 天。
                val daily = repo.dailyForecast(lat, lng).drop(1)
                val hourly = repo.hourlyPoints(lat, lng, now, hours = 36)
                val qw = repo.nowcast(lat, lng)
                _state.value = WeatherUiState.Ready(
                    sunTimes = st,
                    morningGlow = morning,
                    eveningGlow = evening,
                    blueHour = blue,
                    cloudSea = sea,
                    stargazing = stars,
                    daily = daily,
                    hourly = hourly,
                    locationName = cityProvider.cachedCity() ?: cityProvider.cityName.value,
                    now = qw,
                    currentTempC = qw?.temp?.toDoubleOrNull() ?: nowElems?.temperatureC,
                    currentHumidity = qw?.humidity?.toDoubleOrNull() ?: nowElems?.relativeHumidity,
                    // 和风在公制单位下风速给的是 km/h，Open-Meteo 给的是 m/s，统一到 m/s。
                    currentWindMs = qw?.windSpeed?.toDoubleOrNull()?.let { it / 3.6 }
                        ?: nowElems?.windSpeedMs,
                    updatedAtMs = System.currentTimeMillis(),
                    usingCache = nowElems == null || qw == null,
                )
            } catch (e: Exception) {
                _state.value = WeatherUiState.Error(e.message ?: "未知错误")
            }
        }
    }
}
