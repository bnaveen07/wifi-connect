package com.bnaveen07.wificonnect.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bnaveen07.wificonnect.model.ConnectionStatus
import com.bnaveen07.wificonnect.model.WiFiConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceControlCard(
    isServiceRunning: Boolean,
    autoSwitchEnabled: Boolean,
    fastScanningEnabled: Boolean,
    batteryOptimizationEnabled: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onToggleAutoSwitch: () -> Unit,
    onToggleFastScanning: () -> Unit,
    onToggleBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceRunning) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isServiceRunning) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isServiceRunning) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else 
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.WifiTethering else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(12.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "WiFi Manager Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isServiceRunning) "Active - Smart WiFi management enabled" else "Inactive - Manual connections only",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning) 
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showSettings = !showSettings },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isServiceRunning) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                onStartService()
                            } else {
                                onStopService()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
            
            if (isServiceRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Automatically connecting to the strongest available networks",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isServiceRunning) 
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Settings section
            AnimatedVisibility(
                visible = showSettings,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Auto-switch setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Network Switching",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Automatically switch to stronger networks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = autoSwitchEnabled,
                            onCheckedChange = { onToggleAutoSwitch() },
                            enabled = isServiceRunning
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Scan frequency setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fast Scanning",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (fastScanningEnabled) 
                                    "Scan every 8 seconds (higher battery usage)" 
                                else 
                                    "Scan every 15 seconds (battery saving)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = fastScanningEnabled,
                            onCheckedChange = { onToggleFastScanning() },
                            enabled = isServiceRunning
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Battery optimization setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Battery Optimization",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (batteryOptimizationEnabled) 
                                    "Adjusts scanning and switching based on battery level" 
                                else 
                                    "Fixed scanning behavior regardless of battery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = batteryOptimizationEnabled,
                            onCheckedChange = { onToggleBatteryOptimization() },
                            enabled = isServiceRunning
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusCard(
    connectionState: WiFiConnectionState,
    modifier: Modifier = Modifier
) {
    val statusColor = when (connectionState.connectionStatus) {
        ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> MaterialTheme.colorScheme.tertiary
        ConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val statusText = when (connectionState.connectionStatus) {
        ConnectionStatus.CONNECTED -> "Connected"
        ConnectionStatus.CONNECTING -> "Connecting..."
        ConnectionStatus.AUTHENTICATING -> "Authenticating..."
        ConnectionStatus.FAILED -> "Connection Failed"
        ConnectionStatus.DISCONNECTED -> "Disconnected"
    }
    
    val statusIcon = when (connectionState.connectionStatus) {
        ConnectionStatus.CONNECTED -> Icons.Default.Wifi
        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> Icons.Default.Wifi
        ConnectionStatus.FAILED -> Icons.Default.WifiOff
        ConnectionStatus.DISCONNECTED -> Icons.Default.WifiOff
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    connectionState.connectedNetwork?.let { network ->
                        Text(
                            text = network.ssid,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${network.signalStrengthDescription} (${network.level} dBm)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            if (connectionState.isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "WiFi is enabled • Last scan: ${formatLastScanTime(connectionState.lastScanTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "WiFi is disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatLastScanTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3600_000}h ago"
    }
}
