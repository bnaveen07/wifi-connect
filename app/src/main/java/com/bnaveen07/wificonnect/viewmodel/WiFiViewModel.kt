package com.bnaveen07.wificonnect.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bnaveen07.wificonnect.data.WiFiPasswordManager
import com.bnaveen07.wificonnect.model.WiFiConnectionState
import com.bnaveen07.wificonnect.model.WiFiNetwork
import com.bnaveen07.wificonnect.service.WiFiManagerService
import kotlinx.coroutines.launch

class WiFiViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context: Context = application.applicationContext
    private val passwordManager = WiFiPasswordManager.getInstance(context)
    
    private val _isServiceRunning = MutableLiveData(false)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning
    
    private val _selectedNetwork = MutableLiveData<WiFiNetwork?>()
    val selectedNetwork: LiveData<WiFiNetwork?> = _selectedNetwork
    
    private val _autoSwitchEnabled = MutableLiveData(true)
    val autoSwitchEnabled: LiveData<Boolean> = _autoSwitchEnabled
    
    private val _fastScanningEnabled = MutableLiveData(true)
    val fastScanningEnabled: LiveData<Boolean> = _fastScanningEnabled
    
    private val _batteryOptimizationEnabled = MutableLiveData(true)
    val batteryOptimizationEnabled: LiveData<Boolean> = _batteryOptimizationEnabled
    
    val connectionState: LiveData<WiFiConnectionState> = WiFiManagerService.connectionState
    
    init {
        // Initialize with default state
        WiFiManagerService.connectionState.value = WiFiConnectionState()
        
        // Auto-connect to saved networks on startup
        autoConnectToSavedNetworks()
    }
    
    private fun autoConnectToSavedNetworks() {
        viewModelScope.launch {
            try {
                // Start service first to begin scanning
                WiFiManagerService.start(context)
                _isServiceRunning.value = true
                
                // Get saved passwords
                val savedNetworks = passwordManager.getAllSavedNetworks()
                if (savedNetworks.isNotEmpty()) {
                    // Service will automatically handle connecting to saved networks
                    // based on signal strength through its intelligent connection logic
                }
            } catch (e: Exception) {
                _isServiceRunning.value = false
            }
        }
    }
    
    fun startWiFiService() {
        viewModelScope.launch {
            try {
                WiFiManagerService.start(context)
                _isServiceRunning.value = true
            } catch (e: Exception) {
                _isServiceRunning.value = false
            }
        }
    }
    
    fun stopWiFiService() {
        viewModelScope.launch {
            try {
                WiFiManagerService.stop(context)
                _isServiceRunning.value = false
            } catch (e: Exception) {
                // Service might already be stopped
            }
        }
    }
    
    fun selectNetwork(network: WiFiNetwork) {
        viewModelScope.launch {
            // Check if we can already connect to this network
            val canConnect = passwordManager.canConnectToNetwork(network.ssid)
            
            if (network.isSecured && !canConnect) {
                // Network is secured and we don't have password or system saved - ask for password
                _selectedNetwork.value = network
            } else {
                // Network is open OR we have credentials for it - connect directly
                connectToNetwork(network, "", false)
            }
        }
    }
    
    fun clearSelectedNetwork() {
        _selectedNetwork.value = null
    }
    
    fun connectToNetwork(network: WiFiNetwork, password: String, shouldSave: Boolean) {
        viewModelScope.launch {
            try {
                // Save password if requested
                if (shouldSave && network.isSecured && password.isNotBlank()) {
                    passwordManager.savePassword(network.ssid, password)
                }
                
                // Clear selected network
                clearSelectedNetwork()
                
                // For secured networks without password, check if it might be system-saved
                if (network.isSecured && password.isEmpty()) {
                    // Let the service attempt connection - it will try system-saved credentials
                    // This handles the case where user clicks on a network that's saved in Android settings
                }
                
                // Trigger manual connection through service
                WiFiManagerService.connectToSpecificNetwork(network, password)
                
            } catch (e: Exception) {
                // Handle error - network might not be reachable or password incorrect
            }
        }
    }
    
    fun forgetNetwork(ssid: String) {
        viewModelScope.launch {
            try {
                passwordManager.removePassword(ssid)
                // Trigger a refresh of the connection state
                refreshNetworks()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun toggleAutoSwitch() {
        val newValue = !(_autoSwitchEnabled.value ?: true)
        _autoSwitchEnabled.value = newValue
        // Send this preference to the service
        WiFiManagerService.updateAutoSwitchSetting(newValue)
    }
    
    fun toggleFastScanning() {
        val newValue = !(_fastScanningEnabled.value ?: true)
        _fastScanningEnabled.value = newValue
        // Send this preference to the service
        WiFiManagerService.updateScanInterval(newValue)
    }
    
    fun toggleBatteryOptimization() {
        val newValue = !(_batteryOptimizationEnabled.value ?: true)
        _batteryOptimizationEnabled.value = newValue
        // Send this preference to the service
        WiFiManagerService.updateBatteryOptimization(newValue)
    }
    
    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing
    
    fun refreshNetworks() {
        if (_isServiceRunning.value != true) return
        
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                // Force a network scan through the service
                WiFiManagerService.forceNetworkScan()
                // Reset refreshing state after a short delay
                kotlinx.coroutines.delay(2000)
                _isRefreshing.value = false
            } catch (e: Exception) {
                _isRefreshing.value = false
            }
        }
    }
    
    fun getSavedNetworks(): LiveData<Set<String>> {
        val savedNetworks = MutableLiveData<Set<String>>()
        viewModelScope.launch {
            val networks = passwordManager.getAllSavedNetworks()
            savedNetworks.postValue(networks)
        }
        return savedNetworks
    }
}
