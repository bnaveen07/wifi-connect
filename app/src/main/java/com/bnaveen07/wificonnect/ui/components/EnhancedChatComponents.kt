package com.bnaveen07.wificonnect.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bnaveen07.wificonnect.model.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EnhancedChatMessage(
    message: ChatMessage,
    showSender: Boolean = true,
    onMessageClick: () -> Unit = {},
    onMarkAsRead: () -> Unit = {}
) {
    val isFromMe = message.isFromMe
    
    // Animation for new messages
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "messageScale"
    )
    
    // Unread message indicator animation
    val unreadAlpha by animateFloatAsState(
        targetValue = if (!message.isRead && !isFromMe) 1f else 0f,
        animationSpec = tween(300),
        label = "unreadAlpha"
    )
    
    LaunchedEffect(message.id) {
        if (!message.isRead && !isFromMe) {
            kotlinx.coroutines.delay(1000) // Mark as read after 1 second of display
            onMarkAsRead()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
        ) {
            if (!isFromMe) {
                // Sender avatar
                if (showSender) {
                    UserAvatar(
                        name = message.senderName,
                        isOnline = true,
                        size = 32.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            
            // Message bubble
            Column(
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                // Sender name (if not from me and should show)
                if (!isFromMe && showSender) {
                    Text(
                        text = message.senderName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                
                // Message content card
                Card(
                    modifier = Modifier
                        .clickable { onMessageClick() }
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFromMe) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(
                        topStart = if (isFromMe) 16.dp else if (showSender) 4.dp else 16.dp,
                        topEnd = if (isFromMe) if (showSender) 4.dp else 16.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (!message.isRead && !isFromMe) 4.dp else 2.dp
                    )
                ) {
                    Box {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Message content
                            Text(
                                text = message.displayContent,
                                color = if (isFromMe) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 16.sp,
                                lineHeight = 20.sp
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Message metadata row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Encryption indicator
                                if (message.isEncrypted) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Encrypted",
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isFromMe) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                
                                // Timestamp
                                Text(
                                    text = message.formattedTime,
                                    fontSize = 11.sp,
                                    color = if (isFromMe) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    }
                                )
                                
                                // Delivery status for sent messages
                                if (isFromMe) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    DeliveryStatusIcon(
                                        status = message.deliveryStatus,
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        
                        // Unread indicator
                        if (!message.isRead && !isFromMe) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = unreadAlpha),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
                
                // Message reactions or additional info could go here
            }
            
            if (isFromMe) {
                Spacer(modifier = Modifier.width(8.dp))
                // My avatar
                if (showSender) {
                    UserAvatar(
                        name = "Me",
                        isOnline = true,
                        size = 32.dp
                    )
                }
            }
        }
    }
}

@Composable
fun DeliveryStatusIcon(
    status: DeliveryStatus,
    tint: Color
) {
    val icon = when (status) {
        DeliveryStatus.SENDING -> Icons.Default.Schedule
        DeliveryStatus.SENT -> Icons.Default.Done
        DeliveryStatus.DELIVERED -> Icons.Default.DoneAll
        DeliveryStatus.READ -> Icons.Default.DoneAll
        DeliveryStatus.FAILED -> Icons.Default.Error
    }
    
    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(12.dp),
        tint = if (status == DeliveryStatus.READ) {
            Color.Green
        } else if (status == DeliveryStatus.FAILED) {
            Color.Red
        } else {
            tint
        }
    )
}

@Composable
fun UserAvatar(
    name: String,
    isOnline: Boolean,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box {
        Card(
            modifier = Modifier.size(size),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = if (isOnline) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4).sp
                )
            }
        }
        
        // Online indicator
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .background(
                        color = Color.Green,
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun ChatTypingIndicator(
    userNames: List<String>
) {
    if (userNames.isEmpty()) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dots by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "typingDots"
    )
    
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = userNames.first(),
                isOnline = true,
                size = 24.dp
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = when {
                    userNames.size == 1 -> "${userNames.first()} is typing"
                    userNames.size == 2 -> "${userNames[0]} and ${userNames[1]} are typing"
                    else -> "${userNames.first()} and ${userNames.size - 1} others are typing"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // Animated dots
            Text(
                text = "•".repeat(dots.toInt() + 1),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MessageDateSeparator(
    date: Long
) {
    val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val messageDate = Calendar.getInstance().apply { timeInMillis = date }
    
    val dateText = when {
        isSameDay(today, messageDate) -> "Today"
        isSameDay(yesterday, messageDate) -> "Yesterday"
        else -> dateFormat.format(Date(date))
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Divider(modifier = Modifier.weight(1f))
        Text(
            text = dateText,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Divider(modifier = Modifier.weight(1f))
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
