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
    val messageType: MessageType = MessageType.TEXT
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
    val myName: String = "",
    val myIpAddress: String = "",
    val connectedUsers: List<ChatUser> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val privateMessages: Map<String, List<ChatMessage>> = emptyMap(), // IP -> Messages
    val isDiscovering: Boolean = false,
    val connectionStatus: ChatConnectionStatus = ChatConnectionStatus.DISCONNECTED,
    val currentChatMode: ChatMode = ChatMode.GROUP,
    val selectedUser: ChatUser? = null,
    val encryptionEnabled: Boolean = true
)

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
