package com.example.kitty.servers

import android.content.Context
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pulls MegaPlay AES key/IV (+ PNG segment strip size) from their public
 * player JS at runtime. Keys rotate in `newclient.min.js`.
 */
class MegaPlayCrypto(
    context: Context? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .build(),
) {
    private val prefs = context?.getSharedPreferences("anikitty_megaplay_crypto", Context.MODE_PRIVATE)
    private val cached = AtomicReference<Keys?>(null)

    data class Keys(
        val key: ByteArray,
        val iv: ByteArray,
        val stripBytes: Int,
        val fetchedAtMs: Long,
    )

    fun decryptEnc(enc: String, playerOrigin: String): JSONObject {
        val keys = keysFor(playerOrigin)
        val padded = enc + "=".repeat((4 - enc.length % 4) % 4)
        val raw = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        val key = keys.key.copyOf(32)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(keys.iv))
        val plain = String(cipher.doFinal(raw), StandardCharsets.UTF_8)
        return JSONObject(plain)
    }

    fun stripBytes(playerOrigin: String): Int = keysFor(playerOrigin).stripBytes

    fun keysFor(playerOrigin: String): Keys {
        val now = System.currentTimeMillis()
        cached.get()?.takeIf { now - it.fetchedAtMs < TTL_MS }?.let { return it }
        prefs?.let { p ->
            val key = p.getString(PREF_KEY, null)
            val iv = p.getString(PREF_IV, null)
            val at = p.getLong(PREF_AT, 0L)
            val strip = p.getInt(PREF_STRIP, FALLBACK_STRIP)
            if (!key.isNullOrBlank() && !iv.isNullOrBlank() && now - at < TTL_MS) {
                return Keys(
                    key.toByteArray(StandardCharsets.UTF_8),
                    iv.toByteArray(StandardCharsets.UTF_8),
                    strip,
                    at,
                ).also { cached.set(it) }
            }
        }
        val fetched = runCatching { fetchKeys(playerOrigin) }.getOrNull()
        if (fetched != null) {
            cached.set(fetched)
            prefs?.edit()
                ?.putString(PREF_KEY, String(fetched.key, StandardCharsets.UTF_8))
                ?.putString(PREF_IV, String(fetched.iv, StandardCharsets.UTF_8))
                ?.putInt(PREF_STRIP, fetched.stripBytes)
                ?.putLong(PREF_AT, fetched.fetchedAtMs)
                ?.apply()
            return fetched
        }
        prefs?.let { p ->
            val key = p.getString(PREF_KEY, null)
            val iv = p.getString(PREF_IV, null)
            if (!key.isNullOrBlank() && !iv.isNullOrBlank()) {
                return Keys(
                    key.toByteArray(StandardCharsets.UTF_8),
                    iv.toByteArray(StandardCharsets.UTF_8),
                    p.getInt(PREF_STRIP, FALLBACK_STRIP),
                    p.getLong(PREF_AT, 0L),
                ).also { cached.set(it) }
            }
        }
        return Keys(FALLBACK_KEY, FALLBACK_IV, FALLBACK_STRIP, 0L).also { cached.set(it) }
    }

    private fun fetchKeys(playerOrigin: String): Keys {
        val origin = playerOrigin.trimEnd('/')
        val candidates = listOf(
            "$origin/lib/newclient.min.js",
            "$origin/lib/newclient.min.js?v=4.22",
            "https://megaplay.buzz/lib/newclient.min.js",
        )
        var lastError: Exception? = null
        for (url in candidates) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Accept", "*/*")
                    .header("Referer", "$origin/")
                    .get()
                    .build()
                http.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful || body.isBlank()) error("HTTP ${res.code}")
                    val parsed = parseKeys(body) ?: error("key markers missing")
                    return Keys(
                        key = parsed.first.toByteArray(StandardCharsets.UTF_8),
                        iv = parsed.second.toByteArray(StandardCharsets.UTF_8),
                        stripBytes = parseStripBytes(body),
                        fetchedAtMs = System.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("MegaPlay key fetch failed")
    }

    companion object {
        private const val TTL_MS = 6 * 60 * 60 * 1000L
        private const val PREF_KEY = "aes_key"
        private const val PREF_IV = "aes_iv"
        private const val PREF_STRIP = "strip_bytes"
        private const val PREF_AT = "fetched_at"
        private const val FALLBACK_STRIP = 252
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // Emergency fallback only — live key is scraped from newclient.min.js.
        private val FALLBACK_KEY = "i?LMTAx0Q6,:}50U".toByteArray(StandardCharsets.UTF_8)
        private val FALLBACK_IV = "W0;27ToaUpl_P%'c".toByteArray(StandardCharsets.UTF_8)

        private val KEY_PATTERNS = listOf(
            Regex("""TRUST_AES_KEY"\s*\]\s*,\s*"([^"]{16})""""),
            Regex("""TRUST_AES_KEY"\s*,\s*"([^"]{16})""""),
            Regex("""trustAesKey[^"]*"TRUST_AES_KEY"\s*\]\s*,\s*"([^"]{16})""""),
        )
        private val IV_PATTERNS = listOf(
            Regex("""TRUST_AES_IV"\s*\]\s*,\s*"([^"]{16})""""),
            Regex("""TRUST_AES_IV"\s*,\s*"([^"]{16})""""),
            Regex("""trustAesIv[^"]*"TRUST_AES_IV"\s*\]\s*,\s*"([^"]{16})""""),
        )
        private val STRIP_PATTERN = Regex(
            """(?:stripBytes","STRIP_BYTES"\]?,(\d+)|STRIP_BYTES"\s*\]\s*,\s*(\d+))""",
        )

        fun parseKeys(js: String): Pair<String, String>? {
            val key = KEY_PATTERNS.firstNotNullOfOrNull { it.find(js)?.groupValues?.getOrNull(1) }
                ?: return null
            val iv = IV_PATTERNS.firstNotNullOfOrNull { it.find(js)?.groupValues?.getOrNull(1) }
                ?: return null
            if (key.length != 16 || iv.length != 16) return null
            return key to iv
        }

        fun parseStripBytes(js: String): Int {
            val m = STRIP_PATTERN.find(js) ?: return FALLBACK_STRIP
            return m.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() }
                ?.takeIf { it > 0 }
                ?: FALLBACK_STRIP
        }
    }
}
