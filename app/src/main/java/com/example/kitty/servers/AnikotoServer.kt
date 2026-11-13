package com.example.kitty.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import com.example.kitty.skip.SkipInterval
import com.example.kitty.skip.SkipTimes
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * On-device Anikoto → MegaPlay resolve.
 * Returns playable request URL + headers only; Media3 pulls bytes from the CDN.
 */
class AnikotoServer(
    private val cache: ResolveCache? = null,
    private val crypto: MegaPlayCrypto = MegaPlayCrypto(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build(),
) : StreamResolver {
    private val skipMemory = ConcurrentHashMap<String, SkipTimes>()

    override val server = StreamServer(
        id = ID,
        name = "Anikoto",
        description = "Fast on-device URL resolve. Playback bandwidth stays on this phone.",
        iconUrl = SITE_ICON_URL,
        siteUrl = ANIKOTO_BASE,
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
        val titles = buildTitleQueries(titleHint)
        val mapped = mapWatchPage(anilistId, titles)
        val episodes = fetchEpisodes(mapped.siteId, mapped.watchUrl)
        val ep = episodes.firstOrNull { it.number == episode }
            ?: error("Episode $episode not found")
        if (mode == "dub" && !ep.hasDub) error("Dub unavailable for episode $episode")
        if (mode != "dub" && !ep.hasSub) error("Sub unavailable for episode $episode")
        val referer = "${mapped.watchUrl.trimEnd('/')}/ep-$episode"
        val embeds = prioritizeEmbeds(resolveServerEmbeds(ep.ids, mode, mapped.watchUrl))
        val skip = embeds.firstOrNull { !it.skip.isEmpty }?.skip ?: SkipTimes()
        if (!skip.isEmpty) skipMemory[skipKey(anilistId, episode)] = skip
        val out = ArrayList<PlayableSource>()
        val seen = HashSet<String>()
        // Collect every labeled quality we can (1080p / 720p / …) across embeds so
        // the player quality sheet can list them all, with CDN fallbacks.
        var lastResolveError: String? = null
        for (embed in embeds.take(8)) {
            val embedUrl = embed.url
            val label = embed.label
            runCatching { resolveEmbed(embedUrl, referer) }
                .onSuccess { resolved ->
                    if (!probeMaster(resolved.file, resolved.origin)) {
                        lastResolveError = "CDN refused ${resolved.file.take(64)}"
                        return@onSuccess
                    }
                    val strip = crypto.stripBytes(resolved.origin)
                    for ((url, quality) in resolved.labeled.ifEmpty {
                        listOf(resolved.file to "Stream")
                    }) {
                        if (!url.startsWith("http") || !seen.add(url)) continue
                        out += PlayableSource(
                            url = url,
                            headers = streamHeaders(resolved.origin),
                            label = qualityLabel(label, quality),
                            stripBytes = strip,
                            subtitles = resolved.subtitles,
                            skip = skip,
                        )
                    }
                }
                .onFailure { lastResolveError = it.message }
            if (out.size >= 16) break
        }
        if (out.isEmpty()) {
            error(lastResolveError ?: "No playable $mode sources")
        }
        // Highest resolution first so 1080p (when present) is the default pick.
        out.sortedByDescending { qualityHeight(it.label) }
    }

    /**
     * Intro and credits published by Anikoto for this episode.
     * Used for every stream provider — playback can be Gotaku or anything else,
     * the skip window still comes from Anikoto's episode page.
     */
    suspend fun loadSkipTimes(
        anilistId: Int,
        episode: Int,
        titleHint: String?,
    ): SkipTimes = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episode <= 0) return@withContext SkipTimes()
        val key = skipKey(anilistId, episode)
        skipMemory[key]?.let { return@withContext it }
        val found = try {
            val mapped = mapWatchPage(anilistId, buildTitleQueries(titleHint))
            val ep = fetchEpisodes(mapped.siteId, mapped.watchUrl)
                .firstOrNull { it.number == episode }
                ?: return@withContext SkipTimes()
            fetchSkipData(ep.ids, mapped.watchUrl)
        } catch (_: Exception) {
            return@withContext SkipTimes()
        }
        skipMemory[key] = found
        found
    }

    /** Cheap GET on the master playlist with MegaPlay referer — skip dead CDNs early. */
    private fun probeMaster(masterUrl: String, origin: String): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(masterUrl)
                .header("User-Agent", CHROME_UA)
                .header("Accept", "*/*")
                .header("Referer", "$origin/")
                .header("Origin", origin)
                .get()
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return false
                val body = res.body?.string().orEmpty()
                body.contains("#EXTM3U")
            }
        }.getOrDefault(false)
    }

    private data class MappedShow(val siteId: String, val watchUrl: String)
    private data class SiteEpisode(
        val number: Int,
        val ids: String,
        val hasSub: Boolean,
        val hasDub: Boolean,
    )
    private data class ServerEmbed(val url: String, val label: String, val skip: SkipTimes)
    private data class ResolvedEmbed(
        val origin: String,
        val file: String,
        val labeled: List<Pair<String, String>>,
        val subtitles: List<SubtitleTrack>,
    )

    private fun mapWatchPage(anilistId: Int, titles: List<String>): MappedShow {
        cache?.getShow(ID, anilistId)?.let { hit ->
            // Trust the cache for rematches; fall through if the page is gone.
            val html = runCatching { fetchText(hit.watchUrl, ANIKOTO_BASE) }.getOrNull()
            if (html != null && parseWatchId(html) == hit.siteId) {
                return MappedShow(hit.siteId, hit.watchUrl)
            }
        }
        val seen = LinkedHashSet<String>()
        val ranked = ArrayList<Pair<String, Int>>() // url to score
        for (q in titles.take(8)) {
            val slug = titleSlug(q)
            if (slug.isNotEmpty()) ranked += "$ANIKOTO_BASE/watch/$slug" to 1
            val searchUrl = "$ANIKOTO_BASE/ajax/anime/search?keyword=${enc(q)}"
            runCatching {
                val data = fetchJson(searchUrl, ANIKOTO_BASE)
                val html = data.optJSONObject("result")?.optString("html")
                    ?: data.optString("result")
                parseSearchCandidates(html).forEach { (url, name) ->
                    val score = if (searchNameMatches(name, titles)) 10 else 2
                    ranked += url to score
                }
            }
        }
        for ((url, _) in ranked.sortedByDescending { it.second }.take(10)) {
            if (!seen.add(url)) continue
            val abs = absoluteUrl(url)
            val html = runCatching { fetchText(abs, ANIKOTO_BASE) }.getOrNull() ?: continue
            if (!pageMatchesAnilist(html, anilistId, titles)) continue
            val siteId = parseWatchId(html) ?: continue
            val mapped = MappedShow(siteId, abs)
            cache?.putShow(ID, anilistId, mapped.siteId, mapped.watchUrl)
            return mapped
        }
        error("No watch page for AniList $anilistId")
    }

    private fun fetchEpisodes(siteId: String, watchUrl: String): List<SiteEpisode> {
        cache?.getEpisodes(ID, siteId)?.let { cached ->
            return cached.map {
                SiteEpisode(it.number, it.ids, it.hasSub, it.hasDub)
            }
        }
        val data = fetchJson("$ANIKOTO_BASE/ajax/episode/list/$siteId?style=multi", watchUrl)
        val html = data.optString("result")
        val eps = parseEpisodes(html)
        if (eps.isEmpty()) error("Empty episode list")
        cache?.putEpisodes(
            ID,
            siteId,
            eps.map {
                ResolveCache.CachedEpisode(it.number, it.ids, it.hasSub, it.hasDub)
            },
        )
        return eps
    }

    private fun resolveServerEmbeds(
        episodeIds: String,
        mode: String,
        watchUrl: String,
    ): List<ServerEmbed> {
        val links = serverLinks(episodeIds, mode, watchUrl)
        if (links.isEmpty()) error("Anikoto: no $mode servers")
        val out = ArrayList<ServerEmbed>()
        for ((linkId, label) in links.take(6)) {
            val result = fetchServerResult(linkId, watchUrl) ?: continue
            val url = result.optString("url").trim()
            if (!url.startsWith("http")) continue
            out += ServerEmbed(url, label, parseSkipData(result.optJSONObject("skip_data")))
        }
        if (out.isEmpty()) error("Anikoto: no embed URLs")
        return out
    }

    /** Read intro/credits from the first Anikoto server that publishes them. */
    private fun fetchSkipData(episodeIds: String, watchUrl: String): SkipTimes {
        val links = serverLinks(episodeIds, "sub", watchUrl)
            .ifEmpty { serverLinks(episodeIds, "dub", watchUrl) }
        for ((linkId, _) in links.take(4)) {
            val result = fetchServerResult(linkId, watchUrl) ?: continue
            val skip = parseSkipData(result.optJSONObject("skip_data"))
            if (!skip.isEmpty) return skip
        }
        return SkipTimes()
    }

    private fun serverLinks(episodeIds: String, mode: String, watchUrl: String): List<Pair<String, String>> {
        val listUrl = "$ANIKOTO_BASE/ajax/server/list?servers=${enc(episodeIds)}"
        val data = runCatching { fetchJson(listUrl, watchUrl) }.getOrNull() ?: return emptyList()
        if (data.optInt("status") != 200) return emptyList()
        return parseServerLinks(data.optString("result"), mode)
    }

    private fun fetchServerResult(linkId: String, watchUrl: String): JSONObject? {
        val body = runCatching {
            fetchJson("$ANIKOTO_BASE/ajax/server?get=${enc(linkId)}", watchUrl)
        }.getOrNull() ?: return null
        if (body.optInt("status") != 200) return null
        return body.optJSONObject("result")
    }

    private fun parseSkipData(raw: JSONObject?): SkipTimes {
        if (raw == null) return SkipTimes()
        return SkipTimes(
            intro = skipInterval(raw, "intro"),
            outro = skipInterval(raw, "outro"),
            recap = skipInterval(raw, "recap"),
            preview = skipInterval(raw, "preview"),
        )
    }

    private fun skipInterval(raw: JSONObject, key: String): SkipInterval? {
        val arr = raw.optJSONArray(key)
        val start: Double
        val end: Double
        if (arr != null && arr.length() >= 2) {
            start = arr.optDouble(0, Double.NaN)
            end = arr.optDouble(1, Double.NaN)
        } else {
            val obj = raw.optJSONObject(key) ?: return null
            start = obj.optDouble("start", obj.optDouble("startTime", Double.NaN))
            end = obj.optDouble("end", obj.optDouble("endTime", Double.NaN))
        }
        if (!start.isFinite() || !end.isFinite() || end <= start || start < 0.0 || end > 8 * 3600.0) {
            return null
        }
        return SkipInterval(start, end)
    }

    private fun skipKey(anilistId: Int, episode: Int) = "$anilistId:$episode"

    private fun resolveEmbed(embedUrl: String, referer: String): ResolvedEmbed {
        var pageUrl = embedUrl
        var origin = originOf(embedUrl)
        var html = fetchText(embedUrl, referer)
        var dataId = parseDataId(html)

        if (dataId == null) {
            val iframe = IFRAME_SRC.find(html)?.groupValues?.getOrNull(1)
            if (!iframe.isNullOrBlank()) {
                val inner = when {
                    iframe.startsWith("//") -> "https:$iframe"
                    iframe.startsWith("/") -> "$origin$iframe"
                    else -> iframe
                }
                runCatching {
                    html = fetchText(inner, embedUrl)
                    origin = originOf(inner)
                    pageUrl = inner
                    dataId = parseDataId(html)
                }
            }
        }
        if (dataId == null) {
            STREAM_ID.find(embedUrl)?.groupValues?.getOrNull(1)?.let {
                if (isValidDataId(it)) dataId = it
            }
        }
        val id = dataId ?: error("MegaPlay data-id missing")
        val sParam = pageUrl.toHttpUrl().queryParameter("s")
        val srcBuilder = "$origin/stream/getSourcesNew?id=${enc(id)}".toHttpUrl().newBuilder()
        if (!sParam.isNullOrBlank()) srcBuilder.addQueryParameter("s", sParam)
        val srcJson = fetchJson(srcBuilder.build().toString(), pageUrl, origin)
            .put("_anikittyOrigin", origin)
        val file = extractFile(srcJson) ?: error("MegaPlay file missing")
        val labeled = extractLabeled(srcJson).ifEmpty { listOf(file to "Stream") }
        val subs = extractSubtitles(srcJson)
        return ResolvedEmbed(origin = origin, file = file, labeled = labeled, subtitles = subs)
    }

    private fun extractSubtitles(data: JSONObject): List<SubtitleTrack> {
        fun fromTracks(tracks: JSONArray?): List<SubtitleTrack> {
            if (tracks == null) return emptyList()
            val out = ArrayList<SubtitleTrack>()
            for (i in 0 until tracks.length()) {
                val row = tracks.optJSONObject(i) ?: continue
                val kind = row.optString("kind").ifBlank { row.optString("type") }
                if (kind.isNotBlank() &&
                    !kind.contains("caption", ignoreCase = true) &&
                    !kind.contains("sub", ignoreCase = true)
                ) {
                    continue
                }
                val file = row.optString("file").ifBlank { row.optString("url") }
                if (!file.startsWith("http")) continue
                val label = row.optString("label")
                    .ifBlank { row.optString("lang") }
                    .ifBlank { row.optString("language") }
                    .ifBlank { "Subtitle ${out.size + 1}" }
                val language = row.optString("lang").ifBlank { row.optString("language") }.ifBlank { null }
                val mime = when {
                    file.contains(".vtt", ignoreCase = true) -> "text/vtt"
                    file.contains(".srt", ignoreCase = true) -> "application/x-subrip"
                    file.contains(".ass", ignoreCase = true) || file.contains(".ssa", ignoreCase = true) ->
                        "text/x-ssa"
                    else -> null
                }
                out += SubtitleTrack(url = file, label = label, language = language, mimeType = mime)
            }
            return out
        }
        fromTracks(data.optJSONArray("tracks")).takeIf { it.isNotEmpty() }?.let { return it }
        val enc = data.optString("enc")
        if (enc.isNotBlank()) {
            val origin = data.optString("_anikittyOrigin").ifBlank { "https://megaplay.buzz" }
            val decrypted = runCatching { crypto.decryptEnc(enc, origin) }.getOrNull()
            if (decrypted != null) {
                fromTracks(decrypted.optJSONArray("tracks")).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return emptyList()
    }

    private fun extractFile(data: JSONObject): String? {
        data.optJSONObject("sources")?.optString("file")
            ?.takeIf { it.startsWith("http") }
            ?.let { return it }
        data.optJSONArray("sources")?.let { arr ->
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i)?.optString("file").orEmpty()
                if (f.startsWith("http")) return f
            }
        }
        val enc = data.optString("enc")
        if (enc.isNotBlank()) {
            val origin = data.optString("_anikittyOrigin").ifBlank { "https://megaplay.buzz" }
            val decrypted = crypto.decryptEnc(enc, origin)
            decrypted.optString("file").takeIf { it.startsWith("http") }?.let { return it }
            decrypted.optJSONObject("sources")?.optString("file")
                ?.takeIf { it.startsWith("http") }
                ?.let { return it }
        }
        return null
    }

    private fun extractLabeled(data: JSONObject): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val seen = HashSet<String>()
        fun addFrom(obj: JSONObject) {
            val url = obj.optString("file").ifBlank { obj.optString("url") }
            if (!url.startsWith("http") || !seen.add(url)) return
            val quality = obj.optString("quality")
                .ifBlank { obj.optString("label") }
                .ifBlank { obj.optString("res") }
                .ifBlank { obj.optString("resolution") }
                .ifBlank { obj.optString("type") }
                .ifBlank { "Stream" }
            out += url to quality
        }
        fun absorb(sources: Any?) {
            when (sources) {
                is JSONArray -> for (i in 0 until sources.length()) {
                    sources.optJSONObject(i)?.let(::addFrom)
                }
                is JSONObject -> {
                    // Single file object OR map of quality → url/object.
                    if (sources.has("file") || sources.has("url")) {
                        addFrom(sources)
                    } else {
                        val keys = sources.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            when (val v = sources.opt(key)) {
                                is JSONObject -> addFrom(v)
                                is String -> if (v.startsWith("http") && seen.add(v)) {
                                    out += v to key
                                }
                            }
                        }
                    }
                }
            }
        }
        absorb(data.opt("sources"))
        absorb(data.opt("source"))
        if (out.isEmpty()) {
            val enc = data.optString("enc")
            if (enc.isNotBlank()) {
                val origin = data.optString("_anikittyOrigin").ifBlank { "https://megaplay.buzz" }
                val decrypted = runCatching { crypto.decryptEnc(enc, origin) }.getOrNull()
                if (decrypted != null) {
                    absorb(decrypted.opt("sources"))
                    absorb(decrypted.opt("source"))
                    if (out.isEmpty()) {
                        decrypted.optString("file").takeIf { it.startsWith("http") }?.let {
                            out += it to "Stream"
                        }
                    }
                }
            }
        }
        if (out.isEmpty()) {
            extractFile(data)?.let { out += it to "Stream" }
        }
        return out.sortedByDescending { qualityHeight(it.second) }
    }

    private fun fetchText(url: String, referer: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", CHROME_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("HTTP ${res.code} for $url")
            return body
        }
    }

    private fun fetchJson(url: String, referer: String, origin: String? = null): JSONObject {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", CHROME_UA)
            .header("Accept", "application/json,text/plain,*/*")
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
        if (!origin.isNullOrBlank()) b.header("Origin", origin)
        http.newCall(b.build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("HTTP ${res.code} for $url")
            return JSONObject(body)
        }
    }

    companion object {
        const val ID = "anikoto"

        private const val ANIKOTO_BASE = "https://anikoto.cz"
        /** Official site favicon — shown in Extension Store / installed sources. */
        private const val SITE_ICON_URL =
            "https://www.google.com/s2/favicons?domain=anikoto.cz&sz=128"
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val WATCH_ID = Regex(
            """(?i)id=["']watch-main["'][^>]*\bdata-id=["'](\d+)["']|data-id=["'](\d+)["'][^>]*\bid=["']watch-main["']""",
        )
        private val SEARCH_ITEM = Regex(
            """(?is)<a\s+class=["'][^"']*\bitem\b[^"']*["'][^>]+href=["']([^"']+/watch/[^"']+)["'][\s\S]{0,900}?<div\s+class=["'][^"']*\bname\b[^"']*["'][^>]*>(.*?)</div>""",
        )
        private val EPISODE = Regex(
            """(?is)<a\b[^>]*\bdata-num=["'](\d+)["'][^>]*\bdata-sub=["']([^"']*)["'][^>]*\bdata-dub=["']([^"']*)["'][^>]*\bdata-ids=["']([^"']+)["'][^>]*>""",
        )
        private val SERVER_LI = Regex(
            """(?is)<li\b[^>]*\bdata-link-id=["']([^"']+)["'][^>]*>(.*?)</li>""",
        )
        private val PLAYER_ID = Regex(
            """(?i)id=["']megaplay-player["'][^>]*data-id=["']([^"']+)["']|data-id=["']([^"']+)["'][^>]*id=["']megaplay-player["']""",
        )
        private val ANY_DATA_ID = Regex("""(?i)data-id=["']([^"']+)["']""")
        private val IFRAME_SRC = Regex("""(?i)<iframe[^>]+\bsrc=["']([^"']*/stream/[^"']*)["']""")
        private val STREAM_ID = Regex("""(?i)/stream/s-\d+/([^/]+)/""")
        private val TAG = Regex("""<[^>]+>""")

        private fun enc(s: String): String =
            URLEncoder.encode(s, StandardCharsets.UTF_8.name())

        private fun htmlUnescape(s: String): String =
            s.replace("&amp;", "&")
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("\\/", "/")

        private fun stripTags(s: String): String = TAG.replace(s, "").trim()

        private fun titleSlug(title: String): String {
            val sb = StringBuilder()
            var dash = false
            for (ch in title) {
                when {
                    ch.isLetterOrDigit() -> {
                        sb.append(ch.lowercaseChar())
                        dash = false
                    }
                    sb.isNotEmpty() && !dash -> {
                        sb.append('-')
                        dash = true
                    }
                }
            }
            return sb.trimEnd('-').toString()
        }

        private fun normalizeTitleKey(title: String): String {
            var s = htmlUnescape(title).lowercase()
            // Season / part variants → comparable tokens ("season 2" == "2nd season").
            s = s.replace(Regex("""\b(\d+)(?:st|nd|rd|th)\s*season\b"""), " season $1 ")
            s = s.replace(Regex("""\bseason\s*(\d+)\b"""), " season $1 ")
            s = s.replace(Regex("""\bpart\s*(\d+)\b"""), " part $1 ")
            s = s.replace(Regex("""\b2nd\b"""), " 2 ")
            s = s.replace(Regex("""\b3rd\b"""), " 3 ")
            s = s.replace(Regex("""\b(\d+)(st|nd|rd|th)\b"""), " $1 ")
            val sb = StringBuilder()
            var space = false
            for (ch in s) {
                when {
                    ch.isLetterOrDigit() -> {
                        sb.append(ch)
                        space = false
                    }
                    sb.isNotEmpty() && !space -> {
                        sb.append(' ')
                        space = true
                    }
                }
            }
            return sb.trim().toString()
        }

        private fun titlesLooselyMatch(a: String, b: String): Boolean {
            val na = normalizeTitleKey(a)
            val nb = normalizeTitleKey(b)
            if (na.isEmpty() || nb.isEmpty()) return false
            if (na == nb) return true
            if (na.contains(nb) || nb.contains(na)) return true
            val ta = na.split(' ').filter { it.length > 2 }.toSet()
            val tb = nb.split(' ').filter { it.length > 2 }.toSet()
            if (ta.isEmpty() || tb.isEmpty()) return false
            val inter = ta.intersect(tb)
            val need = minOf(3, minOf(ta.size, tb.size))
            return inter.size >= need
        }

        private fun buildTitleQueries(hint: String?): List<String> {
            val raw = hint?.trim().orEmpty()
            if (raw.isEmpty()) return emptyList()
            val out = LinkedHashSet<String>()
            // Callers may pass "English || Romaji || …"
            val parts = raw.split("||").map { it.trim() }.filter { it.isNotEmpty() }
            for (p in parts.ifEmpty { listOf(raw) }) {
                out += p
                out += p.replace(Regex("""(?i)\bseason\s+(\d+)\b""")) { m ->
                    val n = m.groupValues[1].toInt()
                    when (n) {
                        1 -> "1st Season"
                        2 -> "2nd Season"
                        3 -> "3rd Season"
                        else -> "${n}th Season"
                    }
                }
                out += p.replace(Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s+season\b"""), "Season $1")
                p.split(':', '—').map { it.trim() }.filter { it.length >= 3 }.forEach { out += it }
                val base = p.replace(
                    Regex("""(?i)[\s:]*((?:season|part)\s*\d+|\d+(?:st|nd|rd|th)\s*season)\s*$"""),
                    "",
                ).trim()
                if (base.length >= 3) out += base
            }
            return out.toList()
        }

        private fun parseWatchId(html: String): String? {
            val m = WATCH_ID.find(html) ?: return null
            return m.groupValues[1].ifBlank { m.groupValues[2] }.takeIf { it.isNotBlank() }
        }

        private fun pageAnilistId(html: String): Int? {
            val patterns = listOf(
                Regex(""""idAnilist"\s*:\s*(\d+)"""),
                Regex("""\\"idAnilist\\"\s*:\s*(\d+)"""),
                Regex("""/anime/banner/(\d+)-"""),
                Regex("""/anime/cover/(?:small|medium|large)/b(?:x)?(\d+)-"""),
            )
            for (re in patterns) {
                re.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
            }
            return null
        }

        private fun parsePageTitle(html: String): String? {
            val h1 = Regex(
                """(?is)<h1[^>]*\bclass=["'][^"']*\btitle\b[^"']*["'][^>]*>(.*?)</h1>""",
            ).find(html)
            if (h1 != null) {
                val t = htmlUnescape(stripTags(h1.groupValues[1]))
                if (t.isNotBlank()) return t
            }
            val title = Regex("""(?is)<title>\s*Watch\s+(.*?)\s+Episode\s+\d+""").find(html)
            return title?.groupValues?.getOrNull(1)?.let { htmlUnescape(it.trim()) }?.takeIf { it.isNotBlank() }
        }

        private fun pageMatchesAnilist(html: String, anilistId: Int, expectedTitles: List<String>): Boolean {
            pageAnilistId(html)?.let { return it == anilistId }
            val pageTitle = parsePageTitle(html) ?: return false
            return expectedTitles.any { titlesLooselyMatch(it, pageTitle) }
        }

        private fun searchNameMatches(candidateName: String, expectedTitles: List<String>): Boolean =
            expectedTitles.any { titlesLooselyMatch(it, candidateName) }

        private fun parseSearchCandidates(html: String): List<Pair<String, String>> {
            val out = ArrayList<Pair<String, String>>()
            val seen = HashSet<String>()
            for (m in SEARCH_ITEM.findAll(html)) {
                val url = htmlUnescape(m.groupValues[1])
                if (!seen.add(url)) continue
                val name = htmlUnescape(stripTags(m.groupValues[2]))
                out += url to name
            }
            return out
        }

        private fun parseEpisodes(html: String): List<SiteEpisode> {
            val out = ArrayList<SiteEpisode>()
            for (m in EPISODE.findAll(html)) {
                val number = m.groupValues[1].toIntOrNull() ?: continue
                if (number <= 0) continue
                out += SiteEpisode(
                    number = number,
                    hasSub = m.groupValues[2] == "1",
                    hasDub = m.groupValues[3] == "1",
                    ids = htmlUnescape(m.groupValues[4]),
                )
            }
            return out
        }

        private fun parseServerLinks(html: String, mode: String): List<Pair<String, String>> {
            val needle = """data-type="$mode""""
            var start = html.indexOf(needle)
            if (start < 0 && mode == "dub") start = html.indexOf("""data-type="sub"""")
            if (start < 0) start = 0
            val relativeEnd = html.indexOf("""<div class="type"""", start + 1)
            val end = if (relativeEnd >= 0) relativeEnd else html.length
            val block = html.substring(start, end)
            return SERVER_LI.findAll(block).mapNotNull { m ->
                val id = htmlUnescape(m.groupValues[1])
                if (id.isEmpty()) return@mapNotNull null
                val label = htmlUnescape(stripTags(m.groupValues[2]))
                id to label
            }.toList()
        }

        private fun isValidDataId(id: String): Boolean =
            id.isNotEmpty() && id.length <= 256 &&
                id.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }

        private fun parseDataId(html: String): String? {
            PLAYER_ID.find(html)?.let { m ->
                val id = m.groupValues[1].ifBlank { m.groupValues[2] }
                if (isValidDataId(id)) return id
            }
            for (m in ANY_DATA_ID.findAll(html)) {
                val id = m.groupValues[1]
                if (isValidDataId(id)) return id
            }
            return null
        }

        private fun originOf(url: String): String = try {
            val u = url.toHttpUrl()
            "${u.scheme}://${u.host}"
        } catch (_: Exception) {
            "https://megaplay.buzz"
        }

        private fun absoluteUrl(url: String): String =
            if (url.startsWith("http")) url else "$ANIKOTO_BASE$url"

        private fun streamHeaders(origin: String): Map<String, String> = mapOf(
            "Referer" to "$origin/",
            "Origin" to origin,
            "User-Agent" to CHROME_UA,
        )

        private fun qualityLabel(server: String, quality: String): String {
            val s = server.trim()
            val q = quality.trim()
            return when {
                s.isEmpty() && (q.isEmpty() || q.equals("stream", true)) -> "Anikoto"
                s.isEmpty() -> q
                q.isEmpty() || q.equals("stream", true) -> s
                else -> "$s · $q"
            }
        }

        /** Parse 1080 / 720 / "1080p" style labels for sorting highest-first. */
        private fun qualityHeight(label: String): Int {
            Regex("""(\d{3,4})\s*p""", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)
                ?.toIntOrNull()?.let { return it }
            Regex("""\b(2160|1440|1080|720|480|360|240)\b""").find(label)?.groupValues?.get(1)
                ?.toIntOrNull()?.let { return it }
            return when {
                label.contains("ultra", true) || label.contains("4k", true) -> 2160
                label.contains("hd", true) || label.contains("high", true) -> 1080
                label.contains("sd", true) || label.contains("low", true) -> 480
                else -> 0
            }
        }

        /** Prefer non-tcdn / non-shiora first, but keep tcdn (PNG-stripped) as fallback. */
        private fun prioritizeEmbeds(embeds: List<ServerEmbed>): List<ServerEmbed> {
            val (prefer, rest) = embeds.partition { embed ->
                !embed.url.contains("s=tcdn", ignoreCase = true) &&
                    !embed.url.contains("tcdn", ignoreCase = true)
            }
            return prefer + rest
        }
    }
}
