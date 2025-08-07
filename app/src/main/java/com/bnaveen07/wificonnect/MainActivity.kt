package com.bnaveen07.wificonnect

import android.content.pm.PackageManager
import android.content.Intent
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bnaveen07.wificonnect.ui.screen.WiFiManagerScreen
import com.bnaveen07.wificonnect.ui.screen.WiFiChatListScreen
import com.bnaveen07.wificonnect.ui.screen.LocalChatScreen
import com.bnaveen07.wificonnect.ui.theme.WiFiConnectTheme
import com.bnaveen07.wificonnect.utils.PermissionManager
import com.bnaveen07.wificonnect.viewmodel.WiFiViewModel
import com.bnaveen07.wificonnect.service.WiFiDiscoveryService
import com.bnaveen07.wificonnect.service.LocalChatService

class MainActivity : ComponentActivity() {
    
    private var hasPermissions by mutableStateOf(false)
    private lateinit var localChatService: LocalChatService
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            // Start WiFi Discovery Service when permissions are granted
            WiFiDiscoveryService.startService(this)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize LocalChatService
        localChatService = LocalChatService(this)
        
        // Check permissions
        checkAndRequestPermissions()
        
        setContent {
            WiFiConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasPermissions) {
                        AppNavigation(localChatService = localChatService)
                    } else {
                        PermissionRequiredScreen(
                            onRequestPermissions = ::checkAndRequestPermissions
                        )
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop discovery service when app is destroyed
        WiFiDiscoveryService.stopService(this)
    }
    
    private fun checkAndRequestPermissions() {
        hasPermissions = PermissionManager.hasAllPermissions(this)
        
        if (!hasPermissions) {
            val missingPermissions = PermissionManager.getMissingPermissions(this)
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Start service if permissions already granted
            WiFiDiscoveryService.startService(this)
        }
    }
}

@Composable
fun AppNavigation(localChatService: LocalChatService) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "wifi_manager"
    ) {
        composable("wifi_manager") {
            WiFiManagerScreen(
                onNavigateToChat = {
                    navController.navigate("wifi_chat_list")
                }
            )
        }
        
        composable("wifi_chat_list") {
            WiFiChatListScreen(
                onNavigateToChat = { userIp, userName ->
                    if (userIp != null && userName != null) {
                        navController.navigate("private_chat/$userIp/$userName")
                    }
                },
                onNavigateToGroupChat = {
                    navController.navigate("group_chat")
                },
                chatService = localChatService
            )
        }
        
        composable("private_chat/{userIp}/{userName}") { backStackEntry ->
            val userIp = backStackEntry.arguments?.getString("userIp")
            val userName = backStackEntry.arguments?.getString("userName")
            
            LocalChatScreen(
                userName = userName ?: "",
                userIp = userIp,
                isPrivateChat = true,
                onNavigateBack = {
                    navController.popBackStack()
                },
                chatService = localChatService
            )
        }
        
        composable("group_chat") {
            LocalChatScreen(
                userName = "Group",
                userIp = null,
                isPrivateChat = false,
                onNavigateBack = {
                    navController.popBackStack()
                },
                chatService = localChatService
            )
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