package com.comfyui.client.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ResultPreview(
    imageUrl: String,
    isVideo: Boolean = false,
    isGenerating: Boolean = false,
    progress: Float = 0f,
    error: String? = null,
    onFullscreen: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isGenerating -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Generating...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (progress > 0f) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                else -> {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Generated result",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = onSave,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Save",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = onFullscreen,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isVideo) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play video",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
