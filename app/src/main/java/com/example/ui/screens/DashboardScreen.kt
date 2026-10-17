package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MqttBrokerConfig
import com.example.data.model.MqttConnectionState
import com.example.data.model.RpiDeviceConfig
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherTelemetry
import com.example.ui.components.ConnectionStatusBanner
import com.example.ui.components.MiniSparkline
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    currentTelemetry: WeatherTelemetry?,
    history: List<WeatherTelemetry>,
    connectionState: MqttConnectionState,
    deviceConfig: RpiDeviceConfig,
    brokerConfig: MqttBrokerConfig,
    isTablet: Boolean,
    onReconnect: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCharts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = currentTelemetry ?: WeatherTelemetry()

    // Recent historical values for mini sparklines
    val tempHistory = remember(history) { history.takeLast(25).map { it.temperature } }
    val humHistory = remember(history) { history.takeLast(25).map { it.humidity } }
    val pressHistory = remember(history) { history.takeLast(25).map { it.pressure } }
    val aqiHistory = remember(history) { history.takeLast(25).map { it.airQualityIndex.toFloat() } }

    val displayTemp = if (deviceConfig.temperatureUnit == TemperatureUnit.FAHRENHEIT) {
        (telemetry.temperature * 9f / 5f) + 32f
    } else {
        telemetry.temperature
    }

    val displayFeelsLike = if (deviceConfig.temperatureUnit == TemperatureUnit.FAHRENHEIT) {
        (telemetry.feelsLike * 9f / 5f) + 32f
    } else {
        telemetry.feelsLike
    }

    val unitSymbol = deviceConfig.temperatureUnit.symbol

    // Alert checks
    val isTempAlert = telemetry.temperature >= deviceConfig.highTempAlertThreshold
    val isHumAlert = telemetry.humidity >= deviceConfig.highHumidityAlertThreshold
    val isAqiAlert = telemetry.airQualityIndex >= deviceConfig.highAqiAlertThreshold

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Status Banner
        item {
            ConnectionStatusBanner(
                connectionState = connectionState,
                isDemoMode = brokerConfig.isDemoMode,
                onReconnectClick = onReconnect,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        // Active Alert Warning Banner
        if (isTempAlert || isHumAlert || isAqiAlert) {
            item {
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "⚠️ 실시간 환경 임계치 경보",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.labelLarge
                            )
                            val alertMsg = buildList {
                                if (isTempAlert) add("온도 ${telemetry.temperature}°C (기준 ${deviceConfig.highTempAlertThreshold}°C 초과)")
                                if (isHumAlert) add("습도 ${telemetry.humidity}% (기준 ${deviceConfig.highHumidityAlertThreshold}% 초과)")
                                if (isAqiAlert) add("공기질 AQI ${telemetry.airQualityIndex} (기준 ${deviceConfig.highAqiAlertThreshold} 초과)")
                            }.joinToString(", ")
                            Text(
                                text = alertMsg,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Hero Weather Overview Card
        item {
            HeroWeatherCard(
                telemetry = telemetry,
                displayTemp = displayTemp,
                displayFeelsLike = displayFeelsLike,
                unitSymbol = unitSymbol,
                onCardClick = onNavigateToCharts
            )
        }

        // Primary Sensor Telemetry Grid (Adaptive FlowRow)
        item {
            Text(
                text = "실시간 센서 텔레메트리 (1초 전송)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            val columns = if (isTablet) 3 else 2
            val cardModifier = Modifier.fillMaxWidth()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorMetricCard(
                        title = "온도",
                        value = String.format(Locale.US, "%.1f %s", displayTemp, unitSymbol),
                        subtext = "체감: ${String.format(Locale.US, "%.1f %s", displayFeelsLike, unitSymbol)}",
                        icon = Icons.Default.Thermostat,
                        accentColor = Color(0xFFF97316),
                        sparklineValues = tempHistory,
                        modifier = Modifier.weight(1f)
                    )
                    SensorMetricCard(
                        title = "상대 습도",
                        value = String.format(Locale.US, "%.1f %%", telemetry.humidity),
                        subtext = "이슬점: ${String.format(Locale.US, "%.1f°C", telemetry.dewPoint)}",
                        icon = Icons.Default.WaterDrop,
                        accentColor = Color(0xFF0284C7),
                        sparklineValues = humHistory,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorMetricCard(
                        title = "대기압",
                        value = String.format(Locale.US, "%.1f hPa", telemetry.pressure),
                        subtext = if (telemetry.pressure > 1013.25f) "고기압 (맑음 예상)" else "저기압 (흐림 가능)",
                        icon = Icons.Default.Speed,
                        accentColor = Color(0xFF8B5CF6),
                        sparklineValues = pressHistory,
                        modifier = Modifier.weight(1f)
                    )
                    SensorMetricCard(
                        title = "미세먼지 AQI",
                        value = "${telemetry.airQualityIndex} AQI",
                        subtext = "PM2.5: ${telemetry.pm25} ㎍/㎥ (${telemetry.airQualityCategory.labelKo})",
                        icon = Icons.Default.Air,
                        accentColor = Color(telemetry.airQualityCategory.colorHex),
                        sparklineValues = aqiHistory,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorMetricCard(
                        title = "풍속 및 풍향",
                        value = String.format(Locale.US, "%.1f m/s", telemetry.windSpeed),
                        subtext = "풍향: ${telemetry.windDirection}",
                        icon = Icons.Default.Navigation,
                        accentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    SensorMetricCard(
                        title = "조도 센서",
                        value = String.format(Locale.US, "%.0f Lux", telemetry.lightLux),
                        subtext = if (telemetry.lightLux > 400) "주간/밝음" else "야간/실내",
                        icon = Icons.Default.WbSunny,
                        accentColor = Color(0xFFEAB308),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Raspberry Pi Hardware Status Card
        item {
            RpiHardwareCard(
                telemetry = telemetry,
                deviceConfig = deviceConfig
            )
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HeroWeatherCard(
    telemetry: WeatherTelemetry,
    displayTemp: Float,
    displayFeelsLike: Float,
    unitSymbol: String,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onCardClick() }
            .testTag("hero_weather_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Optional banner art header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Raspberry Pi 기상관측소",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "노드 ID: ${telemetry.deviceId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = Color(telemetry.airQualityCategory.colorHex).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "공기 ${telemetry.airQualityCategory.labelKo}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color(telemetry.airQualityCategory.colorHex),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.1f", displayTemp),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = unitSymbol,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                            )
                        }

                        Text(
                            text = "체감 ${String.format(Locale.US, "%.1f %s", displayFeelsLike, unitSymbol)} · 이슬점 ${String.format(Locale.US, "%.1f°C", telemetry.dewPoint)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Weather Status Badge
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                telemetry.rainfall > 0f -> Icons.Default.WaterDrop
                                telemetry.humidity > 80f -> Icons.Default.Air
                                telemetry.lightLux > 500f -> Icons.Default.WbSunny
                                else -> Icons.Default.Thermostat
                            },
                            contentDescription = "Weather Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom telemetry pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryPill(label = "습도", value = "${telemetry.humidity}%")
                    TelemetryPill(label = "기압", value = "${telemetry.pressure} hPa")
                    TelemetryPill(label = "풍속", value = "${telemetry.windSpeed} m/s")
                    TelemetryPill(label = "수신시각", value = telemetry.formattedTime)
                }
            }
        }
    }
}

@Composable
fun TelemetryPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SensorMetricCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    sparklineValues: List<Float> = emptyList()
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .testTag("sensor_metric_${title}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            if (sparklineValues.size >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                MiniSparkline(
                    values = sparklineValues,
                    lineColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }
}

@Composable
fun RpiHardwareCard(
    telemetry: WeatherTelemetry,
    deviceConfig: RpiDeviceConfig,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = "Raspberry Pi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "라즈베리파이 시스템 상태",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SoC 온도: ${telemetry.cpuTemp}°C | 주기: ${deviceConfig.telemetryIntervalMs / 1000.0}초",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = if (telemetry.cpuTemp < 60f) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (telemetry.cpuTemp < 60f) "정상 작동" else "온도 주의",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (telemetry.cpuTemp < 60f) Color(0xFF10B981) else Color(0xFFEF4444),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
