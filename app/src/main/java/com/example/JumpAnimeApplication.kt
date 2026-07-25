package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger

import okhttp3.OkHttpClient
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class JumpAnimeApplication : Application(), ImageLoaderFactory {
    lateinit var container: DependencyContainer

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } catch (e: Exception) {
            // Safe fallback if Firebase config/google-services.json is missing
            android.util.Log.w("JumpAnimeApplication", "Failed to initialize Firebase Crashlytics: ${e.message}")
        }
        container = DependencyContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val originalRequest = chain.request()
                        val originalUrl = originalRequest.url.toString()
                        
                        val requestWithUserAgent = originalRequest.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                            .build()
                        
                        var response = try {
                            chain.proceed(requestWithUserAgent)
                        } catch (e: Exception) {
                            null
                        }

                        // If direct request failed (no response, or error code), try proxy as a fallback
                        if (response == null || !response.isSuccessful) {
                            response?.close()
                            if (originalUrl.contains("myanimelist.net") && !originalUrl.contains("images.weserv.nl")) {
                                try {
                                    val encodedUrl = java.net.URLEncoder.encode(originalUrl, "UTF-8")
                                    val proxiedUrl = "https://images.weserv.nl/?url=$encodedUrl"
                                    val proxiedRequest = originalRequest.newBuilder()
                                        .url(proxiedUrl)
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                                        .build()
                                    response = chain.proceed(proxiedRequest)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }

                        response ?: chain.proceed(requestWithUserAgent)
                    }
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.15)
                    .build()
            }
            .crossfade(150)
            .allowHardware(true)
            .allowRgb565(true)
            .build()
    }
}
