package com.example.kitty.servers

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gotaku / gotakudl crypto used by the on-device [GotakuServer] resolver and
 * [GotakuObfDataSource] playback unwrap.
 *
 * Seal (gotaku.to /api/v1 sealed bodies): AES-GCM + HKDF(api-seal|v1) + zlib when sealed.
 * Embed manifest token: AES-GCM(embedKey, {nonce,stamp}) → base64url(iv‖ct).
 * Manifest body: base64url → AES-GCM(HKDF(embedKey, salt=nonce, info=manifest-resp|v1)).
 * HLS playlists (`/p/1/…`): AES-GCM(HKDF(keySeed, salt=token, info=pl|v1)).
 * Segments (`/s/1/…`): XOR first [segmentBytes] with AES-CTR(keySeed, counter=SHA-256(path)[:8]‖0⁸).
 */
object GotakuCrypto {
    /** Stable IKM observed for `?k=10` sealed API responses. */
    private val API_IKM = byteArrayOf(
        30, 99, 206.toByte(), 236.toByte(), 176.toByte(), 26, 228.toByte(), 147.toByte(),
        55, 35, 152.toByte(), 123, 6, 17, 126, 179.toByte(),
        40, 181.toByte(), 197.toByte(), 210.toByte(), 194.toByte(), 90, 232.toByte(), 35,
        247.toByte(), 20, 130.toByte(), 43, 71, 117, 123, 82,
    )

    /** Stable AES-GCM key used by gotakudl embed for manifest token + response. */
    val EMBED_KEY = byteArrayOf(
        69, 142.toByte(), 84, 3, 196.toByte(), 10, 115, 51,
        39, 6, 223.toByte(), 64, 97, 147.toByte(), 221.toByte(), 107,
        4, 10, 46, 169.toByte(), 122, 107, 181.toByte(), 155.toByte(),
        82, 178.toByte(), 148.toByte(), 166.toByte(), 16, 6, 230.toByte(), 239.toByte(),
    )

    private val API_SEAL: ByteArray by lazy {
        hkdfSha256(API_IKM, ByteArray(0), "api-seal|v1".toByteArray(StandardCharsets.UTF_8), 32)
    }

    private val secureRandom = SecureRandom()

    fun maybeUnsealApi(raw: ByteArray): ByteArray {
        if (raw.isEmpty()) return raw
        val first = raw[0].toInt().toChar()
        if (first == '{' || first == '[') return raw
        // Sealed: 1-byte prefix (k) + 12-byte IV + ciphertext(+tag) → zlib.
        require(raw.size > 13) { "Gotaku sealed body too short" }
        val iv = raw.copyOfRange(1, 13)
        val ct = raw.copyOfRange(13, raw.size)
        val plain = aesGcmDecrypt(API_SEAL, iv, ct)
        return zlibInflate(plain)
    }

    fun buildManifestToken(stamp: String, nonce: String = randomNonce()): Pair<String, String> {
        val plain = """{"nonce":"$nonce","stamp":"$stamp"}"""
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val ct = aesGcmEncrypt(EMBED_KEY, iv, plain.toByteArray(StandardCharsets.UTF_8))
        return nonce to b64Url(iv + ct)
    }

    fun decryptManifestResponse(body: ByteArray, nonce: String): String {
        val blob = b64UrlDecode(String(body, StandardCharsets.UTF_8).trim())
        val salt = b64UrlDecode(nonce)
        val key = hkdfSha256(
            EMBED_KEY,
            salt,
            "manifest-resp|v1".toByteArray(StandardCharsets.UTF_8),
            32,
        )
        require(blob.size > 12) { "Gotaku manifest blob too short" }
        val iv = blob.copyOfRange(0, 12)
        val ct = blob.copyOfRange(12, blob.size)
        return String(aesGcmDecrypt(key, iv, ct), StandardCharsets.UTF_8)
    }

    fun decryptPlaylist(raw: ByteArray, keySeed: ByteArray, playlistToken: String): ByteArray {
        val key = hkdfSha256(
            keySeed,
            playlistToken.toByteArray(StandardCharsets.UTF_8),
            "pl|v1".toByteArray(StandardCharsets.UTF_8),
            32,
        )
        require(raw.size > 12) { "Gotaku playlist too short" }
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        return aesGcmDecrypt(key, iv, ct)
    }

    fun decodeKeySeed(keySeedB64: String): ByteArray = b64UrlDecode(keySeedB64)

    /** Path key for CTR: pathname segments after the first two (`s/1/NAME.ext` → `NAME`). */
    fun segmentPathKey(urlPath: String): String {
        val parts = urlPath.split('/').filter { it.isNotEmpty() }
        val rest = parts.drop(2).joinToString("/")
        return rest.replace(Regex("""\.[^.]*$"""), "")
    }

    fun ctrCounter(pathKey: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(pathKey.toByteArray(StandardCharsets.UTF_8))
        return digest.copyOfRange(0, 8) + ByteArray(8)
    }

    fun ctrKeystream(keySeed: ByteArray, counter: ByteArray, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keySeed, "AES"), IvParameterSpec(counter))
        return cipher.update(ByteArray(length)) ?: ByteArray(0)
    }

    fun randomNonce(): String = b64Url(ByteArray(16).also { secureRandom.nextBytes(it) })

    fun b64Url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun b64UrlDecode(text: String): ByteArray {
        var s = text.trim().replace('-', '+').replace('_', '/')
        val pad = (4 - s.length % 4) % 4
        if (pad > 0) s += "=".repeat(pad)
        return Base64.decode(s, Base64.DEFAULT)
    }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32
        val actualSalt = if (salt.isEmpty()) ByteArray(hashLen) else salt
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        val out = ByteArray(length)
        var offset = 0
        var counter = 1
        var prev = ByteArray(0)
        while (offset < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(prev)
            mac.update(info)
            mac.update(counter.toByte())
            prev = mac.doFinal()
            val copy = minOf(prev.size, length - offset)
            System.arraycopy(prev, 0, out, offset, copy)
            offset += copy
            counter++
        }
        return out
    }

    private fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plain)
    }

    private fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun zlibInflate(data: ByteArray): ByteArray {
        val inflater = java.util.zip.Inflater()
        inflater.setInput(data)
        val buf = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream(data.size * 2)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
}

/** Playback-time Gotaku obfuscation params attached to a [PlayableSource]. */
data class GotakuObf(
    /** Base64url AES key seed from the embed manifest. */
    val keySeed: String,
    /** Manifest `token` — HKDF salt for playlist decrypt (`pl|v1`). */
    val playlistToken: String,
    /** Leading segment bytes to XOR (default 8192). */
    val segmentBytes: Int = 8192,
)
