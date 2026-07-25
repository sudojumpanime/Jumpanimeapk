package com.example.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

data class TorrentItem(
    val title: String,
    val magnet: String,
    val size: String,
    val seeders: Int
)

object NyaaService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val cache = mutableMapOf<String, List<TorrentItem>>()

    suspend fun searchTorrents(query: String): List<TorrentItem> = withContext(Dispatchers.IO) {
        cache[query]?.let { return@withContext it }
        val list = mutableListOf<TorrentItem>()
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://nyaa.si/?page=rss&q=$encodedQuery&c=1_2&f=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val xml = response.body?.string() ?: return@withContext emptyList()
                
                val itemPattern = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL)
                val matcher = itemPattern.matcher(xml)
                
                while (matcher.find()) {
                    val itemContent = matcher.group(1) ?: continue
                    
                    val title = extractTagContent(itemContent, "title")?.removePrefix("<![CDATA[")?.removeSuffix("]]>")?.trim() ?: "Unknown Episode"
                    val infoHash = extractTagContent(itemContent, "infoHash")?.removePrefix("<![CDATA[")?.removeSuffix("]]>")?.trim()
                    
                    val magnet = if (!infoHash.isNullOrEmpty()) {
                        "magnet:?xt=urn:btih:$infoHash&dn=${java.net.URLEncoder.encode(title, "UTF-8")}&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
                    } else {
                        val directMagnet = extractTagContent(itemContent, "magnet") ?: ""
                        if (directMagnet.startsWith("<![CDATA[")) {
                            directMagnet.removePrefix("<![CDATA[").removeSuffix("]]>")
                        } else {
                            directMagnet
                        }
                    }
                    
                    val size = extractTagContent(itemContent, "size") ?: "Unknown Size"
                    val seedersStr = extractTagContent(itemContent, "seeders") ?: "0"
                    val seeders = seedersStr.toIntOrNull() ?: 0
                    
                    if (magnet.isNotEmpty()) {
                        list.add(TorrentItem(title, magnet, size, seeders))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        cache[query] = list
        list
    }

    private fun extractTagContent(content: String, tag: String): String? {
        val pattern = Pattern.compile("<(?:\\w+:)?$tag>(.*?)</(?:\\w+:)?$tag>", Pattern.DOTALL)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else null
    }
}
