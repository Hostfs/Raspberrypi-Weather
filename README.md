# 🌤️ PiWeather - 라즈베리파이 스마트 기상 관측소 IoT 시스템

> **라즈베리파이 환경 센서 데이터를 저지연 MQTT 브로커를 통해 안드로이드 기기로 1초 주기로 실시간 스트리밍하고, 로컬 Room DB에 안전하게 캐싱하는 스마트 기상 관측소 대시보드 애플리케이션입니다.**

---

## 📌 프로그램 개요

**PiWeather**는 실외 또는 실내에 설치된 **라즈베리파이(Raspberry Pi)** IoT 노드로부터 기상 및 환경 센서 데이터를 실시간 수집하여 모바일 및 태블릿 화면에 직관적인 시각화와 통계를 제공하는 프로그램입니다.

- **초기 개발 및 시연을 위한 데모 모드**를 지원하여, 실제 하드웨어가 아직 준비되지 않은 환경에서도 완벽한 가상 물리 센서 데이터로 테스트할 수 있습니다.
- 환경설정 파일(`.env`) 또는 앱 내 설정 UI를 통해 원클릭으로 데모 모드를 켜거나 끄고, 실제 라즈베리파이 하드웨어 센서와 직결할 수 있습니다.
- MQTT를 이용한 **양방향 실시간 동기화**를 통해 안드로이드 앱에서 전송 주기, 센서 오차 캘리브레이션, 경보 임계치, 라즈베리파이 하드웨어 액추에이터(LED, 부저)를 원격 제어합니다.

---

## 🚀 주요 기능 및 핵심 아키텍처

### 1. ⚡ 저지연 MQTT 실시간 데이터 파이프라인 (1초 주기)
- **1초 간격 전송**: 센서 데이터를 1000ms 간격으로 고속 전송하여 딜레이와 지연 시간을 최소화(평균 왕복 지연 10~25ms).
- **포괄적 환경 텔레메트리**:
  - 온도 (°C / °F), 상대 습도 (%), 대기압 (hPa)
  - 미세먼지 및 공기질 지수 (AQI, PM2.5, PM10 ㎍/㎥)
  - 풍속 (m/s) 및 16방위 풍향 (Wind Direction)
  - 강우 감지 (Rainfall mm/h) 및 주변 조도 (Ambient Light Lux)
  - 체감 온도 (Feels Like), 이슬점 (Dew Point) 실시간 산출
  - 라즈베리파이 SoC CPU 온도 모니터링

### 2. 🎛️ 데모 모드 (Demo Mode) & 실장비 연동 (Hardware Mode)
- **`.env` 기반 제어**:
  ```properties
  # 데모 시뮬레이션 모드 (true: 가상 센서, false: 실제 라즈베리파이 하드웨어)
  DEMO_MODE=true
  
  # MQTT 브로커 설정
  MQTT_BROKER_HOST=broker.emqx.io
  MQTT_BROKER_PORT=1883
  MQTT_CLIENT_ID=piweather_android_client
  ```
- **런타임 실시간 스위칭**: 앱 설정 화면의 스위치 하나로 재부팅 없이 즉시 가상 시뮬레이션과 실제 하드웨어 MQTT 스트림을 전환할 수 있습니다.

### 3. 🔄 양방향 실시간 MQTT 설정 동기화
- 앱에서 변경한 설정은 MQTT `rpi/weather/config` 토픽으로 즉시 발행(Publish)되어 라즈베리파이에 실시간 반영됩니다:
  - **전송 주기 조절**: 500ms / 1초(기본) / 2초 / 5초
  - **센서 오차 캘리브레이션**: 실제 정밀 계측기와의 오차를 보정하기 위해 온도(±5°C), 습도(±10%), 기압(±20 hPa) 오프셋을 실시간 동기화
  - **하드웨어 액추에이터 원격 제어**: 라즈베리파이 상태 표시 LED 토글 및 고온/위험 경보 부저(Buzzer) 토글
  - **실시간 경보 임계치**: 고온 경보, 과습 경보, 미세먼지 나쁨 경보

### 4. 📈 실시간 캔버스 차트 및 데이터 분석
- Jetpack Compose의 고성능 `Canvas`를 기반으로 한 60FPS 실시간 그래프.
- 1초 주기 텔레메트리 스트림에 맞춘 큐빅 베지에(Cubic Bezier) 곡선 및 면적 그라디언트.
- **터치 & 드래그 스크러빙**: 그래프를 드래그하면 특정 시점의 측정 시각과 센서 수치가 툴팁으로 표시.
- **시간 필터 및 통계**: 최근 10분(고해상도 실시간), 1시간, 6시간, 전체 기록 필터링 및 현재값/평균값/최저값/최고값/표준편차 자동 산출.

### 5. 📱 반응형 디자인 (Mobile & Tablet)
- **스마트폰**: 한 손 조작에 최적화된 Material 3 하단 `NavigationBar`와 2열 밀도형 센서 카드.
- **태블릿 / 폴더블 / 대화면**: 넓은 가로 화면을 활용하는 측면 `NavigationRail`, 3~4열 그리드, 넓은 캔버스 분석 뷰.

### 6. 💾 오프라인 로컬 캐싱 (Room Database)
- 네트워크가 끊어지거나 오프라인 상태에서도 수집된 센서 데이터가 유실되지 않도록 **Android Room (SQLite)** 에 자동 저장.
- 재연결 시 이전 센서 히스토리가 그대로 유지되어 연속적인 데이터 분석이 가능.

---

## 📡 MQTT 토픽 및 데이터 규격

| 토픽 (Topic) | 방향 | QoS | 설명 |
|---|---|---|---|
| `rpi/weather/telemetry` | RPi ➔ App | 0 | 1초 주기의 실시간 센서 측정 데이터 |
| `rpi/weather/config` | App ➔ RPi / 양방향 | 1 (Retained) | 전송 주기, 캘리브레이션 오프셋, LED/부저 제어 |
| `rpi/weather/status` | RPi ➔ App | 1 (Retained) | 라즈베리파이 온라인/오프라인 LWT(Last Will) |

### 텔레메트리 JSON 페이로드 예시 (`rpi/weather/telemetry`)
```json
{
  "deviceId": "rpi-weather-node-01",
  "temperature": 23.4,
  "humidity": 56.2,
  "pressure": 1012.85,
  "airQualityIndex": 35,
  "pm25": 11.2,
  "pm10": 21.0,
  "windSpeed": 2.1,
  "windDirection": "NW",
  "rainfall": 0.0,
  "lightLux": 520.0,
  "cpuTemp": 42.5,
  "timestamp": 1740000000000
}
```

---

## 🛠️ 라즈베리파이 설치 및 연동 가이드

앱 내 **[라즈베리파이]** 탭에 전체 파이썬 퍼블리셔 코드가 내장되어 있어 **[코드 복사]** 버튼으로 손쉽게 복사할 수 있습니다.

### 1. 라즈베리파이에 필수 패키지 설치
```bash
sudo apt update
sudo apt install -y python3-pip mosquitto mosquitto-clients
pip3 install paho-mqtt adafruit-circuitpython-bme280
```

### 2. 센서 배선 (I2C BME280 기준)
- **VIN (VCC)**: 라즈베리파이 3.3V (Pin 1)
- **GND**: 라즈베리파이 Ground (Pin 6)
- **SCL**: 라즈베리파이 GPIO 3 / SCL (Pin 5)
- **SDA**: 라즈베리파이 GPIO 2 / SDA (Pin 3)

### 3. 파이썬 퍼블리셔 실행
```bash
# 앱 내 [라즈베리파이] 탭에서 복사한 rpi_weather_publisher.py 파일 생성
python3 rpi_weather_publisher.py
```
> *참고: 실제 BME280 센서가 연결되지 않았을 경우, 파이썬 스크립트가 센서 오류로 중단되지 않고 자동으로 가상 센서 알고리즘으로 폴백하여 통신을 유지합니다.*

---

## 📂 프로젝트 구조

```
/
├── .env / .env.example             # 데모 모드 및 MQTT 브로커 환경설정
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt          # 반응형 NavigationRail/Bar 및 메인 뷰
│   │   ├── data/
│   │   │   ├── model/               # Telemetry, Config, Status 데이터 모델
│   │   │   ├── local/               # Room Database (Entity, Dao, SQLite)
│   │   │   ├── mqtt/                # Paho MQTT 클라이언트 & 가상 물리 시뮬레이터
│   │   │   └── repository/          # 데이터 통합 관리 Repository
│   │   └── ui/
│   │       ├── WeatherViewModel.kt  # StateFlow 기반 MVVM 뷰모델
│   │       ├── components/          # Canvas 실시간 차트 & 상태 배너
│   │       ├── screens/             # 대시보드, 차트, 설정, RPi 가이드 화면
│   │       └── theme/               # Material 3 테마 및 사이버 IoT 색상
│   └── src/main/res/                # 반응형 아이콘, 드로어블, 문자열
└── README.md                        # 상세 프로젝트 문서
```
