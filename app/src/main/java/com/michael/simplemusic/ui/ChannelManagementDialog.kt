package com.michael.simplemusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.michael.simplemusic.data.AudioChannel
import com.michael.simplemusic.data.ChannelType

@Composable
fun ChannelManagementDialog(
    channels: List<AudioChannel>,
    activeChannelId: Int?,
    onCreateChannel: (String, ChannelType) -> Unit,
    onRenameChannel: (Int, String) -> Unit,
    onDeleteChannel: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newChannelName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChannelType.FOLDER) }
    var editingChannelId by remember { mutableStateOf<Int?>(null) }
    var editingName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Channels") },
        text = {
            Column(modifier = Modifier.widthIn(min = 280.dp)) {
                // Create new channel
                if (channels.size < 20) {
                    OutlinedTextField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        label = { Text("New channel name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ChannelType.values().forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.name.lowercase().capitalize()) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (newChannelName.isNotBlank()) {
                                onCreateChannel(newChannelName.trim(), selectedType)
                                newChannelName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = newChannelName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Channel")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Text(
                        "Maximum of 20 channels reached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Existing channels list
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(channels) { channel ->
                        if (editingChannelId == channel.id) {
                            // Inline rename mode
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = { editingName = it },
                                singleLine = true,
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            if (editingName.isNotBlank()) {
                                                onRenameChannel(channel.id, editingName.trim())
                                            }
                                            editingChannelId = null
                                        }) {
                                            Icon(Icons.Default.Check, contentDescription = "Save")
                                        }
                                        IconButton(onClick = { editingChannelId = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Normal display
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (channel.id == activeChannelId) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                
                                val typeIcon = when(channel.type) {
                                    ChannelType.FOLDER -> Icons.Default.Folder
                                    ChannelType.RADIO -> Icons.Default.Radio
                                    ChannelType.PODCAST -> Icons.Default.Podcasts
                                }
                                Icon(typeIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    channel.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    editingChannelId = channel.id
                                    editingName = channel.name
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    showDeleteConfirm = channel.id
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    // Delete confirmation dialog
    showDeleteConfirm?.let { channelId ->
        val channelName = channels.find { it.id == channelId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Channel") },
            text = {
                Text("Delete \"$channelName\"? This will remove all saved settings for this channel.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChannel(channelId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun String.capitalize() = this.lowercase().replaceFirstChar { it.uppercase() }
