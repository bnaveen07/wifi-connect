package com.bnaveen07.wificonnect.data

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WiFiPasswordManager private constructor(private val context: Context) {
    
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            
            EncryptedSharedPreferences.create(
                "wifi_passwords",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences("wifi_passwords_fallback", Context.MODE_PRIVATE)
        }
    }
    
    // Track networks that are saved in system but we don't have password for
    private val systemSavedNetworks: SharedPreferences by lazy {
        context.getSharedPreferences("system_saved_networks", Context.MODE_PRIVATE)
    }
    
    companion object {
        @Volatile
        private var INSTANCE: WiFiPasswordManager? = null
        
        fun getInstance(context: Context): WiFiPasswordManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WiFiPasswordManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    suspend fun savePassword(ssid: String, password: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putString(ssid, password)
            .apply()
    }
    
    suspend fun getPassword(ssid: String): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(ssid, null)
    }
    
    suspend fun hasPassword(ssid: String): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.contains(ssid)
    }
    
    suspend fun removePassword(ssid: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove(ssid)
            .apply()
        // Also remove from system-saved tracking
        systemSavedNetworks.edit()
            .remove(ssid)
            .apply()
    }
    
    suspend fun getAllSavedNetworks(): Set<String> = withContext(Dispatchers.IO) {
        val appSavedNetworks = sharedPreferences.all.keys
        val systemSaved = systemSavedNetworks.all.keys
        appSavedNetworks + systemSaved
    }
    
    suspend fun clearAllPasswords() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .clear()
            .apply()
        systemSavedNetworks.edit()
            .clear()
            .apply()
    }
    
    // Check if a network is saved in system settings (not in our app)
    suspend fun isSystemSavedNetwork(ssid: String): Boolean = withContext(Dispatchers.IO) {
        systemSavedNetworks.getBoolean(ssid, false)
    }
    
    // Mark a network as system-saved (detected when we successfully connect without password)
    suspend fun markAsSystemSaved(ssid: String) = withContext(Dispatchers.IO) {
        systemSavedNetworks.edit()
            .putBoolean(ssid, true)
            .apply()
    }
    
    // Check if we can connect to this network (either we have password or it's system-saved)
    suspend fun canConnectToNetwork(ssid: String): Boolean = withContext(Dispatchers.IO) {
        hasPassword(ssid) || isSystemSavedNetwork(ssid) || isNetworkConfiguredInSystem(ssid)
    }
    
    // Check if network is configured in system (for older Android versions)
    private suspend fun isNetworkConfiguredInSystem(ssid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                val configuredNetworks = wifiManager.configuredNetworks
                return@withContext configuredNetworks?.any { 
                    it.SSID?.removeSurrounding("\"") == ssid 
                } ?: false
            } else {
                // For Android 10+, we can't access configured networks
                // We rely on connection success to detect system-saved networks
                return@withContext false
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    // Get all system-saved networks (detected through successful connections)
    suspend fun getSystemSavedNetworks(): Set<String> = withContext(Dispatchers.IO) {
        systemSavedNetworks.all.keys
    }
    
    // Update network status based on connection success
    suspend fun updateNetworkConnectionStatus(ssid: String, connectedSuccessfully: Boolean, hadPassword: Boolean) = withContext(Dispatchers.IO) {
        if (connectedSuccessfully && !hadPassword && !hasPassword(ssid)) {
            // Successfully connected without providing password and we don't have it saved
            // This means it's saved in system settings
            markAsSystemSaved(ssid)
        }
    }
}
