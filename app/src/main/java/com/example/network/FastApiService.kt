package com.example.network

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class StartRequest(
    val payload: Map<String, @JvmSuppressWildcards Any>
)

@JsonClass(generateAdapter = true)
data class StartResponse(
    val session_id: String,
    val activity: String,
    val status: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class UpdateRequest(
    val payload: Map<String, @JvmSuppressWildcards Any>
)

@JsonClass(generateAdapter = true)
data class UpdateResponse(
    val session_id: String,
    val status: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class StatusResponse(
    val session_id: String,
    val activity: String,
    val status: String,
    val updated_at: Double,
    val audio_url: String? = null,
    val audio_filename: String? = null,
    val audio_type: String? = null,
    val error: String? = null
)

interface FastApiService {
    @POST("session/start")
    suspend fun startSession(
        @Body request: StartRequest
    ): Response<StartResponse>

    @POST("session/{session_id}/update")
    suspend fun updateSession(
        @Path("session_id") sessionId: String,
        @Body request: UpdateRequest
    ): Response<UpdateResponse>

    @GET("session/{session_id}/status")
    suspend fun getStatus(
        @Path("session_id") sessionId: String
    ): Response<StatusResponse>

    @Streaming
    @GET("session/{session_id}/audio")
    suspend fun getAudio(
        @Path("session_id") sessionId: String
    ): Response<ResponseBody>

    @DELETE("session/{session_id}")
    suspend fun deleteSession(
        @Path("session_id") sessionId: String
    ): Response<ResponseBody>
}

object NetworkClient {
    private var currentUrl: String = "https://tissue-feast-stabilize.ngrok-free.dev" // Default emulator localhost
    private var service: FastApiService? = null

    fun getService(baseUrl: String = currentUrl): FastApiService {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (service == null || currentUrl != sanitizedUrl) {
            currentUrl = sanitizedUrl
            service = buildService(sanitizedUrl)
        }
        return service!!
    }

    private fun buildService(baseUrl: String): FastApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Generazione musicale via AI can take considerable time
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FastApiService::class.java)
    }
}

