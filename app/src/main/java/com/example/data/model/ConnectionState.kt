package com.example.data.model

enum class ConnectionStatus(val labelKo: String, val labelEn: String) {
    CONNECTED("연결됨", "Connected"),
    CONNECTING("연결 중...", "Connecting..."),
    RECONNECTING("재연결 중...", "Reconnecting..."),
    DISCONNECTED("연결 끊김", "Disconnected"),
    ERROR("연결 오류", "Error"),
    DEMO_MODE("데모 시뮬레이션 모드", "Demo Mode")
}

data class MqttConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DEMO_MODE,
    val latencyMs: Long = 12L,
    val packetsReceived: Long = 0L,
    val lastPacketTimestamp: Long = 0L,
    val brokerAddress: String = "broker.emqx.io:1883",
    val errorMessage: String? = null
)
