package com.example.kitty.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.kitty.servers.PlayableSource
import com.example.kitty.servers.StreamResolver
import com.example.kitty.servers.StreamServer
import com.example.kitty.servers.SubtitleTrack

/**
 * Stream resolver that calls a user-hosted HTTP extension (apiVersion 1).
 */
class HttpExtensionResolver(
    private val manifest: ExtensionManifest,
) : StreamResolver {
    override val server = StreamServer(
        id = manifest.id,
        name = manifest.name,
        description = buildString {
            if (manifest.description.isNotBlank()) append(manifest.description)
            if (manifest.author.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(manifest.author)
            }
            if (isEmpty()) append("Community extension")
            append(" · v").append(manifest.version)
        },
        iconUrl = manifest.iconUrl,
        siteUrl = manifest.siteUrl ?: manifest.homepage,
    )

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> = withContext(Dispatchers.IO) {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        val mode = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        val payload = JSONObject()
            .put("anilistId", anilistId)
            .put("episode", episode)
            .put("audio", mode)
            .put("titleHint", titleHint)
            .toString()
        val req = Request.Builder()
            .url(manifest.resolveEndpoint())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "AniKitty-ExtensionClient/1")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        ExtensionHttp.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("error") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                error(msg ?: "${manifest.name} HTTP ${resp.code}")
            }
            parseSources(body).also {
                if (it.isEmpty()) error("${manifest.name} returned no playable sources")
            }
        }
    }

    private fun parseSources(body: String): List<PlayableSource> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("sources") ?: JSONArray()
        val out = ArrayList<PlayableSource>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val url = s.optString("url", "").trim()
            if (!url.startsWith("http")) continue
            val headersObj = s.optJSONObject("headers")
            val headers = buildMap {
                if (headersObj != null) {
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, headersObj.optString(k, ""))
                    }
                }
            }.filterValues { it.isNotBlank() }
            val subsArr = s.optJSONArray("subtitles")
            val subs = buildList {
                if (subsArr != null) {
                    for (j in 0 until subsArr.length()) {
                        val t = subsArr.getJSONObject(j)
                        val su = t.optString("url", "").trim()
                        if (!su.startsWith("http")) continue
                        add(
                            SubtitleTrack(
                                url = su,
                                label = t.optString("label", "Subs"),
                                language = t.optString("language").takeIf { it.isNotBlank() },
                                mimeType = t.optString("mimeType").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }
            out += PlayableSource(
                url = url,
                headers = headers,
                label = s.optString("label", "Stream").ifBlank { "Stream" },
                stripBytes = s.optInt("stripBytes", 0),
                subtitles = subs,
            )
        }
        require(out.isNotEmpty()) { "Extension returned no playable sources" }
        return out
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
