package com.example.kitty.servers

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicReference

/**
 * Unwraps Gotaku CDN traffic for Media3:
 * - `/p/1/…` playlists: AES-GCM body → clear HLS text
 * - `/s/1/…` segments: XOR first [segmentBytes] with AES-CTR keystream
 *
 * Pass-through for everything else (and when [obf] is null).
 */
@OptIn(UnstableApi::class)
class GotakuObfDataSource(
    private val upstream: DataSource,
    private val obf: AtomicReference<GotakuObf?>,
) : DataSource {
    private var mode = Mode.PASSTHROUGH
    private var playlistBuf: ByteArray? = null
    private var playlistPos = 0
    private var keystream: ByteArray? = null
    private var absPos = 0L
    private var segmentBytes = 0
    private var openedUri: Uri? = null

    private enum class Mode { PASSTHROUGH, PLAYLIST, SEGMENT }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        playlistBuf = null
        playlistPos = 0
        keystream = null
        absPos = dataSpec.position
        mode = Mode.PASSTHROUGH
        segmentBytes = 0
        openedUri = dataSpec.uri

        val params = obf.get()
        val path = dataSpec.uri.path.orEmpty()

        if (params == null) {
            return upstream.open(dataSpec)
        }

        val opened = upstream.open(dataSpec)

        if (isPlaylistPath(path) && dataSpec.position == 0L) {
            val raw = readFully(opened)
            runCatching { upstream.close() }
            val keySeed = GotakuCrypto.decodeKeySeed(params.keySeed)
            val plain = GotakuCrypto.decryptPlaylist(raw, keySeed, params.playlistToken)
            playlistBuf = plain
            playlistPos = 0
            mode = Mode.PLAYLIST
            return plain.size.toLong()
        }

        if (isSegmentPath(path) && dataSpec.position < params.segmentBytes) {
            val keySeed = GotakuCrypto.decodeKeySeed(params.keySeed)
            val pathKey = GotakuCrypto.segmentPathKey(path)
            val counter = GotakuCrypto.ctrCounter(pathKey)
            segmentBytes = params.segmentBytes.coerceAtLeast(0)
            keystream = GotakuCrypto.ctrKeystream(keySeed, counter, segmentBytes)
            mode = Mode.SEGMENT
            absPos = dataSpec.position
            return opened
        }

        mode = Mode.PASSTHROUGH
        return opened
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        when (mode) {
            Mode.PLAYLIST -> {
                val buf = playlistBuf ?: return C.RESULT_END_OF_INPUT
                if (playlistPos >= buf.size) return C.RESULT_END_OF_INPUT
                val n = minOf(length, buf.size - playlistPos)
                System.arraycopy(buf, playlistPos, buffer, offset, n)
                playlistPos += n
                return n
            }
            Mode.SEGMENT -> {
                val n = upstream.read(buffer, offset, length)
                if (n <= 0) return n
                val ks = keystream
                if (ks != null && absPos < segmentBytes) {
                    var i = 0
                    while (i < n && absPos + i < segmentBytes) {
                        val ki = (absPos + i).toInt()
                        buffer[offset + i] = (buffer[offset + i].toInt() xor ks[ki].toInt()).toByte()
                        i++
                    }
                }
                absPos += n
                return n
            }
            Mode.PASSTHROUGH -> return upstream.read(buffer, offset, length)
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: openedUri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (mode == Mode.PLAYLIST) emptyMap() else upstream.responseHeaders

    override fun close() {
        playlistBuf = null
        playlistPos = 0
        keystream = null
        mode = Mode.PASSTHROUGH
        openedUri = null
        runCatching { upstream.close() }
    }

    private fun readFully(declaredLength: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream(
            when {
                declaredLength > 0 -> declaredLength.toInt().coerceAtMost(2_000_000)
                else -> 64 * 1024
            },
        )
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = upstream.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT || n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun isPlaylistPath(path: String): Boolean =
        path.contains("/p/1/") || path.contains("/p/1")

    private fun isSegmentPath(path: String): Boolean =
        path.contains("/s/1/") || path.contains("/s/1")

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val obf: AtomicReference<GotakuObf?> = AtomicReference(null),
    ) : DataSource.Factory {
        fun updateObf(value: GotakuObf?) {
            obf.set(value)
        }

        override fun createDataSource(): DataSource =
            GotakuObfDataSource(upstreamFactory.createDataSource(), obf)
    }
}
