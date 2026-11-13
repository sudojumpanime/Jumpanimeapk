package com.example.kitty.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * On-device Gotaku → gotakudl resolve.
 * Returns encrypted HLS master/variant URLs plus [GotakuObf] so Media3 can
 * decrypt playlists and XOR-deobfuscate segments via [GotakuObfDataSource].
 */
class GotakuServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "Gotaku",
        description = "On-device Gotaku resolve with gotakudl HLS unwrap. Playback stays on this phone.",
        iconUrl = SITE_ICON_URL,
        siteUrl = GOTAKU_ORIGIN,
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
        val titleId = mapTitle(anilistId, titleHint)
        val ep = findEpisode(titleId, episode)
        if (mode == "dub" && !ep.dub) error("Dub unavailable for episode $episode")
        if (mode != "dub" && !ep.sub) error("Sub unavailable for episode $episode")
        val embedType = if (mode == "dub") "dub" else "hard_sub"
        val embedUrl = fetchEmbedUrl(ep.id, embedType)
        val (stamp, manifestBase) = fetchEmbedStamp(embedUrl)
        val (nonce, token) = GotakuCrypto.buildManifestToken(stamp)
        val manifestJson = fetchManifest(manifestBase, token, nonce, embedUrl)
        val manifest = JSONObject(manifestJson)
        val keySeed = manifest.getString("keySeed")
        val playlistToken = manifest.getString("token")
        val source = manifest.getString("source")
        val segmentBytes = manifest.optJSONObject("obf")?.optInt("segmentBytes", 8192) ?: 8192
        val obf = GotakuObf(
            keySeed = keySeed,
            playlistToken = playlistToken,
            segmentBytes = segmentBytes,
        )
        val headers = streamHeaders(embedUrl)
        val keySeedBytes = GotakuCrypto.decodeKeySeed(keySeed)
        val masterRaw = httpBytes(source, embedUrl)
        val masterText = String(
            GotakuCrypto.decryptPlaylist(masterRaw, keySeedBytes, playlistToken),
            StandardCharsets.UTF_8,
        )
        val variants = parseVariants(masterText)
        val out = ArrayList<PlayableSource>()
        if (variants.isEmpty()) {
            out += PlayableSource(
                url = source,
                headers = headers,
                label = "Gotaku",
                stripBytes = 0,
                gotakuObf = obf,
            )
        } else {
            for ((_, url, height) in variants.sortedByDescending { it.height }) {
                out += PlayableSource(
                    url = url,
                    headers = headers,
                    label = if (height > 0) "${height}p" else "Gotaku",
                    stripBytes = 0,
                    gotakuObf = obf,
                )
            }
        }
        // Attach captions when present on the manifest.
        val captions = manifest.optJSONArray("captions") ?: JSONArray()
        if (captions.length() > 0 && out.isNotEmpty()) {
            val subs = buildList {
                for (i in 0 until captions.length()) {
                    val c = captions.optJSONObject(i) ?: continue
                    val u = c.optString("url", c.optString("file", "")).trim()
                    if (!u.startsWith("http")) continue
                    add(
                        SubtitleTrack(
                            url = u,
                            label = c.optString("label", c.optString("lang", "Subs")),
                            language = c.optString("lang").takeIf { it.isNotBlank() },
                            mimeType = null,
                        ),
                    )
                }
            }
            if (subs.isNotEmpty()) {
                val first = out[0]
                out[0] = first.copy(subtitles = subs)
            }
        }
        out
    }

    private fun mapTitle(anilistId: Int, titleHint: String?): String {
        cache?.getShow(CACHE_ID, anilistId)?.let { return it.siteId }
        val queries = buildTitleQueries(titleHint)
        var lastError: String? = null
        val pools = ArrayList<List<TitleHit>>()
        for (q in queries) {
            val hits = runCatching { searchTitles(q) }.getOrElse {
                lastError = it.message
                emptyList()
            }
            if (hits.isNotEmpty()) pools += hits
        }
        if (pools.isEmpty()) {
            val browse = runCatching { searchTitles("") }.getOrElse {
                lastError = it.message ?: lastError
                emptyList()
            }
            if (browse.isNotEmpty()) pools += browse
        }
        for (hits in pools) {
            val id = pickAnilistMatch(hits, anilistId) ?: continue
            cache?.putShow(CACHE_ID, anilistId, id, "$GOTAKU_ORIGIN/title/$id")
            return id
        }
        error(lastError ?: "Gotaku title not found for AniList #$anilistId")
    }

    /**
     * Same AniList id can exist more than once (full series vs a short cut).
     * Keep the entry with the most episodes so One Piece and Monster resolve to the full show.
     */
    private fun pickAnilistMatch(hits: List<TitleHit>, anilistId: Int): String? {
        val posterHits = hits.filter { posterMatchesAnilist(it.poster, anilistId) }
        val pool = (if (posterHits.isNotEmpty()) posterHits else hits).take(8)
        var bestId: String? = null
        var bestCount = -1
        for (hit in pool) {
            val info = runCatching { fetchTitleInfo(hit.id) }.getOrNull() ?: continue
            if (info.anilistId != anilistId) continue
            val count = maxOf(info.latestSub, info.listedTotal)
            if (count > bestCount) {
                bestCount = count
                bestId = hit.id
            }
        }
        return bestId
    }

    private fun fetchTitleInfo(titleId: String): TitleInfo {
        val raw = apiBytes("/api/v1/titles/$titleId?k=10")
        val json = JSONObject(String(GotakuCrypto.maybeUnsealApi(raw), StandardCharsets.UTF_8))
        val title = json.getJSONObject("data").getJSONObject("title")
        val episodes = title.optJSONObject("episodes")
        return TitleInfo(
            anilistId = title.optInt("anilist_id", -1),
            latestSub = episodes?.optInt("latest_sub", 0) ?: 0,
            listedTotal = episodes?.optInt("total", 0) ?: 0,
        )
    }

    private fun posterMatchesAnilist(poster: String, anilistId: Int): Boolean {
        if (anilistId <= 0 || poster.isBlank()) return false
        return Regex("""bx$anilistId(?!\d)""").containsMatchIn(poster)
    }

    private fun searchTitles(query: String): List<TitleHit> {
        val path = if (query.isBlank()) {
            "/api/v1/titles?k=10"
        } else {
            "/api/v1/titles?k=10&q=${enc(query)}"
        }
        val raw = apiBytes(path)
        val json = JSONObject(String(GotakuCrypto.maybeUnsealApi(raw), StandardCharsets.UTF_8))
        val arr = json.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    TitleHit(
                        id = o.getString("id"),
                        name = o.optString("name", ""),
                        poster = o.optString("poster_url", ""),
                    ),
                )
            }
        }
    }

    private fun findEpisode(titleId: String, episode: Int): EpisodeHit {
        cache?.getEpisodes(CACHE_ID, titleId)?.let { cached ->
            cached.firstOrNull { it.number == episode }?.let {
                return EpisodeHit(it.ids, it.number, it.hasSub, it.hasDub)
            }
        }
        val raw = apiBytes("/api/v1/titles/$titleId/episodes?k=10")
        val json = JSONObject(String(GotakuCrypto.maybeUnsealApi(raw), StandardCharsets.UTF_8))
        val arr = json.getJSONArray("data")
        val all = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ResolveCache.CachedEpisode(
                        number = o.optInt("number", -1),
                        ids = o.getString("id"),
                        hasSub = o.optBoolean("sub", true),
                        hasDub = o.optBoolean("dub", false),
                    ),
                )
            }
        }
        cache?.putEpisodes(CACHE_ID, titleId, all)
        val hit = all.firstOrNull { it.number == episode }
            ?: error("Episode $episode not found on Gotaku")
        return EpisodeHit(hit.ids, hit.number, hit.hasSub, hit.hasDub)
    }

    private fun fetchEmbedUrl(episodeId: String, type: String): String {
        val raw = apiBytes("/api/v1/episodes/$episodeId/embed?type=$type&k=10")
        val json = JSONObject(String(GotakuCrypto.maybeUnsealApi(raw), StandardCharsets.UTF_8))
        val url = json.getJSONObject("data").optString("url", "").trim()
        require(url.startsWith("http")) { "Gotaku embed missing url" }
        return url
    }

    private fun fetchEmbedStamp(embedUrl: String): Pair<String, String> {
        val html = httpText(embedUrl, GOTAKU_ORIGIN)
        val stamp = DATA_STAMP.matcher(html).let {
            require(it.find()) { "Gotaku embed missing data-manifest-stamp" }
            it.group(1)
        }
        val base = DATA_BASE.matcher(html).let {
            require(it.find()) { "Gotaku embed missing data-manifest-base" }
            it.group(1)
        }
        return stamp to base
    }

    private fun fetchManifest(
        manifestBase: String,
        token: String,
        nonce: String,
        embedUrl: String,
    ): String {
        val url = "${manifestBase.trimEnd('/')}/$token"
        val body = httpBytes(url, embedUrl)
        return GotakuCrypto.decryptManifestResponse(body, nonce)
    }

    private fun parseVariants(master: String): List<Variant> {
        val lines = master.lines()
        val out = ArrayList<Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val height = Regex("""RESOLUTION=\d+x(\d+)""").find(line)?.groupValues?.get(1)
                    ?.toIntOrNull() ?: 0
                val next = lines.getOrNull(i + 1)?.trim().orEmpty()
                if (next.startsWith("http")) {
                    out += Variant(bw, next, height)
                    i += 2
                    continue
                }
            }
            i++
        }
        return out
    }

    private fun apiBytes(path: String): ByteArray {
        val url = if (path.startsWith("http")) path else "$GOTAKU_ORIGIN$path"
        return httpBytes(url, GOTAKU_ORIGIN)
    }

    private fun httpBytes(url: String, referer: String): ByteArray {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", CHROME_UA)
            .header("Accept", "*/*")
            .header("Referer", referer)
            .header("Origin", originOf(referer))
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) {
                error("Gotaku HTTP ${resp.code} for ${url.take(96)}")
            }
            return body
        }
    }

    private fun httpText(url: String, referer: String): String =
        String(httpBytes(url, referer), StandardCharsets.UTF_8)

    private fun streamHeaders(embedUrl: String): Map<String, String> {
        val origin = originOf(embedUrl)
        return mapOf(
            "User-Agent" to CHROME_UA,
            "Accept" to "*/*",
            "Referer" to embedUrl,
            "Origin" to origin,
        )
    }

    private fun originOf(url: String): String {
        val m = ORIGIN.matcher(url)
        return if (m.find()) m.group(1) else GOTAKU_DL
    }

    private fun buildTitleQueries(titleHint: String?): List<String> {
        val raw = titleHint?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        // Callers pass "Romaji || English || Native". Searching that whole string
        // ranks spin-offs ahead of the series (One Piece films, other "Monster" shows).
        val parts = raw.split("||").map { it.trim() }.filter { it.length >= 2 }
        val seeds = if (parts.isEmpty()) listOf(raw) else parts
        for (full in seeds) {
            out += full
        }
        for (full in seeds) {
            val noParen = full.replace(Regex("""\s*\(.*?\)\s*"""), " ").replace(Regex("""\s+"""), " ").trim()
            if (noParen.length >= 2) out += noParen
            val head = full.substringBefore(':').substringBefore('—').substringBefore(" - ").trim()
            if (head.length >= 2) out += head
            val noSeason = full.replace(Regex("""\s+Season\s+\d+\s*$""", RegexOption.IGNORE_CASE), "").trim()
            if (noSeason.length >= 2) out += noSeason
        }
        return out.filter { it.length >= 2 }.take(8)
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())

    private data class TitleHit(val id: String, val name: String, val poster: String)
    private data class TitleInfo(val anilistId: Int, val latestSub: Int, val listedTotal: Int)
    private data class EpisodeHit(val id: String, val number: Int, val sub: Boolean, val dub: Boolean)
    private data class Variant(val bandwidth: Int, val url: String, val height: Int)

    companion object {
        const val ID = "gotaku"
        /** Bumped so older short-series matches (uncropped One Piece, wrong Monster) are not reused. */
        private const val CACHE_ID = "gotaku-v2"
        private const val GOTAKU_ORIGIN = "https://gotaku.to"
        private const val GOTAKU_DL = "https://gotakudl.top"
        private const val SITE_ICON_URL =
            "https://www.google.com/s2/favicons?domain=gotaku.to&sz=128"
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        private val DATA_STAMP: Pattern =
            Pattern.compile("""data-manifest-stamp=\"([^\"]+)\"""", Pattern.CASE_INSENSITIVE)
        private val DATA_BASE: Pattern =
            Pattern.compile("""data-manifest-base=\"([^\"]+)\"""", Pattern.CASE_INSENSITIVE)
        private val ORIGIN: Pattern =
            Pattern.compile("""^(https?://[^/]+)""", Pattern.CASE_INSENSITIVE)
    }
}
