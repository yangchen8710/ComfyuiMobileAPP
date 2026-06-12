package com.comfyui.client.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.comfyui.client.data.api.ComfyUIApi
import com.comfyui.client.data.model.*
import com.comfyui.client.util.WorkflowParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.BufferedReader
import java.io.InputStreamReader

class WorkflowRepository {

    private val _workflows = MutableStateFlow<List<Workflow>>(emptyList())
    val workflows: StateFlow<List<Workflow>> = _workflows.asStateFlow()

    private var api: ComfyUIApi? = null
    private var serverUrl: String = ""
    private var objectInfoCache: Map<String, JsonObject>? = null
    private var prefs: SharedPreferences? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun initPrefs(context: Context) {
        prefs = context.getSharedPreferences("comfyui_workflows", Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val jsonStr = prefs?.getString("workflows_json", null) ?: return
        try {
            val list = json.decodeFromString<List<PersistedWorkflow>>(jsonStr)
            _workflows.value = list.map { it.toWorkflow() }
        } catch (_: Exception) {}
    }

    private fun saveToPrefs() {
        val list = _workflows.value.map { PersistedWorkflow.fromWorkflow(it) }
        val jsonStr = json.encodeToString(ListSerializer(PersistedWorkflow.serializer()), list)
        prefs?.edit()?.putString("workflows_json", jsonStr)?.apply()
    }

    fun setServerUrl(url: String): Boolean {
        val apiInstance = ComfyUIApi.create(url)
        return if (apiInstance != null) {
            serverUrl = url
            api = apiInstance
            objectInfoCache = null
            true
        } else {
            false
        }
    }

    fun getServerUrl(): String = serverUrl

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api?.getSystemStats()
                ?: return@withContext Result.failure(Exception("API not configured"))
            if (response.isSuccessful) {
                val stats = response.body()
                val devices = stats?.devices?.joinToString(", ") { it.name } ?: "unknown"
                Result.success(devices)
            } else {
                Result.failure(Exception("Server returned ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getObjectInfo(): Result<Map<String, JsonObject>> = withContext(Dispatchers.IO) {
        objectInfoCache?.let { return@withContext Result.success(it) }
        try {
            val response = api?.getObjectInfo()
                ?: return@withContext Result.failure(Exception("API not configured"))
            if (response.isSuccessful) {
                val info = response.body() ?: emptyMap()
                objectInfoCache = info
                Result.success(info)
            } else {
                Result.failure(Exception("Object info fetch failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importWorkflow(jsonString: String, name: String): Workflow {
        val workflow = WorkflowParser.parse(jsonString, name)
        val current = _workflows.value.toMutableList()
        current.add(0, workflow)
        _workflows.value = current
        saveToPrefs()
        return workflow
    }

    fun updateWorkflowJson(id: String, newRawJson: String) {
        _workflows.value = _workflows.value.map {
            if (it.id == id) it.copy(rawJson = newRawJson) else it
        }
        saveToPrefs()
    }

    fun removeWorkflow(id: String) {
        _workflows.value = _workflows.value.filter { it.id != id }
        saveToPrefs()
    }

    fun getWorkflow(id: String): Workflow? {
        return _workflows.value.find { it.id == id }
    }

    suspend fun queuePrompt(prompt: Map<String, JsonElement>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clientId = "android-${System.currentTimeMillis()}"
            val request = QueuePromptRequest(clientId, prompt)
            val response = api?.queuePrompt(request)
                ?: return@withContext Result.failure(Exception("API not configured"))
            if (response.isSuccessful) {
                Result.success(response.body()?.prompt_id ?: "")
            } else {
                Result.failure(Exception("Queue failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(promptId: String): Result<HistoryEntry> = withContext(Dispatchers.IO) {
        try {
            val response = api?.getHistory(promptId)
                ?: return@withContext Result.failure(Exception("API not configured"))
            if (response.isSuccessful) {
                val entry = response.body()?.values?.firstOrNull()
                Result.success(entry ?: HistoryEntry())
            } else {
                Result.failure(Exception("History fetch failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getImageUrl(filename: String, subfolder: String = "", type: String = "output"): String {
        val base = serverUrl.trimEnd('/')
        val params = mutableListOf("filename=$filename")
        if (subfolder.isNotEmpty()) params.add("subfolder=$subfolder")
        params.add("type=$type")
        return "$base/view?${params.joinToString("&")}"
    }

    suspend fun fetchWorkflowList(): Result<List<WorkflowItem>> = withContext(Dispatchers.IO) {
        try {
            val currentApi = this@WorkflowRepository.api ?: return@withContext Result.failure(Exception("API not configured"))
            // Use standard /userdata to list user data directory
            val response = currentApi.getUserDataList(dir = "workflows")
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: ""
                val items = parseUserDataList(body, serverUrl)
                Result.success(items)
            } else {
                Result.failure(Exception("Server returned ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWorkflowFile(path: String, category: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentApi = this@WorkflowRepository.api ?: return@withContext Result.failure(Exception("API not configured"))
            // Encode path separators: workflows/BSS.json -> workflows%2FBSS.json
            val encodedPath = path.replace("/", "%2F")
            val response = currentApi.getUserDataFile(encodedPath)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: ""
                Result.success(body)
            } else {
                Result.failure(Exception("Server returned ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse /userdata?dir=workflows response into WorkflowItem list.
     * The response may be a JSON array of filenames, or a JSON object with file info.
     */
    private fun parseUserDataList(body: String, serverUrl: String): List<WorkflowItem> {
        if (body.isBlank()) return emptyList()
        val trimmed = body.trim()
        return try {
            // Try parsing as JSON array of strings (filenames)
            val list = json.decodeFromString<List<String>>(trimmed)
            list.map { name ->
                WorkflowItem(
                    name = name.removeSuffix(".json"),
                    path = "workflows/$name",
                    category = "workflows",
                    size = 0,
                    modified = 0.0
                )
            }
        } catch (_: Exception) {
            try {
                // Try parsing as JSON array of objects
                val list = json.decodeFromString<List<WorkflowItem>>(trimmed)
                list
            } catch (_: Exception) {
                // Fallback: try parsing as single JSON object with files array
                try {
                    val obj = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(trimmed)
                    val files = obj["files"]?.let { element ->
                        json.decodeFromJsonElement(ListSerializer(WorkflowItem.serializer()), element)
                    } ?: emptyList()
                    files
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }


    // ==================== History ====================

    private fun historyPrefsKey(workflowId: String) = "history_$workflowId"

    fun getLocalHistory(workflowId: String): List<HistoryRecord> {
        val jsonStr = prefs?.getString(historyPrefsKey(workflowId), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<HistoryRecord>>(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHistory(workflowId: String, record: HistoryRecord) {
        android.util.Log.i("HistoryRepo", "saveHistory: id=${record.id}, serverPromptId=${record.serverPromptId}, prefs=${prefs != null}")
        val list = getLocalHistory(workflowId).toMutableList()
        list.add(0, record)
        val jsonStr = json.encodeToString(ListSerializer(HistoryRecord.serializer()), list)
        prefs?.edit()?.putString(historyPrefsKey(workflowId), jsonStr)?.apply()
        android.util.Log.i("HistoryRepo", "saveHistory done: list size=${list.size}")
    }

    fun updateHistoryFav(workflowId: String, historyId: String, isFav: Boolean) {
        val list = getLocalHistory(workflowId).map {
            if (it.id == historyId) it.copy(isFav = isFav) else it
        }
        val jsonStr = json.encodeToString(ListSerializer(HistoryRecord.serializer()), list)
        prefs?.edit()?.putString(historyPrefsKey(workflowId), jsonStr)?.apply()
    }

    fun deleteHistory(workflowId: String, historyId: String) {
        val list = getLocalHistory(workflowId).filter { it.id != historyId }
        val jsonStr = json.encodeToString(ListSerializer(HistoryRecord.serializer()), list)
        prefs?.edit()?.putString(historyPrefsKey(workflowId), jsonStr)?.apply()
    }

    fun deleteAllNonFavHistory(workflowId: String) {
        val list = getLocalHistory(workflowId).filter { it.isFav }
        val jsonStr = json.encodeToString(ListSerializer(HistoryRecord.serializer()), list)
        prefs?.edit()?.putString(historyPrefsKey(workflowId), jsonStr)?.apply()
    }

    fun updateHistoryPromptId(workflowId: String, localId: String, serverPromptId: String) {
        android.util.Log.i("HistoryRepo", "updateHistoryPromptId: localId=$localId, serverPromptId=$serverPromptId")
        val list = getLocalHistory(workflowId)
        android.util.Log.i("HistoryRepo", "updateHistoryPromptId: list size=${list.size}, found=${list.any { it.id == localId }}")
        val updated = list.map {
            if (it.id == localId) it.copy(serverPromptId = serverPromptId) else it
        }
        val jsonStr = json.encodeToString(ListSerializer(HistoryRecord.serializer()), updated)
        prefs?.edit()?.putString(historyPrefsKey(workflowId), jsonStr)?.apply()
        android.util.Log.i("HistoryRepo", "updateHistoryPromptId done: prefs=${prefs != null}")
    }

    fun updateHistoryImages(workflowId: String, historyId: String, imageUrls: List<String>) {
        android.util.Log.i("HistoryRepo", "updateHistoryImages: historyId=$historyId, images=${imageUrls.size}")
        val list = getLocalHistory(workflowId)
        val updated = list.map {
            if (it.id == historyId) it.copy(resultImageUrls = imageUrls) else it
        }
        val jsonStr = json.encodeToString(ListSerializer(HistoryRecord.serializer()), updated)
        prefs?.edit()?.putString(historyPrefsKey(workflowId), jsonStr)?.apply()
        android.util.Log.i("HistoryRepo", "updateHistoryImages done")
    }

    fun hasHistory(workflowId: String): Boolean {
        return getLocalHistory(workflowId).isNotEmpty()
    }

}

@Serializable
data class PersistedWorkflow(
    val id: String,
    val name: String,
    val rawJson: String,
    val importedAt: Long
) {
    fun toWorkflow() = WorkflowParser.parse(rawJson, name).copy(id = id, importedAt = importedAt)

    companion object {
        fun fromWorkflow(w: Workflow) = PersistedWorkflow(w.id, w.name, w.rawJson, w.importedAt)
    }
}

class DanbooruRepository {

    private var allTags: List<DanbooruTag>? = null

    suspend fun loadTags(context: Context): List<DanbooruTag> = withContext(Dispatchers.IO) {
        allTags?.let { return@withContext it }

        val tags = mutableListOf<DanbooruTag>()
        try {
            val inputStream = context.assets.open("danbooru_tags.csv")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            reader.readLine() // skip header

            reader.forEachLine { line ->
                val parts = parseCsvLine(line)
                if (parts.size >= 3) {
                    val name = parts[0].trim()
                    val category = parts[1].trim().toIntOrNull() ?: 0
                    val count = parts[2].trim().toLongOrNull() ?: 0L
                    if (name.isNotEmpty()) {
                        tags.add(DanbooruTag(name = name, category = category, postCount = count))
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        allTags = tags.sortedByDescending { it.postCount }
        allTags!!
    }

    suspend fun autocomplete(context: Context, query: String): List<DanbooruTag> = withContext(Dispatchers.IO) {
        if (query.length < 1) return@withContext emptyList()
        val tags = allTags ?: loadTags(context)
        val q = query.lowercase().trim()
        tags.filter { it.name.lowercase().startsWith(q) }.take(10)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
