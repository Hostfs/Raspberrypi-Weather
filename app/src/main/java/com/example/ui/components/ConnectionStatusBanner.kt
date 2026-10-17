package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.data.model.MqttConnectionState

@Composable
fun ConnectionStatusBanner(
    connectionState: MqttConnectionState,
    isDemoMode: Boolean,
    onReconnectClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusIcon, statusTitle, statusSub) = when (connectionState.status) {
        ConnectionStatus.CONNECTED -> {
            Quad(
                Color(0xFF10B981),
                Icons.Default.CloudDone,
                "MQTT 브로커 정상 연결 (저지연 파이프라인)",
                "지연: ${connectionState.latencyMs}ms | 1초 스트리밍 | 패킷: ${connectionState.packetsReceived}"
            )
        }
        ConnectionStatus.DEMO_MODE -> {
            Quad(
                Color(0xFF0284C7),
                Icons.Default.Sensors,
                "데모 시뮬레이션 모드 (1초 주기 센서 데이터)",
                "가상 센서 물리 동역학 활성화 | 실장비 연동은 설정에서 가능"
            )
        }
        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> {
            Quad(
                Color(0xFFF59E0B),
                Icons.Default.Refresh,
                if (connectionState.status == ConnectionStatus.CONNECTING) "라즈베리파이 MQTT 연결 중..." else "MQTT 재연결 시도 중...",
                connectionState.brokerAddress
            )
        }
        ConnectionStatus.DISCONNECTED -> {
            Quad(
                Color(0xFF64748B),
                Icons.Default.CloudOff,
                "MQTT 브로커 연결 해제됨",
                "오프라인 캐시 모드 동작 중"
            )
        }
        ConnectionStatus.ERROR -> {
            Quad(
                Color(0xFFEF4444),
                Icons.Default.Warning,
                "MQTT 연결 오류 발생",
                connectionState.errorMessage ?: "브로커 주소를 확인해주세요"
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onNavigateToSettings() }
            .testTag("connection_status_banner"),
        color = statusColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(statusColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = "Status Icon",
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = statusSub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            if (!isDemoMode && connectionState.status != ConnectionStatus.CONNECTED) {
                IconButton(
                    onClick = onReconnectClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "재연결",
                        tint = statusColor
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
