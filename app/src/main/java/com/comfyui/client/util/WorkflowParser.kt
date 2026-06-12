package com.comfyui.client.util

import com.comfyui.client.data.model.*
import kotlinx.serialization.json.*

/**
 * Parses ComfyUI workflow JSON into structured [Workflow] objects.
 * Supports both legacy and new API export formats.
 */
object WorkflowParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val CONNECTION_TYPES = setOf(
        "MODEL", "CLIP", "VAE", "CONDITIONING", "LATENT", "PRE_LORA",
        "LORA_STACK", "CONTROL_NET", "MASK", "OUTPUT", "SIGNAL",
        "UPSCALE_MODEL", "STYLE_MODEL", "GLIGEN", "IMAGE"
    )

    fun parse(jsonString: String, name: String = "Untitled"): Workflow {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val nodes = mutableListOf<WorkflowNode>()

        when {
            root.containsKey("nodes") -> parseNewFormat(root, nodes)
            else -> parseLegacyFormat(root, nodes)
        }

        return Workflow(
            id = generateId(name),
            name = name,
            nodes = nodes,
            rawJson = jsonString
        )
    }

    private fun parseNewFormat(root: JsonObject, nodes: MutableList<WorkflowNode>) {
        val nodesArray = root["nodes"]?.jsonArray ?: return

        for (nodeElement in nodesArray) {
            val nodeObj = nodeElement.jsonObject
            val nodeId = nodeObj["id"]?.jsonPrimitive?.content ?: continue
            val nodeType = nodeObj["type"]?.jsonPrimitive?.content ?: "Unknown"
            val title = nodeObj["title"]?.jsonPrimitive?.content
                ?: nodeObj["properties"]?.jsonObject?.get("Node name for S&R")?.jsonPrimitive?.content
                ?: nodeType

            val inputsArray = nodeObj["inputs"]?.jsonArray
            val widgetsValues = nodeObj["widgets_values"]?.jsonArray
            val nodeInputs = parseNodeInputs(nodeType, inputsArray, widgetsValues)

            val nodeOutputs = mutableListOf<NodeOutput>()
            val outputsArray = nodeObj["outputs"]?.jsonArray
            if (outputsArray != null) {
                for (outputElement in outputsArray) {
                    val outputObj = outputElement.jsonObject
                    val outputName = outputObj["name"]?.jsonPrimitive?.content ?: "output"
                    val outputType = outputObj["type"]?.jsonPrimitive?.content ?: "*"
                    nodeOutputs.add(NodeOutput(name = outputName, type = outputType))
                }
            }

            nodes.add(WorkflowNode(nodeId = nodeId, nodeType = nodeType, title = title,
                inputs = nodeInputs, outputs = nodeOutputs, rawData = nodeObj))
        }
    }

    private fun parseNodeInputs(
        nodeType: String,
        inputsArray: JsonArray?,
        widgetsValues: JsonArray?
    ): List<NodeInput> {
        val result = mutableListOf<NodeInput>()

        if (inputsArray != null && inputsArray.isNotEmpty()) {
            var widgetIdx = 0
            for (inputElement in inputsArray) {
                val inputObj = inputElement.jsonObject
                val inputName = inputObj["name"]?.jsonPrimitive?.content ?: continue
                val inputType = inputObj["type"]?.jsonPrimitive?.content ?: ""
                val link = inputObj["link"]?.jsonPrimitive?.contentOrNull

                // Skip connection-type and linked inputs (not user-configurable)
                if (inputType in CONNECTION_TYPES || link != null) continue

                val defaultValue = if (widgetsValues != null && widgetIdx < widgetsValues.size) {
                    val wv = widgetsValues[widgetIdx]
                    widgetIdx++
                    when (wv) {
                        is JsonPrimitive -> wv.content
                        is JsonObject -> wv.toString()
                        is JsonArray -> wv.toString()
                        is JsonNull -> ""
                    }
                } else ""

                result.add(NodeInput(
                    name = inputName,
                    type = inferInputType(inputType, inputName),
                    defaultValue = defaultValue
                ))
            }
        } else if (widgetsValues != null && widgetsValues.isNotEmpty()) {
            val inputName = when {
                nodeType.lowercase().contains("text") -> "text"
                nodeType.lowercase().contains("image") || nodeType.lowercase().contains("load") -> "image"
                else -> "value"
            }
            val defaultValue = when (val wv = widgetsValues[0]) {
                is JsonPrimitive -> wv.content
                else -> wv.toString()
            }
            val inputType = when {
                nodeType.lowercase().contains("text") -> InputType.MULTILINE_TEXT
                nodeType.lowercase().contains("image") -> InputType.IMAGE
                else -> InputType.TEXT
            }
            result.add(NodeInput(name = inputName, type = inputType, defaultValue = defaultValue))
        }

        return result
    }

    private fun inferInputType(comfyType: String, inputName: String): InputType =
        when (comfyType.uppercase()) {
            "STRING" -> if (inputName.lowercase().contains("text") || inputName.lowercase().contains("prompt"))
                InputType.MULTILINE_TEXT else InputType.TEXT
            "INT" -> InputType.INTEGER
            "FLOAT" -> InputType.FLOAT
            "BOOLEAN" -> InputType.BOOLEAN
            else -> InputType.TEXT
        }

    fun findInputNodes(workflow: Workflow): List<WorkflowNode> {
        return workflow.nodes.filter { node ->
            node.title.contains("(APP)", ignoreCase = true) &&
            node.inputs.any { it.type in listOf(InputType.TEXT, InputType.MULTILINE_TEXT, InputType.IMAGE) }
        }
    }

    fun updateWorkflowInput(rawJson: String, nodeId: String, inputName: String, newValue: String): String {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val nodesArray = root["nodes"]?.jsonArray ?: return rawJson

        val updatedNodes = nodesArray.map { nodeElement ->
            val nodeObj = nodeElement.jsonObject
            val nid = nodeObj["id"]?.jsonPrimitive?.content ?: return@map nodeElement

            if (nid != nodeId) return@map nodeElement

            val widgetsValues = nodeObj["widgets_values"]?.jsonArray ?: return@map nodeElement
            val inputsArray = nodeObj["inputs"]?.jsonArray ?: return@map nodeElement

            var widgetIdx = 0
            for (inputElement in inputsArray) {
                val inputObj = inputElement.jsonObject
                val name = inputObj["name"]?.jsonPrimitive?.content ?: continue
                val type = inputObj["type"]?.jsonPrimitive?.content ?: ""
                val link = inputObj["link"]?.jsonPrimitive?.contentOrNull

                if (type in CONNECTION_TYPES || link != null) continue

                if (name == inputName && widgetIdx < widgetsValues.size) {
                    val updatedWidgets = widgetsValues.toMutableList()
                    updatedWidgets[widgetIdx] = JsonPrimitive(newValue)
                    val updatedNode = nodeObj.toMutableMap()
                    updatedNode["widgets_values"] = JsonArray(updatedWidgets)
                    return@map JsonObject(updatedNode)
                }
                widgetIdx++
            }

            if (widgetsValues.isNotEmpty() && inputsArray.isEmpty()) {
                val updatedWidgets = JsonArray(listOf(JsonPrimitive(newValue)))
                val updatedNode = nodeObj.toMutableMap()
                updatedNode["widgets_values"] = updatedWidgets
                return@map JsonObject(updatedNode)
            }

            nodeElement
        }

        val updatedRoot = root.toMutableMap()
        updatedRoot["nodes"] = JsonArray(updatedNodes)
        return JsonObject(updatedRoot).toString()
    }

    /**
     * Convert workflow JSON (new export format) to the /prompt API format.
     * Uses class definitions from /object_info to map widgets to correct input names.
     * Handles dynamic widgets (e.g., Power Lora Loader''s lora_1..lora_N).
     */
    fun toPromptFormat(rawJson: String, objectInfo: Map<String, JsonObject>? = null): JsonObject {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val nodesArray = root["nodes"]?.jsonArray ?: return JsonObject(emptyMap())
        val linksArray = root["links"]?.jsonArray

        val nodeLookup = mutableMapOf<String, JsonObject>()
        for (nodeElement in nodesArray) {
            val nodeObj = nodeElement.jsonObject
            val nid = nodeObj["id"]?.jsonPrimitive?.content ?: continue
            nodeLookup[nid] = nodeObj
        }

        val linkMap = mutableMapOf<String, Pair<String, Int>>()
        if (linksArray != null) {
            for (linkElement in linksArray) {
                val linkArr = linkElement.jsonArray
                if (linkArr.size < 6) continue
                val originId = linkArr[1].jsonPrimitive.content
                val originSlot = linkArr[2].jsonPrimitive.content.toIntOrNull() ?: 0
                val targetId = linkArr[3].jsonPrimitive.content
                val targetSlot = linkArr[4].jsonPrimitive.content.toIntOrNull() ?: 0

                val targetNode = nodeLookup[targetId] ?: continue
                val targetInputs = targetNode["inputs"]?.jsonArray
                val inputName = if (targetInputs != null && targetSlot < targetInputs.size) {
                    targetInputs[targetSlot].jsonObject["name"]?.jsonPrimitive?.content ?: "slot_$targetSlot"
                } else {
                    "slot_$targetSlot"
                }
                linkMap["$targetId:$inputName"] = Pair(originId, originSlot)
            }
        }

        val promptMap = mutableMapOf<String, JsonElement>()

        for (nodeElement in nodesArray) {
            val nodeObj = nodeElement.jsonObject
            val nodeId = nodeObj["id"]?.jsonPrimitive?.content ?: continue
            val nodeType = nodeObj["type"]?.jsonPrimitive?.content ?: "Unknown"
            val title = nodeObj["title"]?.jsonPrimitive?.content
                ?: nodeObj["properties"]?.jsonObject?.get("Node name for S&R")?.jsonPrimitive?.content
                ?: nodeType

            val inputMap = mutableMapOf<String, JsonElement>()
            val widgets = nodeObj["widgets_values"]?.jsonArray?.toMutableList() ?: mutableListOf()
            var widx = 0

            val clsDef = objectInfo?.get(nodeType)

            if (clsDef != null) {
                // Phase 1: Map class-defined inputs using /object_info
                val req = clsDef["input"]?.jsonObject?.get("required")?.jsonObject ?: JsonObject(emptyMap())
                val opt = clsDef["input"]?.jsonObject?.get("optional")?.jsonObject ?: JsonObject(emptyMap())
                val orderedInputs = req.keys + opt.keys

                for (inName in orderedInputs) {
                    val spec = req[inName] ?: opt[inName] ?: continue
                    val specArr = spec.jsonArray
                    if (specArr == null || specArr.isEmpty()) continue

                    val firstElement = specArr[0]
                    val isConn = firstElement is JsonPrimitive && firstElement.content in CONNECTION_TYPES
                    val linkInfo = linkMap["$nodeId:$inName"]

                    if (linkInfo != null) {
                        inputMap[inName] = buildJsonArray {
                            add(JsonPrimitive(linkInfo.first))
                            add(JsonPrimitive(linkInfo.second))
                        }
                    } else if (isConn) {
                        continue
                    } else if (widx < widgets.size) {
                        inputMap[inName] = widgets[widx]
                        widx++
                        // Handle hidden widgets (control_after_generate)
                        val widgetConfig = if (specArr.size > 1) specArr[1] as? JsonObject else null
                        if (widgetConfig?.containsKey("control_after_generate") == true) {
                            // Check if the control mode is "randomize" - if so, replace seed with random value
                            val controlMode = if (widx < widgets.size) {
                                (widgets[widx] as? JsonPrimitive)?.contentOrNull
                            } else null
                            if (controlMode == "randomize" && inName.lowercase().contains("seed")) {
                                val maxSeed = widgetConfig["max"]?.jsonPrimitive?.longOrNull
                                    ?: Long.MAX_VALUE
                                val newSeed = kotlin.random.Random.nextLong(0,
                                    maxSeed.coerceAtMost(Long.MAX_VALUE))
                                inputMap[inName] = JsonPrimitive(newSeed)
                            }
                            widx++ // Skip control mode value
                        }
                    }
                }

                // Phase 2: Handle remaining widgets - dynamic widgets like lora configs
                // Only process complex objects, skip primitives (leftover defaults)
                if (widx < widgets.size) {
                    var loraCounter = 0
                    for (i in widx until widgets.size) {
                        val w = widgets[i]
                        if (w is JsonObject) {
                            if (w.isEmpty()) {
                                // Skip empty objects
                                continue
                            }
                            // Check for "type" hint (e.g., PowerLoraLoaderHeaderWidget)
                            val typeHint = w["type"]?.jsonPrimitive?.contentOrNull
                            if (typeHint != null) {
                                inputMap[typeHint] = w
                            } else if (w.containsKey("on") || w.containsKey("lora")) {
                                // Lora configuration object
                                loraCounter++
                                inputMap["lora_$loraCounter"] = w
                            } else {
                                // Unknown object - assign generic name
                                inputMap["widget_$i"] = w
                            }
                        }
                        // Skip primitives (strings, numbers, booleans) - they are leftover defaults
                    }
                }
            } else {
                // No object_info: fallback to simpler heuristic
                fillInputsFromWorkflow(nodeObj, linkMap, widgets, widx, inputMap, nodeType)
            }

            val nodeData = buildJsonObject {
                put("class_type", JsonPrimitive(nodeType))
                put("inputs", JsonObject(inputMap))
                putJsonObject("_meta") {
                    put("title", JsonPrimitive(title))
                }
            }
            promptMap[nodeId] = nodeData
        }

        return JsonObject(promptMap)
    }

    private fun fillInputsFromWorkflow(
        nodeObj: JsonObject,
        linkMap: Map<String, Pair<String, Int>>,
        widgets: MutableList<JsonElement>,
        widxStart: Int,
        inputMap: MutableMap<String, JsonElement>,
        nodeType: String
    ) {
        var w = widxStart
        val nodeId = nodeObj["id"]?.jsonPrimitive?.content ?: return
        val inputsArray = nodeObj["inputs"]?.jsonArray

        if (inputsArray != null && inputsArray.isNotEmpty()) {
            for (inputElement in inputsArray) {
                val inputObj = inputElement.jsonObject
                val inName = inputObj["name"]?.jsonPrimitive?.content ?: continue
                val inType = inputObj["type"]?.jsonPrimitive?.content ?: ""
                val linkInfo = linkMap["$nodeId:$inName"]

                if (linkInfo != null) {
                    inputMap[inName] = buildJsonArray {
                        add(JsonPrimitive(linkInfo.first))
                        add(JsonPrimitive(linkInfo.second))
                    }
                } else if (inType in CONNECTION_TYPES) {
                    continue
                } else if (w < widgets.size) {
                    inputMap[inName] = widgets[w]
                    w++
                }
            }
        } else if (widgets.isNotEmpty()) {
            val inName = when {
                nodeType.lowercase().contains("text") -> "text"
                nodeType.lowercase().contains("image") -> "image"
                else -> "value"
            }
            inputMap[inName] = widgets[0]
        }
    }

    private fun parseLegacyFormat(root: JsonObject, nodes: MutableList<WorkflowNode>) {
        for ((nodeId, nodeElement) in root) {
            val nodeObj = nodeElement.jsonObject
            val nodeType = nodeObj["class_type"]?.jsonPrimitive?.content ?: continue
            val title = nodeObj["_meta"]?.jsonObject?.get("title")?.jsonPrimitive?.content ?: nodeType

            val nodeInputs = mutableListOf<NodeInput>()
            val inputsObj = nodeObj["inputs"]?.jsonObject
            if (inputsObj != null) {
                for ((inputName, inputValue) in inputsObj) {
                    val defaultValue = when (inputValue) {
                        is JsonPrimitive -> inputValue.content
                        is JsonArray -> inputValue.toString()
                        else -> ""
                    }
                    nodeInputs.add(NodeInput(
                        name = inputName,
                        type = InputType.TEXT,
                        defaultValue = defaultValue,
                        widgetConfig = mapOf("format" to "legacy")
                    ))
                }
            }

            nodes.add(WorkflowNode(
                nodeId = nodeId,
                nodeType = nodeType,
                title = title,
                inputs = nodeInputs,
                outputs = emptyList(),
                rawData = nodeObj
            ))
        }
    }

    private fun generateId(name: String): String =
        "${name.hashCode()}-${System.currentTimeMillis()}"
}
