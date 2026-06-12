package com.comfyui.client.data.api

import com.comfyui.client.data.model.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ComfyUIApi {

    @GET("/system_stats")
    suspend fun getSystemStats(): Response<SystemStats>

    @GET("/object_info")
    suspend fun getObjectInfo(): Response<Map<String, JsonObject>>

    @POST("/prompt")
    suspend fun queuePrompt(@Body request: QueuePromptRequest): Response<QueuePromptResponse>

    @GET("/history/{promptId}")
    suspend fun getHistory(@Path("promptId") promptId: String): Response<Map<String, HistoryEntry>>

    @GET("/queue")
    suspend fun getQueue(): Response<Any>

    @POST("/interrupt")
    suspend fun interrupt(): Response<Any>

    @Streaming
    @GET("/view")
    suspend fun getOutputImage(
        @Query("filename") filename: String,
        @Query("subfolder") subfolder: String = "",
        @Query("type") type: String = "output"
    ): Response<okhttp3.ResponseBody>

    // Use standard ComfyUI /userdata endpoint (no plugin needed)
    // List directory: GET /userdata?dir=workflows  (returns JSON array of filenames)
    // Get file: GET /userdata/{encoded_path}  (e.g., /userdata/workflows%2FBSS.json)
    @GET("/userdata")
    suspend fun getUserDataList(
        @Query("dir") dir: String? = null
    ): Response<okhttp3.ResponseBody>

    @GET("/userdata/{path}")
    suspend fun getUserDataFile(
        @Path(value = "path", encoded = true) path: String
    ): Response<okhttp3.ResponseBody>

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun create(baseUrl: String): ComfyUIApi? {
            return try {
                val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                Retrofit.Builder()
                    .baseUrl(normalizedUrl)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(ComfyUIApi::class.java)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
