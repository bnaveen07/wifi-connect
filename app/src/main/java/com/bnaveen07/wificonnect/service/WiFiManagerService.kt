package com.bnaveen07.wificonnect.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.bnaveen07.wificonnect.MainActivity
import com.bnaveen07.wificonnect.R
import com.bnaveen07.wificonnect.data.WiFiPasswordManager
import com.bnaveen07.wificonnect.model.ConnectionStatus
import com.bnaveen07.wificonnect.model.WiFiConnectionState
import com.bnaveen07.wificonnect.model.WiFiNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WiFiManagerService : Service() {
    
    companion object {
        const val CHANNEL_ID = "WiFiManagerService"
        const val NOTIFICATION_ID = 1
        const val FAST_SCAN_INTERVAL_MS = 8000L // 8 seconds for better responsiveness
        const val NORMAL_SCAN_INTERVAL_MS = 15000L // 15 seconds for battery saving
        const val BATTERY_SAVER_SCAN_INTERVAL_MS = 30000L // 30 seconds for low battery
        const val CONNECTION_TIMEOUT_MS = 25000L // 25 seconds
        const val MIN_SIGNAL_STRENGTH = -75 // Improved minimum threshold
        const val SIGNAL_IMPROVEMENT_THRESHOLD = 8 // dBm improvement needed to switch
        const val CONNECTION_STABILITY_TIME = 15000L // 15 seconds before considering switch
        
        // Battery-based thresholds
        const val LOW_BATTERY_THRESHOLD = 20 // Below 20% is considered low battery
        const val CRITICAL_BATTERY_THRESHOLD = 10 // Below 10% is critical
        const val HIGH_BATTERY_THRESHOLD = 50 // Above 50% allows aggressive scanning
        
        val connectionState = MutableLiveData<WiFiConnectionState>()
        private var serviceInstance: WiFiManagerService? = null
        
        fun start(context: Context) {
            val intent = Intent(context, WiFiManagerService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, WiFiManagerService::class.java)
            context.stopService(intent)
        }
        
        fun forceNetworkScan() {
            serviceInstance?.performManualScan()
        }
        
        fun connectToSpecificNetwork(network: WiFiNetwork, password: String) {
            serviceInstance?.connectToSpecificNetworkInternal(network, password)
        }
        
        fun updateAutoSwitchSetting(enabled: Boolean) {
            serviceInstance?.updateAutoSwitchEnabled(enabled)
        }
        
        fun updateScanInterval(fastScanning: Boolean) {
            serviceInstance?.updateScanInterval(fastScanning)
        }
        
        fun updateBatteryOptimization(enabled: Boolean) {
            serviceInstance?.updateBatteryOptimization(enabled)
        }
    }
    
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var passwordManager: WiFiPasswordManager
    private lateinit var batteryManager: BatteryManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentState = WiFiConnectionState()
    private var lastConnectionTime = 0L
    private var connectionAttempts = mutableMapOf<String, Int>()
    
    // Settings
    private var autoSwitchEnabled = true
    private var fastScanningEnabled = true
    private var currentScanInterval = FAST_SCAN_INTERVAL_MS
    private var batteryOptimizationEnabled = true
    private var currentBatteryLevel = 100
    
    // Notification control
    private var lastNotificationText = ""
    private var lastNotificationTime = 0L
    private val NOTIFICATION_UPDATE_INTERVAL = 10000L // Update notification at most every 10 seconds
    
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    processScanResults()
                }
            }
        }
    }
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                currentBatteryLevel = (level * 100 / scale.toFloat()).toInt()
                
                // Adjust scanning behavior based on battery level
                adjustScanningForBattery()
            }
        }
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            updateConnectionStatus()
        }
        
        override fun onLost(network: Network) {
            super.onLost(network)
            updateConnectionStatus()
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            updateConnectionStatus()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        serviceInstance = this
        
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        passwordManager = WiFiPasswordManager.getInstance(this)
        
        createNotificationChannel()
        
        // Register receivers
        val scanFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, scanFilter)
        
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, batteryFilter)
        
        // Initialize battery level
        updateBatteryLevel()
        
        // Register network callback
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        // Initialize state with saved networks
        serviceScope.launch {
            val savedNetworks = passwordManager.getAllSavedNetworks()
            // Import system-saved networks
            importSystemSavedNetworks()
            currentState = currentState.copy(savedNetworks = savedNetworks)
            updateState()
        }
        
        // Initialize connection status
        updateConnectionStatus()
        
        // Start scanning
        startPeriodicScanning()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        
        serviceInstance = null
        
        scanJob?.cancel()
        connectionJob?.cancel()
        
        try {
            unregisterReceiver(wifiScanReceiver)
            unregisterReceiver(batteryReceiver)
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Receivers might already be unregistered
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WiFi Manager Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Manages WiFi connections automatically"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val connectedNetwork = currentState.connectedNetwork
        val contentText = when {
            connectedNetwork != null -> "Connected to ${connectedNetwork.ssid}"
            currentState.isScanning -> "Scanning for networks..."
            else -> "Monitoring WiFi connections"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Manager Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(force: Boolean = false) {
        val connectedNetwork = currentState.connectedNetwork
        val currentText = when {
            connectedNetwork != null -> "Connected to ${connectedNetwork.ssid}"
            currentState.isScanning -> "Scanning for networks..."
            else -> "Monitoring WiFi connections"
        }
        
        val currentTime = System.currentTimeMillis()
        val shouldUpdate = force || 
                          currentText != lastNotificationText || 
                          (currentTime - lastNotificationTime) > NOTIFICATION_UPDATE_INTERVAL
        
        if (shouldUpdate) {
            notificationManager.notify(NOTIFICATION_ID, createNotification())
            lastNotificationText = currentText
            lastNotificationTime = currentTime
        }
    }
    
    private fun startPeriodicScanning() {
        scanJob = serviceScope.launch {
            while (isActive) {
                performWifiScan()
                // Also update connection status periodically
                updateConnectionStatus()
                delay(currentScanInterval)
            }
        }
    }
    
    private fun performWifiScan() {
        if (checkLocationPermission()) {
            currentState = currentState.copy(isScanning = true)
            updateState()
            
            val success = wifiManager.startScan()
            if (!success) {
                // Scan failed, try again later
                currentState = currentState.copy(isScanning = false)
                updateState()
            }
        }
    }
    
    fun performManualScan() {
        // Force an immediate scan
        performWifiScan()
    }
    
    fun connectToSpecificNetworkInternal(network: WiFiNetwork, password: String) {
        serviceScope.launch {
            try {
                // Update network with provided password info
                val networkWithPassword = if (password.isNotBlank()) {
                    network.copy(hasStoredPassword = true)
                } else {
                    network
                }
                
                // Temporarily save password if provided
                if (password.isNotBlank() && network.isSecured) {
                    passwordManager.savePassword(network.ssid, password)
                }
                
                // Trigger connection
                connectToNetworkWithPassword(networkWithPassword)
                
            } catch (e: Exception) {
                // Connection failed
            }
        }
    }
    
    private fun processScanResults() {
        if (!checkLocationPermission()) return
        
        val scanResults = wifiManager.scanResults ?: emptyList()
        
        serviceScope.launch {
            val savedNetworks = passwordManager.getAllSavedNetworks()
            
            val networks = scanResults.map { scanResult ->
                val ssid = scanResult.SSID ?: "Unknown"
                val hasAppPassword = passwordManager.hasPassword(ssid)
                val isSystemSaved = passwordManager.isSystemSavedNetwork(ssid)
                val canConnect = passwordManager.canConnectToNetwork(ssid)
                
                WiFiNetwork(
                    ssid = ssid,
                    bssid = scanResult.BSSID,
                    level = scanResult.level,
                    frequency = scanResult.frequency,
                    capabilities = scanResult.capabilities,
                    hasStoredPassword = canConnect // True if we can connect (app password or system saved)
                )
            }.filter { it.ssid.isNotBlank() && it.ssid != "Unknown" }
                .groupBy { it.ssid }
                .map { (_, networksWithSameSSID) ->
                    // Keep the strongest signal for each SSID
                    networksWithSameSSID.maxByOrNull { it.level } ?: networksWithSameSSID.first()
                }
                .sortedByDescending { it.connectionScore } // Use connection score instead of just signal
            
            currentState = currentState.copy(
                availableNetworks = networks,
                savedNetworks = savedNetworks,
                isScanning = false,
                lastScanTime = System.currentTimeMillis()
            )
            
            updateState()
            updateNotification() // Keep this one - scan results are important updates
            
            // Intelligent network switching
            evaluateIntelligentNetworkSwitching(networks)
        }
    }
    
    private fun evaluateIntelligentNetworkSwitching(networks: List<WiFiNetwork>) {
        if (!autoSwitchEnabled) return
        
        val currentNetwork = currentState.connectedNetwork
        val now = System.currentTimeMillis()
        
        // Don't switch too soon after last connection
        if (now - lastConnectionTime < CONNECTION_STABILITY_TIME) return
        
        serviceScope.launch {
            // Filter to viable networks (good signal and have password if needed)
            // Use battery-aware signal threshold
            val signalThreshold = getBatteryAwareSignalThreshold()
            
            val viableNetworks = networks.filter { network ->
                network.level > signalThreshold && 
                (!network.isSecured || passwordManager.canConnectToNetwork(network.ssid)) &&
                connectionAttempts.getOrDefault(network.ssid, 0) < 3 // Max 3 attempts
            }
            
            val bestNetwork = viableNetworks.maxByOrNull { it.connectionScore }
            
            when {
                // No current connection, connect to best available
                currentNetwork == null && bestNetwork != null -> {
                    connectToNetworkWithPassword(bestNetwork)
                }
                
                // Current connection exists, evaluate if we should switch
                currentNetwork != null && bestNetwork != null -> {
                    val currentNetworkInScan = networks.find { it.ssid == currentNetwork.ssid }
                    
                    if (currentNetworkInScan != null && shouldSwitchNetwork(currentNetworkInScan, bestNetwork)) {
                        connectToNetworkWithPassword(bestNetwork)
                    }
                }
            }
        }
    }
    
    private fun shouldSwitchNetwork(current: WiFiNetwork, candidate: WiFiNetwork): Boolean {
        // Don't switch to the same network
        if (current.ssid == candidate.ssid) return false
        
        // Get battery-aware thresholds
        val criticalSignalLevel = getBatteryAwareSignalThreshold()
        val improvementThreshold = getBatteryAwareImprovementThreshold()
        
        // Switch if current signal is very weak (battery-aware)
        if (current.level < criticalSignalLevel) return true
        
        // Switch if candidate has significantly better score (battery-aware threshold)
        val scoreDifference = candidate.connectionScore - current.connectionScore
        val minScoreDifference = when {
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> 30 // Need major improvement
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> 25 // Moderate improvement
            else -> 20 // Normal threshold
        }
        if (scoreDifference > minScoreDifference) return true
        
        // Switch if candidate has much better signal and is saved (battery-aware)
        val signalDifference = candidate.level - current.level
        if (signalDifference > improvementThreshold && candidate.hasStoredPassword) return true
        
        // Battery-aware 5GHz switching
        if (current.frequency < 5000 && candidate.frequency >= 5000) {
            val minSignalDifferenceFor5G = when {
                currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> 5 // More conservative on low battery
                else -> -5 // Normal aggressive switching
            }
            if (signalDifference > minSignalDifferenceFor5G) return true
        }
        
        return false
    }
    
    private fun connectToNetworkWithPassword(network: WiFiNetwork) {
        if (connectionJob?.isActive == true) return
        
        connectionJob = serviceScope.launch {
            try {
                currentState = currentState.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    connectedNetwork = network.copy(isConnecting = true)
                )
                updateState()
                updateNotification(force = true) // Force update when starting connection
                
                val password = if (network.isSecured && passwordManager.hasPassword(network.ssid)) {
                    passwordManager.getPassword(network.ssid) ?: ""
                } else {
                    ""
                }
                
                val hadAppPassword = password.isNotEmpty()
                
                // Try connection with stored password first
                var success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connectToNetworkModern(network, password)
                } else {
                    connectToNetworkLegacy(network, password)
                }
                
                // If connection failed and we don't have a stored password,
                // try connecting without password (system might have it saved)
                if (!success && network.isSecured && password.isEmpty()) {
                    success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        connectToNetworkModernWithoutPassword(network)
                    } else {
                        // For legacy, try to connect using system saved configuration
                        connectToNetworkLegacySystemSaved(network)
                    }
                }
                
                if (success) {
                    lastConnectionTime = System.currentTimeMillis()
                    connectionAttempts.remove(network.ssid) // Reset attempt count on success
                    
                    // Update network connection status for future reference
                    passwordManager.updateNetworkConnectionStatus(
                        network.ssid, 
                        true, 
                        hadAppPassword
                    )
                    
                    // Wait a bit for connection to establish
                    delay(3000)
                    updateConnectionStatus()
                } else {
                    // Increment attempt count on failure
                    connectionAttempts[network.ssid] = connectionAttempts.getOrDefault(network.ssid, 0) + 1
                    
                    // Update network connection status
                    passwordManager.updateNetworkConnectionStatus(
                        network.ssid, 
                        false, 
                        hadAppPassword
                    )
                    
                    currentState = currentState.copy(
                        connectionStatus = ConnectionStatus.FAILED,
                        connectedNetwork = null
                    )
                    updateState()
                }
                
            } catch (e: Exception) {
                connectionAttempts[network.ssid] = connectionAttempts.getOrDefault(network.ssid, 0) + 1
                
                // Update network connection status
                serviceScope.launch {
                    passwordManager.updateNetworkConnectionStatus(
                        network.ssid, 
                        false, 
                        false
                    )
                }
                
                currentState = currentState.copy(
                    connectionStatus = ConnectionStatus.FAILED,
                    connectedNetwork = null
                )
                updateState()
            }
        }
    }
    
    private fun connectToNetworkModern(network: WiFiNetwork, password: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val specifierBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(network.ssid)
                
                if (network.isSecured && password.isNotEmpty()) {
                    specifierBuilder.setWpa2Passphrase(password)
                }
                
                val specifier = specifierBuilder.build()
                
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build()
                
                connectivityManager.requestNetwork(request, networkCallback)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
    
    @Suppress("DEPRECATION")
    private fun connectToNetworkLegacy(network: WiFiNetwork, password: String): Boolean {
        return try {
            val config = WifiConfiguration().apply {
                SSID = "\"${network.ssid}\""
                if (network.isSecured && password.isNotEmpty()) {
                    when {
                        network.capabilities.contains("WPA") -> {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                            preSharedKey = "\"$password\""
                        }
                        network.capabilities.contains("WEP") -> {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                            wepKeys[0] = "\"$password\""
                            wepTxKeyIndex = 0
                        }
                        else -> {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        }
                    }
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }
            
            val networkId = wifiManager.addNetwork(config)
            if (networkId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun connectToNetworkModernWithoutPassword(network: WiFiNetwork): Boolean {
        return try {
            // For modern Android, try to connect using just SSID
            // This will work if the network is already saved in system settings
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(network.ssid)
                .build()
                
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()
                
            // This will trigger system to use saved credentials if available
            connectivityManager.requestNetwork(request, networkCallback)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun connectToNetworkLegacySystemSaved(network: WiFiNetwork): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Try to find existing configuration for this network
                @Suppress("DEPRECATION")
                val configuredNetworks = wifiManager.configuredNetworks
                val existingConfig = configuredNetworks?.find { 
                    it.SSID?.removeSurrounding("\"") == network.ssid 
                }
                
                if (existingConfig != null) {
                    // Found existing configuration, try to connect
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(existingConfig.networkId, true)
                    wifiManager.reconnect()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun updateConnectionStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkInfo = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val isWifiConnected = networkInfo?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wifiInfo = wifiManager.connectionInfo
        
        if (isWifiConnected && wifiInfo != null) {
            val connectedNetwork = WiFiNetwork(
                ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: "Unknown",
                bssid = wifiInfo.bssid ?: "",
                level = wifiInfo.rssi,
                frequency = wifiInfo.frequency,
                capabilities = "",
                isConnected = true
            )
            
            currentState = currentState.copy(
                isEnabled = wifiManager.isWifiEnabled,
                connectedNetwork = connectedNetwork,
                connectionStatus = ConnectionStatus.CONNECTED
            )
        } else {
            currentState = currentState.copy(
                isEnabled = wifiManager.isWifiEnabled,
                connectedNetwork = null,
                connectionStatus = ConnectionStatus.DISCONNECTED
            )
        }
        
        updateState()
        updateNotification(force = true) // Force update for connection status changes
    }
    
    private fun updateState() {
        handler.post {
            connectionState.value = currentState
        }
    }
    
    private suspend fun importSystemSavedNetworks() {
        try {
            // For Android 10+ (API 29+), we cannot access saved WiFi configurations
            // due to privacy restrictions. However, we can detect saved networks
            // in other ways.
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // For older Android versions, try to access configured networks
                @Suppress("DEPRECATION")
                val configuredNetworks = wifiManager.configuredNetworks
                configuredNetworks?.forEach { config ->
                    val ssid = config.SSID?.removeSurrounding("\"")
                    if (!ssid.isNullOrBlank()) {
                        // Mark network as system-saved (but we don't have the password)
                        // This will at least let users know which networks are saved
                        // and we can attempt connection without password
                        
                        // We cannot save the actual password as it's not accessible
                        // But we can mark it as having a saved configuration
                        val hasStoredPassword = passwordManager.hasPassword(ssid)
                        if (!hasStoredPassword) {
                            // Don't save empty password, just note that it's system-saved
                            // The connection logic will handle system-saved networks differently
                        }
                    }
                }
            } else {
                // For Android 10+, we rely on connection attempts to identify
                // saved networks. When a connection succeeds without us providing
                // a password, we know it was system-saved.
                
                // Check currently connected network - if we're connected without
                // having the password stored, it's system-saved
                val activeNetwork = connectivityManager.activeNetwork
                val networkInfo = connectivityManager.getNetworkCapabilities(activeNetwork)
                val isWifiConnected = networkInfo?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                
                if (isWifiConnected) {
                    val wifiInfo = wifiManager.connectionInfo
                    if (wifiInfo != null) {
                        val connectedSsid = wifiInfo.ssid?.removeSurrounding("\"")
                        if (!connectedSsid.isNullOrBlank() && !passwordManager.hasPassword(connectedSsid)) {
                            // This network is connected but we don't have its password stored
                            // This indicates it's saved in system settings
                            // We'll handle this in the connection logic
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Failed to import system networks, continue with manual management
        }
    }
    
    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun updateAutoSwitchEnabled(enabled: Boolean) {
        autoSwitchEnabled = enabled
        // Update the current state
        currentState = currentState.copy(autoSwitchEnabled = enabled)
        updateState()
    }
    
    fun updateScanInterval(fastScanning: Boolean) {
        fastScanningEnabled = fastScanning
        updateOptimalScanInterval()
        
        // Restart scanning with new interval
        scanJob?.cancel()
        startPeriodicScanning()
    }
    
    fun updateBatteryOptimization(enabled: Boolean) {
        batteryOptimizationEnabled = enabled
        updateOptimalScanInterval()
        
        // Restart scanning with updated settings
        scanJob?.cancel()
        startPeriodicScanning()
    }
    
    private fun updateBatteryLevel() {
        try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryIntent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                currentBatteryLevel = (level * 100 / scale.toFloat()).toInt()
            }
        } catch (e: Exception) {
            currentBatteryLevel = 100 // Assume full battery if can't read
        }
    }
    
    private fun adjustScanningForBattery() {
        if (batteryOptimizationEnabled) {
            updateOptimalScanInterval()
            
            // If battery is critically low, restart scanning with longer interval
            if (currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD) {
                scanJob?.cancel()
                startPeriodicScanning()
            }
        }
    }
    
    private fun updateOptimalScanInterval() {
        currentScanInterval = when {
            !batteryOptimizationEnabled -> {
                // Battery optimization disabled, use user preference
                if (fastScanningEnabled) FAST_SCAN_INTERVAL_MS else NORMAL_SCAN_INTERVAL_MS
            }
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> {
                // Critical battery: very slow scanning
                BATTERY_SAVER_SCAN_INTERVAL_MS
            }
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> {
                // Low battery: force normal scanning regardless of user preference
                NORMAL_SCAN_INTERVAL_MS
            }
            currentBatteryLevel >= HIGH_BATTERY_THRESHOLD -> {
                // High battery: allow fast scanning if enabled
                if (fastScanningEnabled) FAST_SCAN_INTERVAL_MS else NORMAL_SCAN_INTERVAL_MS
            }
            else -> {
                // Medium battery: normal scanning
                NORMAL_SCAN_INTERVAL_MS
            }
        }
    }
    
    private fun getBatteryAwareSignalThreshold(): Int {
        return when {
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> -65 // Only switch for very strong signals
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> -70 // Moderate threshold
            else -> MIN_SIGNAL_STRENGTH // Normal threshold (-75)
        }
    }
    
    private fun getBatteryAwareImprovementThreshold(): Int {
        return when {
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> 15 // Need significant improvement
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> 12 // Moderate improvement needed
            else -> SIGNAL_IMPROVEMENT_THRESHOLD // Normal threshold (8 dBm)
        }
    }
}
