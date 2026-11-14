package com.example.kitty.download

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.example.kitty.servers.GotakuObfDataSource
import com.example.kitty.servers.PlayableSource
import com.example.kitty.servers.SegmentStripDataSource
import com.example.kitty.servers.ServerCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class KittyDownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }

data class KittyDownload(
    val key: String,
    val animeId: String,
    val anilistId: Int,
    val animeTitle: String,
    val poster: String?,
    val episode: Int,
    val status: KittyDownloadStatus,
    val progress: Float = 0f,
    val sizeBytes: Long = 0L,
    val filePath: String? = null,
    val error: String? = null,
)

/**
 * Saves episodes to the phone as a single playable file.
 *
 * It reads the stream through the same chain the player uses (headers, MegaPlay
 * unwrap, Gotaku decrypt), so protected sources download as clean video.
 * Handles HLS (TS or fragmented MP4, plain or AES-128) and direct files.
 * Subtitles are not downloaded.
 *
 * Downloads run while the app process is alive; an interrupted one can be retried.
 */
@OptIn(UnstableApi::class)
class KittyDownloadManager(
    context: Context,
    private val catalog: ServerCatalog,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("kitty_downloads", Context.MODE_PRIVATE)
    private val dir: File = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "downloads")
        .also { it.mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<KittyDownload>> = _items.asStateFlow()

    fun keyFor(animeId: String, episode: Int) = "${animeId}_$episode"

    fun enqueue(animeId: String, anilistId: Int, title: String, poster: String?, episode: Int) {
        val key = keyFor(animeId, episode)
        val existing = _items.value.firstOrNull { it.key == key }
        if (existing != null &&
            (existing.status == KittyDownloadStatus.DONE ||
                existing.status == KittyDownloadStatus.DOWNLOADING ||
                existing.status == KittyDownloadStatus.QUEUED)
        ) return
        put(
            KittyDownload(
                key = key, animeId = animeId, anilistId = anilistId, animeTitle = title,
                poster = poster, episode = episode, status = KittyDownloadStatus.QUEUED,
            ),
        )
        start(key)
    }

    fun retry(key: String) {
        val item = _items.value.firstOrNull { it.key == key } ?: return
        if (item.status != KittyDownloadStatus.FAILED) return
        put(item.copy(status = KittyDownloadStatus.QUEUED, progress = 0f, error = null))
        start(key)
    }

    fun delete(key: String) {
        jobs.remove(key)?.cancel()
        val item = _items.value.firstOrNull { it.key == key }
        item?.filePath?.let { runCatching { File(it).delete() } }
        runCatching { File(dir, "$key.part").delete() }
        _items.update { list -> list.filterNot { it.key == key } }
        persist()
    }

    private fun start(key: String) {
        jobs[key]?.cancel()
        jobs[key] = scope.launch {
            gate.withLock { runDownload(key) }
        }
    }

    private suspend fun runDownload(key: String) {
        val item = _items.value.firstOrNull { it.key == key } ?: return
        put(item.copy(status = KittyDownloadStatus.DOWNLOADING, progress = 0f, error = null))
        try {
            val resolved = catalog.resolveWithFailover(
                anilistId = item.anilistId,
                episode = item.episode,
                audio = "sub",
                titleHint = item.animeTitle,
            )
            var lastError: Throwable? = null
            for (source in resolved.sources) {
                try {
                    val file = downloadSource(key, source)
                    update(key) {
                        it.copy(
                            status = KittyDownloadStatus.DONE,
                            progress = 1f,
                            filePath = file.absolutePath,
                            sizeBytes = file.length(),
                            error = null,
                        )
                    }
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    runCatching { File(dir, "$key.part").delete() }
                }
            }
            throw lastError ?: IllegalStateException("No playable source found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            update(key) {
                it.copy(
                    status = KittyDownloadStatus.FAILED,
                    error = e.message?.takeIf { m -> m.isNotBlank() } ?: "Download failed",
                )
            }
        }
    }

    // ---- download ---------------------------------------------------------------

    private suspend fun downloadSource(key: String, source: PlayableSource): File {
        val factory = buildFactory(source)
        val isHls = source.url.contains(".m3u8", ignoreCase = true) || source.gotakuObf != null
        return if (isHls) downloadHls(key, source, factory) else downloadDirect(key, source, factory)
    }

    private suspend fun downloadDirect(key: String, source: PlayableSource, factory: DataSource.Factory): File {
        val part = File(dir, "$key.part")
        val ds = factory.createDataSource()
        try {
            val length = ds.open(DataSpec(Uri.parse(source.url)))
            val buf = ByteArray(128 * 1024)
            var total = 0L
            FileOutputStream(part).use { out ->
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val n = ds.read(buf, 0, buf.size)
                    if (n == C.RESULT_END_OF_INPUT) break
                    out.write(buf, 0, n)
                    total += n
                    if (length > 0) {
                        val p = (total.toFloat() / length.toFloat()).coerceIn(0f, 0.99f)
                        update(key) { it.copy(progress = p, sizeBytes = total) }
                    }
                }
            }
        } finally {
            runCatching { ds.close() }
        }
        return finish(key, part, "mp4")
    }

    private suspend fun downloadHls(key: String, source: PlayableSource, factory: DataSource.Factory): File {
        var playlistUri = source.url
        var text = String(readBytes(factory, Uri.parse(playlistUri)), Charsets.UTF_8)
        if (text.contains("#EXT-X-STREAM-INF")) {
            playlistUri = bestVariant(text, playlistUri)
            text = String(readBytes(factory, Uri.parse(playlistUri)), Charsets.UTF_8)
        }

        val segments = ArrayList<HlsSegment>()
        var mapUri: String? = null
        var seq = 0L
        var currentKey: HlsKey? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    seq = line.substringAfter(':').trim().toLongOrNull() ?: 0L

                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    val method = attrs["METHOD"]?.uppercase() ?: "NONE"
                    currentKey = when (method) {
                        "NONE" -> null
                        "AES-128" -> HlsKey(
                            uri = resolveUrl(playlistUri, attrs["URI"] ?: error("Encrypted stream has no key")),
                            iv = attrs["IV"],
                        )
                        else -> error("Unsupported encryption ($method)")
                    }
                }

                line.startsWith("#EXT-X-MAP:") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    attrs["URI"]?.let { mapUri = resolveUrl(playlistUri, it) }
                }

                line.isNotEmpty() && !line.startsWith("#") -> {
                    segments.add(HlsSegment(resolveUrl(playlistUri, line), currentKey, seq))
                    seq++
                }
            }
        }
        if (segments.isEmpty()) error("Playlist has no segments")

        val part = File(dir, "$key.part")
        val keyCache = HashMap<String, ByteArray>()
        var written = 0L
        FileOutputStream(part).use { out ->
            mapUri?.let {
                val init = readBytes(factory, Uri.parse(it))
                out.write(init)
                written += init.size
            }
            for ((index, segment) in segments.withIndex()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                var bytes = readBytes(factory, Uri.parse(segment.url))
                val k = segment.key
                if (k != null) {
                    val keyBytes = keyCache.getOrPut(k.uri) { readBytes(factory, Uri.parse(k.uri)) }
                    bytes = decrypt(bytes, keyBytes, ivFor(k, segment.sequence))
                }
                out.write(bytes)
                written += bytes.size
                val p = ((index + 1).toFloat() / segments.size.toFloat()).coerceIn(0f, 0.99f)
                update(key) { it.copy(progress = p, sizeBytes = written) }
            }
        }
        return finish(key, part, if (mapUri != null) "mp4" else "ts")
    }

    private fun finish(key: String, part: File, ext: String): File {
        val target = File(dir, "$key.$ext")
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        return target
    }

    private fun buildFactory(source: PlayableSource): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(source.headers["User-Agent"] ?: DEFAULT_UA)
            .setDefaultRequestProperties(source.headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val gotaku = GotakuObfDataSource.Factory(http).also { it.updateObf(source.gotakuObf) }
        val strip = AtomicInteger(
            when {
                source.gotakuObf != null -> source.stripBytes.coerceAtLeast(0)
                source.stripBytes > 0 -> source.stripBytes
                else -> SegmentStripDataSource.DEFAULT_STRIP_BYTES
            },
        )
        return SegmentStripDataSource.Factory(gotaku, stripBytes = strip)
    }

    private fun readBytes(factory: DataSource.Factory, uri: Uri): ByteArray {
        val ds = factory.createDataSource()
        try {
            ds.open(DataSpec(uri))
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ds.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            runCatching { ds.close() }
        }
    }

    // ---- HLS helpers -----------------------------------------------------------

    private data class HlsKey(val uri: String, val iv: String?)
    private data class HlsSegment(val url: String, val key: HlsKey?, val sequence: Long)

    private fun parseAttributes(raw: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val regex = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")
        for (m in regex.findAll(raw)) {
            result[m.groupValues[1]] = m.groupValues[2].trim().removeSurrounding("\"")
        }
        return result
    }

    private fun bestVariant(master: String, masterUrl: String): String {
        var bestBandwidth = -1L
        var bestUri: String? = null
        val lines = master.lines().map { it.trim() }
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF:")) continue
            val bandwidth = parseAttributes(lines[i].substringAfter(':'))["BANDWIDTH"]?.toLongOrNull() ?: 0L
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            if (bandwidth > bestBandwidth) {
                bestBandwidth = bandwidth
                bestUri = uri
            }
        }
        return resolveUrl(masterUrl, bestUri ?: error("Playlist has no variants"))
    }

    private fun resolveUrl(base: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return try {
            java.net.URI(base).resolve(ref).toString()
        } catch (e: Exception) {
            base.substringBeforeLast('/') + "/" + ref.trimStart('/')
        }
    }

    private fun ivFor(key: HlsKey, sequence: Long): ByteArray {
        val hex = key.iv?.removePrefix("0x")?.removePrefix("0X")
        if (!hex.isNullOrEmpty()) {
            val padded = hex.padStart(32, '0')
            return ByteArray(16) { i -> padded.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }
        val iv = ByteArray(16)
        for (i in 0 until 8) iv[15 - i] = ((sequence shr (8 * i)) and 0xFF).toByte()
        return iv
    }

    private fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    // ---- state / persistence ----------------------------------------------------

    private fun put(item: KittyDownload) {
        _items.update { list -> list.filterNot { it.key == item.key } + item }
        persist()
    }

    private fun update(key: String, change: (KittyDownload) -> KittyDownload) {
        var statusChanged = false
        _items.update { list ->
            list.map {
                if (it.key == key) {
                    val next = change(it)
                    if (next.status != it.status) statusChanged = true
                    next
                } else it
            }
        }
        if (statusChanged) persist()
    }

    private fun persist() {
        val arr = JSONArray()
        for (d in _items.value) {
            arr.put(
                JSONObject()
                    .put("key", d.key).put("animeId", d.animeId).put("anilistId", d.anilistId)
                    .put("title", d.animeTitle).put("poster", d.poster ?: JSONObject.NULL)
                    .put("episode", d.episode).put("status", d.status.name)
                    .put("progress", d.progress.toDouble()).put("size", d.sizeBytes)
                    .put("file", d.filePath ?: JSONObject.NULL).put("error", d.error ?: JSONObject.NULL),
            )
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun load(): List<KittyDownload> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                var status = runCatching { KittyDownloadStatus.valueOf(o.getString("status")) }
                    .getOrDefault(KittyDownloadStatus.FAILED)
                var error = o.optString("error").takeIf { it.isNotBlank() && it != "null" }
                // A download that was running when the app died cannot be resumed.
                if (status == KittyDownloadStatus.DOWNLOADING || status == KittyDownloadStatus.QUEUED) {
                    status = KittyDownloadStatus.FAILED
                    error = "Interrupted — tap Retry"
                }
                KittyDownload(
                    key = o.getString("key"),
                    animeId = o.getString("animeId"),
                    anilistId = o.getInt("anilistId"),
                    animeTitle = o.getString("title"),
                    poster = o.optString("poster").takeIf { it.isNotBlank() && it != "null" },
                    episode = o.getInt("episode"),
                    status = status,
                    progress = o.optDouble("progress", 0.0).toFloat(),
                    sizeBytes = o.optLong("size", 0L),
                    filePath = o.optString("file").takeIf { it.isNotBlank() && it != "null" },
                    error = error,
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_LIST = "items_json"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
