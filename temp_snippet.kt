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
                    Column {
                        Text("Server connected", style = MaterialTheme.typography.labelLarge)
                        Text(
                            serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                false -> {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Server unreachable", style = MaterialTheme.typography.labelLarge)
                        Text(
                            serverUrl.ifBlank { "Tap to configure" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
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
