package com.comfyui.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.comfyui.client.data.model.HistoryRecord
import com.comfyui.client.data.repository.WorkflowRepository
import com.comfyui.client.util.ImageSaver
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    workflowId: String,
    workflowName: String,
    repository: WorkflowRepository,
    onHistoryClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var history by remember { mutableStateOf(repository.getLocalHistory(workflowId)) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    fun refresh() { history = repository.getLocalHistory(workflowId) }

    val scope = rememberCoroutineScope()
    
    // Auto-fetch images for records with serverPromptId but no images
    LaunchedEffect(Unit) {
        history.filter { it.resultImageUrls.isEmpty() && it.serverPromptId != null }.forEach { record ->
            try {
                repository.getHistory(record.serverPromptId!!).onSuccess { entry ->
                    val urls = mutableListOf<String>()
                    for ((_, nodeOutput) in entry.outputs) {
                        nodeOutput.images?.forEach { img ->
                            urls.add(repository.getImageUrl(
                                filename = img.filename, subfolder = img.subfolder, type = img.type))
                        }
                        nodeOutput.gifs?.forEach { gif ->
                            urls.add(repository.getImageUrl(
                                filename = gif.filename, subfolder = gif.subfolder, type = gif.type))
                        }
                    }
                    if (urls.isNotEmpty()) {
                        repository.updateHistoryImages(workflowId, record.id, urls)
                        history = repository.getLocalHistory(workflowId)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Delete all non-favored
                    if (history.any { !it.isFav }) {
                        var showDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear non-favorites")
                        }
                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text("Clear non-favorites") },
                                text = { Text("Delete all history entries that are not marked as favorite?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        repository.deleteAllNonFavHistory(workflowId)
                                        refresh()
                                        showDialog = false
                                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                                }
                            )
                        }
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
        ) {
            // Workflow name header
            Text(
                text = workflowName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No history yet", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history.size) { index ->
                        val record = history[index]
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete entry?") },
                                text = { Text(dateFormat.format(Date(record.createdAt))) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        repository.deleteHistory(workflowId, record.id)
                                        refresh()
                                        showDeleteConfirm = false
                                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                                }
                            )
                        }
                        
                        HistoryCard(
                            record = record,
                            workflowId = workflowId,
                            repository = repository,
                            dateFormat = dateFormat,
                            onFavClick = {
                                repository.updateHistoryFav(workflowId, record.id, !record.isFav)
                                refresh()
                            },
                            onDeleteClick = { showDeleteConfirm = true },
                            onClick = { onHistoryClick(record.id) },
                            onDataChanged = { refresh() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    record: HistoryRecord,
    workflowId: String,
    repository: WorkflowRepository,
    dateFormat: SimpleDateFormat,
    onFavClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit,
    onDataChanged: () -> Unit
) {
    var showPreview by remember { mutableStateOf(false) }
    var fetchedImages by remember { mutableStateOf<List<String>?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val effectiveImages = fetchedImages ?: record.resultImageUrls
    
    if (showPreview && effectiveImages.isNotEmpty()) {
        Dialog(onDismissRequest = { showPreview = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val pagerState = rememberPagerState(pageCount = { effectiveImages.size.coerceAtLeast(1) })
                    // Top bar: date | page count | save | close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            dateFormat.format(Date(record.createdAt)),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1
                        )
                        if (effectiveImages.size > 1) {
                            Text(
                                "  ${pagerState.currentPage + 1}/${effectiveImages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            scope.launch {
                                val imgToSave = if (effectiveImages.size == 1) effectiveImages.first()
                                    else effectiveImages.first()
                                val result = ImageSaver.saveToGallery(ctx, imgToSave)
                                Toast.makeText(ctx,
                                    if (result.isSuccess) "Saved to gallery" else "Save failed",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Save",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showPreview = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close",
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (effectiveImages.size == 1) {
                        AsyncImage(
                            model = effectiveImages.first(),
                            contentDescription = "Result",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth()
                            ) { page ->
                                AsyncImage(
                                    model = effectiveImages[page],
                                    contentDescription = "Result $page",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }

                        }
                    }

                }
            }
        }
    }
    
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail - always show, clickable (fetches from server if blank)
            val hasImages = effectiveImages.isNotEmpty()
            val canFetch = !hasImages && record.serverPromptId != null
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (hasImages) {
                            showPreview = true
                        } else if (canFetch && !isFetching) {
                            scope.launch {
                                isFetching = true
                                try {
                                    repository.getHistory(record.serverPromptId!!).onSuccess { entry ->
                                        val urls = mutableListOf<String>()
                                        for ((_, nodeOutput) in entry.outputs) {
                                            nodeOutput.images?.forEach { img ->
                                                urls.add(repository.getImageUrl(
                                                    filename = img.filename, subfolder = img.subfolder, type = img.type))
                                            }
                                            nodeOutput.gifs?.forEach { gif ->
                                                urls.add(repository.getImageUrl(
                                                    filename = gif.filename, subfolder = gif.subfolder, type = gif.type))
                                            }
                                        }
                                        if (urls.isNotEmpty()) {
                                            fetchedImages = urls
                                            repository.updateHistoryImages(workflowId, record.id, urls)
                                            onDataChanged()
                                            showPreview = true
                                        }
                                    }
                                } catch (_: Exception) {}
                                isFetching = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isFetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else if (hasImages) {
                    AsyncImage(
                        model = effectiveImages.first(),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "No image",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Info: time + promptId
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(Date(record.createdAt)),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                val displayId = record.serverPromptId ?: "????serverPromptId"
                Text(
                    text = displayId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Fav button
            IconButton(onClick = onFavClick) {
                Icon(
                    if (record.isFav) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = if (record.isFav) "Unfavorite" else "Favorite",
                    tint = if (record.isFav) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
