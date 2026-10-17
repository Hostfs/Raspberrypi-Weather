package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ConnectionStatus
import com.example.data.model.MqttBrokerConfig
import com.example.data.model.MqttConnectionState
import com.example.data.model.RpiDeviceConfig
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherTelemetry
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MetricType(val titleKo: String, val titleEn: String, val unit: String) {
    TEMPERATURE("온도", "Temperature", "°C"),
    HUMIDITY("습도", "Humidity", "%"),
    PRESSURE("기압", "Pressure", "hPa"),
    AIR_QUALITY("공기질", "Air Quality", "AQI"),
    WIND("풍속", "Wind Speed", "m/s"),
    CPU_TEMP("라즈베리파이 CPU", "RPi SoC Temp", "°C")
}

enum class TimeWindow(val labelKo: String, val seconds: Long) {
    MIN_10("최근 10분", 600L),
    HOUR_1("최근 1시간", 3600L),
    HOUR_6("최근 6시간", 21600L),
    ALL("전체 기록", Long.MAX_VALUE)
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WeatherRepository.getInstance(application)

    val currentTelemetry: StateFlow<WeatherTelemetry?> = repository.currentTelemetry
    val connectionState: StateFlow<MqttConnectionState> = repository.connectionState
    val deviceConfig: StateFlow<RpiDeviceConfig> = repository.deviceConfig
    val brokerConfig: StateFlow<MqttBrokerConfig> = repository.brokerConfig

    val history: StateFlow<List<WeatherTelemetry>> = repository.recentHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val cachedCount: StateFlow<Int> = repository.readingCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _selectedMetric = MutableStateFlow(MetricType.TEMPERATURE)
    val selectedMetric: StateFlow<MetricType> = _selectedMetric.asStateFlow()

    private val _selectedTimeWindow = MutableStateFlow(TimeWindow.MIN_10)
    val selectedTimeWindow: StateFlow<TimeWindow> = _selectedTimeWindow.asStateFlow()

    fun selectMetric(metric: MetricType) {
        _selectedMetric.value = metric
    }

    fun selectTimeWindow(window: TimeWindow) {
        _selectedTimeWindow.value = window
    }

    fun setDemoMode(enabled: Boolean) {
        repository.setDemoMode(enabled)
    }

    fun updateTelemetryInterval(intervalMs: Long) {
        val current = deviceConfig.value
        repository.updateDeviceConfig(current.copy(telemetryIntervalMs = intervalMs))
    }

    fun updateCalibration(tempOffset: Float, humOffset: Float, pressOffset: Float) {
        val current = deviceConfig.value
        repository.updateDeviceConfig(
            current.copy(
                tempCalibrationOffset = tempOffset,
                humidityCalibrationOffset = humOffset,
                pressureCalibrationOffset = pressOffset
            )
        )
    }

    fun updateThresholds(tempLimit: Float, humLimit: Float, aqiLimit: Int) {
        val current = deviceConfig.value
        repository.updateDeviceConfig(
            current.copy(
                highTempAlertThreshold = tempLimit,
                highHumidityAlertThreshold = humLimit,
                highAqiAlertThreshold = aqiLimit
            )
        )
    }

    fun toggleStatusLed(enabled: Boolean) {
        val current = deviceConfig.value
        repository.updateDeviceConfig(current.copy(statusLedEnabled = enabled))
    }

    fun toggleBuzzer(enabled: Boolean) {
        val current = deviceConfig.value
        repository.updateDeviceConfig(current.copy(buzzerAlertEnabled = enabled))
    }

    fun toggleTemperatureUnit() {
        val current = deviceConfig.value
        val newUnit = if (current.temperatureUnit == TemperatureUnit.CELSIUS) {
            TemperatureUnit.FAHRENHEIT
        } else {
            TemperatureUnit.CELSIUS
        }
        repository.updateDeviceConfig(current.copy(temperatureUnit = newUnit))
    }

    fun updateBroker(host: String, port: Int, client: String) {
        val current = brokerConfig.value
        repository.updateBrokerConfig(
            current.copy(
                host = host.trim(),
                port = port,
                clientId = client.trim()
            )
        )
    }

    fun reconnect() {
        repository.reconnect()
    }

    fun clearCachedData() {
        repository.clearCachedData()
    }
}
