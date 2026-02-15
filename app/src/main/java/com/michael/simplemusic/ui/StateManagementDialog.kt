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
import com.michael.simplemusic.data.ListeningState

@Composable
fun StateManagementDialog(
    states: List<ListeningState>,
    activeStateId: Int?,
    onCreateState: (String) -> Unit,
    onRenameState: (Int, String) -> Unit,
    onDeleteState: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newStateName by remember { mutableStateOf("") }
    var editingStateId by remember { mutableStateOf<Int?>(null) }
    var editingName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Listening States") },
        text = {
            Column(modifier = Modifier.widthIn(min = 280.dp)) {
                // Create new state
                if (states.size < 5) {
                    OutlinedTextField(
                        value = newStateName,
                        onValueChange = { newStateName = it },
                        label = { Text("New state name") },
                        singleLine = true,
                        trailingIcon = {
                            if (newStateName.isNotBlank()) {
                                IconButton(onClick = {
                                    onCreateState(newStateName.trim())
                                    newStateName = ""
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Text(
                        "Maximum of 5 states reached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Existing states list
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(states) { state ->
                        if (editingStateId == state.id) {
                            // Inline rename mode
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = { editingName = it },
                                singleLine = true,
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            if (editingName.isNotBlank()) {
                                                onRenameState(state.id, editingName.trim())
                                            }
                                            editingStateId = null
                                        }) {
                                            Icon(Icons.Default.Check, contentDescription = "Save")
                                        }
                                        IconButton(onClick = { editingStateId = null }) {
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
                                if (state.id == activeStateId) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    state.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    editingStateId = state.id
                                    editingName = state.name
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    showDeleteConfirm = state.id
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
    showDeleteConfirm?.let { stateId ->
        val stateName = states.find { it.id == stateId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete State") },
            text = {
                Text("Delete \"$stateName\"? This will remove the saved folder and playback position.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteState(stateId)
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
