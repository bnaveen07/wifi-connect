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
fun LocalChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val chatState by viewModel.chatState.observeAsState(ChatState())
    var messageText by remember { mutableStateOf("") }
    var showUserNameDialog by remember { mutableStateOf(false) }
    var showDataManagementDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Name editing state
    var isEditingName by remember { mutableStateOf(false) }
    var editingNameText by remember { mutableStateOf("") }
    
    // Generate random name if empty
    val displayName = remember(chatState.myName) {
        if (chatState.myName.isEmpty()) {
            generateRandomName()
        } else {
            chatState.myName
        }
    }
    
    // Check for saved user name and auto-start if available
    LaunchedEffect(Unit) {
        if (!chatState.isEnabled && viewModel.hasSavedUserName()) {
            val savedName = viewModel.getSavedUserName()
            if (savedName.isNotEmpty()) {
                viewModel.startChat(savedName)
            }
        }
    }
    
    // Animation states for enhanced UX
    val connectionStatusAlpha by animateFloatAsState(
        targetValue = if (chatState.isEnabled) 1f else 0.5f,
        animationSpec = tween(500), label = "Connection Status Alpha"
    )
    
    val topBarScale by animateFloatAsState(
        targetValue = if (chatState.isEnabled) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "TopBar Scale"
    )
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(chatState.messages.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
            EnhancedTopAppBar(
                chatState = chatState,
                displayName = displayName,
                isEditingName = isEditingName,
                editingNameText = editingNameText,
                topBarScale = topBarScale,
                connectionStatusAlpha = connectionStatusAlpha,
                onBack = onBack,
                onStartEditing = { 
                    isEditingName = true
                    editingNameText = displayName
                },
                onFinishEditing = { newName ->
                    isEditingName = false
                    if (newName.isNotBlank() && newName != displayName) {
                        if (chatState.isEnabled) {
                            // Update name in running chat
                            // Note: This would need a new ViewModel method
                            // viewModel.updateMyName(newName.trim())
                        }
                    }
                },
                onCancelEditing = {
                    isEditingName = false
                    editingNameText = ""
                },
                onTextChange = { editingNameText = it },
                onModeToggle = { 
                    val newMode = if (chatState.currentChatMode == ChatMode.GROUP) 
                        ChatMode.PRIVATE else ChatMode.GROUP
                    viewModel.switchChatMode(newMode)
                    if (newMode == ChatMode.GROUP) {
                        viewModel.selectUser(null)
                    }
                },
                onDataManagement = { showDataManagementDialog = true },
                onStopChat = { viewModel.stopChat() }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = chatState.isEnabled,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut()
            ) {
                EnhancedChatInputBar(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    onSendMessage = {
                        if (messageText.isNotBlank()) {
                            when (chatState.currentChatMode) {
                                ChatMode.GROUP -> viewModel.sendMessage(messageText)
                                ChatMode.PRIVATE -> {
                                    chatState.selectedUser?.let { user ->
                                        viewModel.sendPrivateMessage(messageText, user)
                                    }
                                }
                            }
                            messageText = ""
                        }
                    },
                    currentMode = chatState.currentChatMode,
                    selectedUser = chatState.selectedUser
                )
            }
        },
        floatingActionButton = {
            // Always visible FAB - shows on both setup and chat screens
            FloatingActionButton(
                onClick = { 
                    if (!chatState.isEnabled) {
                        if (viewModel.hasSavedUserName()) {
                            val savedName = viewModel.getSavedUserName()
                            viewModel.startChat(savedName)
                        } else {
                            showUserNameDialog = true
                        }
                    } else {
                        // When connected, FAB can refresh discovery or show options
                        viewModel.refreshDiscovery()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.scale(
                    animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), 
                        label = "FAB Scale"
                    ).value
                )
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !chatState.isEnabled -> {
                    ChatSetupScreen(
                        viewModel = viewModel,
                        onStartChat = { 
                            if (viewModel.hasSavedUserName()) {
                                // Use saved name directly
                                val savedName = viewModel.getSavedUserName()
                                viewModel.startChat(savedName)
                            } else {
                                // Show dialog to get name
                                showUserNameDialog = true
                            }
                        },
                        onChangeName = { showUserNameDialog = true }
                    )
                }
                chatState.isEnabled && chatState.connectionStatus == ChatConnectionStatus.CONNECTING -> {
                    LoadingScreen("Starting chat service... Status: ${chatState.connectionStatus}")
                }
                chatState.isEnabled && chatState.connectionStatus == ChatConnectionStatus.ERROR -> {
                    ErrorScreen(
                        message = "Failed to start chat service",
                        onRetry = { showUserNameDialog = true }
                    )
                }
                chatState.isEnabled -> {
                    // MINIMAL CHAT: Using simplified but functional chat interface
                    // This handles CONNECTED state and any other enabled states
                    MinimalChatInterface(
                        chatState = chatState,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    
    // Show dialogs
    if (showUserNameDialog) {
        UserNameDialog(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedTopAppBar(
    chatState: ChatState,
    displayName: String,
    isEditingName: Boolean,
    editingNameText: String,
    topBarScale: Float,
    connectionStatusAlpha: Float,
    onBack: () -> Unit,
    onStartEditing: () -> Unit,
    onFinishEditing: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onTextChange: (String) -> Unit,
    onModeToggle: () -> Unit,
    onDataManagement: () -> Unit,
    onStopChat: () -> Unit
) {
    TopAppBar(
        modifier = Modifier
            .scale(topBarScale)
            .shadow(
                elevation = if (chatState.isEnabled) 4.dp else 2.dp,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            ),
        title = { 
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Inline Name Editor
                    InlineNameEditor(
                        displayName = displayName,
                        isEditing = isEditingName,
                        editingText = editingNameText,
                        onStartEditing = onStartEditing,
                        onFinishEditing = onFinishEditing,
                        onCancelEditing = onCancelEditing,
                        onTextChange = onTextChange,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Connection status indicator
                    AnimatedConnectionIndicator(
                        isConnected = chatState.isEnabled,
                        alpha = connectionStatusAlpha
                    )
                }
                
                if (chatState.isEnabled) {
                    val modeText = when (chatState.currentChatMode) {
                        ChatMode.GROUP -> "${chatState.connectedUsers.size} users • Group Chat"
                        ChatMode.PRIVATE -> chatState.selectedUser?.let { "Private with ${it.name}" } ?: "Select user"
                    }
                    Text(
                        text = modeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f))
            ) {
                Icon(
                    Icons.Default.ArrowBack, 
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            if (chatState.isEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Chat mode toggle with enhanced styling
                    EnhancedActionButton(
                        icon = if (chatState.currentChatMode == ChatMode.GROUP) 
                            Icons.Outlined.Person else Icons.Outlined.Group,
                        contentDescription = "Switch chat mode",
                        onClick = onModeToggle,
                        isActive = true
                    )
                    
                    // Data management button
                    EnhancedActionButton(
                        icon = Icons.Outlined.Storage,
                        contentDescription = "Data Management",
                        onClick = onDataManagement,
                        isActive = false
                    )
                    
                    // Stop chat button with warning color
                    EnhancedActionButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Stop chat",
                        onClick = onStopChat,
                        isActive = false,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun AnimatedConnectionIndicator(
    isConnected: Boolean,
    alpha: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Connection Pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ), label = "Pulse Scale"
    )
    
    Box(
        modifier = Modifier
            .scale(if (isConnected) pulseScale else 1f)
            .size(8.dp)
            .clip(CircleShape)
            .background(
                (if (isConnected) Color.Green else MaterialTheme.colorScheme.outline).copy(alpha = alpha)
            )
    )
}

@Composable
private fun EnhancedActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "Button Scale"
    )
    
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isActive) tint.copy(alpha = 0.1f) else Color.Transparent
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = tint.copy(alpha = 0.3f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(if (isActive) 22.dp else 20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedChatInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    currentMode: ChatMode,
    selectedUser: ChatUser?
) {
    val sendButtonScale by animateFloatAsState(
        targetValue = if (messageText.isNotBlank()) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "Send Button Scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Mode indicator
            if (currentMode == ChatMode.PRIVATE && selectedUser != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Private chat with ${selectedUser.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Input area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    placeholder = { 
                        Text(
                            text = when (currentMode) {
                                ChatMode.GROUP -> "Message everyone..."
                                ChatMode.PRIVATE -> selectedUser?.let { "Message ${it.name}..." } ?: "Select a user first..."
                            }
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = currentMode == ChatMode.GROUP || selectedUser != null
                )
                
                // Send button with animation
                FloatingActionButton(
                    onClick = onSendMessage,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(sendButtonScale),
                    containerColor = if (messageText.isNotBlank()) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (messageText.isNotBlank()) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = if (messageText.isNotBlank()) 6.dp else 2.dp
                    )
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "Send message",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSetupScreen(
    viewModel: ChatViewModel,
    onStartChat: () -> Unit,
    onChangeName: () -> Unit = {}
) {
    // Animation states
    val floatAnimation by rememberInfiniteTransition(label = "Float Animation").animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "Float Y"
    )
    
    val scaleAnimation by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "Scale Animation"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surface
                    ),
                    radius = 800f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .scale(scaleAnimation),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated chat icon
            Box(
                modifier = Modifier
                    .offset(y = floatAnimation.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape
                    )
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    )
                    .padding(24.dp)
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Title with gradient text effect
            Text(
                text = "WiFi Chat",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Connect. Chat. Collaborate.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Chat with other users connected to the same WiFi network. No internet required!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Enhanced start button
            Button(
                onClick = onStartChat,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val savedName = if (viewModel.hasSavedUserName()) viewModel.getSavedUserName() else ""
                    val buttonText = if (savedName.isNotEmpty()) "Continue as $savedName" else "Start Chatting"
                    
                    Icon(
                        Icons.Outlined.Chat, 
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        buttonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Enhanced info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "How it works",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val features = listOf(
                        "📶 Connect to the same WiFi network",
                        "👥 Other users need this app too",
                        "💬 Messages stay within your network",
                        "🔒 Private & group chat modes available",
                        "📱 No internet data usage required"
                    )
                    
                    features.forEach { feature ->
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    
                    // Show change name option if there's a saved name
                    if (viewModel.hasSavedUserName()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = onChangeName,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Change Name",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalChatInterface(
    chatState: ChatState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(chatState.messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Status Header
        MinimalStatusHeader(chatState = chatState)
        
        // Messages Area
        Box(
            modifier = Modifier.weight(1f)
        ) {
            when {
                chatState.connectionStatus == ChatConnectionStatus.CONNECTING -> {
                    // Enhanced connecting screen
                    ConnectingStatusScreen(chatState = chatState)
                }
                chatState.connectionStatus == ChatConnectionStatus.ERROR -> {
                    // Error state
                    ErrorStatusScreen(chatState = chatState)
                }
                chatState.connectionStatus == ChatConnectionStatus.CONNECTED -> {
                    // Connected - show chat
                    if (chatState.messages.isEmpty()) {
                        EmptyMessagesPlaceholder(chatState = chatState)
                    } else {
                        MinimalMessagesArea(
                            messages = chatState.messages,
                            listState = listState,
                            myName = chatState.myName
                        )
                    }
                }
                else -> {
                    // Unknown state
                    UnknownStatusScreen(chatState = chatState)
                }
            }
        }
        
        // Input Bar (only show when connected)
        if (chatState.connectionStatus == ChatConnectionStatus.CONNECTED) {
            MinimalInputBar(
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSendMessage = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                }
            )
        }
    }
}

@Composable
private fun MinimalStatusHeader(
    chatState: ChatState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (chatState.connectionStatus) {
                ChatConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                ChatConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Icon
            val statusIcon = when (chatState.connectionStatus) {
                ChatConnectionStatus.CONNECTED -> Icons.Default.CheckCircle
                ChatConnectionStatus.CONNECTING -> Icons.Default.Refresh
                ChatConnectionStatus.ERROR -> Icons.Default.Error
                else -> Icons.Default.Info
            }
            
            val statusColor = when (chatState.connectionStatus) {
                ChatConnectionStatus.CONNECTED -> Color.Green
                ChatConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
            
            Icon(
                statusIcon,
                contentDescription = "Status",
                modifier = Modifier.size(24.dp),
                tint = statusColor
            )
            
            // Status Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (chatState.connectionStatus) {
                        ChatConnectionStatus.CONNECTED -> "✅ Connected"
                        ChatConnectionStatus.CONNECTING -> "🔄 Connecting..."
                        ChatConnectionStatus.ERROR -> "❌ Connection Failed"
                        else -> "⚡ Unknown Status"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "User: ${chatState.myName} • Users: ${chatState.connectedUsers.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Connection indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        when (chatState.connectionStatus) {
                            ChatConnectionStatus.CONNECTED -> Color.Green
                            ChatConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                            ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> Color.Gray
                        },
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ConnectingStatusScreen(
    chatState: ChatState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Connecting to Chat Service...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Debug Info:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Status: ${chatState.connectionStatus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Enabled: ${chatState.isEnabled}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Name: ${chatState.myName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Users: ${chatState.connectedUsers.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Messages: ${chatState.messages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorStatusScreen(
    chatState: ChatState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "Error",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Text(
                text = "Connection Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
            
            Text(
                text = "Please check your connection and try again",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UnknownStatusScreen(
    chatState: ChatState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Info",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            
            Text(
                text = "Service Status Unknown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Status: ${chatState.connectionStatus}\nEnabled: ${chatState.isEnabled}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyMessagesPlaceholder(
    chatState: ChatState
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.Message,
                contentDescription = "Messages",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Start a conversation with other users on your network!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MinimalMessagesArea(
    messages: List<ChatMessage>,
    listState: LazyListState,
    myName: String
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            MinimalMessageItem(
                message = message,
                isFromMe = message.senderName == myName
            )
        }
    }
}

@Composable
private fun MinimalMessageItem(
    message: ChatMessage,
    isFromMe: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        if (isFromMe) {
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromMe) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ),
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
                if (!isFromMe) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Text(
                    text = message.displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromMe) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFromMe) 
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        
        if (!isFromMe) {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                placeholder = { Text("Type a message...") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = RoundedCornerShape(20.dp)
            )
            
            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(48.dp),
                containerColor = if (messageText.isNotBlank()) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SimpleConnectedTestScreen(
    chatState: ChatState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (chatState.connectionStatus) {
                    ChatConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                    ChatConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                    ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status icon with animation
                val infiniteTransition = rememberInfiniteTransition(label = "Status Animation")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ), label = "Scale"
                )
                
                val statusIcon = when (chatState.connectionStatus) {
                    ChatConnectionStatus.CONNECTED -> Icons.Default.CheckCircle
                    ChatConnectionStatus.CONNECTING -> Icons.Default.Refresh
                    ChatConnectionStatus.ERROR -> Icons.Default.Error
                    else -> Icons.Default.Info
                }
                
                val statusColor = when (chatState.connectionStatus) {
                    ChatConnectionStatus.CONNECTED -> Color.Green
                    ChatConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                    ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                
                Icon(
                    statusIcon,
                    contentDescription = "Status",
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale),
                    tint = statusColor
                )
                
                Text(
                    text = when (chatState.connectionStatus) {
                        ChatConnectionStatus.CONNECTED -> "🎉 Chat Service Connected!"
                        ChatConnectionStatus.CONNECTING -> "🔄 Connecting to Chat Service..."
                        ChatConnectionStatus.ERROR -> "❌ Connection Failed"
                        else -> "⚡ Service Status Unknown"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (chatState.connectionStatus) {
                        ChatConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                        ChatConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = when (chatState.connectionStatus) {
                        ChatConnectionStatus.CONNECTED -> "Service is working perfectly!"
                        ChatConnectionStatus.CONNECTING -> "Please wait while service initializes..."
                        ChatConnectionStatus.ERROR -> "Service failed to start"
                        else -> "Service status unclear"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Detailed debugging information
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Name: ${chatState.myName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Status: ${chatState.connectionStatus}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (chatState.isEnabled) Icons.Outlined.Check else Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (chatState.isEnabled) Color.Green else Color.Red
                        )
                        Text(
                            text = "Service: ${if (chatState.isEnabled) "Enabled" else "Disabled"}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Group,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Users: ${chatState.connectedUsers.size}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (chatState.connectionStatus == ChatConnectionStatus.CONNECTED) {
                        "✅ LocalChatService initialization successful\n✅ State transitions working correctly\n✅ No crashes in service layer"
                    } else {
                        "🔄 Debugging service initialization...\n📊 Monitoring state transitions\n🛠️ Checking for issues"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when (chatState.connectionStatus) {
                        ChatConnectionStatus.CONNECTED -> "Next: Debug complex UI components individually"
                        ChatConnectionStatus.CONNECTING -> "Waiting for service to reach CONNECTED state..."
                        ChatConnectionStatus.ERROR -> "Check logs for error details"
                        else -> "Check service initialization"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun ChatContentScreen(
    chatState: ChatState,
    listState: LazyListState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        // Users list sidebar
        UsersListSidebar(
            users = chatState.connectedUsers,
            myName = chatState.myName,
            selectedUser = chatState.selectedUser,
            currentMode = chatState.currentChatMode,
            onUserSelected = { user ->
                viewModel.selectUser(user)
                if (user != null) {
                    viewModel.markMessagesAsRead(user.ipAddress)
                }
            },
            onRefreshDiscovery = { viewModel.refreshDiscovery() },
            modifier = Modifier.width(200.dp)
        )
        
        // Chat messages
        ChatMessagesArea(
            messages = getCurrentMessages(chatState),
            listState = listState,
            currentMode = chatState.currentChatMode,
            selectedUser = chatState.selectedUser,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun getCurrentMessages(chatState: ChatState): List<ChatMessage> {
    return when (chatState.currentChatMode) {
        ChatMode.GROUP -> chatState.messages
        ChatMode.PRIVATE -> {
            chatState.selectedUser?.let { user ->
                chatState.privateMessages[user.ipAddress] ?: emptyList()
            } ?: emptyList()
        }
    }
}

@Composable
private fun UsersListSidebar(
    users: List<ChatUser>,
    myName: String,
    selectedUser: ChatUser?,
    currentMode: ChatMode,
    onUserSelected: (ChatUser?) -> Unit,
    onRefreshDiscovery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with mode indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentMode == ChatMode.GROUP) 
                                Icons.Outlined.Group else Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (currentMode == ChatMode.GROUP) "Group Chat" else "Private Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (currentMode == ChatMode.PRIVATE && selectedUser != null) {
                        Text(
                            text = "Chatting with ${selectedUser.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (currentMode == ChatMode.PRIVATE && selectedUser != null) {
                    IconButton(
                        onClick = { onUserSelected(null) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Back to group",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current user (me)
            EnhancedUserItem(
                name = myName,
                displayName = "$myName (You)",
                deviceName = "This device",
                isOnline = true,
                isMe = true,
                isSelected = false,
                unreadCount = 0,
                onClick = { }
            )
            
            if (users.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Connected users title
                Text(
                    text = "Connected Users (${users.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                users.forEach { user ->
                    EnhancedUserItem(
                        name = user.name,
                        displayName = user.name,
                        deviceName = user.deviceName,
                        isOnline = user.isOnline,
                        isMe = false,
                        isSelected = selectedUser?.ipAddress == user.ipAddress,
                        unreadCount = user.unreadCount,
                        onClick = {
                            if (currentMode == ChatMode.PRIVATE) {
                                onUserSelected(if (selectedUser?.ipAddress == user.ipAddress) null else user)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Looking for users...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Make sure others have\nstarted the chat too",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Refresh discovery button
                        Button(
                            onClick = onRefreshDiscovery,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Search",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedUserItem(
    name: String,
    displayName: String,
    deviceName: String,
    isOnline: Boolean,
    isMe: Boolean,
    isSelected: Boolean = false,
    unreadCount: Int = 0,
    onClick: () -> Unit = {}
) {
    val cardScale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "Card Scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                isMe -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status indicator with animation
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) Color.Green.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)
                        )
                )
                
                // Inner dot
                val pulseScale by rememberInfiniteTransition(label = "User Status Pulse").animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500),
                        repeatMode = RepeatMode.Reverse
                    ), label = "Pulse"
                )
                
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(if (isOnline) pulseScale else 1f)
                        .background(
                            if (isOnline) Color.Green else Color.Gray,
                            CircleShape
                        )
                )
                
                // Me indicator
                if (isMe) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = "You",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // User info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isMe || isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Unread count badge
            if (unreadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatMessagesArea(
    messages: List<ChatMessage>,
    listState: LazyListState,
    currentMode: ChatMode,
    selectedUser: ChatUser?,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        EmptyMessagesState(currentMode, selectedUser, modifier)
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)),
                    modifier = Modifier.animateItemPlacement()
                ) {
                    EnhancedChatMessageItem(
                        message = message,
                        showPrivateIndicator = currentMode == ChatMode.GROUP && message.isPrivate
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessagesState(
    currentMode: ChatMode,
    selectedUser: ChatUser?,
    modifier: Modifier = Modifier
) {
    val floatAnimation by rememberInfiniteTransition(label = "Empty State Float").animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "Empty Float Y"
    )
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = floatAnimation.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Message,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = when (currentMode) {
                    ChatMode.GROUP -> "No messages yet"
                    ChatMode.PRIVATE -> if (selectedUser != null) 
                        "No messages with ${selectedUser.name}" 
                    else "Select a user to start private chat"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when (currentMode) {
                    ChatMode.GROUP -> "Be the first to send a message!"
                    ChatMode.PRIVATE -> if (selectedUser != null) 
                        "Start your private conversation" 
                    else "Choose someone to chat with privately"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EnhancedChatMessageItem(
    message: ChatMessage,
    showPrivateIndicator: Boolean = false
) {
    val bubbleScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "Bubble Scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(bubbleScale),
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        if (message.isFromMe) {
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.isFromMe) 20.dp else 6.dp,
                        bottomEnd = if (message.isFromMe) 6.dp else 20.dp
                    )
                ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    message.isFromMe && message.isPrivate -> MaterialTheme.colorScheme.tertiary
                    message.isFromMe -> MaterialTheme.colorScheme.primary
                    message.isPrivate -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isFromMe) 20.dp else 6.dp,
                bottomEnd = if (message.isFromMe) 6.dp else 20.dp
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header with sender info and indicators
                if (!message.isFromMe || message.isPrivate || showPrivateIndicator) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!message.isFromMe) {
                            Text(
                                text = message.senderName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (message.isPrivate) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        // Indicators row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isPrivate && showPrivateIndicator) {
                                Surface(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Lock,
                                            contentDescription = "Private message",
                                            modifier = Modifier.size(10.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = "Private",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                            
                            if (message.isEncrypted) {
                                Surface(
                                    color = Color.Green.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Security,
                                            contentDescription = "Encrypted",
                                            modifier = Modifier.size(10.dp),
                                            tint = Color.Green.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Encrypted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Green.copy(alpha = 0.8f),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Message content
                Text(
                    text = message.displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        message.isFromMe && message.isPrivate -> MaterialTheme.colorScheme.onTertiary
                        message.isFromMe -> MaterialTheme.colorScheme.onPrimary
                        message.isPrivate -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Footer with time and private message info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isPrivate && message.isFromMe && !message.recipientName.isNullOrEmpty()) {
                        Text(
                            text = "to ${message.recipientName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                message.isFromMe && message.isPrivate -> MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.7f)
                                message.isFromMe -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    
                    Text(
                        text = message.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            message.isFromMe && message.isPrivate -> MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.7f)
                            message.isFromMe -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            message.isPrivate -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        
        if (!message.isFromMe) {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                placeholder = { Text("Type a message...") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send message",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

@Composable
private fun UserNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var userName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Your Name") },
        text = {
            Column {
                Text("Choose a name that other users will see in the chat.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (userName.isNotBlank()) {
                        onConfirm(userName.trim())
                    }
                },
                enabled = userName.isNotBlank()
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
private fun InlineNameEditor(
    displayName: String,
    isEditing: Boolean,
    editingText: String,
    onStartEditing: () -> Unit,
    onFinishEditing: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }
    
    Box(modifier = modifier) {
        if (isEditing) {
            // Editing mode - show text field
            OutlinedTextField(
                value = editingText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                singleLine = true,
                placeholder = {
                    Text(
                        "Enter your name",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onFinishEditing(editingText)
                    }
                )
            )
        } else {
            // Display mode - show clickable text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartEditing() }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
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
