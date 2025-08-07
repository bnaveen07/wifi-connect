package com.bnaveen07.wificonnect.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bnaveen07.wificonnect.model.*
import com.bnaveen07.wificonnect.viewmodel.ChatViewModel
import com.bnaveen07.wificonnect.ui.components.ChatDataManagementDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedLocalChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val chatState by viewModel.chatState.observeAsState(ChatState())
    var showUserNameDialog by remember { mutableStateOf(false) }
    var showDataManagementDialog by remember { mutableStateOf(false) }
    var showUserList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    // Check for saved user name and auto-start if available
    LaunchedEffect(Unit) {
        if (!chatState.isEnabled && viewModel.hasSavedUserName()) {
            val savedName = viewModel.getSavedUserName()
            if (savedName.isNotEmpty()) {
                viewModel.startChat(savedName)
            }
        }
    }

    Scaffold(
        topBar = {
            EnhancedTopBar(
                chatState = chatState,
                onBack = onBack,
                onUserListClick = { showUserList = !showUserList },
                onSettingsClick = { showSettings = true },
                onModeToggle = { 
                    val newMode = if (chatState.currentChatMode == ChatMode.GROUP) 
                        ChatMode.PRIVATE else ChatMode.GROUP
                    viewModel.switchChatMode(newMode)
                    if (newMode == ChatMode.GROUP) {
                        viewModel.selectUser(null)
                    }
                },
                onRefresh = { viewModel.refreshDiscovery() }
            )
        },
        bottomBar = {
            if (chatState.isEnabled && chatState.connectionStatus == ChatConnectionStatus.CONNECTED) {
                EnhancedBottomInputBar(
                    chatState = chatState,
                    viewModel = viewModel
                )
            }
        },
        floatingActionButton = {
            EnhancedFAB(
                chatState = chatState,
                onClick = { 
                    if (!chatState.isEnabled) {
                        if (viewModel.hasSavedUserName()) {
                            val savedName = viewModel.getSavedUserName()
                            viewModel.startChat(savedName)
                        } else {
                            showUserNameDialog = true
                        }
                    } else {
                        viewModel.refreshDiscovery()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // User List Panel (collapsible)
            AnimatedVisibility(
                visible = showUserList,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                EnhancedUserListPanel(
                    chatState = chatState,
                    viewModel = viewModel,
                    onCloseList = { showUserList = false }
                )
            }
            
            // Main Content Area
            when {
                !chatState.isEnabled -> {
                    EnhancedWelcomeScreen(
                        onStartChat = { 
                            if (viewModel.hasSavedUserName()) {
                                val savedName = viewModel.getSavedUserName()
                                viewModel.startChat(savedName)
                            } else {
                                showUserNameDialog = true
                            }
                        },
                        hasSavedName = viewModel.hasSavedUserName(),
                        savedName = if (viewModel.hasSavedUserName()) viewModel.getSavedUserName() else ""
                    )
                }
                chatState.connectionStatus == ChatConnectionStatus.CONNECTING -> {
                    EnhancedConnectingScreen(chatState = chatState)
                }
                chatState.connectionStatus == ChatConnectionStatus.ERROR -> {
                    EnhancedErrorScreen(
                        chatState = chatState,
                        onRetry = { 
                            if (viewModel.hasSavedUserName()) {
                                viewModel.startChat(viewModel.getSavedUserName())
                            }
                        }
                    )
                }
                chatState.connectionStatus == ChatConnectionStatus.CONNECTED -> {
                    EnhancedChatArea(
                        chatState = chatState,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    
    // Dialogs
    if (showUserNameDialog) {
        EnhancedUserNameDialog(
            onConfirm = { userName ->
                viewModel.startChat(userName)
                showUserNameDialog = false
            },
            onDismiss = { showUserNameDialog = false }
        )
    }
    
    if (showDataManagementDialog) {
        ChatDataManagementDialog(
            chatViewModel = viewModel,
            onDismiss = { showDataManagementDialog = false }
        )
    }
    
    if (showSettings) {
        EnhancedSettingsDialog(
            chatState = chatState,
            onDismiss = { showSettings = false },
            onDataManagement = { 
                showSettings = false
                showDataManagementDialog = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedTopBar(
    chatState: ChatState,
    onBack: () -> Unit,
    onUserListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onModeToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = when (chatState.currentChatMode) {
                        ChatMode.GROUP -> "Group Chat"
                        ChatMode.PRIVATE -> chatState.selectedUser?.name ?: "Private Chat"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (chatState.isEnabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Connection status
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    when (chatState.connectionStatus) {
                                        ChatConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
                                        ChatConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                                        ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                                        else -> Color.Gray
                                    },
                                    CircleShape
                                )
                        )
                        
                        Text(
                            text = "${chatState.connectedUsers.size} users online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        
                        if (chatState.myIpAddress.isNotEmpty() && chatState.myIpAddress != "127.0.0.1") {
                            Text(
                                text = "• ${chatState.myIpAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            if (chatState.isEnabled) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
                
                IconButton(onClick = onUserListClick) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = chatState.connectedUsers.size.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Icon(
                        Icons.Default.People,
                        contentDescription = "Users"
                    )
                }
                
                IconButton(onClick = onModeToggle) {
                    Icon(
                        when (chatState.currentChatMode) {
                            ChatMode.GROUP -> Icons.Default.Lock
                            ChatMode.PRIVATE -> Icons.Default.Group
                        },
                        contentDescription = "Toggle Mode"
                    )
                }
            }
            
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = when (chatState.connectionStatus) {
                ChatConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                ChatConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    )
}

@Composable
private fun EnhancedFAB(
    chatState: ChatState,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), 
        label = "FAB Scale"
    )
    
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.scale(scale),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            when {
                !chatState.isEnabled -> Icons.Default.Add
                chatState.connectionStatus == ChatConnectionStatus.CONNECTING -> Icons.Default.Refresh
                chatState.connectionStatus == ChatConnectionStatus.CONNECTED -> Icons.Default.Chat
                else -> Icons.Default.Error
            },
            contentDescription = when {
                !chatState.isEnabled -> "Start Chat"
                chatState.connectionStatus == ChatConnectionStatus.CONNECTING -> "Connecting..."
                chatState.connectionStatus == ChatConnectionStatus.CONNECTED -> "Chat Active"
                else -> "Error"
            }
        )
    }
}

// The remaining composables will follow this enhanced pattern
// I'll create the key ones to make the chat functional and beautiful

@Composable
private fun EnhancedUserListPanel(
    chatState: ChatState,
    viewModel: ChatViewModel,
    onCloseList: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Users (${chatState.connectedUsers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onCloseList) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Group chat option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        viewModel.selectUser(null)
                        viewModel.switchChatMode(ChatMode.GROUP)
                        onCloseList()
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (chatState.currentChatMode == ChatMode.GROUP) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = "Group Chat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Group Chat",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (chatState.currentChatMode == ChatMode.GROUP) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            if (chatState.connectedUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                chatState.connectedUsers.forEach { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { 
                                viewModel.selectUser(user)
                                viewModel.switchChatMode(ChatMode.PRIVATE)
                                onCloseList()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (chatState.selectedUser?.ipAddress == user.ipAddress) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // User avatar
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.firstOrNull()?.toString()?.uppercase() ?: "U",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Text(
                                    text = "${user.deviceName} • ${user.ipAddress}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            
                            if (chatState.selectedUser?.ipAddress == user.ipAddress) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.PersonSearch,
                            contentDescription = "No Users",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        
                        Text(
                            text = "No users discovered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedBottomInputBar(
    chatState: ChatState,
    viewModel: ChatViewModel
) {
    var messageText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Mode indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when (chatState.currentChatMode) {
                        ChatMode.GROUP -> Icons.Default.Group
                        ChatMode.PRIVATE -> Icons.Default.Lock
                    },
                    contentDescription = "Mode",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = when (chatState.currentChatMode) {
                        ChatMode.GROUP -> "Sending to group chat"
                        ChatMode.PRIVATE -> if (chatState.selectedUser != null) 
                            "Private message to ${chatState.selectedUser!!.name}" 
                        else "Select a user for private chat"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (chatState.currentChatMode == ChatMode.PRIVATE && chatState.selectedUser != null) {
                    Text(
                        text = "🔒 Encrypted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Input field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = when {
                                chatState.currentChatMode == ChatMode.PRIVATE && chatState.selectedUser == null -> 
                                    "Select a user to start chatting..."
                                chatState.currentChatMode == ChatMode.GROUP -> 
                                    "Type a message to everyone..."
                                chatState.currentChatMode == ChatMode.PRIVATE && chatState.selectedUser != null -> 
                                    "Type a private message..."
                                else -> "Ready to chat..."
                            }
                        )
                    },
                    enabled = chatState.currentChatMode == ChatMode.GROUP || 
                             (chatState.currentChatMode == ChatMode.PRIVATE && chatState.selectedUser != null),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { 
                            if (messageText.isNotBlank()) {
                                when (chatState.currentChatMode) {
                                    ChatMode.GROUP -> viewModel.sendMessage(messageText.trim())
                                    ChatMode.PRIVATE -> {
                                        chatState.selectedUser?.let { user ->
                                            viewModel.sendPrivateMessage(messageText.trim(), user)
                                        }
                                    }
                                }
                                messageText = ""
                                keyboardController?.hide()
                            }
                        }
                    ),
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp)
                )
                
                // Send button
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            when (chatState.currentChatMode) {
                                ChatMode.GROUP -> viewModel.sendMessage(messageText.trim())
                                ChatMode.PRIVATE -> {
                                    chatState.selectedUser?.let { user ->
                                        viewModel.sendPrivateMessage(messageText.trim(), user)
                                    }
                                }
                            }
                            messageText = ""
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (messageText.isNotBlank() && 
                        (chatState.currentChatMode == ChatMode.GROUP || chatState.selectedUser != null)) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send Message",
                        modifier = Modifier.size(20.dp),
                        tint = if (messageText.isNotBlank() && 
                            (chatState.currentChatMode == ChatMode.GROUP || chatState.selectedUser != null)) 
                            MaterialTheme.colorScheme.onPrimary 
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// Additional enhanced screens will be added to complete the functionality

@Composable
private fun EnhancedWelcomeScreen(
    onStartChat: () -> Unit,
    hasSavedName: Boolean,
    savedName: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Animated icon
                val infiniteTransition = rememberInfiniteTransition(label = "Welcome Animation")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000),
                        repeatMode = RepeatMode.Reverse
                    ), label = "Icon Scale"
                )
                
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Chat",
                    modifier = Modifier
                        .size(64.dp)
                        .scale(scale),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "WiFi Connect Chat",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Connect and chat with nearby devices on the same WiFi network",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (hasSavedName) Icons.Default.PlayArrow else Icons.Default.Person,
                        contentDescription = "Start",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasSavedName) "Start Chat as $savedName" else "Set Your Name & Start"
                    )
                }
                
                // Features card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✨ Features:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        listOf(
                            "💬 Real-time messaging",
                            "👥 Group & private chats", 
                            "🔒 End-to-end encryption",
                            "📡 No internet required",
                            "🔍 Auto device discovery"
                        ).forEach { feature ->
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedConnectingScreen(
    chatState: ChatState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Animated connecting indicator
                val infiniteTransition = rememberInfiniteTransition(label = "Connecting Animation")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "Rotation"
                )
                
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp
                )
                
                Text(
                    text = "🔄 Connecting to Chat Service",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Setting up secure connections and discovering nearby users...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                
                // Connection details
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "👤 User: ${chatState.myName}",
                        "🌐 IP: ${chatState.myIpAddress}",
                        "⚡ Status: ${chatState.connectionStatus}"
                    ).forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedErrorScreen(
    chatState: ChatState,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                Text(
                    text = "❌ Connection Failed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Unable to start the chat service. Please check your WiFi connection and try again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedChatArea(
    chatState: ChatState,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Get current messages based on mode
    val currentMessages = remember(chatState.currentChatMode, chatState.selectedUser, chatState.messages, chatState.privateMessages) {
        when (chatState.currentChatMode) {
            ChatMode.GROUP -> chatState.messages
            ChatMode.PRIVATE -> {
                chatState.selectedUser?.let { user ->
                    chatState.privateMessages[user.ipAddress] ?: emptyList()
                } ?: emptyList()
            }
        }
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(currentMessages.size - 1)
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (currentMessages.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    when (chatState.currentChatMode) {
                        ChatMode.GROUP -> Icons.Default.Group
                        ChatMode.PRIVATE -> Icons.Default.PersonPin
                    },
                    contentDescription = "Empty Chat",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when (chatState.currentChatMode) {
                        ChatMode.GROUP -> "Welcome to Group Chat! 👥"
                        ChatMode.PRIVATE -> if (chatState.selectedUser != null) 
                            "Private chat with ${chatState.selectedUser!!.name} 💬" 
                        else "Select a user to start private chat 👤"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = when (chatState.currentChatMode) {
                        ChatMode.GROUP -> "Start chatting with ${chatState.connectedUsers.size} connected users"
                        ChatMode.PRIVATE -> if (chatState.selectedUser != null) 
                            "Your conversation is end-to-end encrypted" 
                        else "Choose someone from the users list above"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = currentMessages,
                    key = { it.id }
                ) { message ->
                    EnhancedMessageBubble(
                        message = message,
                        isFromMe = message.isFromMe,
                        isPrivateChat = chatState.currentChatMode == ChatMode.PRIVATE,
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }
}

@Composable
private fun EnhancedMessageBubble(
    message: ChatMessage,
    isFromMe: Boolean,
    isPrivateChat: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isFromMe) {
            // Sender avatar for incoming messages
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.senderName.firstOrNull()?.toString()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromMe) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromMe) 16.dp else 4.dp,
                bottomEnd = if (isFromMe) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Message header (sender name and encryption status)
                if (!isFromMe || isPrivateChat) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFromMe) "You" else message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isFromMe) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isEncrypted) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Encrypted",
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isFromMe) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    }
                                )
                            }
                            
                            if (isPrivateChat) {
                                Icon(
                                    Icons.Default.PersonPin,
                                    contentDescription = "Private",
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isFromMe) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // Message content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromMe) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Timestamp
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFromMe) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        
        if (isFromMe) {
            Spacer(modifier = Modifier.width(8.dp))
            
            // My avatar for outgoing messages
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Me",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EnhancedUserNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "User Name",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Enter Your Name")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Choose a name that others will see when you chat with them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name") },
                    placeholder = { Text("Enter your chat name...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { 
                            if (name.isNotBlank()) {
                                onConfirm(name.trim())
                            }
                        }
                    )
                )
                
                if (name.isBlank()) {
                    Button(
                        onClick = { 
                            name = generateRandomName()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Random Name",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Random Name")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Start Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EnhancedSettingsDialog(
    chatState: ChatState,
    onDismiss: () -> Unit,
    onDataManagement: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Chat Settings")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Statistics
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📊 Session Statistics",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        
                        listOf(
                            "👥 Connected Users: ${chatState.connectedUsers.size}",
                            "💬 Group Messages: ${chatState.messages.size}",
                            "🔒 Private Conversations: ${chatState.privateMessages.size}",
                            "🌐 Your IP: ${chatState.myIpAddress}",
                            "⚡ Status: ${chatState.connectionStatus}"
                        ).forEach { stat ->
                            Text(
                                text = stat,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                // Data management button
                OutlinedButton(
                    onClick = onDataManagement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = "Data Management",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Chat Data")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun generateRandomName(): String {
    val adjectives = listOf(
        "Awesome", "Cool", "Smart", "Bright", "Quick", "Happy", "Lucky", "Swift",
        "Brave", "Clever", "Bold", "Mighty", "Super", "Epic", "Sharp", "Wise",
        "Zen", "Nova", "Stellar", "Cosmic", "Dynamic", "Rapid", "Elite", "Prime"
    )
    
    val nouns = listOf(
        "Coder", "Hacker", "Ninja", "Wizard", "Master", "Guru", "Pro", "Expert",
        "Genius", "Legend", "Hero", "Champion", "Warrior", "Knight", "Captain",
        "Phoenix", "Dragon", "Tiger", "Eagle", "Wolf", "Lion", "Falcon", "Shark"
    )
    
    val randomAdjective = adjectives.random()
    val randomNoun = nouns.random()
    val randomNumber = (10..99).random()
    
    return "$randomAdjective$randomNoun$randomNumber"
}
