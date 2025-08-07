package com.bnaveen07.wificonnect.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import com.bnaveen07.wificonnect.model.*
import com.bnaveen07.wificonnect.utils.ChatEncryption
import com.bnaveen07.wificonnect.data.ChatDataManager

class LocalChatService(private val context: Context) {
    private val tag = "LocalChatService"
    private val discoveryPort = 8888
    private val chatPort = 8889
    
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var isRunning = false
    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    private val connectedClients = ConcurrentHashMap<String, Socket>()
    private val discoveredUsers = ConcurrentHashMap<String, ChatUser>()
    private val privateMessageHistory = ConcurrentHashMap<String, MutableList<ChatMessage>>()
    private val sessionToken = ChatEncryption.generateSessionToken()
    private val dataManager = ChatDataManager(context)
    
    init {
        // Generate encryption keys
        ChatEncryption.generateKeyPair()
        
        // Load saved data
        loadSavedChatData()
    }
    
    fun getSavedUserName(): String {
        return dataManager.getMyName()
    }
    
    fun hasSavedUserName(): Boolean {
        return getSavedUserName().isNotEmpty()
    }
    
    fun startChatService(userName: String) {
        if (isRunning) {
            Log.w(tag, "Chat service already running")
            return
        }
        
        serviceScope.launch {
            try {
                Log.d(tag, "Starting chat service for user: $userName")
                isRunning = true
                
                // Save the user name for future use
                dataManager.saveMyName(userName)
                
                _chatState.value = _chatState.value.copy(
                    isEnabled = true,
                    myName = userName,
                    connectionStatus = ChatConnectionStatus.CONNECTING
                )
                
                // Get IP address first
                val myIp = getLocalIpAddress()
                Log.d(tag, "Local IP address: $myIp")
                
                if (myIp == "127.0.0.1") {
                    Log.e(tag, "Could not get valid WiFi IP address")
                    _chatState.value = _chatState.value.copy(
                        connectionStatus = ChatConnectionStatus.ERROR
                    )
                    return@launch
                }
                
                _chatState.value = _chatState.value.copy(
                    myIpAddress = myIp
                )
                
                // Start server for incoming connections
                Log.d(tag, "Starting chat server...")
                startChatServer()
                
                // Start discovery service
                Log.d(tag, "Starting discovery service...")
                startDiscoveryService(userName)
                
                _chatState.value = _chatState.value.copy(
                    connectionStatus = ChatConnectionStatus.CONNECTED
                )
                
                Log.d(tag, "Chat service started successfully on IP: $myIp")
                
            } catch (e: Exception) {
                Log.e(tag, "Error starting chat service", e)
                _chatState.value = _chatState.value.copy(
                    connectionStatus = ChatConnectionStatus.ERROR,
                    isEnabled = false
                )
                isRunning = false
            }
        }
    }
    
    fun stopChatService() {
        isRunning = false
        
        try {
            serverSocket?.close()
            discoverySocket?.close()
            connectedClients.values.forEach { it.close() }
            connectedClients.clear()
            discoveredUsers.clear()
            
            _chatState.value = ChatState()
            
            serviceScope.cancel()
            serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            
            Log.d(tag, "Chat service stopped")
            
        } catch (e: Exception) {
            Log.e(tag, "Error stopping chat service", e)
        }
    }
    
    private suspend fun startChatServer() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(chatPort)
                
                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        handleNewClient(clientSocket)
                    } catch (e: SocketException) {
                        if (isRunning) {
                            Log.e(tag, "Server socket error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in chat server", e)
            }
        }
    }
    
    private fun handleNewClient(clientSocket: Socket) {
        serviceScope.launch {
            try {
                val clientIp = clientSocket.inetAddress.hostAddress ?: return@launch
                connectedClients[clientIp] = clientSocket
                
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                
                while (isRunning && !clientSocket.isClosed) {
                    val message = reader.readLine() ?: break
                    handleIncomingMessage(message, clientIp)
                }
                
            } catch (e: Exception) {
                Log.e(tag, "Error handling client", e)
            } finally {
                val clientIp = clientSocket.inetAddress.hostAddress
                clientIp?.let { connectedClients.remove(it) }
                clientSocket.close()
            }
        }
    }
    
    private fun handleIncomingMessage(messageJson: String, senderIp: String) {
        try {
            val json = JSONObject(messageJson)
            val type = json.getString("type")
            
            when (type) {
                "chat_message" -> {
                    val isPrivate = json.optBoolean("isPrivate", false)
                    val recipientIp = json.optString("recipientIp", "")
                    val isEncrypted = json.optBoolean("isEncrypted", false)
                    var content = json.getString("content")
                    
                    // Decrypt if encrypted
                    if (isEncrypted) {
                        content = ChatEncryption.decryptMessage(content, senderIp)
                    }
                    
                    val chatMessage = ChatMessage(
                        id = json.getString("id"),
                        content = content,
                        senderName = json.getString("senderName"),
                        senderIp = senderIp,
                        timestamp = json.getLong("timestamp"),
                        isFromMe = false,
                        isPrivate = isPrivate,
                        recipientIp = recipientIp,
                        isEncrypted = isEncrypted
                    )
                    
                    if (isPrivate) {
                        // Handle private message
                        val currentPrivateMessages = privateMessageHistory.getOrPut(senderIp) { mutableListOf() }
                        currentPrivateMessages.add(chatMessage)
                        
                        // Auto-save private messages
                        dataManager.savePrivateMessages(senderIp, currentPrivateMessages)
                        
                        _chatState.value = _chatState.value.copy(
                            privateMessages = privateMessageHistory.toMap()
                        )
                    } else {
                        // Handle group message
                        val currentMessages = _chatState.value.messages.toMutableList()
                        currentMessages.add(chatMessage)
                        
                        // Auto-save group messages
                        dataManager.saveGroupMessages(currentMessages)
                        
                        _chatState.value = _chatState.value.copy(
                            messages = currentMessages
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing incoming message", e)
        }
    }
    
    private suspend fun startDiscoveryService(userName: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Creating discovery socket on port $discoveryPort")
                discoverySocket = DatagramSocket(discoveryPort)
                discoverySocket!!.broadcast = true
                discoverySocket!!.soTimeout = 5000 // 5 second timeout for receive
                
                Log.d(tag, "Discovery socket created successfully")
                
                // Start discovery broadcast
                serviceScope.launch {
                    var broadcastCount = 0
                    while (isRunning) {
                        try {
                            broadcastDiscovery(userName)
                            broadcastCount++
                            Log.d(tag, "Discovery broadcast #$broadcastCount sent")
                            
                            // Also try direct connection to common IP ranges for better discovery
                            if (broadcastCount % 3 == 0) { // Every 3rd broadcast
                                tryDirectDiscovery(userName)
                            }
                            
                            delay(5000) // Broadcast every 5 seconds
                        } catch (e: Exception) {
                            Log.e(tag, "Error in discovery broadcast", e)
                        }
                    }
                }
                
                // Listen for discovery responses
                val buffer = ByteArray(1024)
                var messageCount = 0
                while (isRunning && !discoverySocket!!.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        discoverySocket!!.receive(packet)
                        messageCount++
                        Log.d(tag, "Received discovery message #$messageCount from ${packet.address.hostAddress}")
                        handleDiscoveryMessage(packet)
                    } catch (e: SocketTimeoutException) {
                        // Timeout is expected, continue listening
                        continue
                    } catch (e: SocketException) {
                        if (isRunning) {
                            Log.e(tag, "Discovery socket error", e)
                        }
                        break
                    } catch (e: Exception) {
                        Log.e(tag, "Error receiving discovery message", e)
                    }
                }
                
                Log.d(tag, "Discovery service loop ended")
                
            } catch (e: BindException) {
                Log.e(tag, "Discovery port $discoveryPort is already in use", e)
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Error in discovery service", e)
                throw e
            }
        }
    }
    
    private fun broadcastDiscovery(userName: String) {
        try {
            val message = JSONObject().apply {
                put("type", "discovery")
                put("userName", userName)
                put("deviceName", getDeviceName())
                put("publicKey", ChatEncryption.getPublicKey())
                put("sessionToken", sessionToken)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            val broadcastAddress = getBroadcastAddress()
            Log.d(tag, "Broadcasting discovery to: ${broadcastAddress.hostAddress}")
            
            val packet = DatagramPacket(
                message.toByteArray(),
                message.length,
                broadcastAddress,
                discoveryPort
            )
            
            discoverySocket?.send(packet)
            Log.d(tag, "Discovery broadcast sent successfully")
            
        } catch (e: Exception) {
            Log.e(tag, "Error broadcasting discovery: ${e.message}", e)
        }
    }
    
    private fun handleDiscoveryMessage(packet: DatagramPacket) {
        try {
            val message = String(packet.data, 0, packet.length)
            val json = JSONObject(message)
            val type = json.getString("type")
            
            if (type == "discovery") {
                val senderIp = packet.address.hostAddress ?: return
                val myIp = getLocalIpAddress()
                
                Log.d(tag, "Processing discovery from $senderIp (my IP: $myIp)")
                
                // Don't add ourselves
                if (senderIp == myIp) {
                    Log.d(tag, "Ignoring discovery from self")
                    return
                }
                
                val userName = json.getString("userName")
                val deviceName = json.getString("deviceName")
                
                val user = ChatUser(
                    name = userName,
                    ipAddress = senderIp,
                    deviceName = deviceName,
                    publicKey = json.optString("publicKey", ""),
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                
                val wasNewUser = !discoveredUsers.containsKey(senderIp)
                discoveredUsers[senderIp] = user
                
                val currentUsers = discoveredUsers.values.toList()
                _chatState.value = _chatState.value.copy(
                    connectedUsers = currentUsers
                )
                
                Log.d(tag, "User ${if (wasNewUser) "added" else "updated"}: $userName from $senderIp (total users: ${currentUsers.size})")
                
                // Send discovery response
                sendDiscoveryResponse(packet.address)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling discovery message: ${e.message}", e)
        }
    }
    
    private fun sendDiscoveryResponse(address: InetAddress) {
        try {
            val response = JSONObject().apply {
                put("type", "discovery")
                put("userName", _chatState.value.myName)
                put("deviceName", getDeviceName())
                put("publicKey", ChatEncryption.getPublicKey())
                put("sessionToken", sessionToken)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            val packet = DatagramPacket(
                response.toByteArray(),
                response.length,
                address,
                discoveryPort
            )
            
            discoverySocket?.send(packet)
            
        } catch (e: Exception) {
            Log.e(tag, "Error sending discovery response", e)
        }
    }
    
    private fun tryDirectDiscovery(userName: String) {
        serviceScope.launch {
            try {
                val myIp = getLocalIpAddress()
                val networkPrefix = myIp.substringBeforeLast(".")
                
                Log.d(tag, "Starting direct discovery for network $networkPrefix.*")
                
                // Try common IP ranges in the local network
                for (i in 1..254) {
                    if (!isRunning) break
                    
                    val targetIp = "$networkPrefix.$i"
                    if (targetIp == myIp) continue // Skip self
                    
                    try {
                        val message = JSONObject().apply {
                            put("type", "discovery")
                            put("userName", userName)
                            put("deviceName", getDeviceName())
                            put("publicKey", ChatEncryption.getPublicKey())
                            put("sessionToken", sessionToken)
                            put("timestamp", System.currentTimeMillis())
                        }.toString()
                        
                        val packet = DatagramPacket(
                            message.toByteArray(),
                            message.length,
                            InetAddress.getByName(targetIp),
                            discoveryPort
                        )
                        
                        discoverySocket?.send(packet)
                    } catch (e: Exception) {
                        // Ignore individual send failures - this is expected for many IPs
                    }
                    
                    if (i % 50 == 0) {
                        delay(100) // Small delay every 50 IPs to prevent overwhelming
                    }
                }
                
                Log.d(tag, "Direct discovery scan completed for network $networkPrefix.*")
                
            } catch (e: Exception) {
                Log.e(tag, "Error in direct discovery", e)
            }
        }
    }
    
    fun sendMessage(content: String, isPrivate: Boolean = false, recipientUser: ChatUser? = null) {
        if (content.isBlank()) return
        
        serviceScope.launch {
            try {
                val currentState = _chatState.value
                var finalContent = content
                val isEncrypted = currentState.encryptionEnabled && isPrivate
                
                // Encrypt private messages
                if (isEncrypted && recipientUser != null) {
                    finalContent = ChatEncryption.encryptMessage(content, recipientUser.ipAddress)
                }
                
                val message = ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    content = content, // Store original content for sender
                    senderName = currentState.myName,
                    senderIp = currentState.myIpAddress,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = true,
                    isPrivate = isPrivate,
                    recipientIp = recipientUser?.ipAddress ?: "",
                    recipientName = recipientUser?.name ?: "",
                    isEncrypted = isEncrypted
                )
                
                if (isPrivate && recipientUser != null) {
                    // Add to private message history
                    val currentPrivateMessages = privateMessageHistory.getOrPut(recipientUser.ipAddress) { mutableListOf() }
                    currentPrivateMessages.add(message)
                    
                    // Auto-save private messages
                    dataManager.savePrivateMessages(recipientUser.ipAddress, currentPrivateMessages)
                    
                    _chatState.value = currentState.copy(
                        privateMessages = privateMessageHistory.toMap()
                    )
                    
                    // Send private message
                    val messageJson = createMessageJson(message, finalContent)
                    sendMessageToUser(recipientUser.ipAddress, messageJson)
                } else {
                    // Add to group messages
                    val currentMessages = currentState.messages.toMutableList()
                    currentMessages.add(message)
                    
                    // Auto-save group messages
                    dataManager.saveGroupMessages(currentMessages)
                    
                    _chatState.value = currentState.copy(messages = currentMessages)
                    
                    // Send to all users
                    val messageJson = createMessageJson(message, finalContent)
                    discoveredUsers.values.forEach { user ->
                        sendMessageToUser(user.ipAddress, messageJson)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(tag, "Error sending message", e)
            }
        }
    }
    
    private fun createMessageJson(message: ChatMessage, content: String): String {
        return JSONObject().apply {
            put("type", "chat_message")
            put("id", message.id)
            put("content", content)
            put("senderName", message.senderName)
            put("timestamp", message.timestamp)
            put("isPrivate", message.isPrivate)
            put("recipientIp", message.recipientIp)
            put("isEncrypted", message.isEncrypted)
        }.toString()
    }
    
    fun switchChatMode(mode: ChatMode) {
        _chatState.value = _chatState.value.copy(currentChatMode = mode)
    }
    
    fun selectUser(user: ChatUser?) {
        _chatState.value = _chatState.value.copy(
            selectedUser = user,
            currentChatMode = if (user != null) ChatMode.PRIVATE else ChatMode.GROUP
        )
    }
    
    fun getPrivateMessages(userIp: String): List<ChatMessage> {
        return privateMessageHistory[userIp] ?: emptyList()
    }
    
    fun markMessagesAsRead(userIp: String) {
        // Update unread count for user
        val user = discoveredUsers[userIp]
        if (user != null) {
            discoveredUsers[userIp] = user.copy(unreadCount = 0)
            _chatState.value = _chatState.value.copy(
                connectedUsers = discoveredUsers.values.toList()
            )
        }
    }
    
    // Load saved chat data
    private fun loadSavedChatData() {
        try {
            // Load group messages
            val groupMessages = dataManager.loadGroupMessages()
            
            // Load private messages
            val privateMessages = dataManager.loadAllPrivateMessages()
            privateMessageHistory.clear()
            privateMessages.forEach { (userIp, messages) ->
                privateMessageHistory[userIp] = messages.toMutableList()
            }
            
            // Load known users
            val knownUsers = dataManager.loadKnownUsers()
            knownUsers.forEach { user ->
                discoveredUsers[user.ipAddress] = user.copy(isOnline = false)
            }
            
            // Load chat settings
            val (encryptionEnabled, _) = dataManager.loadChatSettings()
            
            // Update state with loaded data
            _chatState.value = _chatState.value.copy(
                messages = groupMessages,
                privateMessages = privateMessages,
                connectedUsers = discoveredUsers.values.toList(),
                encryptionEnabled = encryptionEnabled
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Error loading saved chat data", e)
        }
    }
    
    // Save current chat data
    fun saveChatData() {
        try {
            val currentState = _chatState.value
            
            // Save group messages
            dataManager.saveGroupMessages(currentState.messages)
            
            // Save private messages
            privateMessageHistory.forEach { (userIp, messages) ->
                dataManager.savePrivateMessages(userIp, messages)
            }
            
            // Save known users
            dataManager.saveKnownUsers(discoveredUsers.values.toList())
            
            // Save chat settings
            dataManager.saveChatSettings(currentState.encryptionEnabled, true)
            
            // Save my name
            if (currentState.myName.isNotEmpty()) {
                dataManager.saveMyName(currentState.myName)
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Error saving chat data", e)
        }
    }
    
    // Clear all chat data
    fun clearAllChatData() {
        try {
            dataManager.clearAllChatData()
            
            // Clear in-memory data
            privateMessageHistory.clear()
            
            // Update state
            _chatState.value = _chatState.value.copy(
                messages = emptyList(),
                privateMessages = emptyMap()
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Error clearing chat data", e)
        }
    }
    
    // Clear group messages only
    fun clearGroupMessages() {
        try {
            dataManager.clearGroupMessages()
            
            _chatState.value = _chatState.value.copy(
                messages = emptyList()
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Error clearing group messages", e)
        }
    }
    
    // Clear private messages for specific user
    fun clearPrivateMessages(userIp: String) {
        try {
            dataManager.clearPrivateMessages(userIp)
            privateMessageHistory.remove(userIp)
            
            _chatState.value = _chatState.value.copy(
                privateMessages = privateMessageHistory.toMap()
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Error clearing private messages", e)
        }
    }
    
    // Clear all private messages
    fun clearAllPrivateMessages() {
        try {
            dataManager.clearAllPrivateMessages()
            privateMessageHistory.clear()
            
            _chatState.value = _chatState.value.copy(
                privateMessages = emptyMap()
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Error clearing all private messages", e)
        }
    }
    
    // Get chat statistics
    fun getChatStatistics(): com.bnaveen07.wificonnect.data.ChatStatistics {
        return dataManager.getChatStatistics()
    }
    
    // Export chat data as JSON string
    fun exportChatData(): String {
        return try {
            val exportData = org.json.JSONObject().apply {
                put("groupMessages", org.json.JSONArray().apply {
                    _chatState.value.messages.forEach { message ->
                        put(messageToExportJson(message))
                    }
                })
                
                put("privateMessages", org.json.JSONObject().apply {
                    privateMessageHistory.forEach { (userIp, messages) ->
                        put(userIp, org.json.JSONArray().apply {
                            messages.forEach { message ->
                                put(messageToExportJson(message))
                            }
                        })
                    }
                })
                
                put("exportTime", System.currentTimeMillis())
                put("appVersion", "1.0")
            }
            exportData.toString(2) // Pretty print with indent
        } catch (e: Exception) {
            Log.e(tag, "Error exporting chat data", e)
            "{\"error\": \"Failed to export chat data\"}"
        }
    }
    
    private fun messageToExportJson(message: ChatMessage): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", message.id)
            put("content", message.content)
            put("senderName", message.senderName)
            put("timestamp", message.timestamp)
            put("formattedTime", message.formattedTime)
            put("isFromMe", message.isFromMe)
            put("isPrivate", message.isPrivate)
            if (message.isPrivate) {
                put("recipientName", message.recipientName)
            }
        }
    }
    
    private suspend fun sendMessageToUser(ipAddress: String, message: String) {
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, chatPort), 5000)
                
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(message)
                
                socket.close()
                
            } catch (e: Exception) {
                Log.d(tag, "Could not send message to $ipAddress: ${e.message}")
            }
        }
    }
    
    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            
            // Check if we have a valid IP address
            if (ipInt == 0) {
                Log.w(tag, "WiFi IP address is 0, trying alternative method")
                return getAlternativeIpAddress()
            }
            
            val ipAddress = String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
            
            Log.d(tag, "Found local IP address: $ipAddress")
            return ipAddress
            
        } catch (e: Exception) {
            Log.e(tag, "Error getting WiFi IP address", e)
            return getAlternativeIpAddress()
        }
    }
    
    private fun getAlternativeIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val ipAddress = address.hostAddress ?: continue
                            // Check if it's a local network address
                            if (ipAddress.startsWith("192.168.") || 
                                ipAddress.startsWith("10.") || 
                                ipAddress.startsWith("172.")) {
                                Log.d(tag, "Found alternative IP address: $ipAddress")
                                return ipAddress
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting alternative IP address", e)
        }
        
        Log.w(tag, "Could not find valid IP address, using localhost")
        return "127.0.0.1"
    }
    
    private fun getBroadcastAddress(): InetAddress {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            
            if (dhcpInfo.ipAddress == 0 || dhcpInfo.netmask == 0) {
                Log.w(tag, "DHCP info not available, using default broadcast")
                return InetAddress.getByName("255.255.255.255")
            }
            
            val broadcast = dhcpInfo.ipAddress or (dhcpInfo.netmask.inv())
            val bytes = ByteArray(4)
            bytes[0] = (broadcast and 0xff).toByte()
            bytes[1] = (broadcast shr 8 and 0xff).toByte()
            bytes[2] = (broadcast shr 16 and 0xff).toByte()
            bytes[3] = (broadcast shr 24 and 0xff).toByte()
            
            val broadcastAddress = InetAddress.getByAddress(bytes)
            Log.d(tag, "Found broadcast address: ${broadcastAddress.hostAddress}")
            return broadcastAddress
            
        } catch (e: Exception) {
            Log.e(tag, "Error getting broadcast address", e)
            return try {
                InetAddress.getByName("255.255.255.255")
            } catch (ex: Exception) {
                Log.e(tag, "Failed to get default broadcast address", ex)
                throw ex
            }
        }
    }
    
    private fun getDeviceName(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                android.provider.Settings.Global.getString(
                    context.contentResolver,
                    android.provider.Settings.Global.DEVICE_NAME
                ) ?: Build.MODEL
            } else {
                Build.MODEL
            }
        } catch (e: Exception) {
            Build.MODEL
        }
    }
    
    fun refreshDiscovery() {
        if (!isRunning) return
        
        serviceScope.launch {
            try {
                Log.d(tag, "Manually refreshing discovery...")
                val userName = _chatState.value.myName
                
                // Send immediate broadcast
                broadcastDiscovery(userName)
                
                // Try direct discovery
                tryDirectDiscovery(userName)
                
                Log.d(tag, "Manual discovery refresh completed")
            } catch (e: Exception) {
                Log.e(tag, "Error in manual discovery refresh", e)
            }
        }
    }
}
