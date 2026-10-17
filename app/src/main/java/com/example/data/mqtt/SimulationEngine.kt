package com.example.data.mqtt

import com.example.data.model.AirQualityLevel
import com.example.data.model.RpiDeviceConfig
import com.example.data.model.WeatherTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Realistic Environmental Weather Simulation Engine for Demo Mode.
 * Generates continuous 1-second telemetry with physical sensor dynamics.
 */
class SimulationEngine(
    private val scope: CoroutineScope
) {
    private val _telemetryStream = MutableSharedFlow<WeatherTelemetry>(extraBufferCapacity = 64)
    val telemetryStream: SharedFlow<WeatherTelemetry> = _telemetryStream.asSharedFlow()

    private var simulationJob: Job? = null

    // Base environmental state variables for smooth continuity
    private var baseTemp = 21.8f
    private var baseHumidity = 54.2f
    private var basePressure = 1014.2f
    private var baseAqi = 34
    private var baseWind = 2.4f
    private var currentIntervalMs = 1000L
    private var currentConfig = RpiDeviceConfig()
    private var stepCount = 0L

    fun start(intervalMs: Long = 1000L, config: RpiDeviceConfig = RpiDeviceConfig()) {
        currentIntervalMs = intervalMs.coerceAtLeast(200L)
        currentConfig = config
        stop()

        simulationJob = scope.launch {
            while (isActive) {
                stepCount++
                val telemetry = generateRealisticReading()
                _telemetryStream.emit(telemetry)
                delay(currentIntervalMs)
            }
        }
    }

    fun updateConfig(config: RpiDeviceConfig) {
        currentConfig = config
        if (config.telemetryIntervalMs != currentIntervalMs) {
            currentIntervalMs = config.telemetryIntervalMs.coerceAtLeast(200L)
            start(currentIntervalMs, config)
        }
    }

    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
    }

    val isRunning: Boolean get() = simulationJob?.isActive == true

    private fun generateRealisticReading(): WeatherTelemetry {
        // Subtle drift with micro-fluctuations (mimicking real BME280/SHT31 sensor noise)
        val timeSec = (stepCount * (currentIntervalMs / 1000.0)).toFloat()

        // Diurnal wave + random micro-noise
        val tempWave = (sin(timeSec * 0.05f) * 0.4f) + ((Random.nextFloat() - 0.5f) * 0.08f)
        val rawTemp = (baseTemp + tempWave) + currentConfig.tempCalibrationOffset

        // Humidity inversely correlates with temperature plus sensor drift
        val humWave = (-cos(timeSec * 0.05f) * 0.6f) + ((Random.nextFloat() - 0.5f) * 0.15f)
        val rawHum = (baseHumidity + humWave + currentConfig.humidityCalibrationOffset).coerceIn(10f, 99f)

        // Barometric pressure subtle wave (simulating semi-diurnal atmospheric tide)
        val pressWave = (sin(timeSec * 0.02f) * 0.3f) + ((Random.nextFloat() - 0.5f) * 0.05f)
        val rawPress = basePressure + pressWave + currentConfig.pressureCalibrationOffset

        // Wind gusts
        val windGust = if (Random.nextFloat() > 0.85f) Random.nextFloat() * 1.8f else 0.0f
        val rawWind = (baseWind + (sin(timeSec * 0.1f) * 0.5f) + windGust).coerceAtLeast(0.2f)

        val windDirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val dirIndex = (((sin(timeSec * 0.03f) + 1.0f) * 0.5f) * (windDirs.size - 1)).toInt().coerceIn(0, windDirs.size - 1)
        val windDir = windDirs[dirIndex]

        // Air Quality PM2.5 / PM10
        val aqiDrift = ((Random.nextFloat() - 0.5f) * 2.0f).toInt()
        val aqi = (baseAqi + aqiDrift).coerceIn(15, 160)
        val pm25 = (aqi * 0.35f) + (Random.nextFloat() * 1.2f)
        val pm10 = (pm25 * 1.85f) + (Random.nextFloat() * 2.5f)

        // Rain: mostly 0, occasionally slight shower simulation if humidity > 70%
        val rain = if (rawHum > 75f && Random.nextFloat() > 0.7f) Random.nextFloat() * 1.5f else 0.0f

        // Ambient Light: diurnal curve ~ 300 to 900 Lux indoor/outdoor
        val lightLux = 450f + (sin(timeSec * 0.08f) * 120f) + (Random.nextFloat() * 25f)

        // Raspberry Pi SoC CPU temperature (~40-48°C)
        val cpuTemp = 42.0f + (sin(timeSec * 0.04f) * 2.0f) + ((Random.nextFloat() - 0.5f) * 0.4f)

        return WeatherTelemetry(
            deviceId = "rpi-weather-node-01 (Demo)",
            temperature = ((rawTemp * 10).roundToInt() / 10f),
            humidity = ((rawHum * 10).roundToInt() / 10f),
            pressure = ((rawPress * 100).roundToInt() / 100f),
            airQualityIndex = aqi,
            pm25 = ((pm25 * 10).roundToInt() / 10f),
            pm10 = ((pm10 * 10).roundToInt() / 10f),
            windSpeed = ((rawWind * 10).roundToInt() / 10f),
            windDirection = windDir,
            rainfall = ((rain * 10).roundToInt() / 10f),
            lightLux = ((lightLux * 10).roundToInt() / 10f),
            cpuTemp = ((cpuTemp * 10).roundToInt() / 10f),
            timestamp = System.currentTimeMillis(),
            isSimulated = true
        )
    }
}
