package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RpiSetupScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val pythonScriptCode = """
# ==============================================================================
# Raspberry Pi Smart Weather Station MQTT Publisher
# Auto-publishes telemetry every 1 second and syncs bidirectional configurations
# ==============================================================================
import json
import time
import math
import random
import paho.mqtt.client as mqtt

BROKER_HOST = "broker.emqx.io"  # 또는 로컬 라즈베리파이 IP (예: "localhost")
BROKER_PORT = 1883
TELEMETRY_TOPIC = "rpi/weather/telemetry"
CONFIG_TOPIC = "rpi/weather/config"
STATUS_TOPIC = "rpi/weather/status"

# Default configuration state
config = {
    "telemetryIntervalMs": 1000,
    "tempCalibrationOffset": 0.0,
    "humidityCalibrationOffset": 0.0,
    "pressureCalibrationOffset": 0.0,
    "highTempAlertThreshold": 32.0,
    "statusLedEnabled": True,
    "buzzerAlertEnabled": False
}

# Try importing real hardware sensor library
try:
    import board
    import busio
    import adafruit_bme280
    i2c = busio.I2C(board.SCL, board.SDA)
    bme280 = adafruit_bme280.Adafruit_BME280_I2C(i2c)
    HAS_HARDWARE = True
    print("[HW] BME280 sensor detected via I2C")
except Exception as e:
    HAS_HARDWARE = False
    print("[SIM] Real BME280 not found, running precision sensor simulation")

def get_cpu_temp():
    try:
        with open("/sys/class/thermal/thermal_zone0/temp", "r") as f:
            return round(float(f.read()) / 1000.0, 1)
    except Exception:
        return 42.5

def on_connect(client, userdata, flags, rc):
    print(f"[MQTT] Connected with result code {rc}")
    client.subscribe(CONFIG_TOPIC)
    client.publish(STATUS_TOPIC, json.dumps({"status": "online", "hardware": HAS_HARDWARE}), retain=True)

def on_message(client, userdata, msg):
    global config
    try:
        new_config = json.loads(msg.payload.decode())
        config.update(new_config)
        print(f"[CONFIG] Updated configuration from Android app: {config}")
    except Exception as e:
        print(f"[ERROR] Failed to parse incoming config: {e}")

client = mqtt.Client(client_id="rpi_weather_publisher")
client.on_connect = on_connect
client.on_message = on_message
client.will_set(STATUS_TOPIC, json.dumps({"status": "offline"}), retain=True)

client.connect(BROKER_HOST, BROKER_PORT, 60)
client.loop_start()

print(f"[STARTED] Streaming weather telemetry to {BROKER_HOST}...")

step = 0
try:
    while True:
        step += 1
        now_ms = int(time.time() * 1000)
        
        if HAS_HARDWARE:
            raw_temp = bme280.temperature + config["tempCalibrationOffset"]
            raw_hum = bme280.humidity + config["humidityCalibrationOffset"]
            raw_press = bme280.pressure + config["pressureCalibrationOffset"]
        else:
            t_wave = math.sin(step * 0.05) * 0.4 + (random.random() - 0.5) * 0.08
            raw_temp = 22.4 + t_wave + config["tempCalibrationOffset"]
            raw_hum = 55.0 - (math.cos(step * 0.05) * 0.6) + config["humidityCalibrationOffset"]
            raw_press = 1013.25 + math.sin(step * 0.02) * 0.3 + config["pressureCalibrationOffset"]

        telemetry = {
            "deviceId": "rpi-weather-node-01",
            "temperature": round(raw_temp, 1),
            "humidity": round(raw_hum, 1),
            "pressure": round(raw_press, 2),
            "airQualityIndex": int(36 + random.randint(-2, 2)),
            "pm25": round(11.5 + random.random() * 0.8, 1),
            "pm10": round(22.0 + random.random() * 1.5, 1),
            "windSpeed": round(2.3 + (random.random() * 0.5), 1),
            "windDirection": "NW",
            "rainfall": 0.0,
            "lightLux": round(520.0 + math.sin(step * 0.08) * 100, 1),
            "cpuTemp": get_cpu_temp(),
            "timestamp": now_ms
        }

        payload = json.dumps(telemetry)
        client.publish(TELEMETRY_TOPIC, payload, qos=0)
        
        interval_sec = max(0.2, config["telemetryIntervalMs"] / 1000.0)
        time.sleep(interval_sec)

except KeyboardInterrupt:
    print("[STOPPED] Terminating publisher")
    client.loop_stop()
    client.disconnect()
""".trimIndent()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("rpi_setup_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = "RPI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "라즈베리파이 하드웨어 연동 가이드",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "라즈베리파이 3/4/5 및 Zero 2W 완벽 호환",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Setup commands
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
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "CLI",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1단계: 라즈베리파이 터미널 설치 명령어",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val commandText = "sudo apt update && sudo apt install -y python3-pip mosquitto\npip3 install paho-mqtt adafruit-circuitpython-bme280"
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = commandText,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Hardware Pinout Guide
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
                        text = "2단계: 센서 핀아웃 연결 (BME280 / I2C)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• VIN / VCC ➔ 라즈베리파이 Pin 1 (3.3V Power)\n• GND ➔ 라즈베리파이 Pin 6 (Ground)\n• SCL ➔ 라즈베리파이 Pin 5 (GPIO 3 - I2C SCL)\n• SDA ➔ 라즈베리파이 Pin 3 (GPIO 2 - I2C SDA)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Ready to run Python Script Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Python",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3단계: rpi_weather_publisher.py",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("RPi Weather Python Script", pythonScriptCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "파이썬 코드가 클립보드에 복사되었습니다!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_python_script_button")
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("코드 복사")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Color(0xFF0B1320),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    text = pythonScriptCode,
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
