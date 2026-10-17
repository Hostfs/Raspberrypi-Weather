package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MqttBrokerConfig
import com.example.data.model.RpiDeviceConfig
import com.example.data.model.TemperatureUnit
import java.util.Locale

@Composable
fun SettingsScreen(
    brokerConfig: MqttBrokerConfig,
    deviceConfig: RpiDeviceConfig,
    cachedCount: Int,
    onToggleDemoMode: (Boolean) -> Unit,
    onUpdateBroker: (String, Int, String) -> Unit,
    onUpdateInterval: (Long) -> Unit,
    onUpdateCalibration: (Float, Float, Float) -> Unit,
    onUpdateThresholds: (Float, Float, Int) -> Unit,
    onToggleStatusLed: (Boolean) -> Unit,
    onToggleBuzzer: (Boolean) -> Unit,
    onToggleTemperatureUnit: () -> Unit,
    onClearCache: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var brokerHost by remember(brokerConfig.host) { mutableStateOf(brokerConfig.host) }
    var brokerPortStr by remember(brokerConfig.port) { mutableStateOf(brokerConfig.port.toString()) }
    var clientId by remember(brokerConfig.clientId) { mutableStateOf(brokerConfig.clientId) }

    // Calibration state
    var tempOffset by remember(deviceConfig.tempCalibrationOffset) { mutableFloatStateOf(deviceConfig.tempCalibrationOffset) }
    var humOffset by remember(deviceConfig.humidityCalibrationOffset) { mutableFloatStateOf(deviceConfig.humidityCalibrationOffset) }
    var pressOffset by remember(deviceConfig.pressureCalibrationOffset) { mutableFloatStateOf(deviceConfig.pressureCalibrationOffset) }

    // Threshold state
    var tempLimit by remember(deviceConfig.highTempAlertThreshold) { mutableFloatStateOf(deviceConfig.highTempAlertThreshold) }
    var humLimit by remember(deviceConfig.highHumidityAlertThreshold) { mutableFloatStateOf(deviceConfig.highHumidityAlertThreshold) }
    var aqiLimit by remember(deviceConfig.highAqiAlertThreshold) { mutableIntStateOf(deviceConfig.highAqiAlertThreshold) }

    var showClearDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Section: Demo Mode vs Real Hardware MQTT
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (brokerConfig.isDemoMode) Color(0xFF0284C7).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "데모 시뮬레이션 모드 (Demo Mode)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (brokerConfig.isDemoMode)
                                    "현재 가상 센서값 생성 중 (.env 및 설정 토글 가능)"
                                else
                                    "실제 라즈베리파이 하드웨어 센서 MQTT 연동 중",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = brokerConfig.isDemoMode,
                            onCheckedChange = { onToggleDemoMode(it) },
                            modifier = Modifier.testTag("demo_mode_switch")
                        )
                    }

                    if (!brokerConfig.isDemoMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "✓ 실제 라즈베리파이 하드웨어 모드 활성화됨. 하단 브로커 설정을 확인하세요.",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
        }

        // MQTT Pipeline & Broker Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Broker",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MQTT 브로커 및 저지연 파이프라인 설정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = brokerHost,
                        onValueChange = { brokerHost = it },
                        label = { Text("MQTT 브로커 호스트 / IP") },
                        placeholder = { Text("192.168.0.50 또는 broker.emqx.io") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("broker_host_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = brokerPortStr,
                            onValueChange = { brokerPortStr = it },
                            label = { Text("포트 (Port)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("broker_port_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("클라이언트 ID") },
                            modifier = Modifier
                                .weight(2f)
                                .testTag("broker_client_id_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val port = brokerPortStr.toIntOrNull() ?: 1883
                                onUpdateBroker(brokerHost, port, clientId)
                                onReconnect()
                            },
                            modifier = Modifier.testTag("save_broker_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Save")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("설정 저장 및 브로커 연결")
                        }
                    }
                }
            }
        }

        // Bidirectional Real-time Config Sync via MQTT
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sync",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "라즈베리파이 양방향 실시간 동기화",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Transmission interval
                    Text(
                        text = "센서 데이터 전송 주기 (현재: ${deviceConfig.telemetryIntervalMs}ms)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "요구사항: 1초 간격 전송으로 센서 지연 최소화 (MQTT 동기화)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val intervals = listOf(500L, 1000L, 2000L, 5000L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        intervals.forEach { interval ->
                            val isSelected = deviceConfig.telemetryIntervalMs == interval
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateInterval(interval) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${interval / 1000.0}초",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (interval == 1000L) {
                                        Text(
                                            text = "(권장)",
                                            fontSize = 9.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Hardware Actuator Switches
                    Text(
                        text = "라즈베리파이 하드웨어 원격 제어 (MQTT rpi/weather/config)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "하드웨어 상태 표시 LED 활성화",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = deviceConfig.statusLedEnabled,
                            onCheckedChange = { onToggleStatusLed(it) },
                            modifier = Modifier.testTag("hardware_led_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "경보 부저(Buzzer) 알림 활성화",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = deviceConfig.buzzerAlertEnabled,
                            onCheckedChange = { onToggleBuzzer(it) },
                            modifier = Modifier.testTag("hardware_buzzer_switch")
                        )
                    }
                }
            }
        }

        // Sensor Calibration Offsets
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "실제 센서값 오차 보정 (Calibration Offset)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "라즈베리파이 센서와 실제 환경값의 오차를 보정합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Temperature Offset Slider
                    Text(
                        text = "온도 보정: ${String.format(Locale.US, "%+.1f°C", tempOffset)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = tempOffset,
                        onValueChange = {
                            tempOffset = it
                            onUpdateCalibration(tempOffset, humOffset, pressOffset)
                        },
                        valueRange = -5.0f..5.0f,
                        steps = 20,
                        modifier = Modifier.testTag("temp_offset_slider")
                    )

                    // Humidity Offset Slider
                    Text(
                        text = "습도 보정: ${String.format(Locale.US, "%+.1f%%", humOffset)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = humOffset,
                        onValueChange = {
                            humOffset = it
                            onUpdateCalibration(tempOffset, humOffset, pressOffset)
                        },
                        valueRange = -10.0f..10.0f,
                        steps = 20,
                        modifier = Modifier.testTag("hum_offset_slider")
                    )

                    // Pressure Offset Slider
                    Text(
                        text = "기압 보정: ${String.format(Locale.US, "%+.1f hPa", pressOffset)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = pressOffset,
                        onValueChange = {
                            pressOffset = it
                            onUpdateCalibration(tempOffset, humOffset, pressOffset)
                        },
                        valueRange = -20.0f..20.0f,
                        steps = 20,
                        modifier = Modifier.testTag("press_offset_slider")
                    )
                }
            }
        }

        // Offline Room Database Caching & Data Management
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cached,
                            contentDescription = "Database",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "오프라인 로컬 캐싱 (Room Database)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "오프라인 또는 통신 두절 시에도 데이터가 유실되지 않도록 디바이스 로컬 SQLite에 센서 텔레메트리를 안전하게 영구 캐싱합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "현재 저장된 센서 레코드",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$cachedCount 개 데이터",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            ),
                            modifier = Modifier.testTag("clear_cache_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("캐시 비우기")
                        }
                    }
                }
            }
        }

        // Unit system toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "온도 표시 단위: ${deviceConfig.temperatureUnit.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onToggleTemperatureUnit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(if (deviceConfig.temperatureUnit == TemperatureUnit.CELSIUS) "화씨(°F)로 변경" else "섭씨(°C)로 변경")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("로컬 캐시 삭제") },
            text = { Text("저장된 모든 센서 히스토리 기록을 삭제하시겠습니까? 실시간 수신은 계속 진행됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearCache()
                        showClearDialog = false
                    }
                ) {
                    Text("삭제", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}
