package com.comfyui.client.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.comfyui.client.data.repository.WorkflowRepository
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPreviewScreen(
    imageUrl: String,
    onBack: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
                contentScale = ContentScale.Fit
            )

            if (scale != 1f) {
                SmallFloatingActionButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset zoom")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: WorkflowRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedUrl = remember { getSavedServerUrl(context) }
    var serverUrl by remember { mutableStateOf(savedUrl) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isSaved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Server Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    testResult = null
                    isSaved = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ComfyUI Server URL") },
                placeholder = { Text("http://your-comfyui-server:8188") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = null
                            repository.setServerUrl(serverUrl)
                            val result = repository.testConnection()
                            testResult = if (result.isSuccess) {
                                val devices = result.getOrDefault("")
                                "Connected \u2714 ($devices)"
                            } else {
                                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                                "Failed: $msg"
                            }
                            isTesting = false
                        }
                    },
                    enabled = !isTesting && serverUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isTesting) "Testing..." else "Test Connection")
                }

                Button(
                    onClick = {
                        saveServerUrl(context, serverUrl)
                        repository.setServerUrl(serverUrl)
                        isSaved = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = serverUrl.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            }

            testResult?.let {
                val isSuccess = it.startsWith("Connected")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isSuccess) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (isSaved) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Server URL saved", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Divider()

            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium
            )

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ComfyUI Client v1.0.0", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "An Android client for ComfyUI that supports workflow import, node configuration, Danbooru tag autocomplete, and result preview.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun getSavedServerUrl(context: Context): String {
    val prefs = context.getSharedPreferences("comfyui_prefs", Context.MODE_PRIVATE)
    return prefs.getString("server_url", "http://your-comfyui-server:8188") ?: ""
}

private fun saveServerUrl(context: Context, url: String) {
    context.getSharedPreferences("comfyui_prefs", Context.MODE_PRIVATE)
        .edit().putString("server_url", url).apply()
}
