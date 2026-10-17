package com.example.data.model

import com.squareup.moshi.JsonClass

enum class TemperatureUnit(val symbol: String, val label: String) {
    CELSIUS("°C", "섭씨 (°C)"),
    FAHRENHEIT("°F", "화씨 (°F)")
}

@JsonClass(generateAdapter = true)
data class RpiDeviceConfig(
    val telemetryIntervalMs: Long = 1000L, // 1초 전송 기본값
    val tempCalibrationOffset: Float = 0.0f,
    val humidityCalibrationOffset: Float = 0.0f,
    val pressureCalibrationOffset: Float = 0.0f,
    val highTempAlertThreshold: Float = 32.0f,
    val highHumidityAlertThreshold: Float = 75.0f,
    val lowPressureWarningThreshold: Float = 1000.0f,
    val highAqiAlertThreshold: Int = 100,
    val statusLedEnabled: Boolean = true,
    val buzzerAlertEnabled: Boolean = false,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

data class MqttBrokerConfig(
    val host: String = "broker.emqx.io",
    val port: Int = 1883,
    val clientId: String = "piweather_android",
    val telemetryTopic: String = "rpi/weather/telemetry",
    val configTopic: String = "rpi/weather/config",
    val statusTopic: String = "rpi/weather/status",
    val isDemoMode: Boolean = true
)
