package com.example.data.model

import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class WeatherTelemetry(
    val deviceId: String = "rpi-weather-node-01",
    val temperature: Float = 22.5f,
    val humidity: Float = 55.0f,
    val pressure: Float = 1013.25f,
    val airQualityIndex: Int = 38,
    val pm25: Float = 11.2f,
    val pm10: Float = 21.0f,
    val windSpeed: Float = 2.1f,
    val windDirection: String = "NW",
    val rainfall: Float = 0.0f,
    val lightLux: Float = 520.0f,
    val cpuTemp: Float = 42.5f,
    val timestamp: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false
) {
    /**
     * Calculate Dew Point using Magnus-Tetens approximation formula
     */
    val dewPoint: Float
        get() {
            val a = 17.27f
            val b = 237.7f
            val alpha = ((a * temperature) / (b + temperature)) + ln((humidity.coerceIn(1f, 100f) / 100.0).toFloat())
            return (b * alpha) / (a - alpha)
        }

    /**
     * Calculate Heat Index (Feels like temperature in °C)
     */
    val feelsLike: Float
        get() {
            if (temperature < 20f) return temperature
            // Simplified Steadman / Rothfusz formula for Celsius
            val t = temperature
            val r = humidity
            val hi = -8.78469475556 + 1.61139411 * t + 2.33854883889 * r -
                    0.14611605 * t * r - 0.012308094 * (t * t) -
                    0.0164248277778 * (r * r) + 0.002211732 * (t * t) * r +
                    0.00072546 * t * (r * r) - 0.000003582 * (t * t) * (r * r)
            return hi.toFloat().coerceAtLeast(temperature - 2f)
        }

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val formattedDateTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val airQualityCategory: AirQualityLevel
        get() = when {
            airQualityIndex <= 50 -> AirQualityLevel.GOOD
            airQualityIndex <= 100 -> AirQualityLevel.MODERATE
            airQualityIndex <= 150 -> AirQualityLevel.UNHEALTHY_SENSITIVE
            airQualityIndex <= 200 -> AirQualityLevel.UNHEALTHY
            else -> AirQualityLevel.VERY_UNHEALTHY
        }
}

enum class AirQualityLevel(val labelKo: String, val labelEn: String, val colorHex: Long) {
    GOOD("좋음", "Good", 0xFF10B981),
    MODERATE("보통", "Moderate", 0xFFF59E0B),
    UNHEALTHY_SENSITIVE("민감군주의", "Sensitive Warning", 0xFFF97316),
    UNHEALTHY("나쁨", "Unhealthy", 0xFFEF4444),
    VERY_UNHEALTHY("매우나쁨", "Hazardous", 0xFF9333EA)
}
