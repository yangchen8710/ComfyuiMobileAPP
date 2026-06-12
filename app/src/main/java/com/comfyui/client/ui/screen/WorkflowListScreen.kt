package com.comfyui.client.ui.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyui.client.data.model.Workflow
import com.comfyui.client.data.repository.WorkflowRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    repository: WorkflowRepository,
    onWorkflowClick: (String) -> Unit,
    onImportClick: () -> Unit,
    onServerBrowseClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val workflows by repository.workflows.collectAsState()
    val context = LocalContext.current
    val serverUrl = remember { getSavedServerUrl(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ComfyUI Client") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Server status bar
            ServerStatusBar(
                serverUrl = serverUrl,
                repository = repository,
                onSettingsClick = onSettingsClick
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onServerBrowseClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("From Server")
                }
                Button(
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import JSON")
                }
            }

            if (workflows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No workflows yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Import from server or paste JSON",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(workflows) { workflow ->
                        WorkflowCard(
                            workflow = workflow,
                            onClick = { onWorkflowClick(workflow.id) },
                            onDelete = { repository.removeWorkflow(workflow.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerStatusBar(
    serverUrl: String,
    repository: WorkflowRepository,
    onSettingsClick: () -> Unit
) {
    var isChecking by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(serverUrl) {
        if (serverUrl.isNotBlank()) {
            isChecking = true
            repository.setServerUrl(serverUrl)
            connected = repository.testConnection().isSuccess
            isChecking = false
        }
    }

    val clickModifier = if (connected != true) {
        Modifier.clickable { onSettingsClick() }
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(clickModifier),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isChecking || connected == null -> MaterialTheme.colorScheme.surfaceVariant
            connected == true -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Checking server...", style = MaterialTheme.typography.bodyMedium)
            } else when (connected) {
                true -> {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Server connected", style = MaterialTheme.typography.labelLarge)
                }
                false -> {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Server unreachable", style = MaterialTheme.typography.labelLarge)
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Configure",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                null -> {}
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    workflow: Workflow,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${workflow.nodes.size} nodes \u00B7 ${dateFormat.format(Date(workflow.importedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete workflow",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun getSavedServerUrl(context: Context): String {
    val prefs = context.getSharedPreferences("comfyui_prefs", Context.MODE_PRIVATE)
    return prefs.getString("server_url", "http://your-comfyui-server:8188") ?: ""
}
