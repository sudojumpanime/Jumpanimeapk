package com.example.kitty.cast

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.example.kitty.servers.PlayableSource

/**
 * Thin bridge between local ExoPlayer and Media3 [CastPlayer].
 * Returns null when Play Services / Cast is unavailable on the device.
 *
 * Note: the default Cast receiver cannot send custom Referer headers some
 * stream hosts require — those sources may fail on TV while working on-phone.
 */
@OptIn(UnstableApi::class)
class CastSessionHelper private constructor(
    val castContext: CastContext,
    val castPlayer: CastPlayer,
) {
    var casting: Boolean = false
        private set

    fun setAvailabilityListener(listener: SessionAvailabilityListener?) {
        castPlayer.setSessionAvailabilityListener(listener)
    }

    fun isCastSessionAvailable(): Boolean = castPlayer.isCastSessionAvailable

    fun load(source: PlayableSource, title: String, episode: Int, positionMs: Long) {
        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Episode $episode")
                    .build(),
            )
            .apply {
                if (source.url.contains(".m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        castPlayer.setMediaItem(item, positionMs.coerceAtLeast(0L))
        castPlayer.prepare()
        castPlayer.playWhenReady = true
        casting = true
    }

    fun stopCasting() {
        if (!casting) return
        runCatching { castPlayer.stop() }
        casting = false
    }

    fun release() {
        setAvailabilityListener(null)
        runCatching { castPlayer.release() }
        casting = false
    }

    companion object {
        fun create(context: Context): CastSessionHelper? = runCatching {
            val castContext = CastContext.getSharedInstance(context.applicationContext)
            val castPlayer = CastPlayer(castContext)
            CastSessionHelper(castContext, castPlayer)
        }.getOrNull()
    }
}
