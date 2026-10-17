package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.WeatherDao
import com.example.data.local.WeatherReadingEntity
import com.example.data.model.ConnectionStatus
import com.example.data.model.MqttBrokerConfig
import com.example.data.model.MqttConnectionState
import com.example.data.model.RpiDeviceConfig
import com.example.data.model.WeatherTelemetry
import com.example.data.mqtt.MqttWeatherClient
import com.example.data.mqtt.SimulationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WeatherRepository private constructor(
    context: Context,
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "WeatherRepository"

        @Volatile
        private var INSTANCE: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WeatherRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun parseEnvDemoMode(): Boolean {
            return try {
                // Read from BuildConfig injected via .env / Secrets Gradle plugin
                val demoField = BuildConfig::class.java.getField("DEMO_MODE")
                val value = demoField.get(null)?.toString()
                value?.equals("true", ignoreCase = true) ?: true
            } catch (e: Exception) {
                // If not present in BuildConfig, default to demo mode true as requested in user prompt
                true
            }
        }

        private fun parseEnvBrokerHost(): String {
            return try {
                val hostField = BuildConfig::class.java.getField("MQTT_BROKER_HOST")
                hostField.get(null)?.toString() ?: "broker.emqx.io"
            } catch (e: Exception) {
                "broker.emqx.io"
            }
        }

        private fun parseEnvBrokerPort(): Int {
            return try {
                val portField = BuildConfig::class.java.getField("MQTT_BROKER_PORT")
                portField.get(null)?.toString()?.toIntOrNull() ?: 1883
            } catch (e: Exception) {
                1883
            }
        }
    }

    private val weatherDao: WeatherDao = AppDatabase.getDatabase(context).weatherDao()
    private val mqttClient = MqttWeatherClient(appScope)
    private val simulationEngine = SimulationEngine(appScope)

    private val initialDemoMode = parseEnvDemoMode()
    private val initialHost = parseEnvBrokerHost()
    private val initialPort = parseEnvBrokerPort()

    private val _brokerConfig = MutableStateFlow(
        MqttBrokerConfig(
            host = initialHost,
            port = initialPort,
            isDemoMode = initialDemoMode
        )
    )
    val brokerConfig: StateFlow<MqttBrokerConfig> = _brokerConfig.asStateFlow()

    private val _deviceConfig = MutableStateFlow(RpiDeviceConfig())
    val deviceConfig: StateFlow<RpiDeviceConfig> = _deviceConfig.asStateFlow()

    private val _currentTelemetry = MutableStateFlow<WeatherTelemetry?>(null)
    val currentTelemetry: StateFlow<WeatherTelemetry?> = _currentTelemetry.asStateFlow()

    private val _connectionState = MutableStateFlow(
        MqttConnectionState(
            status = if (initialDemoMode) ConnectionStatus.DEMO_MODE else ConnectionStatus.CONNECTING,
            brokerAddress = "$initialHost:$initialPort"
        )
    )
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    val recentHistory: Flow<List<WeatherTelemetry>> = weatherDao.getRecentReadings(limit = 120)
        .map { list -> list.map { it.toTelemetry() } }

    val readingCount: Flow<Int> = weatherDao.getReadingCount()

    init {
        // Collect telemetry from Simulation Engine
        appScope.launch {
            simulationEngine.telemetryStream.collect { telemetry ->
                if (_brokerConfig.value.isDemoMode) {
                    processIncomingReading(telemetry)
                }
            }
        }

        // Collect telemetry from MQTT Client
        appScope.launch {
            mqttClient.incomingTelemetry.collect { telemetry ->
                if (!_brokerConfig.value.isDemoMode) {
                    processIncomingReading(telemetry)
                }
            }
        }

        // Collect config sync from Raspberry Pi
        appScope.launch {
            mqttClient.incomingConfig.collect { configFromRpi ->
                _deviceConfig.value = configFromRpi
            }
        }

        // Mirror MQTT connection state when in live mode
        appScope.launch {
            mqttClient.connectionState.collect { state ->
                if (!_brokerConfig.value.isDemoMode) {
                    _connectionState.value = state
                }
            }
        }

        // Start mode according to initial configuration
        if (initialDemoMode) {
            startDemoMode()
        } else {
            startLiveMqtt()
        }

        // Load most recent cached reading from Room as initial state if available
        appScope.launch(Dispatchers.IO) {
            weatherDao.getLatestReading().collect { cached ->
                if (_currentTelemetry.value == null && cached != null) {
                    _currentTelemetry.value = cached.toTelemetry()
                }
            }
        }
    }

    private suspend fun processIncomingReading(telemetry: WeatherTelemetry) {
        _currentTelemetry.value = telemetry

        // Persist to Room local SQLite database for offline retention
        try {
            weatherDao.insertReading(WeatherReadingEntity.fromTelemetry(telemetry))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache reading into Room", e)
        }
    }

    fun setDemoMode(enabled: Boolean) {
        if (_brokerConfig.value.isDemoMode == enabled) return
        _brokerConfig.value = _brokerConfig.value.copy(isDemoMode = enabled)

        if (enabled) {
            startDemoMode()
        } else {
            startLiveMqtt()
        }
    }

    private fun startDemoMode() {
        mqttClient.disconnect()
        _connectionState.value = MqttConnectionState(
            status = ConnectionStatus.DEMO_MODE,
            brokerAddress = "${_brokerConfig.value.host}:${_brokerConfig.value.port}",
            latencyMs = 12L
        )
        simulationEngine.start(_deviceConfig.value.telemetryIntervalMs, _deviceConfig.value)
    }

    private fun startLiveMqtt() {
        simulationEngine.stop()
        _connectionState.value = MqttConnectionState(
            status = ConnectionStatus.CONNECTING,
            brokerAddress = "${_brokerConfig.value.host}:${_brokerConfig.value.port}"
        )
        mqttClient.connect(_brokerConfig.value)
    }

    fun updateBrokerConfig(newConfig: MqttBrokerConfig) {
        _brokerConfig.value = newConfig
        if (!newConfig.isDemoMode) {
            mqttClient.connect(newConfig)
        }
    }

    fun reconnect() {
        if (!_brokerConfig.value.isDemoMode) {
            mqttClient.connect(_brokerConfig.value)
        } else {
            simulationEngine.start(_deviceConfig.value.telemetryIntervalMs, _deviceConfig.value)
        }
    }

    fun updateDeviceConfig(newConfig: RpiDeviceConfig) {
        _deviceConfig.value = newConfig
        if (_brokerConfig.value.isDemoMode) {
            simulationEngine.updateConfig(newConfig)
        } else {
            mqttClient.publishConfig(newConfig)
        }
    }

    fun clearCachedData() {
        appScope.launch(Dispatchers.IO) {
            weatherDao.clearAll()
        }
    }
}
