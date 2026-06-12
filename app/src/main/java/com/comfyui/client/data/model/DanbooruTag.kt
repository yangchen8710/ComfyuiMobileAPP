package com.comfyui.client.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

@Serializable
data class DanbooruTag(
    @SerialName("label") val name: String = "",
    @SerialName("value") val value: String = "",
    val category: Int = 0,
    @SerialName("post_count") val postCount: Long = 0
) {
    val categoryName: String
        get() = when (category) {
            0 -> "General"
            1 -> "Artist"
            3 -> "Copyright"
            4 -> "Character"
            5 -> "Meta"
            else -> "Other"
        }
}

@Serializable
data class ServerConfig(
    val url: String = "http://your-comfyui-server:8188",
    val clientId: String = ""
)

@Serializable
data class QueuePromptRequest(
    val client_id: String,
    val prompt: Map<String, kotlinx.serialization.json.JsonElement>
)

@Serializable
data class QueuePromptResponse(
    val prompt_id: String,
    val number: Int? = null,
    val node_errors: JsonObject? = null
)

@Serializable
data class HistoryEntry(
    val prompt: JsonElement? = null,
    val outputs: Map<String, OutputsByNode> = emptyMap(),
    val status: QueueStatus? = null
)

@Serializable
data class OutputsByNode(
    val images: List<ImageOutput>? = null,
    val gifs: List<ImageOutput>? = null,
    val text: List<String>? = null
)

@Serializable
data class ImageOutput(
    val filename: String,
    val subfolder: String = "",
    val type: String = "output"
)

@Serializable
data class QueueStatus(
    val status_str: String = "",
    val completed: Boolean = false,
    val messages: kotlinx.serialization.json.JsonArray? = null
)

@Serializable
data class SystemStats(
    val system: SystemInfo? = null,
    val devices: List<DeviceInfo>? = null
)

@Serializable
data class SystemInfo(
    val os: String = "",
    val python_version: String = "",
    val embedded_python: Boolean = false
)

@Serializable
data class DeviceInfo(
    val name: String = "",
    val type: String = "",
    val index: Int = 0,
    val vram_total: Long = 0,
    val vram_free: Long = 0
)
