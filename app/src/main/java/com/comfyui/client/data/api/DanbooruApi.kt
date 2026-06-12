package com.comfyui.client.data.api

import com.comfyui.client.data.model.DanbooruTag
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface DanbooruApi {

    @GET("autocomplete.json")
    suspend fun autocompleteTags(
        @Query("search[query]") query: String,
        @Query("search[type]") type: String = "tag_query",
        @Query("limit") limit: Int = 20,
        @Query("version") version: Int = 1
    ): Response<List<DanbooruTag>>

    companion object {
        private const val BASE_URL = "https://danbooru.donmai.us/"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun create(): DanbooruApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("User-Agent", "ComfyUIClient/1.0")
                        .build()
                    chain.proceed(request)
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DanbooruApi::class.java)
        }
    }
}
