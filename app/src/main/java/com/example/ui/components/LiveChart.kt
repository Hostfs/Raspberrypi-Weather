package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WeatherTelemetry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun LiveTelemetryChart(
    dataPoints: List<WeatherTelemetry>,
    valueExtractor: (WeatherTelemetry) -> Float,
    unit: String,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "📡 센서 데이터를 수집하는 중입니다...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1초 간격으로 실시간 데이터가 표시됩니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    // Values to plot (sorted chronologically)
    val sortedData = remember(dataPoints) { dataPoints.sortedBy { it.timestamp } }
    val values = remember(sortedData) { sortedData.map { valueExtractor(it) } }

    val rawMin = values.minOrNull() ?: 0f
    val rawMax = values.maxOrNull() ?: 100f
    val rangePadding = if (rawMax - rawMin < 1f) 1f else (rawMax - rawMin) * 0.1f
    val minY = rawMin - rangePadding
    val maxY = rawMax + rangePadding
    val valueRange = if (maxY - minY == 0f) 1f else (maxY - minY)

    var touchedIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        // Scrubbing Header
        val currentScrubbed = touchedIndex?.let { idx ->
            if (idx in sortedData.indices) sortedData[idx] else null
        } ?: sortedData.lastOrNull()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val scrubValue = currentScrubbed?.let { valueExtractor(it) } ?: 0f
                Text(
                    text = String.format(Locale.US, "%.1f %s", scrubValue, unit),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = lineColor
                )
                val timeStr = currentScrubbed?.let {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                } ?: "--:--:--"
                Text(
                    text = if (touchedIndex != null) "📍 탐색 시각: $timeStr" else "실시간 측정: $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "최저: %.1f", rawMin),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "최고: %.1f", rawMax),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Canvas Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("live_telemetry_canvas")
                .pointerInput(sortedData) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val n = sortedData.size
                            if (n > 1) {
                                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                touchedIndex = (frac * (n - 1)).toInt()
                            }
                        },
                        onDrag = { change, _ ->
                            val n = sortedData.size
                            if (n > 1) {
                                val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                                touchedIndex = (frac * (n - 1)).toInt()
                            }
                        },
                        onDragEnd = { touchedIndex = null },
                        onDragCancel = { touchedIndex = null }
                    )
                }
                .pointerInput(sortedData) {
                    detectTapGestures(
                        onPress = { offset ->
                            val n = sortedData.size
                            if (n > 1) {
                                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                touchedIndex = (frac * (n - 1)).toInt()
                                tryAwaitRelease()
                                touchedIndex = null
                            }
                        }
                    )
                }
        ) {
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            val areaFillColor = lineColor.copy(alpha = 0.15f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val n = values.size

                // Draw Horizontal Grid Lines (3 lines)
                val gridSteps = 3
                for (i in 0..gridSteps) {
                    val y = h * (i.toFloat() / gridSteps)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }

                if (n < 2) return@Canvas

                val stepX = w / (n - 1)
                val linePath = Path()
                val fillPath = Path()

                var firstPoint = Offset.Zero

                for (i in 0 until n) {
                    val v = values[i]
                    val x = i * stepX
                    val y = h - ((v - minY) / valueRange) * h

                    if (i == 0) {
                        firstPoint = Offset(x, y)
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        // Smooth Cubic Bezier curve
                        val prevX = (i - 1) * stepX
                        val prevY = h - ((values[i - 1] - minY) / valueRange) * h
                        val cx1 = prevX + (x - prevX) / 2f
                        val cy1 = prevY
                        val cx2 = prevX + (x - prevX) / 2f
                        val cy2 = y
                        linePath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                    }

                    if (i == n - 1) {
                        fillPath.lineTo(x, h)
                        fillPath.close()
                    }
                }

                // Draw Area Gradient under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )

                // Draw Main Line
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Scrubbing Indicator if active
                touchedIndex?.let { idx ->
                    if (idx in 0 until n) {
                        val scrubX = idx * stepX
                        val scrubY = h - ((values[idx] - minY) / valueRange) * h

                        // Vertical guide line
                        drawLine(
                            color = lineColor.copy(alpha = 0.8f),
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Highlight Dot
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                    }
                }

                // Latest Realtime Point Pulsing Dot
                if (touchedIndex == null && n > 0) {
                    val lastX = (n - 1) * stepX
                    val lastY = h - ((values.last() - minY) / valueRange) * h

                    drawCircle(
                        color = lineColor.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 4.5.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }
        }
    }
}

/**
 * Lightweight Mini Sparkline for Metric Cards
 */
@Composable
fun MiniSparkline(
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) return

    val rawMin = values.minOrNull() ?: 0f
    val rawMax = values.maxOrNull() ?: 100f
    val range = if (rawMax - rawMin == 0f) 1f else (rawMax - rawMin)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = values.size
        val stepX = w / (n - 1)

        val path = Path()
        for (i in 0 until n) {
            val x = i * stepX
            val y = h - ((values[i] - rawMin) / range) * (h * 0.8f) - (h * 0.1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
