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
    private val heartbeatPort = 8890
    private val appSignature = "WiFiConnect_v1.0"

    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var heartbeatSocket: DatagramSocket? = null
    private var isRunning = false
    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastNetworkScan = 0L
    private val networkScanInterval = 30000L // 30 seconds

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    // Enhanced state management for better syncing
    private val _discoveryState = MutableStateFlow(DiscoveryState())
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val connectedClients = ConcurrentHashMap<String, Socket>()
    private val discoveredUsers = ConcurrentHashMap<String, ChatUser>()
    private val privateMessageHistory = ConcurrentHashMap<String, MutableList<ChatMessage>>()
    private val userLastSeen = ConcurrentHashMap<String, Long>()
    private val userDeviceInfo = ConcurrentHashMap<String, DeviceInfo>()
    private val unreadMessageCounts = ConcurrentHashMap<String, Int>()
    private val sessionToken = ChatEncryption.generateSessionToken()
    private val dataManager = ChatDataManager(context)

    init {
        loadChatData()
    }

    fun start() {
        if (isRunning) return

        serviceScope.launch {
            try {
                if (!currentCoroutineContext().isActive) return@launch

                isRunning = true
                Log.d(tag, "Starting LocalChatService")
                _chatState.value = ChatState(
                    isEnabled = true,
                    isServiceRunning = true,
                    connectionStatus = ChatConnectionStatus.CONNECTING,
                    myIpAddress = getMyIpAddress(),
                    myName = _chatState.value.myName.ifEmpty { "User_${getMyIpAddress().substringAfterLast(".")}" }
                )

                // Start all components concurrently
                launch { startDiscoveryListener() }
                launch { startChatServer() }
                launch { startHeartbeatSender() }
                launch { startDiscoveryBroadcast() }
                
                // Update status to connected when all components started
                _chatState.value = _chatState.value.copy(
                    connectionStatus = ChatConnectionStatus.CONNECTED
                )
                
                Log.d(tag, "LocalChatService started successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error starting service", e)
                isRunning = false
            }
        }
    }

    fun setUserName(name: String) {
        _chatState.value = _chatState.value.copy(myName = name)
    }

    fun startWithUserName(userName: String) {
        setUserName(userName)
        start()
    }

    fun stop() {
        try {
            if (!isRunning) return
            
            Log.d(tag, "Stopping LocalChatService...")
            isRunning = false
            _chatState.value = _chatState.value.copy(
                isEnabled = false,
                isServiceRunning = false,
                connectionStatus = ChatConnectionStatus.DISCONNECTED
            )
            
            // Update discovery state
            updateDiscoveryState()
            
            // Close sockets and cancel jobs
            serverSocket?.close()
            discoverySocket?.close()
            heartbeatSocket?.close()
            
            connectedClients.values.forEach { it.close() }
            connectedClients.clear()
            
            // Cancel the service scope and create a new one for restart
            serviceScope.cancel()
            serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            
            Log.d(tag, "LocalChatService stopped")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping service", e)
        }
    }

    private fun updateDiscoveryState() {
        val currentUsers = discoveredUsers.values.map { user ->
            val unreadCount = unreadMessageCounts[user.ipAddress] ?: 0
            user.copy(unreadCount = unreadCount)
        }
        _discoveryState.value = _discoveryState.value.copy(
            discoveredUsers = currentUsers
        )
    }

    private fun loadChatData() {
        serviceScope.launch {
            try {
                val unreadCounts = unreadMessageCounts
                
                val (groupMessages, privateMessages) = dataManager.getAllMessages()
                privateMessageHistory.clear()
                privateMessageHistory.putAll(privateMessages.mapValues { it.value.toMutableList() })
                
                val updatedUsers = discoveredUsers.values.map { user ->
                    val unreadCount = unreadCounts[user.ipAddress] ?: 0
                    user.copy(unreadCount = unreadCount)
                }
                
                // Update unread counts for each user
                privateMessageHistory.forEach { (userIp, messages) ->
                    unreadMessageCounts[userIp] = messages.count { !it.isFromMe && !it.isRead }
                }
                
                // Update state
                dataManager.saveGroupMessages(groupMessages)
                
                _chatState.value = _chatState.value.copy(
                    messages = groupMessages,
                    privateMessages = privateMessageHistory.toMap()
                )
                
                updateDiscoveryState()
                
            } catch (e: Exception) {
                Log.e(tag, "Error loading chat data", e)
            }
        }
    }

    private fun getMyIpAddress(): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            (ipAddress and 0xff),
            (ipAddress shr 8 and 0xff),
            (ipAddress shr 16 and 0xff),
            (ipAddress shr 24 and 0xff)
        )
    }

    private suspend fun startDiscoveryListener() {
        withContext(Dispatchers.IO) {
            try {
                // Close existing socket if any
                discoverySocket?.close()
                
                // Try to bind to the discovery port with retries
                var attempts = 0
                var currentPort = discoveryPort
                var socketCreated = false
                
                while (attempts < 5 && !socketCreated && currentCoroutineContext().isActive) {
                    try {
                        discoverySocket = DatagramSocket(currentPort).apply {
                            reuseAddress = true
                            soTimeout = 1000 // 1 second timeout for receive operations
                        }
                        socketCreated = true
                        Log.d(tag, "Discovery listener started on port $currentPort")
                    } catch (e: java.net.BindException) {
                        attempts++
                        currentPort = discoveryPort + attempts
                        Log.w(tag, "Port $currentPort in use, trying ${currentPort + 1}")
                        if (attempts >= 5) {
                            throw e
                        }
                    }
                }

                while (currentCoroutineContext().isActive && isRunning && discoverySocket != null) {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    
                    try {
                        discoverySocket?.receive(packet)
                        val data = String(packet.data, 0, packet.length)
                        handleDiscoveryMessage(data, packet.address)
                    } catch (e: SocketTimeoutException) {
                        // Timeout is normal, continue listening
                    } catch (e: Exception) {
                        if (currentCoroutineContext().isActive && isRunning) {
                            Log.e(tag, "Error in discovery listener", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive && isRunning) {
                    Log.e(tag, "Error starting discovery listener", e)
                }
            }
        }
    }

    private suspend fun startChatServer() {
        withContext(Dispatchers.IO) {
            try {
                // Close existing server socket if any
                serverSocket?.close()
                
                // Try to bind to the chat port with retries
                var attempts = 0
                var currentPort = chatPort
                var socketCreated = false
                
                while (attempts < 5 && !socketCreated && currentCoroutineContext().isActive) {
                    try {
                        serverSocket = ServerSocket(currentPort).apply {
                            reuseAddress = true
                            soTimeout = 1000 // 1 second timeout for accept operations
                        }
                        socketCreated = true
                        Log.d(tag, "Chat server started on port $currentPort")
                    } catch (e: java.net.BindException) {
                        attempts++
                        currentPort = chatPort + attempts
                        Log.w(tag, "Chat port $currentPort in use, trying ${currentPort + 1}")
                        if (attempts >= 5) {
                            throw e
                        }
                    }
                }

                // Accept connections
                while (currentCoroutineContext().isActive && isRunning && serverSocket != null) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        if (clientSocket != null) {
                            serviceScope.launch {
                                handleClientConnection(clientSocket)
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout is normal, continue listening
                    } catch (e: Exception) {
                        if (currentCoroutineContext().isActive && isRunning) {
                            Log.e(tag, "Error accepting client connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive && isRunning) {
                    Log.e(tag, "Error starting chat server", e)
                }
            }
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val clientIp = socket.inetAddress.hostAddress ?: ""
            
            connectedClients[clientIp] = socket
            
            while (currentCoroutineContext().isActive && isRunning && !socket.isClosed) {
                val message = reader.readLine() ?: break
                handleIncomingMessage(message, clientIp)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling client connection", e)
        } finally {
            try {
                socket.close()
                connectedClients.remove(socket.inetAddress.hostAddress)
            } catch (e: Exception) {
                Log.e(tag, "Error closing client socket", e)
            }
        }
    }

    private fun handleDiscoveryMessage(data: String, address: InetAddress) {
        try {
            if (!data.startsWith(appSignature)) return

            val parts = data.split("|")
            if (parts.size >= 3) {
                val userIp = parts[1]
                val userName = parts[2]
                val deviceModel = if (parts.size > 3) parts[3] else "Unknown"
                val isAppUser = if (parts.size > 4) parts[4].toBoolean() else true

                if (userIp != _chatState.value.myIpAddress) {
                    userDeviceInfo[userIp] = DeviceInfo(
                        ipAddress = userIp,
                        deviceModel = deviceModel,
                        isAppUser = isAppUser,
                        lastSeen = System.currentTimeMillis()
                    )

                    discoveredUsers[userIp] = ChatUser(
                        name = userName,
                        deviceName = deviceModel,
                        ipAddress = userIp,
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        unreadCount = unreadMessageCounts[userIp] ?: 0
                    )

                    userLastSeen[userIp] = System.currentTimeMillis()
                    updateDiscoveryState()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling discovery message", e)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val clientIp = socket.inetAddress.hostAddress ?: ""
            
            connectedClients[clientIp] = socket
            Log.d(tag, "Client connected: $clientIp")

            while (currentCoroutineContext().isActive && !socket.isClosed && isRunning) {
                val message = reader.readLine() ?: break
                handleIncomingMessage(message, clientIp)
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive && isRunning) {
                Log.e(tag, "Error handling client", e)
            }
        } finally {
            try {
                socket.close()
                connectedClients.remove(socket.inetAddress.hostAddress)
            } catch (e: Exception) {
                Log.e(tag, "Error closing client socket", e)
            }
        }
    }

    private suspend fun handleIncomingMessage(message: String, senderIp: String) {
        try {
            val json = JSONObject(message)
            val messageType = json.optString("type", "")

            when (messageType) {
                "chat_message" -> {
                    val messageId = json.optString("id", "")
                    val content = json.optString("content", "")
                    val senderName = json.optString("senderName", "")
                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    val isPrivate = json.optBoolean("isPrivate", false)
                    val recipientIp = json.optString("recipientIp", "")
                    val isEncrypted = json.optBoolean("isEncrypted", false)

                    var finalContent = content
                    if (isEncrypted) {
                        finalContent = ChatEncryption.decryptMessage(content, senderIp, _chatState.value.myIpAddress)
                    }

                    val chatMessage = ChatMessage(
                        id = messageId,
                        content = finalContent,
                        senderName = senderName,
                        senderIp = senderIp,
                        timestamp = timestamp,
                        isFromMe = false,
                        isPrivate = isPrivate,
                        recipientIp = recipientIp,
                        recipientName = _chatState.value.myName,
                        isEncrypted = isEncrypted,
                        isRead = false,
                        deliveryStatus = DeliveryStatus.DELIVERED
                    )

                    // Send delivery acknowledgment
                    sendDeliveryAck(senderIp, messageId)

                    if (isPrivate && recipientIp == _chatState.value.myIpAddress) {
                        // Add to private message history
                        val currentPrivateMessages = privateMessageHistory.getOrPut(senderIp) { mutableListOf() }
                        currentPrivateMessages.add(chatMessage)

                        // Update unread count
                        unreadMessageCounts[senderIp] = (unreadMessageCounts[senderIp] ?: 0) + 1

                        // Auto-save private messages
                        dataManager.savePrivateMessages(senderIp, currentPrivateMessages)

                        _chatState.value = _chatState.value.copy(
                            privateMessages = privateMessageHistory.toMap()
                        )

                        updateDiscoveryState()
                        Log.d(tag, "Received private message from $senderName")
                    } else if (!isPrivate) {
                        // Add to group messages
                        val currentState = _chatState.value
                        val currentMessages = currentState.messages.toMutableList()
                        currentMessages.add(chatMessage)

                        // Auto-save group messages
                        dataManager.saveGroupMessages(currentMessages)

                        _chatState.value = currentState.copy(messages = currentMessages)
                        Log.d(tag, "Received group message from $senderName")
                    }
                }
                "delivery_ack" -> {
                    val messageId = json.optString("messageId", "")
                    updateMessageDeliveryStatus(messageId, DeliveryStatus.DELIVERED)
                }
                "read_receipt" -> {
                    val messageId = json.optString("messageId", "")
                    updateMessageDeliveryStatus(messageId, DeliveryStatus.READ)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling incoming message", e)
        }
    }

    private suspend fun sendDeliveryAck(recipientIp: String, messageId: String) {
        try {
            val ackJson = JSONObject().apply {
                put("type", "delivery_ack")
                put("messageId", messageId)
            }.toString()

            sendMessageToUser(recipientIp, ackJson)
        } catch (e: Exception) {
            Log.e(tag, "Error sending delivery ack", e)
        }
    }

    private suspend fun sendReadReceipt(recipientIp: String, messageId: String) {
        try {
            val receiptJson = JSONObject().apply {
                put("type", "read_receipt")
                put("messageId", messageId)
            }.toString()

            sendMessageToUser(recipientIp, receiptJson)
        } catch (e: Exception) {
            Log.e(tag, "Error sending read receipt", e)
        }
    }

    private fun updateMessageDeliveryStatus(messageId: String, status: DeliveryStatus) {
        try {
            val currentState = _chatState.value

            // Update in group messages
            val updatedGroupMessages = currentState.messages.map { message ->
                if (message.id == messageId) {
                    message.copy(deliveryStatus = status)
                } else {
                    message
                }
            }

            // Update in private messages
            val updatedPrivateMessages = currentState.privateMessages.mapValues { (_, messages) ->
                messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(deliveryStatus = status)
                    } else {
                        message
                    }
                }
            }

            _chatState.value = currentState.copy(
                messages = updatedGroupMessages,
                privateMessages = updatedPrivateMessages
            )
        } catch (e: Exception) {
            Log.e(tag, "Error updating message delivery status", e)
        }
    }

    private suspend fun startHeartbeatSender() {
        withContext(Dispatchers.IO) {
            while (currentCoroutineContext().isActive && isRunning) {
                try {
                    delay(5000) // Send heartbeat every 5 seconds
                    if (!currentCoroutineContext().isActive || !isRunning) break
                    
                    broadcastEnhancedDiscovery()
                } catch (e: CancellationException) {
                    Log.d(tag, "Heartbeat sender cancelled")
                    break
                } catch (e: Exception) {
                    if (currentCoroutineContext().isActive && isRunning) {
                        Log.e(tag, "Error in heartbeat sender", e)
                    }
                }
            }
        }
    }

    private suspend fun startDiscoveryBroadcast() {
        withContext(Dispatchers.IO) {
            try {
                performComprehensiveNetworkScan()
                performAdvancedNetworkScan()
                Log.d(tag, "Initial discovery broadcast completed")
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive && isRunning) {
                    Log.e(tag, "Error in discovery broadcast", e)
                }
            }
        }
    }

    private suspend fun broadcastEnhancedDiscovery() {
        withContext(Dispatchers.IO) {
            try {
                val deviceInfo = "${Build.MODEL}_${Build.MANUFACTURER}"
                val message = "$appSignature|${_chatState.value.myIpAddress}|${_chatState.value.myName}|$deviceInfo|true"
                
                val broadcastAddress = getBroadcastAddress()
                val socket = DatagramSocket()
                
                try {
                    val data = message.toByteArray()
                    val packet = DatagramPacket(data, data.size, broadcastAddress, discoveryPort)
                    socket.send(packet)
                    Log.d(tag, "Enhanced discovery broadcast sent")
                } finally {
                    socket.close()
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive && isRunning) {
                    Log.e(tag, "Error broadcasting enhanced discovery", e)
                }
            }
        }
    }

    private fun getBroadcastAddress(): InetAddress {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wifiManager.dhcpInfo
        val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
        return InetAddress.getByAddress(
            byteArrayOf(
                (broadcast and 0xff).toByte(),
                (broadcast shr 8 and 0xff).toByte(),
                (broadcast shr 16 and 0xff).toByte(),
                (broadcast shr 24 and 0xff).toByte()
            )
        )
    }

    private suspend fun performAdvancedNetworkScan() {
        withContext(Dispatchers.IO) {
            try {
                val baseIp = getMyIpAddress().substringBeforeLast(".")
                val jobs = mutableListOf<Job>()

                for (i in 1..254) {
                    val job = launch {
                        val targetIp = "$baseIp.$i"
                        if (targetIp != _chatState.value.myIpAddress) {
                            try {
                                val address = InetAddress.getByName(targetIp)
                                if (address.isReachable(1000)) {
                                    checkForAppUser(targetIp)
                                }
                            } catch (e: Exception) {
                                // Ignore unreachable hosts
                            }
                        }
                    }
                    jobs.add(job)
                }

                jobs.joinAll()
            } catch (e: Exception) {
                Log.e(tag, "Error in advanced network scan", e)
            }
        }
    }

    private suspend fun checkForAppUser(ipAddress: String) {
        try {
            // Try to connect and check if it's an app user
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(ipAddress, chatPort), 2000)
                
                // If connection successful, it's likely an app user
                val deviceName = userDeviceInfo[ipAddress]?.deviceModel ?: "Unknown Device"
                val lastSeen = userLastSeen[ipAddress] ?: System.currentTimeMillis()
                
                // Only add if not seen recently (avoid duplicates)
                if (System.currentTimeMillis() - lastSeen > 10000) { // 10 seconds
                    userLastSeen[ipAddress] = System.currentTimeMillis()
                    
                    val user = ChatUser(
                        name = "User_${ipAddress.substringAfterLast(".")}",
                        deviceName = deviceName,
                        ipAddress = ipAddress,
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        unreadCount = unreadMessageCounts[ipAddress] ?: 0
                    )
                    
                    discoveredUsers[ipAddress] = user
                    updateDiscoveryState()
                }
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            // Not an app user or not reachable
        }
    }

    private suspend fun performComprehensiveNetworkScan(currentState: ChatState = _chatState.value) {
        withContext(Dispatchers.IO) {
            try {
                // Clean up offline users
                val currentTime = System.currentTimeMillis()
                val onlineUsers = discoveredUsers.filter { (_, user) ->
                    val lastSeenTime = userLastSeen[user.ipAddress] ?: 0
                    currentTime - lastSeenTime < 60000 // Consider offline after 1 minute
                }

                discoveredUsers.clear()
                discoveredUsers.putAll(onlineUsers)

                // Update discovery state
                updateDiscoveryState()

                val currentNetworkSSID = getCurrentNetworkSSID()
                
                if (System.currentTimeMillis() - lastNetworkScan > networkScanInterval) {
                    lastNetworkScan = System.currentTimeMillis()

                    serviceScope.launch {
                        try {
                            Log.d(tag, "Starting comprehensive network scan")
                            performAdvancedNetworkScan()
                            performComprehensiveNetworkScan(_chatState.value)
                            Log.d(tag, "Network scan completed")
                        } catch (e: Exception) {
                            Log.e(tag, "Error in network scan", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in comprehensive network scan", e)
            }
        }
    }

    private fun getCurrentNetworkSSID(): String {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            connectionInfo.ssid?.removeSurrounding("\"") ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
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
                    finalContent = ChatEncryption.encryptMessage(content, currentState.myIpAddress, recipientUser.ipAddress)
                }

                val message = ChatMessage(
                    id = "${System.currentTimeMillis()}_${currentState.myIpAddress}", // Unique ID
                    content = content, // Store original content for sender
                    senderName = currentState.myName,
                    senderIp = currentState.myIpAddress,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = true,
                    isPrivate = isPrivate,
                    recipientIp = recipientUser?.ipAddress ?: "",
                    recipientName = recipientUser?.name ?: "",
                    isEncrypted = isEncrypted,
                    isRead = true, // Sender has read their own message
                    deliveryStatus = DeliveryStatus.SENDING
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
                    val success = sendMessageToUser(recipientUser.ipAddress, messageJson)
                    
                    // Update delivery status based on send result
                    if (success) {
                        updateMessageDeliveryStatus(message.id, DeliveryStatus.SENT)
                        Log.d(tag, "Private message sent successfully to ${recipientUser.name}")
                    } else {
                        updateMessageDeliveryStatus(message.id, DeliveryStatus.FAILED)
                        Log.w(tag, "Failed to send private message to ${recipientUser.name}")
                    }
                } else {
                    // Add to group messages
                    val currentMessages = currentState.messages.toMutableList()
                    currentMessages.add(message)

                    // Auto-save group messages
                    dataManager.saveGroupMessages(currentMessages)

                    _chatState.value = currentState.copy(messages = currentMessages)

                    // Send to all users
                    val messageJson = createMessageJson(message, finalContent)
                    var successCount = 0
                    val totalUsers = discoveredUsers.values.size
                    
                    discoveredUsers.values.forEach { user ->
                        val success = sendMessageToUser(user.ipAddress, messageJson)
                        if (success) successCount++
                    }

                    // Update delivery status based on send results
                    val finalStatus = when {
                        successCount == 0 -> DeliveryStatus.FAILED
                        successCount == totalUsers -> DeliveryStatus.SENT
                        else -> DeliveryStatus.SENT // Partial delivery still counts as sent
                    }
                    updateMessageDeliveryStatus(message.id, finalStatus)
                    
                    Log.d(tag, "Group message sent to $successCount/$totalUsers users")
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
        serviceScope.launch {
            try {
                // Reset unread count
                unreadMessageCounts[userIp] = 0

                val currentPrivateMessages = privateMessageHistory[userIp]?.toMutableList()
                currentPrivateMessages?.forEachIndexed { index, message ->
                    if (!message.isFromMe && !message.isRead) {
                        // Mark as read
                        currentPrivateMessages[index] = message.copy(isRead = true)
                        
                        // Send read receipt to sender
                        sendReadReceipt(message.senderIp, message.id)
                    }
                }

                // Save updated messages
                currentPrivateMessages?.let {
                    dataManager.savePrivateMessages(userIp, it)

                    _chatState.value = _chatState.value.copy(
                        privateMessages = privateMessageHistory.toMap()
                    )
                }

                updateDiscoveryState()
            } catch (e: Exception) {
                Log.e(tag, "Error marking messages as read", e)
            }
        }
    }

    private suspend fun sendMessageToUser(ipAddress: String, message: String): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, chatPort), 5000)
            
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(message)
            
            socket.close()
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to send message to $ipAddress", e)
            false
        }
    }

    fun saveChatData() {
        serviceScope.launch {
            try {
                dataManager.saveGroupMessages(_chatState.value.messages)
                privateMessageHistory.forEach { (userIp, messages) ->
                    dataManager.savePrivateMessages(userIp, messages)
                }
                dataManager.saveDiscoveredUsers(discoveredUsers.values.toList())
                Log.d(tag, "Chat data saved successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error saving chat data", e)
            }
        }
    }

    fun clearAllChatData() {
        serviceScope.launch {
            dataManager.clearAllChatData()
            privateMessageHistory.clear()
            _chatState.value = _chatState.value.copy(
                messages = emptyList(),
                privateMessages = emptyMap()
            )
        }
    }

    fun clearGroupMessages() {
        serviceScope.launch {
            dataManager.clearGroupMessages()
            _chatState.value = _chatState.value.copy(messages = emptyList())
        }
    }

    fun clearPrivateMessages(userIp: String) {
        serviceScope.launch {
            dataManager.clearPrivateMessages(userIp)
            privateMessageHistory.remove(userIp)
            _chatState.value = _chatState.value.copy(
                privateMessages = privateMessageHistory.toMap()
            )
        }
    }

    fun clearAllPrivateMessages() {
        serviceScope.launch {
            privateMessageHistory.keys.forEach { userIp ->
                dataManager.clearPrivateMessages(userIp)
            }
            privateMessageHistory.clear()
            _chatState.value = _chatState.value.copy(privateMessages = emptyMap())
        }
    }

    fun getChatStatistics(): ChatStatistics {
        val currentState = _chatState.value
        return ChatStatistics(
            totalUsers = discoveredUsers.size,
            totalGroupMessages = currentState.messages.size,
            totalPrivateMessages = privateMessageHistory.values.sumOf { it.size },
            unreadMessages = unreadMessageCounts.values.sum()
        )
    }

    fun exportChatData(): String {
        return dataManager.exportChatData(_chatState.value.messages, privateMessageHistory.toMap())
    }

    fun refreshDiscovery() {
        serviceScope.launch {
            broadcastEnhancedDiscovery()
            performAdvancedNetworkScan()
        }
    }
}
