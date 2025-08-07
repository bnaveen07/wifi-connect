package com.bnaveen07.wificonnect.model

data class WiFiNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int, // Signal strength in dBm
    val frequency: Int,
    val capabilities: String,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val hasStoredPassword: Boolean = false,
    val priority: Int = 0, // Higher number = higher priority
    val lastConnected: Long = 0L // Timestamp of last successful connection
) {
    val signalStrength: Int
        get() = when {
            level >= -30 -> 100
            level >= -50 -> 75
            level >= -60 -> 50
            level >= -70 -> 25
            else -> 0
        }
        
    val signalStrengthDescription: String
        get() = when {
            level >= -30 -> "Excellent"
            level >= -50 -> "Good"
            level >= -60 -> "Fair"
            level >= -70 -> "Weak"
            else -> "Very Weak"
        }
        
    val isSecured: Boolean
        get() = capabilities.contains("WPA") || capabilities.contains("WEP")
    
    val securityType: SecurityType
        get() = when {
            capabilities.contains("WPA3") -> SecurityType.WPA3
            capabilities.contains("WPA2") -> SecurityType.WPA2
            capabilities.contains("WPA") -> SecurityType.WPA
            capabilities.contains("WEP") -> SecurityType.WEP
            else -> SecurityType.OPEN
        }
    
    val frequencyBand: FrequencyBand
        get() = when {
            frequency >= 5000 -> FrequencyBand.BAND_5GHZ
            frequency >= 2400 -> FrequencyBand.BAND_2_4GHZ
            else -> FrequencyBand.UNKNOWN
        }
    
    // Calculate connection score based on signal strength, frequency, and priority
    val connectionScore: Int
        get() {
            var score = signalStrength
            
            // Prefer 5GHz over 2.4GHz
            if (frequencyBand == FrequencyBand.BAND_5GHZ) score += 10
            
            // Add priority bonus
            score += priority * 5
            
            // Bonus for stored password
            if (hasStoredPassword) score += 15
            
            // Penalty for very weak signals
            if (level < -75) score -= 20
            
            return score.coerceIn(0, 150)
        }
}

data class WiFiConnectionState(
    val isEnabled: Boolean = false,
    val connectedNetwork: WiFiNetwork? = null,
    val availableNetworks: List<WiFiNetwork> = emptyList(),
    val savedNetworks: Set<String> = emptySet(),
    val isScanning: Boolean = false,
    val lastScanTime: Long = 0L,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val autoSwitchEnabled: Boolean = true,
    val scanInterval: Long = 10000L // 10 seconds default
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
    AUTHENTICATING
}

enum class SecurityType {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA3
}

enum class FrequencyBand {
    BAND_2_4GHZ,
    BAND_5GHZ,
    UNKNOWN
}
