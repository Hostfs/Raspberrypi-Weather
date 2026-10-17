package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionStatus
import com.example.ui.WeatherViewModel
import com.example.ui.screens.ChartsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RpiSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class ScreenTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("대시보드", Icons.Filled.Thermostat, Icons.Outlined.Thermostat),
    CHARTS("센서 차트", Icons.Filled.Equalizer, Icons.Outlined.Equalizer),
    SETTINGS("MQTT 설정", Icons.Filled.Settings, Icons.Outlined.Settings),
    RPI_SETUP("라즈베리파이", Icons.Filled.DeveloperBoard, Icons.Outlined.DeveloperBoard)
}

class MainActivity : ComponentActivity() {
    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                PiWeatherApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiWeatherApp(viewModel: WeatherViewModel) {
    val currentTelemetry by viewModel.currentTelemetry.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val deviceConfig by viewModel.deviceConfig.collectAsStateWithLifecycle()
    val brokerConfig by viewModel.brokerConfig.collectAsStateWithLifecycle()
    val cachedCount by viewModel.cachedCount.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val selectedWindow by viewModel.selectedTimeWindow.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }

    // Pulsing animation for 1s real-time telemetry indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // NavigationRail for Tablets & Foldables / Wide Screens
            if (isTablet) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .testTag("tablet_navigation_rail"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Thermostat,
                                    contentDescription = "Logo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "PiWeather",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    ScreenTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            modifier = Modifier.testTag("rail_tab_${tab.name}")
                        )
                    }
                }
            }

            // Main Content Area
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PiWeather IoT",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                // Real-time 1s heartbeat indicator
                                val isLive = connectionState.status == ConnectionStatus.CONNECTED ||
                                        connectionState.status == ConnectionStatus.DEMO_MODE
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .alpha(if (isLive) pulseAlpha else 0.4f)
                                        .background(
                                            if (connectionState.status == ConnectionStatus.CONNECTED) Color(0xFF10B981)
                                            else if (connectionState.status == ConnectionStatus.DEMO_MODE) Color(0xFF0284C7)
                                            else Color(0xFFEF4444),
                                            CircleShape
                                        )
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.reconnect() },
                                modifier = Modifier.testTag("top_bar_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "새로고침",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    // Bottom Navigation for Phones
                    if (!isTablet) {
                        NavigationBar(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .testTag("phone_bottom_navigation"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            ScreenTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name}")
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = if (isTablet) 1100.dp else 600.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        when (currentTab) {
                            ScreenTab.DASHBOARD -> DashboardScreen(
                                currentTelemetry = currentTelemetry,
                                history = history,
                                connectionState = connectionState,
                                deviceConfig = deviceConfig,
                                brokerConfig = brokerConfig,
                                isTablet = isTablet,
                                onReconnect = { viewModel.reconnect() },
                                onNavigateToSettings = { currentTab = ScreenTab.SETTINGS },
                                onNavigateToCharts = { currentTab = ScreenTab.CHARTS }
                            )

                            ScreenTab.CHARTS -> ChartsScreen(
                                history = history,
                                selectedMetric = selectedMetric,
                                selectedWindow = selectedWindow,
                                onSelectMetric = { viewModel.selectMetric(it) },
                                onSelectWindow = { viewModel.selectTimeWindow(it) }
                            )

                            ScreenTab.SETTINGS -> SettingsScreen(
                                brokerConfig = brokerConfig,
                                deviceConfig = deviceConfig,
                                cachedCount = cachedCount,
                                onToggleDemoMode = { viewModel.setDemoMode(it) },
                                onUpdateBroker = { host, port, client ->
                                    viewModel.updateBroker(host, port, client)
                                },
                                onUpdateInterval = { viewModel.updateTelemetryInterval(it) },
                                onUpdateCalibration = { t, h, p ->
                                    viewModel.updateCalibration(t, h, p)
                                },
                                onUpdateThresholds = { t, h, a ->
                                    viewModel.updateThresholds(t, h, a)
                                },
                                onToggleStatusLed = { viewModel.toggleStatusLed(it) },
                                onToggleBuzzer = { viewModel.toggleBuzzer(it) },
                                onToggleTemperatureUnit = { viewModel.toggleTemperatureUnit() },
                                onClearCache = { viewModel.clearCachedData() },
                                onReconnect = { viewModel.reconnect() }
                            )

                            ScreenTab.RPI_SETUP -> RpiSetupScreen()
                        }
                    }
                }
            }
        }
    }
}
