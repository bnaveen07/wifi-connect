package com.bnaveen07.wificonnect.viewmodel

import android.app.Application
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
    
    private val chatService = LocalChatService(application)
    
    private val _chatState = MutableLiveData<ChatState>()
    val chatState: LiveData<ChatState> = _chatState
    
    init {
        viewModelScope.launch {
            chatService.chatState.collect { state ->
                _chatState.value = state
            }
        }
    }
    
    fun startChat(userName: String) {
        chatService.startChatService(userName)
    }
    
    fun stopChat() {
        chatService.stopChatService()
    }
    
    fun sendMessage(content: String) {
        chatService.sendMessage(content, false, null)
    }
    
    fun sendPrivateMessage(content: String, recipient: ChatUser) {
        chatService.sendMessage(content, true, recipient)
    }
    
    fun switchChatMode(mode: ChatMode) {
        chatService.switchChatMode(mode)
    }
    
    fun selectUser(user: ChatUser?) {
        chatService.selectUser(user)
    }
    
    fun getPrivateMessages(userIp: String): List<ChatMessage> {
        return chatService.getPrivateMessages(userIp)
    }
    
    fun markMessagesAsRead(userIp: String) {
        chatService.markMessagesAsRead(userIp)
    }
    
    // Data management functions
    fun saveChatData() {
        chatService.saveChatData()
    }
    
    fun clearAllChatData() {
        chatService.clearAllChatData()
    }
    
    fun clearGroupMessages() {
        chatService.clearGroupMessages()
    }
    
    fun clearPrivateMessages(userIp: String) {
        chatService.clearPrivateMessages(userIp)
    }
    
    fun clearAllPrivateMessages() {
        chatService.clearAllPrivateMessages()
    }
    
    fun getChatStatistics(): com.bnaveen07.wificonnect.data.ChatStatistics {
        return chatService.getChatStatistics()
    }
    
    fun exportChatData(): String {
        return chatService.exportChatData()
    }
    
    fun refreshDiscovery() {
        chatService.refreshDiscovery()
    }
    
    fun getSavedUserName(): String {
        return chatService.getSavedUserName()
    }
    
    fun hasSavedUserName(): Boolean {
        return chatService.hasSavedUserName()
    }
    
    override fun onCleared() {
        super.onCleared()
        chatService.stopChatService()
    }
}
