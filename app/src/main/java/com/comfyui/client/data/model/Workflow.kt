package com.comfyui.client.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a complete ComfyUI workflow imported from JSON.
 */
@Serializable
data class Workflow(
    val id: String,
    val name: String,
    val nodes: List<WorkflowNode>,
    val rawJson: String,
    val importedAt: Long = System.currentTimeMillis()
)

/**
 * A single node within a ComfyUI workflow.
 */
@Serializable
data class WorkflowNode(
    val nodeId: String,
    val nodeType: String,
    val title: String = "",
    val inputs: List<NodeInput> = emptyList(),
    val outputs: List<NodeOutput> = emptyList(),
    val rawData: JsonObject? = null
)

/**
 * An input field on a workflow node.
 */
@Serializable
data class NodeInput(
    val name: String,
    val type: InputType,
    val defaultValue: String = "",
    val required: Boolean = false,
    val widgetConfig: Map<String, String> = emptyMap()
)

/**
 * An output field on a workflow node.
 */
@Serializable
data class NodeOutput(
    val name: String,
    val type: String
)

/**
 * Types of inputs that can be configured by the user.
 */
@Serializable
enum class InputType {
    TEXT,
    MULTILINE_TEXT,
    IMAGE,
    INTEGER,
    FLOAT,
    BOOLEAN,
    COMBO,
    UNKNOWN
}

/**
 * Configuration state for a user-facing input on a workflow.
 */
@Serializable
data class InputConfig(
    val nodeId: String,
    val inputName: String,
    val isEnabled: Boolean = false,
    val value: String = ""
)
