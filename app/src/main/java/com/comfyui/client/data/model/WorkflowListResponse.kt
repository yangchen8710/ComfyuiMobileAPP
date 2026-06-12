package com.comfyui.client.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowListResponse(
    val success: Boolean = false,
    val workflows: List<WorkflowItem> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class WorkflowItem(
    val name: String,
    val path: String,
    val category: String,
    val size: Long = 0,
    val modified: Double = 0.0
)

@Serializable
data class WorkflowFileResponse(
    val success: Boolean = false,
    val name: String = "",
    val content: String = "",
    val error: String? = null
)
