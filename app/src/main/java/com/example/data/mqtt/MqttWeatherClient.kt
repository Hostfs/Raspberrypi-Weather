package com.example.data.mqtt

import android.util.Log
import com.example.data.model.ConnectionStatus
import com.example.data.model.MqttBrokerConfig
import com.example.data.model.MqttConnectionState
import com.example.data.model.RpiDeviceConfig
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Low-latency MQTT Client for Raspberry Pi Weather Telemetry Pipeline.
 * Subscribes to real-time 1s sensor data and syncs bidirectional configurations.
 */
class MqttWeatherClient(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MqttWeatherClient"
    }

    private var mqttClient: MqttAsyncClient? = null
    private var currentBrokerConfig = MqttBrokerConfig()

    private val _connectionState = MutableStateFlow(MqttConnectionState())
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private val _incomingTelemetry = MutableSharedFlow<WeatherTelemetry>(extraBufferCapacity = 64)
    val incomingTelemetry: SharedFlow<WeatherTelemetry> = _incomingTelemetry.asSharedFlow()

    private val _incomingConfig = MutableSharedFlow<RpiDeviceConfig>(extraBufferCapacity = 16)
    val incomingConfig: SharedFlow<RpiDeviceConfig> = _incomingConfig.asSharedFlow()

    private var packetCounter = 0L

    fun connect(brokerConfig: MqttBrokerConfig) {
        currentBrokerConfig = brokerConfig
        disconnectInternal()

        val serverUri = if (brokerConfig.host.startsWith("tcp://") || brokerConfig.host.startsWith("ssl://")) {
            "${brokerConfig.host}:${brokerConfig.port}"
        } else {
            "tcp://${brokerConfig.host}:${brokerConfig.port}"
        }

        _connectionState.value = _connectionState.value.copy(
            status = ConnectionStatus.CONNECTING,
            brokerAddress = "${brokerConfig.host}:${brokerConfig.port}",
            errorMessage = null
        )

        try {
            val clientId = "${brokerConfig.clientId}_${System.currentTimeMillis() % 10000}"
            mqttClient = MqttAsyncClient(serverUri, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 8
                keepAliveInterval = 20
                // Low latency TCP settings
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.i(TAG, "MQTT Connected to $serverURI (reconnect: $reconnect)")
                    _connectionState.value = _connectionState.value.copy(
                        status = ConnectionStatus.CONNECTED,
                        errorMessage = null
                    )
                    subscribeToTopics()
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT Connection lost: ${cause?.message}")
                    _connectionState.value = _connectionState.value.copy(
                        status = ConnectionStatus.RECONNECTING,
                        errorMessage = cause?.localizedMessage ?: "연결 끊김"
                    )
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == null || message == null) return
                    val payload = String(message.payload, StandardCharsets.UTF_8)
                    handleIncomingMessage(topic, payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Delivery confirmed
                }
            })

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Connection initiation successful")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Connection failed: ${exception?.message}", exception)
                    _connectionState.value = _connectionState.value.copy(
                        status = ConnectionStatus.ERROR,
                        errorMessage = exception?.localizedMessage ?: "브로커 연결 실패"
                    )
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MQTT Init error", e)
            _connectionState.value = _connectionState.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun subscribeToTopics() {
        val client = mqttClient ?: return
        if (!client.isConnected) return

        try {
            val topics = arrayOf(
                currentBrokerConfig.telemetryTopic,
                currentBrokerConfig.configTopic,
                currentBrokerConfig.statusTopic
            )
            val qos = intArrayOf(0, 1, 1) // QoS 0 for high-frequency 1s telemetry, QoS 1 for config

            client.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Subscribed successfully to telemetry & config topics")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to subscribe to topics", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Subscription error", e)
        }
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        scope.launch(Dispatchers.Default) {
            try {
                when (topic) {
                    currentBrokerConfig.telemetryTopic -> {
                        val telemetry = parseTelemetryJson(payload)
                        if (telemetry != null) {
                            packetCounter++
                            val now = System.currentTimeMillis()
                            val latency = (now - telemetry.timestamp).coerceAtLeast(0L).coerceAtMost(5000L)

                            _connectionState.value = _connectionState.value.copy(
                                packetsReceived = packetCounter,
                                latencyMs = if (telemetry.timestamp > 0) latency else 8L,
                                lastPacketTimestamp = now
                            )
                            _incomingTelemetry.emit(telemetry)
                        }
                    }
                    currentBrokerConfig.configTopic -> {
                        val config = parseConfigJson(payload)
                        if (config != null) {
                            _incomingConfig.emit(config)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message on $topic: $payload", e)
            }
        }
    }

    fun publishConfig(config: RpiDeviceConfig) {
        val client = mqttClient
        if (client == null || !client.isConnected) {
            Log.w(TAG, "Cannot publish config: MQTT client not connected")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("telemetryIntervalMs", config.telemetryIntervalMs)
                    put("tempCalibrationOffset", config.tempCalibrationOffset.toDouble())
                    put("humidityCalibrationOffset", config.humidityCalibrationOffset.toDouble())
                    put("pressureCalibrationOffset", config.pressureCalibrationOffset.toDouble())
                    put("highTempAlertThreshold", config.highTempAlertThreshold.toDouble())
                    put("highHumidityAlertThreshold", config.highHumidityAlertThreshold.toDouble())
                    put("lowPressureWarningThreshold", config.lowPressureWarningThreshold.toDouble())
                    put("highAqiAlertThreshold", config.highAqiAlertThreshold)
                    put("statusLedEnabled", config.statusLedEnabled)
                    put("buzzerAlertEnabled", config.buzzerAlertEnabled)
                    put("timestamp", System.currentTimeMillis())
                }

                val message = MqttMessage(json.toString().toByteArray(StandardCharsets.UTF_8)).apply {
                    qos = 1
                    isRetained = true // Retain config so RPi receives it on boot
                }

                client.publish(currentBrokerConfig.configTopic, message)
                Log.i(TAG, "Published config update to ${currentBrokerConfig.configTopic}")
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing config", e)
            }
        }
    }

    fun disconnect() {
        disconnectInternal()
        _connectionState.value = _connectionState.value.copy(
            status = ConnectionStatus.DISCONNECTED
        )
    }

    private fun disconnectInternal() {
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            mqttClient?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Disconnect exception: ${e.message}")
        } finally {
            mqttClient = null
        }
    }

    private fun parseTelemetryJson(jsonStr: String): WeatherTelemetry? {
        return try {
            val obj = JSONObject(jsonStr)
            val devId = obj.optString("deviceId", obj.optString("device_id", "rpi-weather-node-01"))
            val temp = obj.optDouble("temperature", obj.optDouble("temp", 22.0)).toFloat()
            val hum = obj.optDouble("humidity", obj.optDouble("hum", 50.0)).toFloat()
            val press = obj.optDouble("pressure", obj.optDouble("press", 1013.25)).toFloat()
            val aqi = obj.optInt("airQualityIndex", obj.optInt("aqi", 35))
            val pm25 = obj.optDouble("pm25", obj.optDouble("pm2_5", 10.0)).toFloat()
            val pm10 = obj.optDouble("pm10", 20.0).toFloat()
            val wind = obj.optDouble("windSpeed", obj.optDouble("wind", 2.0)).toFloat()
            val windDir = obj.optString("windDirection", obj.optString("direction", "NW"))
            val rain = obj.optDouble("rainfall", obj.optDouble("rain", 0.0)).toFloat()
            val light = obj.optDouble("lightLux", obj.optDouble("lux", 500.0)).toFloat()
            val cpu = obj.optDouble("cpuTemp", obj.optDouble("cpu_temp", 42.0)).toFloat()
            val ts = obj.optLong("timestamp", System.currentTimeMillis())

            WeatherTelemetry(
                deviceId = devId,
                temperature = temp,
                humidity = hum,
                pressure = press,
                airQualityIndex = aqi,
                pm25 = pm25,
                pm10 = pm10,
                windSpeed = wind,
                windDirection = windDir,
                rainfall = rain,
                lightLux = light,
                cpuTemp = cpu,
                timestamp = ts,
                isSimulated = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse telemetry JSON: $jsonStr", e)
            null
        }
    }

    private fun parseConfigJson(jsonStr: String): RpiDeviceConfig? {
        return try {
            val obj = JSONObject(jsonStr)
            RpiDeviceConfig(
                telemetryIntervalMs = obj.optLong("telemetryIntervalMs", 1000L),
                tempCalibrationOffset = obj.optDouble("tempCalibrationOffset", 0.0).toFloat(),
                humidityCalibrationOffset = obj.optDouble("humidityCalibrationOffset", 0.0).toFloat(),
                pressureCalibrationOffset = obj.optDouble("pressureCalibrationOffset", 0.0).toFloat(),
                highTempAlertThreshold = obj.optDouble("highTempAlertThreshold", 32.0).toFloat(),
                highHumidityAlertThreshold = obj.optDouble("highHumidityAlertThreshold", 75.0).toFloat(),
                lowPressureWarningThreshold = obj.optDouble("lowPressureWarningThreshold", 1000.0).toFloat(),
                highAqiAlertThreshold = obj.optInt("highAqiAlertThreshold", 100),
                statusLedEnabled = obj.optBoolean("statusLedEnabled", true),
                buzzerAlertEnabled = obj.optBoolean("buzzerAlertEnabled", false),
                temperatureUnit = TemperatureUnit.CELSIUS,
                lastSyncTimestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config JSON: $jsonStr", e)
            null
        }
    }
}
