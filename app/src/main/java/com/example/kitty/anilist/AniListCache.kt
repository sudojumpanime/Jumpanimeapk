package com.example.kitty.anilist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk + memory cache for AniList GraphQL responses.
 * Cuts repeat traffic so the public API is less likely to rate-limit / flag the app.
 */
class AniListCache(context: Context) {
    private val prefs = context.getSharedPreferences("anikitty_anilist_cache", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, Entry>()

    data class Entry(val body: String, val savedAtMs: Long)

    fun get(key: String, ttlMs: Long): String? {
        val now = System.currentTimeMillis()
        memory[key]?.let { hit ->
            if (now - hit.savedAtMs <= ttlMs) return hit.body
            memory.remove(key)
        }
        val body = prefs.getString("$key.body", null) ?: return null
        val at = prefs.getLong("$key.at", 0L)
        if (now - at > ttlMs) {
            prefs.edit().remove("$key.body").remove("$key.at").apply()
            return null
        }
        return body.also { memory[key] = Entry(it, at) }
    }

    fun put(key: String, body: String) {
        val now = System.currentTimeMillis()
        memory[key] = Entry(body, now)
        prefs.edit()
            .putString("$key.body", body)
            .putLong("$key.at", now)
            .apply()
        trimIfNeeded()
    }

    fun getList(key: String, ttlMs: Long, parse: (JSONArray) -> List<AnimeSummary>): List<AnimeSummary>? {
        val body = get(key, ttlMs) ?: return null
        return runCatching {
            parse(JSONObject(body).getJSONObject("data").getJSONObject("Page").getJSONArray("media"))
        }.getOrNull()
    }

    fun putList(key: String, rawResponse: String) {
        put(key, rawResponse)
    }

    /** Drop feed/search/media entries so the next fetch hits the network. */
    fun clearAll() {
        memory.clear()
        prefs.edit().clear().apply()
    }

    fun invalidate(key: String) {
        memory.remove(key)
        prefs.edit().remove("$key.body").remove("$key.at").apply()
    }

    private fun trimIfNeeded() {
        val all = prefs.all
        if (all.size <= MAX_ENTRIES * 2) return
        val timed = all.keys
            .filter { it.endsWith(".at") }
            .mapNotNull { k ->
                val at = prefs.getLong(k, 0L)
                k.removeSuffix(".at") to at
            }
            .sortedBy { it.second }
        val drop = (timed.size - MAX_ENTRIES).coerceAtLeast(0)
        if (drop == 0) return
        val ed = prefs.edit()
        timed.take(drop).forEach { (base, _) ->
            ed.remove("$base.body").remove("$base.at")
            memory.remove(base)
        }
        ed.apply()
    }

    companion object {
        const val TTL_FEED_MS = 30 * 60 * 1000L
        const val TTL_SEARCH_MS = 10 * 60 * 1000L
        const val TTL_MEDIA_MS = 6 * 60 * 60 * 1000L
        private const val MAX_ENTRIES = 80
    }
}
