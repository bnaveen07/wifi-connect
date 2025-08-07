package com.bnaveen07.wificonnect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.bnaveen07.wificonnect.model.ChatMessage
import com.bnaveen07.wificonnect.model.ChatUser
import com.bnaveen07.wificonnect.model.MessageType
import org.json.JSONArray
import org.json.JSONObject

class ChatDataManager(private val context: Context) {
    
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        "chat_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_GROUP_MESSAGES = "group_messages"
        private const val KEY_PRIVATE_MESSAGES = "private_messages_"
        private const val KEY_KNOWN_USERS = "known_users"
        private const val KEY_MY_NAME = "my_name"
        private const val KEY_CHAT_SETTINGS = "chat_settings"
    }
    
    // Store group messages
    fun saveGroupMessages(messages: List<ChatMessage>) {
        val jsonArray = JSONArray()
        messages.forEach { message ->
            jsonArray.put(messageToJson(message))
        }
        sharedPreferences.edit()
            .putString(KEY_GROUP_MESSAGES, jsonArray.toString())
            .apply()
    }
    
    // Load group messages
    fun loadGroupMessages(): List<ChatMessage> {
        val jsonString = sharedPreferences.getString(KEY_GROUP_MESSAGES, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val messages = mutableListOf<ChatMessage>()
        
        for (i in 0 until jsonArray.length()) {
            try {
                val messageJson = jsonArray.getJSONObject(i)
                messages.add(jsonToMessage(messageJson))
            } catch (e: Exception) {
                // Skip corrupted messages
            }
        }
        
        return messages
    }
    
    // Store private messages for a specific user
    fun savePrivateMessages(userIp: String, messages: List<ChatMessage>) {
        val jsonArray = JSONArray()
        messages.forEach { message ->
            jsonArray.put(messageToJson(message))
        }
        sharedPreferences.edit()
            .putString(KEY_PRIVATE_MESSAGES + userIp, jsonArray.toString())
            .apply()
    }
    
    // Load private messages for a specific user
    fun loadPrivateMessages(userIp: String): List<ChatMessage> {
        val jsonString = sharedPreferences.getString(KEY_PRIVATE_MESSAGES + userIp, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val messages = mutableListOf<ChatMessage>()
        
        for (i in 0 until jsonArray.length()) {
            try {
                val messageJson = jsonArray.getJSONObject(i)
                messages.add(jsonToMessage(messageJson))
            } catch (e: Exception) {
                // Skip corrupted messages
            }
        }
        
        return messages
    }
    
    // Load all private message conversations
    fun loadAllPrivateMessages(): Map<String, List<ChatMessage>> {
        val result = mutableMapOf<String, List<ChatMessage>>()
        val allKeys = sharedPreferences.all.keys
        
        allKeys.filter { it.startsWith(KEY_PRIVATE_MESSAGES) }.forEach { key ->
            val userIp = key.substring(KEY_PRIVATE_MESSAGES.length)
            result[userIp] = loadPrivateMessages(userIp)
        }
        
        return result
    }
    
    // Save known users for quick access
    fun saveKnownUsers(users: List<ChatUser>) {
        val jsonArray = JSONArray()
        users.forEach { user ->
            jsonArray.put(userToJson(user))
        }
        sharedPreferences.edit()
            .putString(KEY_KNOWN_USERS, jsonArray.toString())
            .apply()
    }
    
    // Load known users
    fun loadKnownUsers(): List<ChatUser> {
        val jsonString = sharedPreferences.getString(KEY_KNOWN_USERS, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val users = mutableListOf<ChatUser>()
        
        for (i in 0 until jsonArray.length()) {
            try {
                val userJson = jsonArray.getJSONObject(i)
                users.add(jsonToUser(userJson))
            } catch (e: Exception) {
                // Skip corrupted users
            }
        }
        
        return users
    }
    
    // Save and load user name
    fun saveMyName(name: String) {
        sharedPreferences.edit()
            .putString(KEY_MY_NAME, name)
            .apply()
    }
    
    fun loadMyName(): String? {
        return sharedPreferences.getString(KEY_MY_NAME, null)
    }
    
    // Clear all chat data
    fun clearAllChatData() {
        sharedPreferences.edit().clear().apply()
    }
    
    // Clear group messages only
    fun clearGroupMessages() {
        sharedPreferences.edit()
            .remove(KEY_GROUP_MESSAGES)
            .apply()
    }
    
    // Clear private messages for specific user
    fun clearPrivateMessages(userIp: String) {
        sharedPreferences.edit()
            .remove(KEY_PRIVATE_MESSAGES + userIp)
            .apply()
    }
    
    // Clear all private messages
    fun clearAllPrivateMessages() {
        val editor = sharedPreferences.edit()
        val allKeys = sharedPreferences.all.keys
        allKeys.filter { it.startsWith(KEY_PRIVATE_MESSAGES) }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
    }
    
    // Get all messages (group and private)
    fun getAllMessages(): Pair<List<ChatMessage>, Map<String, List<ChatMessage>>> {
        val groupMessages = loadGroupMessages()
        val privateMessages = loadAllPrivateMessages()
        return Pair(groupMessages, privateMessages)
    }
    
    // Save discovered users
    fun saveDiscoveredUsers(users: List<ChatUser>) {
        saveKnownUsers(users)
    }
    
    // Export chat data
    fun exportChatData(groupMessages: List<ChatMessage>, privateMessages: Map<String, List<ChatMessage>>): String {
        val exportData = JSONObject().apply {
            put("exportTime", System.currentTimeMillis())
            put("groupMessages", JSONArray().apply {
                groupMessages.forEach { put(messageToJson(it)) }
            })
            put("privateMessages", JSONObject().apply {
                privateMessages.forEach { (userIp, messages) ->
                    put(userIp, JSONArray().apply {
                        messages.forEach { put(messageToJson(it)) }
                    })
                }
            })
        }
        return exportData.toString(2)
    }
    
    private fun messageToJson(message: ChatMessage): JSONObject {
        return JSONObject().apply {
            put("id", message.id)
            put("content", message.content)
            put("senderName", message.senderName)
            put("senderIp", message.senderIp)
            put("timestamp", message.timestamp)
            put("isFromMe", message.isFromMe)
            put("isPrivate", message.isPrivate)
            put("recipientIp", message.recipientIp)
            put("recipientName", message.recipientName)
            put("isEncrypted", message.isEncrypted)
            put("messageType", message.messageType.name)
            put("isRead", message.isRead)
        }
    }
    
    private fun jsonToMessage(json: JSONObject): ChatMessage {
        return ChatMessage(
            id = json.optString("id", ""),
            content = json.optString("content", ""),
            senderName = json.optString("senderName", ""),
            senderIp = json.optString("senderIp", ""),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            isFromMe = json.optBoolean("isFromMe", false),
            isPrivate = json.optBoolean("isPrivate", false),
            recipientIp = json.optString("recipientIp", ""),
            recipientName = json.optString("recipientName", ""),
            isEncrypted = json.optBoolean("isEncrypted", false),
            messageType = try {
                MessageType.valueOf(json.optString("messageType", "TEXT"))
            } catch (e: Exception) {
                MessageType.TEXT
            },
            isRead = json.optBoolean("isRead", false)
        )
    }
    
    private fun userToJson(user: ChatUser): JSONObject {
        return JSONObject().apply {
            put("name", user.name)
            put("ipAddress", user.ipAddress)
            put("deviceName", user.deviceName)
            put("isOnline", user.isOnline)
            put("lastSeen", user.lastSeen)
            put("publicKey", user.publicKey)
            put("unreadCount", user.unreadCount)
        }
    }
    
    private fun jsonToUser(json: JSONObject): ChatUser {
        return ChatUser(
            name = json.getString("name"),
            ipAddress = json.getString("ipAddress"),
            deviceName = json.getString("deviceName"),
            isOnline = json.optBoolean("isOnline", false),
            lastSeen = json.optLong("lastSeen", System.currentTimeMillis()),
            publicKey = json.optString("publicKey", ""),
            unreadCount = json.optInt("unreadCount", 0)
        )
    }
    
    fun getChatStatistics(): com.bnaveen07.wificonnect.model.ChatStatistics {
        val groupMessages = loadGroupMessages()
        val privateMessages = loadAllPrivateMessages()
        val totalPrivateMessages = privateMessages.values.sumOf { it.size }
        val unreadMessages = privateMessages.values.sumOf { messages ->
            messages.count { !it.isFromMe && !it.isRead }
        }
        
        return com.bnaveen07.wificonnect.model.ChatStatistics(
            totalUsers = loadKnownUsers().size,
            totalGroupMessages = groupMessages.size,
            totalPrivateMessages = totalPrivateMessages,
            unreadMessages = unreadMessages
        )
    }
}
