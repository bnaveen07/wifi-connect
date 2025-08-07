package com.bnaveen07.wificonnect.model

import java.util.Date

data class ChatMessage(
    val id: String = "",
    val content: String = "",
    val senderName: String = "",
    val senderIp: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val isPrivate: Boolean = false,
    val recipientIp: String = "", // For private messages
    val recipientName: String = "", // For private messages
    val isEncrypted: Boolean = false,
    val messageType: MessageType = MessageType.TEXT,
    val isRead: Boolean = false, // For tracking read status
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT
) {
    val formattedTime: String
        get() {
            val date = Date(timestamp)
            val hours = date.hours.toString().padStart(2, '0')
            val minutes = date.minutes.toString().padStart(2, '0')
            return "$hours:$minutes"
        }

    val displayContent: String
        get() = if (isEncrypted && !isFromMe) "🔒 Encrypted message" else content
}

data class ChatUser(
    val name: String = "",
    val ipAddress: String = "",
    val deviceName: String = "",
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val publicKey: String = "", // For encryption
    val unreadCount: Int = 0 // For private messages
) {
    val isMe: Boolean
        get() = name == "Me" // This will be set properly during implementation
}

data class ChatState(
    val isEnabled: Boolean = false,
    val isServiceRunning: Boolean = false,
    val myName: String = "",
    val myIpAddress: String = "",
    val connectedUsers: List<ChatUser> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val privateMessages: Map<String, List<ChatMessage>> = emptyMap(), // IP -> Messages
    val isDiscovering: Boolean = false,
    val connectionStatus: ChatConnectionStatus = ChatConnectionStatus.DISCONNECTED,
    val currentChatMode: ChatMode = ChatMode.GROUP,
    val selectedUser: ChatUser? = null,
    val encryptionEnabled: Boolean = true,
    val autoSaveEnabled: Boolean = true,
    val searchQuery: String = "",
    val filteredUsers: List<ChatUser> = emptyList(),
    val showOnlineOnly: Boolean = false,
    val showAppUsersOnly: Boolean = false,
    val lastScanTime: Long = 0L
) {
    val hasUsers: Boolean
        get() = connectedUsers.isNotEmpty()
        
    val canSendMessages: Boolean
        get() = isEnabled && connectionStatus == ChatConnectionStatus.CONNECTED
        
    val effectiveUserList: List<ChatUser>
        get() = if (searchQuery.isNotEmpty() || showOnlineOnly || showAppUsersOnly) {
            filteredUsers
        } else {
            connectedUsers
        }
}

enum class ChatConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class ChatMode {
    GROUP,
    PRIVATE
}

enum class MessageType {
    TEXT,
    FILE,
    IMAGE,
    SYSTEM
}

enum class DeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

data class DeviceInfo(
    val ipAddress: String = "",
    val macAddress: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val appVersion: String = "",
    val signalStrength: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
    val isAppUser: Boolean = false,
    val networkSSID: String = "",
    val capabilities: List<String> = emptyList()
)

data class NetworkStats(
    val totalUsers: Int = 0,
    val appUsers: Int = 0,
    val onlineUsers: Int = 0,
    val offlineUsers: Int = 0,
    val networkSSID: String = "",
    val myIP: String = "",
    val lastScanTime: Long = 0L
)

data class DiscoveryState(
    val discoveredUsers: List<ChatUser> = emptyList(),
    val isDiscovering: Boolean = false,
    val lastScanTime: Long = 0L,
    val networkSSID: String = "",
    val totalUsers: Int = 0
)

data class ChatStatistics(
    val totalUsers: Int = 0,
    val totalGroupMessages: Int = 0,
    val totalPrivateMessages: Int = 0,
    val unreadMessages: Int = 0
) {
    val totalMessages: Int
        get() = totalGroupMessages + totalPrivateMessages
    
    val knownUsersCount: Int
        get() = totalUsers
    
    val totalConversations: Int
        get() = totalUsers // Each user represents a potential conversation
    
    val newestMessageTime: Long = 0L
    val oldestMessageTime: Long = 0L
}
