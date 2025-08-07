package com.bnaveen07.wificonnect.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bnaveen07.wificonnect.model.FrequencyBand
import com.bnaveen07.wificonnect.model.SecurityType
import com.bnaveen07.wificonnect.model.WiFiNetwork

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkCard(
    network: WiFiNetwork,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation states
    val animatedElevation by animateDpAsState(
        targetValue = if (isConnected) 8.dp else 2.dp,
        animationSpec = tween(300),
        label = "elevation"
    )
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isConnected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnected -> MaterialTheme.colorScheme.primaryContainer
                network.hasStoredPassword -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Network name and status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                            network.hasStoredPassword -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Status indicators
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isConnected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (network.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (network.hasStoredPassword && !isConnected) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Saved network",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Network details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Security type
                    SecurityChip(securityType = network.securityType)
                    
                    // Frequency band
                    FrequencyChip(frequencyBand = network.frequencyBand)
                    
                    // Connection score (for debugging/advanced users)
                    if (network.hasStoredPassword) {
                        Surface(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Score: ${network.connectionScore}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Enhanced signal strength indicator
                EnhancedSignalStrengthIndicator(
                    strength = network.signalStrength,
                    level = network.level,
                    isConnected = isConnected
                )
                
                Text(
                    text = "${network.level} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isConnected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        network.hasStoredPassword -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    }
                )
            }
        }
    }
}

@Composable
private fun SecurityChip(securityType: SecurityType) {
    val (icon, text, color) = when (securityType) {
        SecurityType.OPEN -> Triple(Icons.Default.LockOpen, "Open", MaterialTheme.colorScheme.tertiary)
        SecurityType.WEP -> Triple(Icons.Default.Lock, "WEP", MaterialTheme.colorScheme.error)
        SecurityType.WPA -> Triple(Icons.Default.Lock, "WPA", MaterialTheme.colorScheme.primary)
        SecurityType.WPA2 -> Triple(Icons.Default.Lock, "WPA2", MaterialTheme.colorScheme.primary)
        SecurityType.WPA3 -> Triple(Icons.Default.Security, "WPA3", MaterialTheme.colorScheme.primary)
    }
    
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FrequencyChip(frequencyBand: FrequencyBand) {
    val (text, color) = when (frequencyBand) {
        FrequencyBand.BAND_5GHZ -> "5GHz" to MaterialTheme.colorScheme.secondary
        FrequencyBand.BAND_2_4GHZ -> "2.4GHz" to MaterialTheme.colorScheme.outline
        FrequencyBand.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EnhancedSignalStrengthIndicator(
    strength: Int,
    level: Int,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val signalIcon = when {
        level >= -30 -> Icons.Default.SignalWifi4Bar
        level >= -50 -> Icons.Default.Wifi
        level >= -60 -> Icons.Default.Wifi
        level >= -70 -> Icons.Default.Wifi
        else -> Icons.Default.WifiOff
    }
    
    val signalColor = getSignalColor(strength)
    
    // Animation for connected network
    val pulseAnimation = rememberInfiniteTransition(label = "signal_pulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "signal_pulse_scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            Icon(
                imageVector = signalIcon,
                contentDescription = "Signal strength",
                tint = signalColor,
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (isConnected) Modifier.graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                        else Modifier
                    )
            )
        }
        
        // Signal strength percentage with animated progress
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$strength%",
                style = MaterialTheme.typography.labelMedium,
                color = signalColor,
                fontWeight = FontWeight.Bold
            )
            
            // Mini signal bars
            SignalBars(strength = strength, color = signalColor)
        }
    }
}

@Composable
private fun SignalBars(strength: Int, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            val isActive = strength > (index * 25)
            val height = (4 + index * 2).dp
            
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height)
                    .background(
                        color = if (isActive) color else color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable
private fun getSignalColor(strength: Int): Color {
    return when {
        strength >= 75 -> Color(0xFF4CAF50) // Green
        strength >= 50 -> Color(0xFFFF9800) // Orange
        strength >= 25 -> Color(0xFFFF5722) // Red-Orange
        else -> Color(0xFFF44336) // Red
    }
}
