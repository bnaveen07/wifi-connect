package com.bnaveen07.wificonnect.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.bnaveen07.wificonnect.model.ChatState
import com.bnaveen07.wificonnect.model.ChatUser
import com.bnaveen07.wificonnect.model.ChatMode
import com.bnaveen07.wificonnect.model.ChatMessage
import com.bnaveen07.wificonnect.service.LocalChatService

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    private var chatService: LocalChatService? = null
    private var ownsChatService = false // Track if we own the service instance
    
    private val _chatState = MutableLiveData<ChatState>()
    val chatState: LiveData<ChatState> = _chatState
    
    init {
        // Initialize with a default service instance
        initializeService()
    }
    
    private fun initializeService() {
        if (chatService == null) {
            chatService = LocalChatService(getApplication())
            ownsChatService = true
            
            viewModelScope.launch {
                chatService?.chatState?.collect { state ->
                    _chatState.value = state
                }
            }
        }
    }
    
    fun setChatService(service: LocalChatService) {
        // Stop our own service if we have one
        if (ownsChatService && chatService != null) {
            chatService?.stop()
        }
        
        chatService = service
        ownsChatService = false
        
        // Collect state from the new service
        viewModelScope.launch {
            chatService?.chatState?.collect { state ->
                _chatState.value = state
            }
        }
    }
    
    fun startChat(userName: String) {
        initializeService()
        chatService?.startWithUserName(userName)
    }
    
    fun stopChat() {
        chatService?.stop()
    }
    
    fun sendMessage(content: String) {
        chatService?.sendMessage(content, false, null)
    }
    
    fun sendPrivateMessage(content: String, recipient: ChatUser) {
        chatService?.sendMessage(content, true, recipient)
    }
    
    fun switchChatMode(mode: ChatMode) {
        chatService?.switchChatMode(mode)
    }
    
    fun selectUser(user: ChatUser?) {
        chatService?.selectUser(user)
    }
    
    fun getPrivateMessages(userIp: String): List<ChatMessage> {
        return chatService?.getPrivateMessages(userIp) ?: emptyList()
    }
    
    fun markMessagesAsRead(userIp: String) {
        chatService?.markMessagesAsRead(userIp)
    }
    
    // Data management functions
    fun saveChatData() {
        chatService?.saveChatData()
    }
    
    fun clearAllChatData() {
        chatService?.clearAllChatData()
    }
    
    fun clearGroupMessages() {
        chatService?.clearGroupMessages()
    }
    
    fun clearPrivateMessages(userIp: String) {
        chatService?.clearPrivateMessages(userIp)
    }
    
    fun clearAllPrivateMessages() {
        chatService?.clearAllPrivateMessages()
    }
    
    fun getChatStatistics(): com.bnaveen07.wificonnect.model.ChatStatistics {
        return chatService?.getChatStatistics() ?: com.bnaveen07.wificonnect.model.ChatStatistics()
    }
    
    fun exportChatData(): String {
        return chatService?.exportChatData() ?: ""
    }
    
    fun refreshDiscovery() {
        chatService?.refreshDiscovery()
    }
    
    // User name management - use SharedPreferences for simple storage
    fun getSavedUserName(): String {
        val prefs = context.getSharedPreferences("wifi_connect_prefs", Context.MODE_PRIVATE)
        return prefs.getString("user_name", "") ?: ""
    }
    
    fun hasSavedUserName(): Boolean {
        return getSavedUserName().isNotEmpty()
    }
    
    fun saveUserName(name: String) {
        val prefs = context.getSharedPreferences("wifi_connect_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_name", name).apply()
    }

    override fun onCleared() {
        super.onCleared()
        // Only stop the service if we own it
        if (ownsChatService) {
            chatService?.stop()
        }
    }
}
