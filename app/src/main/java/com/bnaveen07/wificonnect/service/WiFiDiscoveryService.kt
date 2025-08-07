package com.bnaveen07.wificonnect.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.bnaveen07.wificonnect.R
import com.bnaveen07.wificonnect.model.*
import com.bnaveen07.wificonnect.MainActivity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class WiFiDiscoveryService : Service() {
    private val tag = "WiFiDiscoveryService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var chatService: LocalChatService
    
    // Notification channels
    private val DISCOVERY_CHANNEL_ID = "wifi_discovery_channel"
    private val MESSAGE_CHANNEL_ID = "message_channel"
    private val FOREGROUND_NOTIFICATION_ID = 1001
    
    // State management
    private val _discoveryState = MutableStateFlow(DiscoveryState())
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()
    
    private val knownUsers = mutableSetOf<String>()
    private val unreadMessages = mutableMapOf<String, Int>()
    
    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "WiFi Discovery Service created")
        
        chatService = LocalChatService(this)
        createNotificationChannels()
        startForegroundService()
        
        // Monitor chat state for new users and messages
        serviceScope.launch {
            chatService.chatState.collect { chatState ->
                handleChatStateUpdate(chatState)
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "WiFi Discovery Service started")
        
        when (intent?.action) {
            ACTION_START_DISCOVERY -> startDiscovery()
            ACTION_STOP_DISCOVERY -> stopDiscovery()
            ACTION_OPEN_CHAT -> handleOpenChat(intent)
            ACTION_MARK_READ -> handleMarkAsRead(intent)
            else -> startDiscovery()
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "WiFi Discovery Service destroyed")
        serviceScope.cancel()
        chatService.stop()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Discovery channel
            val discoveryChannel = NotificationChannel(
                DISCOVERY_CHANNEL_ID,
                "WiFi User Discovery",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new WiFi users discovered"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Message channel
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(discoveryChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }
    
    private fun startForegroundService() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, DISCOVERY_CHANNEL_ID)
            .setContentTitle("WiFi Chat Discovery")
            .setContentText("Searching for nearby users...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Open Chat",
                pendingIntent
            )
            .build()
        
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }
    
    private fun startDiscovery() {
        _discoveryState.value = _discoveryState.value.copy(isDiscovering = true)
        updateForegroundNotification("Actively discovering users...")
    }
    
    private fun stopDiscovery() {
        _discoveryState.value = _discoveryState.value.copy(isDiscovering = false)
        updateForegroundNotification("Discovery paused")
    }
    
    private fun updateForegroundNotification(status: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val usersCount = _discoveryState.value.discoveredUsers.size
        val notification = NotificationCompat.Builder(this, DISCOVERY_CHANNEL_ID)
            .setContentTitle("WiFi Chat Discovery")
            .setContentText("$status • $usersCount users found")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
    
    private suspend fun handleChatStateUpdate(chatState: ChatState) {
        // Implementation for handling chat state updates
        // This would process new users and messages
        _discoveryState.value = _discoveryState.value.copy(
            discoveredUsers = chatState.connectedUsers,
            lastUpdateTime = System.currentTimeMillis(),
            totalMessages = chatState.messages.size
        )
    }
    
    private fun handleOpenChat(intent: Intent) {
        val userIp = intent.getStringExtra("user_ip")
        val userName = intent.getStringExtra("user_name")
        val startPrivateChat = intent.getBooleanExtra("start_private_chat", false)
        
        val chatIntent = Intent(this, MainActivity::class.java).apply {
            if (userIp != null && userName != null && startPrivateChat) {
                putExtra("user_ip", userIp)
                putExtra("user_name", userName)
                putExtra("start_private_chat", true)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(chatIntent)
    }
    
    private fun handleMarkAsRead(intent: Intent) {
        val senderIp = intent.getStringExtra("message_sender")
        if (senderIp != null) {
            unreadMessages.remove(senderIp)
            
            // Cancel the notification
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.cancel(senderIp.hashCode() + 10000)
            
            _discoveryState.value = _discoveryState.value.copy(
                unreadCounts = unreadMessages.toMap()
            )
        }
    }
    
    companion object {
        const val ACTION_START_DISCOVERY = "START_DISCOVERY"
        const val ACTION_STOP_DISCOVERY = "STOP_DISCOVERY"
        const val ACTION_OPEN_CHAT = "OPEN_CHAT"
        const val ACTION_MARK_READ = "MARK_READ"
        
        fun startService(context: Context) {
            val intent = Intent(context, WiFiDiscoveryService::class.java).apply {
                action = ACTION_START_DISCOVERY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, WiFiDiscoveryService::class.java)
            context.stopService(intent)
        }
    }
}

data class DiscoveryState(
    val isDiscovering: Boolean = false,
    val discoveredUsers: List<ChatUser> = emptyList(),
    val lastUpdateTime: Long = 0L,
    val totalMessages: Int = 0,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val lastGroupMessageCount: Int = 0,
    val lastPrivateMessageCounts: Map<String, Int> = emptyMap()
)
