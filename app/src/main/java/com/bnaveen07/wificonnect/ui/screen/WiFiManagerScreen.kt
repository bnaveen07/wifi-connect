package com.bnaveen07.wificonnect.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bnaveen07.wificonnect.model.ConnectionStatus
import com.bnaveen07.wificonnect.model.WiFiConnectionState
import com.bnaveen07.wificonnect.model.WiFiNetwork
import com.bnaveen07.wificonnect.ui.components.NetworkCard
import com.bnaveen07.wificonnect.ui.components.ServiceControlCard
import com.bnaveen07.wificonnect.ui.components.ConnectionStatusCard
import com.bnaveen07.wificonnect.ui.components.WiFiPasswordDialog
import com.bnaveen07.wificonnect.viewmodel.WiFiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiManagerScreen(
    viewModel: WiFiViewModel = viewModel(),
    onNavigateToChat: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.observeAsState()
    val isServiceRunning by viewModel.isServiceRunning.observeAsState(false)
    val selectedNetwork by viewModel.selectedNetwork.observeAsState()
    val isRefreshing by viewModel.isRefreshing.observeAsState(false)
    val autoSwitchEnabled by viewModel.autoSwitchEnabled.observeAsState(true)
    val fastScanningEnabled by viewModel.fastScanningEnabled.observeAsState(true)
    val batteryOptimizationEnabled by viewModel.batteryOptimizationEnabled.observeAsState(true)
    
    var showNetworkDetails by remember { mutableStateOf<WiFiNetwork?>(null) }
    var showLocalChat by remember { mutableStateOf(false) }
    
    // Conditional navigation - show details screen, chat screen, or main screen
    when {
        showNetworkDetails != null -> {
            NetworkDetailsScreen(
                network = showNetworkDetails!!,
                onBack = { showNetworkDetails = null },
                onForget = {
                    viewModel.forgetNetwork(showNetworkDetails!!.ssid)
                    showNetworkDetails = null
                }
            )
        }
        showLocalChat -> {
            // Navigate to chat list instead of showing inline
            onNavigateToChat()
            showLocalChat = false
        }
        else -> {
            // Main WiFi Manager Screen
            MainWiFiManagerContent(
                connectionState = connectionState,
                isServiceRunning = isServiceRunning,
                selectedNetwork = selectedNetwork,
                isRefreshing = isRefreshing,
                autoSwitchEnabled = autoSwitchEnabled,
                fastScanningEnabled = fastScanningEnabled,
                batteryOptimizationEnabled = batteryOptimizationEnabled,
                viewModel = viewModel,
                onShowNetworkDetails = { network -> showNetworkDetails = network },
                onShowLocalChat = { showLocalChat = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainWiFiManagerContent(
    connectionState: WiFiConnectionState?,
    isServiceRunning: Boolean,
    selectedNetwork: WiFiNetwork?,
    isRefreshing: Boolean,
    autoSwitchEnabled: Boolean,
    fastScanningEnabled: Boolean,
    batteryOptimizationEnabled: Boolean,
    viewModel: WiFiViewModel,
    onShowNetworkDetails: (WiFiNetwork) -> Unit,
    onShowLocalChat: () -> Unit
) {
    // Show password dialog when a secured network is selected
    selectedNetwork?.let { network ->
        if (network.isSecured) {
            WiFiPasswordDialog(
                network = network,
                onConnect = { password, shouldSave ->
                    viewModel.connectToNetwork(network, password, shouldSave)
                },
                onDismiss = {
                    viewModel.clearSelectedNetwork()
                }
            )
        } else {
            // For open networks, connect immediately
            LaunchedEffect(network) {
                viewModel.connectToNetwork(network, "", false)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "WiFi Connect",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = onShowLocalChat) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "Local Chat",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onShowLocalChat,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Start Local Chat"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Service Control
            ServiceControlCard(
                isServiceRunning = isServiceRunning,
                autoSwitchEnabled = autoSwitchEnabled,
                fastScanningEnabled = fastScanningEnabled,
                batteryOptimizationEnabled = batteryOptimizationEnabled,
                onStartService = viewModel::startWiFiService,
                onStopService = viewModel::stopWiFiService,
                onToggleAutoSwitch = viewModel::toggleAutoSwitch,
                onToggleFastScanning = viewModel::toggleFastScanning,
                onToggleBatteryOptimization = viewModel::toggleBatteryOptimization,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        
        connectionState?.let { state ->
            // Connection Status
            ConnectionStatusCard(
                connectionState = state,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Networks List
            if (state.availableNetworks.isNotEmpty()) {
                NetworksSection(
                    networks = state.availableNetworks,
                    connectedNetwork = state.connectedNetwork,
                    isScanning = state.isScanning,
                    isRefreshing = isRefreshing,
                    onNetworkSelected = viewModel::selectNetwork,
                    onConnectedNetworkDetails = onShowNetworkDetails,
                    onRefresh = viewModel::refreshNetworks
                )
            } else if (isServiceRunning) {
                ScanningIndicator()
            }
        }
    }
}
}

@Composable
private fun NetworksSection(
    networks: List<WiFiNetwork>,
    connectedNetwork: WiFiNetwork?,
    isScanning: Boolean,
    isRefreshing: Boolean,
    onNetworkSelected: (WiFiNetwork) -> Unit,
    onConnectedNetworkDetails: (WiFiNetwork) -> Unit,
    onRefresh: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available Networks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isScanning || isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = if (isRefreshing) "Refreshing..." else "Scanning...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh networks",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(networks) { network ->
                val isConnected = connectedNetwork?.ssid == network.ssid
                NetworkCard(
                    network = network,
                    isConnected = isConnected,
                    onClick = { 
                        if (isConnected) {
                            onConnectedNetworkDetails(network)
                        } else {
                            onNetworkSelected(network)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanningIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanning for WiFi networks...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
