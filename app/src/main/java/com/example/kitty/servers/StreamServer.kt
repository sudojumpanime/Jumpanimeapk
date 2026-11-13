package com.example.kitty.servers

import com.example.kitty.skip.SkipTimes

/**
 * A selectable stream source — bundled or a user-installed HTTP extension.
 *
 * Phone obtains a playable **request URL** (+ headers). HLS download /
 * bandwidth / playback stay on-device (Media3). The dedicated Go auth server
 * never carries stream bytes.
 */
data class StreamServer(
    val id: String,
    val name: String,
    val description: String,
    /** Site / brand icon (favicon or app icon URL). Required for store extensions. */
    val iconUrl: String? = null,
    /** Origin site the resolver scrapes / fronts (for attribution in the Store). */
    val siteUrl: String? = null,
)

data class SubtitleTrack(
    val url: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
)

data class PlayableSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val label: String = "Stream",
    /** MegaPlay PNG-disguise unwrap size for HLS segments (0 = none). */
    val stripBytes: Int = SegmentStripDataSource.DEFAULT_STRIP_BYTES,
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Gotaku playlist/segment unwrap; when set, [GotakuObfDataSource] handles CDN crypto. */
    val gotakuObf: GotakuObf? = null,
    /** Intro / credits from the anime site that supplied this source. Empty when unknown. */
    val skip: SkipTimes = SkipTimes(),
)

interface StreamResolver {
    val server: StreamServer

    /**
     * Resolve a direct playable URL on-device / via extension HTTP.
     * @param titleHint AniList / display title to help map the watch page.
     */
    suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String = "sub",
        titleHint: String? = null,
    ): List<PlayableSource>
}

/** Outcome of [ServerCatalog.resolveWithFailover] across installed providers. */
data class ResolveFailoverResult(
    val sources: List<PlayableSource>,
    val server: StreamServer,
    /** True when a provider other than the previously selected one won. */
    val usedFallback: Boolean,
    val attempted: List<String> = emptyList(),
)
