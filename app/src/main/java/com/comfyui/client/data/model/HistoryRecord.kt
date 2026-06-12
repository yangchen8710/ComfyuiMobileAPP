package com.comfyui.client.data.model

import kotlinx.serialization.Serializable

/**
 * A saved generation run for a workflow.
 * Only stores the user-visible node input values (not full workflow JSON).
 * @param id Local UUID for identification
 * @param serverPromptId Server-side prompt_id for history polling (null until queue succeeds)
 */
@Serializable
data class HistoryRecord(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val configs: List<ConfigSnapshot> = emptyList(),
    val resultImageUrls: List<String> = emptyList(),
    val isFav: Boolean = false,
    val serverPromptId: String? = null
)

/**
 * A snapshot of one input field's value at generation time.
 */
@Serializable
data class ConfigSnapshot(
    val nodeId: String,
    val inputName: String,
    val value: String
)
