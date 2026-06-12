package com.comfyui.client.ui.screen

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.comfyui.client.data.repository.WorkflowRepository
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWorkflowScreen(
    repository: WorkflowRepository,
    onImported: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var workflowJson by remember { mutableStateOf("") }
    var workflowName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPasting by remember { mutableStateOf(true) }
    var isReading by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isReading = true
            errorMessage = null
            try {
                // Read file name
                var fileName = "Imported Workflow"
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                // Read content
                val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""

                if (json.isBlank()) {
                    errorMessage = "File is empty"
                } else {
                    workflowJson = json
                    workflowName = fileName.removeSuffix(".json").removeSuffix(".JSON")
                    isPasting = false
                }
            } catch (e: Exception) {
                errorMessage = "Failed to read file: ${e.message}"
            }
            isReading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Workflow") },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isPasting,
                    onClick = { isPasting = true },
                    label = { Text("Paste JSON") },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = !isPasting,
                    onClick = { isPasting = false },
                    label = { Text("From File") },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (isPasting) {
                OutlinedTextField(
                    value = workflowJson,
                    onValueChange = {
                        workflowJson = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text("Workflow JSON") },
                    placeholder = { Text("Paste your ComfyUI workflow JSON here...") },
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        if (!isReading) {
                            filePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isReading) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Reading file...", style = MaterialTheme.typography.bodyMedium)
                        } else if (workflowJson.isNotEmpty()) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("File loaded", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${workflowJson.length} characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap to select JSON file", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Supports .json workflow files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                if (workflowJson.isNotEmpty()) {
                    OutlinedTextField(
                        value = workflowName,
                        onValueChange = { workflowName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Workflow Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (workflowJson.isBlank()) {
                        errorMessage = "Please paste or select a workflow JSON"
                        return@Button
                    }
                    try {
                        val jsonParser = Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        }
                        jsonParser.parseToJsonElement(workflowJson)
                        val name = workflowName.ifBlank {
                            if (workflowJson.length > 30) {
                                "Untitled Workflow"
                            } else {
                                workflowJson.take(30).replace("\n", " ")
                            }
                        }
                        val workflow = repository.importWorkflow(workflowJson, name)
                        onImported(workflow.id)
                    } catch (e: Exception) {
                        errorMessage = "Invalid JSON: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = workflowJson.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import")
            }
        }
    }
}
