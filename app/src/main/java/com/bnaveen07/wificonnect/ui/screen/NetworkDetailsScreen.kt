package com.bnaveen07.wificonnect.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bnaveen07.wificonnect.model.WiFiNetwork
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDetailsScreen(
    network: WiFiNetwork,
    onBack: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRunningSpeedTest by remember { mutableStateOf(false) }
    var downloadSpeed by remember { mutableStateOf(0.0) }
    var uploadSpeed by remember { mutableStateOf(0.0) }
    var ping by remember { mutableStateOf(0) }
    var speedTestProgress by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Network Details") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Network Info Card
            NetworkInfoCard(network = network)

            // Technical Details Card
            TechnicalDetailsCard(network = network)

            // Speed Test Card
            SpeedTestCard(
                isRunning = isRunningSpeedTest,
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                ping = ping,
                progress = speedTestProgress,
                onStartSpeedTest = {
                    isRunningSpeedTest = true
                    speedTestProgress = 0f
                }
            )
            
            // Speed test effect
            if (isRunningSpeedTest) {
                LaunchedEffect(Unit) {
                    // Simulate ping test
                    delay(500)
                    speedTestProgress = 0.1f
                    ping = kotlin.random.Random.nextInt(10, 50)
                    
                    // Simulate download test
                    repeat(4) { i ->
                        delay(750)
                        speedTestProgress = 0.1f + (i + 1) * 0.2f
                    }
                    downloadSpeed = kotlin.random.Random.nextDouble(10.0, 100.0)
                    
                    // Simulate upload test
                    repeat(4) { i ->
                        delay(750)
                        speedTestProgress = 0.5f + (i + 1) * 0.125f
                    }
                    uploadSpeed = kotlin.random.Random.nextDouble(5.0, 50.0)
                    
                    speedTestProgress = 1.0f
                    delay(500)
                    isRunningSpeedTest = false
                }
            }

            // Network Actions Card
            NetworkActionsCard(
                network = network,
                onForget = onForget
            )
        }
    }
}

@Composable
private fun NetworkInfoCard(network: WiFiNetwork) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        network.level >= -50 -> Icons.Default.SignalWifi4Bar
                        network.level >= -60 -> Icons.Default.Wifi
                        network.level >= -70 -> Icons.Default.Wifi
                        else -> Icons.Default.WifiOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${network.signalStrengthDescription} • ${network.level} dBm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun TechnicalDetailsCard(network: WiFiNetwork) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Technical Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            DetailRow("Security", network.capabilities.takeIf { it.isNotEmpty() } ?: "Open")
            DetailRow("Frequency", "${network.frequency} MHz")
            DetailRow("Channel Width", "Unknown") // Would need actual channel width data
            DetailRow("Signal Strength", "${network.level} dBm (${network.signalStrength}%)")
            DetailRow("BSSID", network.bssid.takeIf { it.isNotBlank() } ?: "Unknown")
            DetailRow("Connection Score", "${network.connectionScore}")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SpeedTestCard(
    isRunning: Boolean,
    downloadSpeed: Double,
    uploadSpeed: Double,
    ping: Int,
    progress: Float,
    onStartSpeedTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (!isRunning) {
                    Button(
                        onClick = onStartSpeedTest
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Test")
                    }
                }
            }
            
            if (isRunning) {
                Spacer(modifier = Modifier.height(16.dp))
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Testing... ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (downloadSpeed > 0 || uploadSpeed > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedResult("Download", downloadSpeed, "Mbps", Icons.Default.Download)
                    SpeedResult("Upload", uploadSpeed, "Mbps", Icons.Default.Upload)
                    SpeedResult("Ping", ping.toDouble(), "ms", Icons.Default.Speed)
                }
            }
        }
    }
}

@Composable
private fun SpeedResult(
    label: String,
    value: Double,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = String.format("%.1f", value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NetworkActionsCard(
    network: WiFiNetwork,
    onForget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedButton(
                onClick = onForget,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Forget Network")
            }
        }
    }
}

@Composable
private fun startSpeedTest(
    onProgress: (Float) -> Unit,
    onDownloadResult: (Double) -> Unit,
    onUploadResult: (Double) -> Unit,
    onPingResult: (Int) -> Unit,
    onComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        // Simulate ping test
        delay(500)
        onProgress(0.1f)
        val pingResult = Random.nextInt(10, 50)
        onPingResult(pingResult)
        
        // Simulate download test
        repeat(4) { i ->
            delay(750)
            onProgress(0.1f + (i + 1) * 0.2f)
        }
        val downloadResult = Random.nextDouble(10.0, 100.0)
        onDownloadResult(downloadResult)
        
        // Simulate upload test
        repeat(4) { i ->
            delay(750)
            onProgress(0.5f + (i + 1) * 0.125f)
        }
        val uploadResult = Random.nextDouble(5.0, 50.0)
        onUploadResult(uploadResult)
        
        onProgress(1.0f)
        delay(500)
        onComplete()
    }
}
