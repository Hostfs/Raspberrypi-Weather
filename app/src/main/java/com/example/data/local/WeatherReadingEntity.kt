package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.WeatherTelemetry

@Entity(
    tableName = "weather_readings",
    indices = [Index(value = ["timestamp"])]
)
data class WeatherReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float,
    val pressure: Float,
    val airQualityIndex: Int,
    val pm25: Float,
    val pm10: Float,
    val windSpeed: Float,
    val windDirection: String,
    val rainfall: Float,
    val lightLux: Float,
    val cpuTemp: Float,
    val isSimulated: Boolean
) {
    fun toTelemetry(): WeatherTelemetry = WeatherTelemetry(
        deviceId = deviceId,
        temperature = temperature,
        humidity = humidity,
        pressure = pressure,
        airQualityIndex = airQualityIndex,
        pm25 = pm25,
        pm10 = pm10,
        windSpeed = windSpeed,
        windDirection = windDirection,
        rainfall = rainfall,
        lightLux = lightLux,
        cpuTemp = cpuTemp,
        timestamp = timestamp,
        isSimulated = isSimulated
    )

    companion object {
        fun fromTelemetry(t: WeatherTelemetry): WeatherReadingEntity = WeatherReadingEntity(
            deviceId = t.deviceId,
            timestamp = t.timestamp,
            temperature = t.temperature,
            humidity = t.humidity,
            pressure = t.pressure,
            airQualityIndex = t.airQualityIndex,
            pm25 = t.pm25,
            pm10 = t.pm10,
            windSpeed = t.windSpeed,
            windDirection = t.windDirection,
            rainfall = t.rainfall,
            lightLux = t.lightLux,
            cpuTemp = t.cpuTemp,
            isSimulated = t.isSimulated
        )
    }
}
