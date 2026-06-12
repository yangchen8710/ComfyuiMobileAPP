package com.comfyui.client.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.comfyui.client.data.model.*
import com.comfyui.client.data.model.HistoryRecord
import com.comfyui.client.data.model.ConfigSnapshot
import com.comfyui.client.data.repository.WorkflowRepository
import com.comfyui.client.ui.component.ImageInputCard
import com.comfyui.client.ui.component.TagAutoCompleteTextField
import com.comfyui.client.util.WorkflowParser
import com.comfyui.client.util.ImageSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NodeConfigScreen(
    workflowId: String,
    historyId: String? = null,
    repository: WorkflowRepository,
    onBack: () -> Unit
) {
    val workflow = repository.getWorkflow(workflowId) ?: return
    val saveCtx = LocalContext.current
    val inputNodes = remember { WorkflowParser.findInputNodes(workflow) }
    val hasAppFilter = remember {
        workflow.nodes.any { node ->
            node.title.contains("(APP)", ignoreCase = true) &&
            node.inputs.any { it.type in listOf(InputType.TEXT, InputType.MULTILINE_TEXT, InputType.IMAGE) }
        }
    }

    val filteredNodes = remember(inputNodes) {
        if (hasAppFilter) {
            inputNodes.filter { it.title.contains("(APP)", ignoreCase = true) }
        } else {
            inputNodes
        }
    }

    val historyRecord = remember(historyId) {
        if (historyId != null) {
            repository.getLocalHistory(workflowId).find { it.id == historyId }
        } else null
    }

    var configs by remember(historyId) {
        android.util.Log.i("NodeConfig", "Init configs: historyId=$historyId, histRecord=${historyRecord?.id}, configCount=${historyRecord?.configs?.size}, nodeCount=${filteredNodes.size}")
        historyRecord?.configs?.forEach { cs ->
            android.util.Log.i("NodeConfig", "  hist config: nodeId=${cs.nodeId}, input=${cs.inputName}, value=${cs.value.take(40)}")
        }
        mutableStateOf(
            filteredNodes.flatMap { node ->
                node.inputs
                    .filter { it.type in listOf(InputType.TEXT, InputType.MULTILINE_TEXT, InputType.IMAGE) }
                    .map { input ->
                        val histValue = historyRecord?.configs
                            ?.find { it.nodeId == node.nodeId && it.inputName == input.name }?.value
                        if (histValue != null) {
                            android.util.Log.i("NodeConfig", "  MATCH: node=${node.nodeId} input=${input.name} value=${histValue.take(40)}")
                        }
                        InputConfig(
                            nodeId = node.nodeId,
                            inputName = input.name,
                            isEnabled = true,
                            value = histValue ?: input.defaultValue
                        )
                    }
            }
        )
    }

    var imageUris by remember { mutableStateOf(mapOf<String, Uri>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableStateOf(0f) }
    var savedPromptId by remember { mutableStateOf<String?>(null) }
    var resultImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var generationError by remember { mutableStateOf<String?>(null) }

    // Show promptId when entering from history
    LaunchedEffect(historyRecord) {
        val hr = historyRecord
        if (hr != null && savedPromptId == null) {
            savedPromptId = hr.serverPromptId
        }
    }

    val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(workflow.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Apply current configs to rawJson and save
                        var updatedJson = workflow.rawJson
                        configs.forEach { config ->
                            if (config.isEnabled) {
                                updatedJson = WorkflowParser.updateWorkflowInput(
                                    updatedJson, config.nodeId, config.inputName, config.value
                                )
                            }
                        }
                        repository.updateWorkflowJson(workflowId, updatedJson)
                        scope.launch {
                            snackbarHostState.showSnackbar("Saved")
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isGenerating = true
                            generationError = null
                            resultImageUrls = emptyList()
                            generationProgress = 0f

                            // Save history immediately before API call
                            val localId = java.util.UUID.randomUUID().toString()
                            // Don't show local UUID
                            val snapshots = configs.filter { it.isEnabled }.map { config ->
                                ConfigSnapshot(config.nodeId, config.inputName, config.value)
                            }
                            repository.saveHistory(workflowId, HistoryRecord(
                                id = localId,
                                workflowId = workflowId,
                                workflowName = workflow.name,
                                configs = snapshots,
                                resultImageUrls = emptyList()
                            ))

                            var updatedJson = workflow.rawJson
                            configs.forEach { config ->
                                val value = if (config.inputName.lowercase().contains("image")) {
                                    imageUris["${config.nodeId}_${config.inputName}"]?.toString() ?: config.value
                                } else {
                                    config.value
                                }
                                if (config.isEnabled) {
                                    updatedJson = WorkflowParser.updateWorkflowInput(
                                        updatedJson, config.nodeId, config.inputName, value
                                    )
                                }
                            }

                            generationProgress = 0.05f
                            val objectInfo = repository.getObjectInfo().getOrNull()
                            generationProgress = 0.1f

                            val promptObj = WorkflowParser.toPromptFormat(updatedJson, objectInfo)
                            val promptMap = promptObj.toMap()

                            repository.queuePrompt(promptMap).onSuccess { promptId ->
                                // Update history with server promptId (keep local UUID as id)
                                repository.updateHistoryPromptId(workflowId, localId, promptId)
                                savedPromptId = promptId
                                generationProgress = 0.2f
                                var attempts = 0
                                while (attempts < 90 && isGenerating) {
                                    delay(2000)
                                    generationProgress = (0.2f + attempts.toFloat() / 90f * 0.7f).coerceIn(0f, 0.9f)

                                    repository.getHistory(promptId).onSuccess { entry ->
                                        val outputs = entry.outputs
                                        val allImageUrls = mutableListOf<String>()

                                        for ((_, nodeOutput) in outputs) {
                                            nodeOutput.images?.forEach { img ->
                                                allImageUrls.add(repository.getImageUrl(
                                                    filename = img.filename,
                                                    subfolder = img.subfolder,
                                                    type = img.type
                                                ))
                                            }
                                            nodeOutput.gifs?.forEach { gif ->
                                                allImageUrls.add(repository.getImageUrl(
                                                    filename = gif.filename,
                                                    subfolder = gif.subfolder,
                                                    type = gif.type
                                                ))
                                            }
                                        }

                                        if (allImageUrls.isNotEmpty()) {
                                            resultImageUrls = allImageUrls
                                            generationProgress = 1f
                                            isGenerating = false
                                            // Update history with images
                                            repository.updateHistoryImages(workflowId, localId, allImageUrls)
                                            return@launch
                                        }

                                        if (entry.status?.completed == true) {
                                            if (entry.status?.status_str == "success") {
                                                generationError = "Generation completed but no output images found."
                                            } else {
                                                generationError = "Generation failed: ${entry.status?.status_str}"
                                            }
                                            isGenerating = false
                                            return@launch
                                        }
                                    }.onFailure { e ->
                                        generationError = "History check failed: ${e.message}"
                                        isGenerating = false
                                        return@launch
                                    }
                                    attempts++
                                }
                                if (isGenerating) {
                                    generationError = "Generation timed out. Check ComfyUI server."
                                    isGenerating = false
                                }
                            }.onFailure { e ->
                                generationError = "Failed to queue: ${e.message}"
                                isGenerating = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating... ${(generationProgress * 100).toInt()}%")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate")
                    }
                }
            }
        }
    ) { padding ->
        if (filteredNodes.isEmpty() && configs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No text or image input nodes marked (APP) found in this workflow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Error message
                item {
                    if (generationError != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    generationError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Progress bar with promptId
                item {
                    if (isGenerating) {
                        LinearProgressIndicator(
                            progress = generationProgress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Prompt ID (only when available)
                if (savedPromptId != null) {
                    item {
                        Text(
                            text = "Run: $savedPromptId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Header
                item {
                    Text(
                        "Configure Inputs",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Results section - below all config cards
                if (resultImageUrls.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Results",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val pagerState = rememberPagerState(pageCount = { resultImageUrls.size })

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                ) { page ->
                                    AsyncImage(
                                        model = resultImageUrls[page],
                                        contentDescription = "Result ${page + 1}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                // Page indicator and save button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${resultImageUrls.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                                                        TextButton(onClick = {
                                        val url = resultImageUrls.getOrNull(pagerState.currentPage) ?: return@TextButton
                                        scope.launch {
                                            val result = ImageSaver.saveToGallery(saveCtx, url)
                                            Toast.makeText(saveCtx,
                                                if (result.isSuccess) "Saved to gallery" else "Save failed",
                                                Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.SaveAlt, contentDescription = "Save", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }

                // Config input cards
                items(configs) { config ->
                    val node = filteredNodes.find { it.nodeId == config.nodeId }
                    val input = node?.inputs?.find { it.name == config.inputName }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = node?.title ?: config.nodeId,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = config.inputName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                Switch(
                                    checked = config.isEnabled,
                                    onCheckedChange = { checked ->
                                        configs = configs.map {
                                            if (it == config) it.copy(isEnabled = checked) else it
                                        }
                                    }
                                )
                            }

                            if (config.isEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))

                                when (input?.type) {
                                    InputType.MULTILINE_TEXT, InputType.TEXT -> {
                                        TagAutoCompleteTextField(
                                            value = config.value,
                                            onValueChange = { newValue ->
                                                configs = configs.map {
                                                    if (it == config) it.copy(value = newValue) else it
                                                }
                                            },
                                            placeholder = if (input.type == InputType.MULTILINE_TEXT)
                                                "Enter prompt with Danbooru tags..." else "Enter value..."
                                        )
                                    }
                                    InputType.IMAGE -> {
                                        val uriKey = "${config.nodeId}_${config.inputName}"
                                        ImageInputCard(
                                            label = "${node?.title} - ${config.inputName}",
                                            imageUri = imageUris[uriKey],
                                            onImageSelected = { uri ->
                                                imageUris = imageUris + (uriKey to uri)
                                            },
                                            onImageRemoved = {
                                                imageUris = imageUris - uriKey
                                            }
                                        )
                                    }
                                    else -> {
                                        OutlinedTextField(
                                            value = config.value,
                                            onValueChange = { newValue ->
                                                configs = configs.map {
                                                    if (it == config) it.copy(value = newValue) else it
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(config.inputName) },
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                
            }
        }
    }
}
