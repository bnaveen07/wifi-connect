package com.bnaveen07.wificonnect

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bnaveen07.wificonnect.ui.screen.WiFiManagerScreen
import com.bnaveen07.wificonnect.ui.theme.WiFiConnectTheme
import com.bnaveen07.wificonnect.utils.PermissionManager
import com.bnaveen07.wificonnect.viewmodel.WiFiViewModel

class MainActivity : ComponentActivity() {
    
    private var hasPermissions by mutableStateOf(false)
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (!hasPermissions) {
            // Show rationale or guide user to settings
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check permissions
        checkAndRequestPermissions()
        
        setContent {
            WiFiConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasPermissions) {
                        WiFiManagerScreen()
                    } else {
                        PermissionRequiredScreen(
                            onRequestPermissions = ::checkAndRequestPermissions
                        )
                    }
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        hasPermissions = PermissionManager.hasAllPermissions(this)
        
        if (!hasPermissions) {
            val missingPermissions = PermissionManager.getMissingPermissions(this)
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "WiFi Connect needs the following permissions to work properly:\n\n" +
                    "• Location access - Required to scan for WiFi networks\n" +
                    "• WiFi access - To manage WiFi connections\n" +
                    "• Notifications - To show connection status",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
    }
}