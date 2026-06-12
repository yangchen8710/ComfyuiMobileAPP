import kotlinx.serialization.json.*

val json = Json { ignoreUnknownKeys = true; isLenient = true }
val raw = java.io.File("C:\\Users\\Cheny\\Documents\\dev02\\BSS.json").readText()
val root = json.parseToJsonElement(raw).jsonObject

// Simulate toPromptFormat
val promptMap = mutableMapOf<String, JsonElement>()
val nodesArray = root["nodes"]?.jsonArray ?: return

for (nodeElement in nodesArray) {
    val nodeObj = nodeElement.jsonObject
    val nodeId = nodeObj["id"]?.jsonPrimitive?.content ?: continue
    val nodeType = nodeObj["type"]?.jsonPrimitive?.content ?: "Unknown"

    val inputMap = mutableMapOf<String, JsonElement>()
    val widgetsValues = nodeObj["widgets_values"]?.jsonArray
    val inputsArray = nodeObj["inputs"]?.jsonArray

    if (inputsArray != null) {
        var widgetIdx = 0
        for (inputElement in inputsArray) {
            val inputObj = inputElement.jsonObject
            val inputName = inputObj["name"]?.jsonPrimitive?.content ?: continue
            val value = when {
                inputObj.containsKey("default") -> inputObj["default"]!!
                widgetsValues != null && widgetIdx < widgetsValues.size -> widgetsValues[widgetIdx]
                else -> JsonNull
            }
            inputMap[inputName] = value
            widgetIdx++
        }
    } else if (widgetsValues != null && widgetsValues.isNotEmpty()) {
        val inputName = when {
            nodeType.lowercase().contains("text") -> "text"
            else -> "value"
        }
        inputMap[inputName] = widgetsValues[0]
    }

    val nodeData = buildJsonObject {
        put("class_type", JsonPrimitive(nodeType))
        put("inputs", JsonObject(inputMap))
        putJsonObject("_meta") {
            put("title", JsonPrimitive(nodeType))
        }
    }
    promptMap[nodeId] = nodeData
}

// Check a few nodes
val sample = promptMap.toList().take(3)
for ((id, node) in sample) {
    println(" -> ")
    println("  inputs: ")
}
println("Total nodes: ")
