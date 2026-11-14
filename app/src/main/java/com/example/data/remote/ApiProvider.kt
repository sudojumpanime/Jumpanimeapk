package com.example.data.remote

import com.example.data.local.SessionManager
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class ApiProvider(
    private val sessionManager: SessionManager,
    private val anilistToken: () -> String? = { null },
) {
    private var api: JumpAnimeApiService? = null
    private var jikanApi: JikanApiService? = null
    private var currentBaseUrl: String = ""

    fun getApi(): JumpAnimeApiService {
        var baseUrl = sessionManager.getBaseUrl()
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
        
        if (api == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            api = createService(baseUrl)
        }
        return api!!
    }

    fun getJikanApi(): JikanApiService {
        if (jikanApi == null) {
            jikanApi = createJikanService()
        }
        return jikanApi!!
    }

    private var aniListApi: AniListApiService? = null

    fun getAniListApi(): AniListApiService {
        if (aniListApi == null) {
            aniListApi = createAniListService()
        }
        return aniListApi!!
    }

    private fun createAniListService(): AniListApiService {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }
        val contentType = "application/json".toMediaType()
        
        return Retrofit.Builder()
            .baseUrl("https://graphql.anilist.co/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AniListApiService::class.java)
    }

    private fun createJikanService(): JikanApiService {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }
        val contentType = "application/json".toMediaType()
        
        return Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/v4/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(JikanApiService::class.java)
    }

    private fun createService(baseUrl: String): JumpAnimeApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.example.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                
                sessionManager.getToken()?.let {
                    requestBuilder.header("Authorization", "Bearer $it")
                }
                // Comments are tied to the user's AniList identity: the server checks this token
                // with AniList once and must not store it.
                anilistToken()?.let {
                    requestBuilder.header("X-AniList-Token", it)
                }
                
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()

        val contentType = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(JumpAnimeApiService::class.java)
    }
}
