package com.bnaveen07.wificonnect.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bnaveen07.wificonnect.data.ChatStatistics
import com.bnaveen07.wificonnect.viewmodel.ChatViewModel
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDataManagementDialog(
    chatViewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmTitle by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }
    var statistics by remember { mutableStateOf<ChatStatistics?>(null) }
    var showStatistics by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        statistics = chatViewModel.getChatStatistics()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Chat Data Management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Statistics Button
                DataActionButton(
                    icon = Icons.Default.Info,
                    title = "View Statistics",
                    description = "View chat statistics and storage info",
                    onClick = { showStatistics = true }
                )

                Divider()

                // Save Data Button
                DataActionButton(
                    icon = Icons.Default.Save,
                    title = "Save Chat Data",
                    description = "Manually save current chat data",
                    onClick = {
                        chatViewModel.saveChatData()
                        // Show success message
                    }
                )

                // Export Data Button
                DataActionButton(
                    icon = Icons.Default.Share,
                    title = "Export Chat Data",
                    description = "Export chat data as JSON file",
                    onClick = {
                        val exportData = chatViewModel.exportChatData()
                        exportChatDataToFile(context, exportData)
                    }
                )

                Divider()

                // Clear Group Messages
                DataActionButton(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Group Messages",
                    description = "Remove all group chat messages",
                    onClick = {
                        confirmTitle = "Clear Group Messages"
                        confirmMessage = "Are you sure you want to delete all group messages? This action cannot be undone."
                        confirmAction = { chatViewModel.clearGroupMessages() }
                        showConfirmDialog = true
                    }
                )

                // Clear Private Messages
                DataActionButton(
                    icon = Icons.Default.PersonRemove,
                    title = "Clear Private Messages",
                    description = "Remove all private messages",
                    onClick = {
                        confirmTitle = "Clear Private Messages"
                        confirmMessage = "Are you sure you want to delete all private messages? This action cannot be undone."
                        confirmAction = { chatViewModel.clearAllPrivateMessages() }
                        showConfirmDialog = true
                    }
                )

                // Clear All Data
                DataActionButton(
                    icon = Icons.Default.Delete,
                    title = "Clear All Chat Data",
                    description = "Remove all messages and chat data",
                    onClick = {
                        confirmTitle = "Clear All Chat Data"
                        confirmMessage = "Are you sure you want to delete ALL chat data? This includes all messages, user data, and settings. This action cannot be undone."
                        confirmAction = { chatViewModel.clearAllChatData() }
                        showConfirmDialog = true
                    }
                )

                // Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmAction?.invoke()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Statistics Dialog
    if (showStatistics && statistics != null) {
        StatisticsDialog(
            statistics = statistics!!,
            onDismiss = { showStatistics = false }
        )
    }
}

@Composable
private fun DataActionButton(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Action",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatisticsDialog(
    statistics: ChatStatistics,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Chat Statistics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Statistics Items
                StatisticItem("Total Group Messages", statistics.totalGroupMessages.toString())
                StatisticItem("Total Private Messages", statistics.totalPrivateMessages.toString())
                StatisticItem("Total Messages", statistics.totalMessages.toString())
                StatisticItem("Known Users", statistics.knownUsersCount.toString())
                StatisticItem("Total Conversations", statistics.totalConversations.toString())
                
                if (statistics.newestMessageTime > 0) {
                    StatisticItem(
                        "Last Message", 
                        dateFormatter.format(Date(statistics.newestMessageTime))
                    )
                }
                
                if (statistics.oldestMessageTime > 0) {
                    StatisticItem(
                        "Oldest Message", 
                        dateFormatter.format(Date(statistics.oldestMessageTime))
                    )
                }

                // Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun exportChatDataToFile(context: android.content.Context, data: String) {
    try {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "wifi_chat_export_$timeStamp.json"
        
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(data)
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Export Chat Data"))
        
    } catch (e: Exception) {
        // Handle error - could show a toast or error dialog
        android.util.Log.e("ChatDataManagement", "Error exporting chat data", e)
    }
}
