package com.example.kitty.servers

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Fast rematch of AniList → provider show pages across episodes / sessions. */
class ResolveCache(context: Context) {
    private val prefs = context.getSharedPreferences("anikitty_resolve_cache", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, ShowMap>()
    private val episodeMemory = ConcurrentHashMap<String, Pair<Long, List<CachedEpisode>>>()

    data class ShowMap(val siteId: String, val watchUrl: String)
    data class CachedEpisode(
        val number: Int,
        val ids: String,
        val hasSub: Boolean,
        val hasDub: Boolean,
    )

    fun getShow(provider: String, anilistId: Int): ShowMap? {
        val key = key(provider, anilistId)
        memory[key]?.let { return it }
        val siteId = prefs.getString("$key.site", null) ?: return null
        val watchUrl = prefs.getString("$key.url", null) ?: return null
        return ShowMap(siteId, watchUrl).also { memory[key] = it }
    }

    fun putShow(provider: String, anilistId: Int, siteId: String, watchUrl: String) {
        val key = key(provider, anilistId)
        val map = ShowMap(siteId, watchUrl)
        memory[key] = map
        prefs.edit()
            .putString("$key.site", siteId)
            .putString("$key.url", watchUrl)
            .apply()
    }

    fun getEpisodes(provider: String, siteId: String): List<CachedEpisode>? {
        val hit = episodeMemory[epKey(provider, siteId)] ?: return null
        if (System.currentTimeMillis() - hit.first > EPISODE_TTL_MS) {
            episodeMemory.remove(epKey(provider, siteId))
            return null
        }
        return hit.second
    }

    fun putEpisodes(provider: String, siteId: String, episodes: List<CachedEpisode>) {
        episodeMemory[epKey(provider, siteId)] = System.currentTimeMillis() to episodes
    }

    private fun key(provider: String, anilistId: Int) = "$provider:$anilistId"
    private fun epKey(provider: String, siteId: String) = "$provider:eps:$siteId"

    private companion object {
        const val EPISODE_TTL_MS = 30 * 60 * 1000L
    }
}
