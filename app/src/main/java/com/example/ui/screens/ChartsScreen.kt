package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WeatherTelemetry
import com.example.ui.MetricType
import com.example.ui.TimeWindow
import com.example.ui.components.LiveTelemetryChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun ChartsScreen(
    history: List<WeatherTelemetry>,
    selectedMetric: MetricType,
    selectedWindow: TimeWindow,
    onSelectMetric: (MetricType) -> Unit,
    onSelectWindow: (TimeWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter history based on time window
    val now = System.currentTimeMillis()
    val filteredHistory = remember(history, selectedWindow) {
        if (selectedWindow == TimeWindow.ALL) {
            history
        } else {
            val cutoff = now - (selectedWindow.seconds * 1000L)
            history.filter { it.timestamp >= cutoff }
        }
    }

    val valueExtractor: (WeatherTelemetry) -> Float = when (selectedMetric) {
        MetricType.TEMPERATURE -> { t -> t.temperature }
        MetricType.HUMIDITY -> { t -> t.humidity }
        MetricType.PRESSURE -> { t -> t.pressure }
        MetricType.AIR_QUALITY -> { t -> t.airQualityIndex.toFloat() }
        MetricType.WIND -> { t -> t.windSpeed }
        MetricType.CPU_TEMP -> { t -> t.cpuTemp }
    }

    val chartColor = when (selectedMetric) {
        MetricType.TEMPERATURE -> Color(0xFFF97316)
        MetricType.HUMIDITY -> Color(0xFF0284C7)
        MetricType.PRESSURE -> Color(0xFF8B5CF6)
        MetricType.AIR_QUALITY -> Color(0xFF10B981)
        MetricType.WIND -> Color(0xFF06B6D4)
        MetricType.CPU_TEMP -> Color(0xFFEF4444)
    }

    // Statistics Calculation
    val values = remember(filteredHistory, selectedMetric) { filteredHistory.map { valueExtractor(it) } }
    val currentVal = values.lastOrNull() ?: 0f
    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 0f
    val avgVal = if (values.isNotEmpty()) values.average().toFloat() else 0f
    val stdDev = if (values.size > 1) {
        val variance = values.map { (it - avgVal) * (it - avgVal) }.average()
        sqrt(variance).toFloat()
    } else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("charts_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Metric Selector Horizontal Chips
        item {
            Text(
                text = "모니터링 센서 항목",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(MetricType.entries.toTypedArray()) { metric ->
                    FilterChip(
                        selected = metric == selectedMetric,
                        onClick = { onSelectMetric(metric) },
                        label = { Text("${metric.titleKo} (${metric.unit})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chartColor.copy(alpha = 0.2f),
                            selectedLabelColor = chartColor
                        ),
                        modifier = Modifier.testTag("chip_metric_${metric.name}")
                    )
                }
            }
        }

        // Time Window Selector Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "분석 주기 필터",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeWindow.entries.forEach { window ->
                        Surface(
                            color = if (window == selectedWindow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectWindow(window) }
                        ) {
                            Text(
                                text = window.labelKo,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (window == selectedWindow) FontWeight.Bold else FontWeight.Normal,
                                color = if (window == selectedWindow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Live Dynamic Interactive Chart
        item {
            LiveTelemetryChart(
                dataPoints = filteredHistory,
                valueExtractor = valueExtractor,
                unit = selectedMetric.unit,
                lineColor = chartColor
            )
        }

        // Statistical Analytics Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Stats",
                            tint = chartColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedMetric.titleKo} 통계 요약 (${filteredHistory.size}개 데이터)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(
                            label = "현재 실시간",
                            value = String.format(Locale.US, "%.1f %s", currentVal, selectedMetric.unit),
                            color = chartColor
                        )
                        StatItem(
                            label = "평균치 (Avg)",
                            value = String.format(Locale.US, "%.1f %s", avgVal, selectedMetric.unit),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatItem(
                            label = "최저 (Min)",
                            value = String.format(Locale.US, "%.1f %s", minVal, selectedMetric.unit),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatItem(
                            label = "최고 (Max)",
                            value = String.format(Locale.US, "%.1f %s", maxVal, selectedMetric.unit),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Recent Telemetry Readings Log
        item {
            Text(
                text = "최근 텔레메트리 로그 (1초 주기)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(filteredHistory.takeLast(10).reversed()) { item ->
            val v = valueExtractor(item)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(chartColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.formattedTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = String.format(Locale.US, "%.1f %s", v, selectedMetric.unit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = chartColor
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
