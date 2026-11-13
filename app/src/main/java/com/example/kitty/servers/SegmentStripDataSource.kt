package com.example.kitty.servers

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * MegaPlay CDN wraps some HLS segments in a tiny fake PNG header.
 * Their web player strips [stripBytes] (default 252) for matching hosts.
 * We also sniff PNG magic / image Content-Type so unwrap still works when
 * CDN hosts rotate off the known regex.
 */
@OptIn(UnstableApi::class)
class SegmentStripDataSource(
    private val upstream: DataSource,
    private val stripUrlRe: Pattern = DEFAULT_STRIP_URL_RE,
    private val stripBytes: AtomicInteger = AtomicInteger(DEFAULT_STRIP_BYTES),
) : DataSource {
    private var bytesSkipped = 0
    private var pushback: ByteArray? = null
    private var pushbackPos = 0

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        bytesSkipped = 0
        pushback = null
        pushbackPos = 0
        val opened = upstream.open(dataSpec)
        if (dataSpec.position != 0L) return opened

        val n = stripBytes.get().coerceAtLeast(0)
        if (n <= 0) return opened

        val url = dataSpec.uri.toString()
        val contentType = responseHeaders["Content-Type"]
            ?.joinToString(",")
            .orEmpty()
            .lowercase()
        // Never strip subtitle / text fetches — PNG unwrap is HLS-segment only.
        // Stripping VTT/SRT bytes makes captions selectable but blank on screen.
        if (isSubtitleOrText(url, contentType)) {
            return opened
        }
        val urlSaysStrip = shouldStrip(url)
        val typeSaysStrip = contentType.contains("image/png") ||
            contentType.contains("image/x-png")

        // Peek a few bytes to detect a fake PNG container even on unknown hosts.
        val peek = ByteArray(minOf(8, n))
        val got = readFullyInto(peek)
        val looksPng = got >= 8 &&
            peek[0] == 0x89.toByte() &&
            peek[1] == 'P'.code.toByte() &&
            peek[2] == 'N'.code.toByte() &&
            peek[3] == 'G'.code.toByte()

        if (looksPng || urlSaysStrip || typeSaysStrip) {
            // Drop peeked header bytes, then skip the rest of the PNG disguise.
            val already = got
            val remaining = (n - already).coerceAtLeast(0)
            val extra = skipFully(remaining)
            bytesSkipped = already + extra
            pushback = null
            pushbackPos = 0
        } else if (got > 0) {
            // Not a disguised PNG — feed peeked bytes back to the player.
            pushback = peek.copyOf(got)
            pushbackPos = 0
            bytesSkipped = 0
        }

        return when {
            opened == C.LENGTH_UNSET.toLong() -> C.LENGTH_UNSET.toLong()
            else -> (opened - bytesSkipped).coerceAtLeast(0L)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        val pb = pushback
        if (pb != null && pushbackPos < pb.size) {
            val n = minOf(length, pb.size - pushbackPos)
            System.arraycopy(pb, pushbackPos, buffer, offset, n)
            pushbackPos += n
            if (pushbackPos >= pb.size) {
                pushback = null
                pushbackPos = 0
            }
            return n
        }
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        bytesSkipped = 0
        pushback = null
        pushbackPos = 0
        upstream.close()
    }

    private fun shouldStrip(url: String): Boolean = stripUrlRe.matcher(url).find()

    private fun isSubtitleOrText(url: String, contentType: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains(".vtt") || lower.contains(".srt") ||
            lower.contains(".ass") || lower.contains(".ssa") ||
            lower.contains("subtitle") || lower.contains("/subs")
        ) {
            return true
        }
        return contentType.contains("text/") ||
            contentType.contains("vtt") ||
            contentType.contains("subrip") ||
            contentType.contains("ssa")
    }

    private fun readFullyInto(buf: ByteArray): Int {
        var got = 0
        while (got < buf.size) {
            val n = upstream.read(buf, got, buf.size - got)
            if (n == C.RESULT_END_OF_INPUT || n < 0) break
            got += n
        }
        return got
    }

    private fun skipFully(count: Int): Int {
        if (count <= 0) return 0
        var skipped = 0
        val buf = ByteArray(4096)
        while (skipped < count) {
            val want = minOf(buf.size, count - skipped)
            val read = upstream.read(buf, 0, want)
            if (read == C.RESULT_END_OF_INPUT || read < 0) break
            skipped += read
        }
        return skipped
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val stripUrlRe: Pattern = DEFAULT_STRIP_URL_RE,
        private val stripBytes: AtomicInteger = AtomicInteger(DEFAULT_STRIP_BYTES),
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            SegmentStripDataSource(upstreamFactory.createDataSource(), stripUrlRe, stripBytes)

        fun updateStripBytes(n: Int) {
            if (n > 0) stripBytes.set(n)
        }
    }

    companion object {
        const val DEFAULT_STRIP_BYTES = 252
        val DEFAULT_STRIP_URL_RE: Pattern = Pattern.compile(
            "ibyteimg\\.com|tiktokcdn\\.com|ipstatp\\.com|yoot\\.akirax\\.buzz|" +
                "byteimg\\.com|tiktokv\\.com|muscdn\\.com|akamaized\\.net",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
