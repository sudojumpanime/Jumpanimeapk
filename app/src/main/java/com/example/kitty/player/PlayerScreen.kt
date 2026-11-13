package com.example.kitty.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.media3.cast.SessionAvailabilityListener
import com.example.kitty.cast.CastSessionHelper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.example.kitty.servers.GotakuObfDataSource
import com.example.kitty.servers.PlayableSource
import com.example.kitty.servers.SegmentStripDataSource
import com.example.kitty.servers.SubtitleTrack
import com.example.kitty.skip.SkipTimes
import com.example.kitty.theme.KittyInk
import com.example.kitty.theme.PlayerChrome
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private enum class Panel { None, Quality, Subs, Style, Display, Speed }

/** Soft “next episode?” offer when this much remains. */
private const val NEAR_END_THRESHOLD_MS = 90_000L
/** Ignore tiny clips / trailers for the near-end offer. */
private const val NEAR_END_MIN_DURATION_MS = 120_000L
private const val AUTOPLAY_COUNTDOWN_SEC = 10

/** Caption backdrop: none, soft tint, solid box, or frosted/blurred glass look. */
private enum class SubBgMode { None, Soft, Solid, Blur }

/**
 * Fully user-controlled caption look.
 * [sizeSp] is the real text size in sp — no forced “large” floor.
 * [bottomPad] is Media3 bottom padding fraction — higher lifts captions up.
 */
private data class SubStyle(
    val sizeSp: Float = DEFAULT_SUB_SIZE_SP,
    val bottomPad: Float = DEFAULT_SUB_BOTTOM_PAD,
    val color: Int = AndroidColor.WHITE,
    val bgMode: SubBgMode = SubBgMode.Soft,
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
)

private data class TextOption(
    val id: String,
    val label: String,
    /** null = off/auto (no hard override) */
    val mediaGroup: TrackGroup? = null,
    val trackIndex: Int = 0,
)

/** HLS / progressive video variants the user can lock (1080p, 720p, …). */
private data class VideoOption(
    val id: String,
    val label: String,
    val height: Int = 0,
    val width: Int = 0,
    val mediaGroup: TrackGroup? = null,
    val trackIndex: Int = 0,
)

/**
 * Full-bleed anime player with custom chrome:
 * immersive fullscreen, quality + subtitle pickers, caption style, skip chips.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    episode: Int,
    sources: List<PlayableSource>,
    anilistId: Int = 0,
    titleHint: String? = null,
    loadSkip: suspend (anilistId: Int, episode: Int, titleHint: String?) -> SkipTimes =
        { _, _, _ -> SkipTimes() },
    startPositionMs: Long = 0L,
    onClose: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var sourceIndex by remember(sources) { mutableIntStateOf(0) }
    var skipTimes by remember { mutableStateOf(SkipTimes()) }
    var autoSkippedRecap by remember { mutableStateOf(false) }
    var didSeekStart by remember { mutableStateOf(false) }
    var resumeAtMs by remember { mutableLongStateOf(startPositionMs) }
    /** True until the first successful resume seek or a user quality pick. */
    var allowHistoryFallback by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.None) }
    var immersive by remember { mutableStateOf(true) }
    var resizeMode by remember {
        mutableIntStateOf(
            context.getSharedPreferences("anikitty_player", 0)
                .getInt("resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT),
        )
    }
    var subStyle by remember {
        mutableStateOf(
            loadSubStyle(context.getSharedPreferences("anikitty_player", 0)),
        )
    }
    var subsEnabled by remember { mutableStateOf(true) }
    var selectedTextId by remember { mutableStateOf("auto") }
    var textOptions by remember { mutableStateOf<List<TextOption>>(emptyList()) }
    var selectedVideoId by remember { mutableStateOf("auto") }
    var videoOptions by remember { mutableStateOf<List<VideoOption>>(emptyList()) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val playerPrefs = remember { context.getSharedPreferences("anikitty_player", 0) }
    var playbackSpeed by remember {
        mutableFloatStateOf(playerPrefs.getFloat("playback_speed", 1f).coerceIn(0.5f, 2f))
    }
    var holdBoost by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var seekFlashDir by remember { mutableIntStateOf(0) } // -1 left, +1 right, 0 none
    var isCasting by remember { mutableStateOf(false) }
    var castHint by remember { mutableStateOf<String?>(null) }
    // Next-episode UX: soft offer near the end, then 10s autoplay once ended.
    var nearEndDismissed by remember(episode) { mutableStateOf(false) }
    var playbackEnded by remember(episode) { mutableStateOf(false) }
    var autoplayCancelled by remember(episode) { mutableStateOf(false) }
    var autoplaySecLeft by remember(episode) { mutableIntStateOf(AUTOPLAY_COUNTDOWN_SEC) }
    var nextTriggered by remember(episode) { mutableStateOf(false) }

    val source = sources.getOrNull(sourceIndex)
    val stripBytes = remember { AtomicInteger(SegmentStripDataSource.DEFAULT_STRIP_BYTES) }
    val castHelper = remember { CastSessionHelper.create(context.applicationContext) }

    fun closePanelOrExit() {
        if (panel != Panel.None) {
            panel = Panel.None
        } else {
            onClose()
        }
    }

    BackHandler(onBack = { closePanelOrExit() })

    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(immersive, activity, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                // Keep edge-to-edge (MainActivity.enableEdgeToEdge); only restore bars.
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Fresh DefaultTrackSelector per ExoPlayer — release() owns the selector, so a
    // remembered shared instance would be detached after the first quality switch.
    val playerBundle = remember(source?.url, source?.subtitles, source?.gotakuObf) {
        if (source == null) return@remember null
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.headers["User-Agent"] ?: DEFAULT_UA)
            .setDefaultRequestProperties(source.headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
        val gotakuFactory = GotakuObfDataSource.Factory(httpFactory).also {
            it.updateObf(source.gotakuObf)
        }
        // Gotaku sources must not inherit MegaPlay's default PNG strip.
        stripBytes.set(
            when {
                source.gotakuObf != null -> source.stripBytes.coerceAtLeast(0)
                source.stripBytes > 0 -> source.stripBytes
                else -> SegmentStripDataSource.DEFAULT_STRIP_BYTES
            },
        )
        val factory = SegmentStripDataSource.Factory(gotakuFactory, stripBytes = stripBytes)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_500, 40_000, 1_500, 2_000)
            .build()
        val selector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setLoadControl(loadControl)
            .setTrackSelector(selector)
            .build()
            .apply {
                val itemBuilder = MediaItem.Builder().setUri(source.url)
                // Gotaku CDN playlists have no .m3u8 suffix but are HLS after unwrap.
                if (source.url.contains(".m3u8", ignoreCase = true) || source.gotakuObf != null) {
                    itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                val configs = source.subtitles.mapIndexedNotNull { index, track ->
                    subtitleConfig(track, default = index == 0)
                }
                if (configs.isNotEmpty()) {
                    itemBuilder.setSubtitleConfigurations(configs)
                }
                setMediaItem(itemBuilder.build())
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playbackParameters = PlaybackParameters(playbackSpeed)
                prepare()
            }
        PlayerBundle(exo, selector)
    }
    val player = playerBundle?.player
    val trackSelector = playerBundle?.trackSelector
    val activePlayer: Player? = if (isCasting) castHelper?.castPlayer else player
    val latestPlayer by rememberUpdatedState(player)
    val latestSources by rememberUpdatedState(sources)
    val latestSourceIndex by rememberUpdatedState(sourceIndex)
    val latestTitle by rememberUpdatedState(title)
    val latestEpisode by rememberUpdatedState(episode)
    val latestPosition by rememberUpdatedState(positionMs)
    val latestOnNextEpisode by rememberUpdatedState(onNextEpisode)

    fun goNextEpisode() {
        if (nextTriggered) return
        val next = latestOnNextEpisode ?: return
        nextTriggered = true
        next()
    }

    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    val nearEndOffer = onNextEpisode != null &&
        !playbackEnded &&
        !nearEndDismissed &&
        !nextTriggered &&
        durationMs >= NEAR_END_MIN_DURATION_MS &&
        remainingMs in 1L..NEAR_END_THRESHOLD_MS
    val showEndAutoplay = onNextEpisode != null &&
        playbackEnded &&
        !autoplayCancelled &&
        !nextTriggered

    DisposableEffect(castHelper) {
        val helper = castHelper
        if (helper == null) {
            onDispose { }
        } else {
            helper.setAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    val src = latestSources.getOrNull(latestSourceIndex) ?: return
                    val local = latestPlayer
                    val pos = local?.currentPosition?.coerceAtLeast(0L) ?: latestPosition
                    local?.pause()
                    runCatching {
                        helper.load(src, latestTitle, latestEpisode, pos)
                        isCasting = true
                        castHint = null
                        controlsVisible = true
                    }.onFailure {
                        castHint = "Cast failed — this stream may need headers the TV can't send"
                        isCasting = false
                    }
                }

                override fun onCastSessionUnavailable() {
                    val pos = runCatching {
                        helper.castPlayer.currentPosition.coerceAtLeast(0L)
                    }.getOrDefault(latestPosition)
                    helper.stopCasting()
                    isCasting = false
                    val local = latestPlayer
                    if (pos > 1_000L) {
                        local?.seekTo(pos)
                        positionMs = pos
                    }
                    local?.play()
                    controlsVisible = true
                }
            })
            onDispose {
                helper.setAvailabilityListener(null)
                helper.release()
            }
        }
    }

    // When quality/episode changes mid-cast, push the new URL to the TV.
    // First load is handled by SessionAvailabilityListener.
    LaunchedEffect(source?.url, episode) {
        if (!isCasting) return@LaunchedEffect
        val helper = castHelper ?: return@LaunchedEffect
        val src = source ?: return@LaunchedEffect
        val pos = helper.castPlayer.currentPosition.coerceAtLeast(0L)
            .takeIf { it > 0L } ?: positionMs
        runCatching { helper.load(src, title, episode, pos) }
            .onFailure { castHint = "Couldn't load this quality on Cast" }
    }

    LaunchedEffect(castHint) {
        if (castHint == null) return@LaunchedEffect
        delay(4_200)
        castHint = null
    }

    LaunchedEffect(player, playbackSpeed, holdBoost) {
        val speed = if (holdBoost) 2f else playbackSpeed
        player?.playbackParameters = PlaybackParameters(speed)
    }
    LaunchedEffect(playbackSpeed) {
        playerPrefs.edit().putFloat("playback_speed", playbackSpeed).apply()
    }
    LaunchedEffect(resizeMode) {
        playerPrefs.edit().putInt("resize_mode", resizeMode).apply()
    }

    LaunchedEffect(seekFlashDir) {
        if (seekFlashDir == 0) return@LaunchedEffect
        delay(520)
        seekFlashDir = 0
    }

    DisposableEffect(playerBundle) {
        ready = false
        error = null
        textOptions = emptyList()
        videoOptions = emptyList()
        val activePlayer = playerBundle?.player
        val activeSelector = playerBundle?.trackSelector
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                ready = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = activePlayer?.duration?.coerceAtLeast(0L) ?: 0L
                    error = null
                    if (playbackEnded) {
                        // User scrubbed/replayed after end — clear autoplay.
                        playbackEnded = false
                        autoplayCancelled = false
                    }
                    val target = resumeAtMs
                    if (!didSeekStart && target > 1_500L && durationMs > target) {
                        activePlayer?.seekTo(target.coerceAtMost(durationMs - 1_000L))
                        positionMs = target
                        resumeAtMs = 0L
                        didSeekStart = true
                        allowHistoryFallback = false
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    playbackEnded = true
                    controlsVisible = true
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onTracksChanged(tracks: Tracks) {
                val options = buildTextOptions(tracks, source?.subtitles.orEmpty())
                textOptions = options
                selectedTextId = rematchTextId(selectedTextId, options)
                val vids = buildVideoOptions(tracks)
                videoOptions = vids
                selectedVideoId = rematchVideoId(selectedVideoId, vids)
                if (activeSelector != null) {
                    applyTextSelection(activeSelector, selectedTextId, options, subsEnabled)
                    applyVideoSelection(activeSelector, selectedVideoId, vids)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (sourceIndex + 1 < sources.size) {
                    val pos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: positionMs
                    // Prefer live position, then an in-flight quality resume target,
                    // and only then watch-history — never rewind a quality switch.
                    resumeAtMs = when {
                        pos > 2_000L -> pos
                        resumeAtMs > 2_000L -> resumeAtMs
                        allowHistoryFallback && startPositionMs > 2_000L -> startPositionMs
                        else -> pos.coerceAtLeast(resumeAtMs)
                    }
                    didSeekStart = false
                    sourceIndex += 1
                } else {
                    val cause = e.cause?.message?.takeIf { it.isNotBlank() }
                    error = listOfNotNull(e.message, cause).joinToString(" — ")
                        .ifBlank { "Playback failed" }
                }
            }
        }
        activePlayer?.addListener(listener)
        onDispose {
            activePlayer?.removeListener(listener)
            activePlayer?.release()
        }
    }

    LaunchedEffect(sources) {
        sourceIndex = 0
        resumeAtMs = startPositionMs
        didSeekStart = false
        allowHistoryFallback = true
        if (sources.isEmpty()) error = "No playable source"
    }

    // Parent may load watch-history into startPositionMs after first composition.
    LaunchedEffect(startPositionMs) {
        if (!didSeekStart && allowHistoryFallback && startPositionMs > resumeAtMs) {
            resumeAtMs = startPositionMs
        }
    }

    // Apply resume even if READY already fired before history arrived.
    LaunchedEffect(resumeAtMs, player, ready, didSeekStart) {
        val p = player ?: return@LaunchedEffect
        if (didSeekStart || !ready || resumeAtMs <= 1_500L) return@LaunchedEffect
        val d = p.duration
        if (d <= resumeAtMs) return@LaunchedEffect
        p.seekTo(resumeAtMs.coerceAtMost(d - 1_000L))
        positionMs = resumeAtMs
        resumeAtMs = 0L
        didSeekStart = true
        allowHistoryFallback = false
    }

    LaunchedEffect(subsEnabled, selectedTextId, textOptions, trackSelector) {
        val selector = trackSelector ?: return@LaunchedEffect
        applyTextSelection(selector, selectedTextId, textOptions, subsEnabled)
    }

    LaunchedEffect(selectedVideoId, videoOptions, trackSelector) {
        val selector = trackSelector ?: return@LaunchedEffect
        applyVideoSelection(selector, selectedVideoId, videoOptions)
    }

    LaunchedEffect(subStyle, playerViewRef) {
        applyCaptionStyle(playerViewRef, subStyle)
        context.getSharedPreferences("anikitty_player", 0).edit()
            .putInt("sub_style_v", SUB_STYLE_VERSION)
            .putFloat("sub_size_sp", subStyle.sizeSp)
            .putFloat("sub_bottom", subStyle.bottomPad)
            .putInt("sub_color", subStyle.color)
            .putString("sub_bg_mode", subStyle.bgMode.name)
            .putBoolean("sub_bg", subStyle.bgMode != SubBgMode.None)
            .putInt("sub_edge", subStyle.edgeType)
            .apply()
    }

    LaunchedEffect(activePlayer, scrubbing, isCasting) {
        var ticks = 0
        val p = activePlayer
        while (p != null) {
            if (!scrubbing) {
                positionMs = p.currentPosition.coerceAtLeast(0L)
                val d = p.duration
                if (d > 0) durationMs = d
                playing = p.isPlaying
                ready = p.playbackState == Player.STATE_READY ||
                    p.playbackState == Player.STATE_BUFFERING ||
                    isCasting
                val active = skipTimes.activeAt(positionMs)
                if (active != null && !isCasting) {
                    val (kind, iv) = active
                    if (kind == "recap" && !autoSkippedRecap) {
                        autoSkippedRecap = true
                        p.seekTo(iv.endMs)
                        positionMs = iv.endMs
                    }
                }
                ticks++
                if (ticks % 8 == 0) {
                    // Don't clobber saved history with ~0 while waiting to resume.
                    val waitingHistory =
                        !didSeekStart && startPositionMs > 2_000L && positionMs < startPositionMs - 1_000L
                    if (!waitingHistory) {
                        onProgress?.invoke(positionMs, durationMs)
                    }
                }
            }
            delay(250)
        }
    }

    LaunchedEffect(anilistId, episode, titleHint) {
        autoSkippedRecap = false
        val fromSource = sources.firstOrNull { !it.skip.isEmpty }?.skip ?: SkipTimes()
        skipTimes = fromSource
        if (anilistId <= 0 || episode <= 0) return@LaunchedEffect
        val loaded = runCatching { loadSkip(anilistId, episode, titleHint) }.getOrDefault(SkipTimes())
        if (!loaded.isEmpty) skipTimes = loaded
    }

    DisposableEffect(Unit) {
        onDispose {
            onProgress?.invoke(positionMs, durationMs)
        }
    }

    LaunchedEffect(nearEndOffer, showEndAutoplay) {
        if (nearEndOffer || showEndAutoplay) {
            controlsVisible = true
        }
    }

    LaunchedEffect(showEndAutoplay, episode, autoplayCancelled, nextTriggered) {
        if (!showEndAutoplay || autoplayCancelled || nextTriggered) return@LaunchedEffect
        for (sec in AUTOPLAY_COUNTDOWN_SEC downTo 1) {
            autoplaySecLeft = sec
            delay(1_000)
            if (autoplayCancelled || !playbackEnded || nextTriggered) return@LaunchedEffect
        }
        goNextEpisode()
    }

    LaunchedEffect(nextTriggered) {
        if (!nextTriggered) return@LaunchedEffect
        // If resolve fails, parent keeps this episode — allow retry.
        delay(15_000)
        nextTriggered = false
    }

    LaunchedEffect(controlsVisible, playing, scrubbing, panel, isCasting, nearEndOffer, showEndAutoplay) {
        if (nearEndOffer || showEndAutoplay) return@LaunchedEffect
        if (controlsVisible && playing && !scrubbing && panel == Panel.None) {
            delay(if (isCasting) 5_500 else 3_600)
            controlsVisible = false
        }
    }

    fun seekBy(deltaMs: Long) {
        val p = activePlayer ?: return
        val target = (p.currentPosition + deltaMs).coerceIn(0L, p.duration.coerceAtLeast(0L))
        p.seekTo(target)
        positionMs = target
        seekFlashDir = if (deltaMs < 0) -1 else 1
        if (!controlsLocked) controlsVisible = true
    }

    fun skipTo(endMs: Long) {
        val p = activePlayer ?: return
        p.seekTo(endMs.coerceIn(0L, p.duration.coerceAtLeast(0L)))
        positionMs = endMs
        controlsVisible = true
    }

    fun pickQuality(index: Int) {
        if (index == sourceIndex || index !in sources.indices) return
        resumeAtMs = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: positionMs
        didSeekStart = false
        allowHistoryFallback = false
        // Keep the user's resolution lock across mirror switches when possible.
        sourceIndex = index
        panel = Panel.None
        controlsVisible = true
    }

    val activeSkip = skipTimes.activeAt(positionMs)
    val qualityLabel = when {
        selectedVideoId != "auto" ->
            videoOptions.firstOrNull { it.id == selectedVideoId }?.label ?: selectedVideoId
        videoOptions.any { it.height >= 1080 } -> "Auto · 1080p"
        videoOptions.any { it.height > 0 } -> {
            val best = videoOptions.filter { it.height > 0 }.maxByOrNull { it.height }
            "Auto · ${best?.label ?: "HD"}"
        }
        else -> source?.label ?: "Quality"
    }
    val subLabel = when {
        !subsEnabled -> "Off"
        selectedTextId == "auto" -> "Auto"
        else -> textOptions.firstOrNull { it.id == selectedTextId }?.label ?: "Subs"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KittyInk)
            .pointerInput(controlsLocked) {
                detectTapGestures(
                    onTap = {
                        if (controlsLocked) {
                            controlsVisible = true
                            return@detectTapGestures
                        }
                        if (panel != Panel.None) {
                            panel = Panel.None
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        if (controlsLocked) return@detectTapGestures
                        // Plex-style: left −10s, right +30s
                        if (offset.x < size.width / 2f) seekBy(-10_000) else seekBy(30_000)
                    },
                )
            }
            .pointerInput(controlsLocked) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (controlsLocked) return@awaitEachGesture
                    val releasedEarly = withTimeoutOrNull(280L) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) return@withTimeoutOrNull true
                        }
                    }
                    if (releasedEarly == null) {
                        holdBoost = true
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) break
                        }
                        holdBoost = false
                    }
                }
            },
    ) {
        AnimatedVisibility(
            visible = holdBoost,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
        ) {
            Text(
                "2×",
                color = PlayerChrome.Ink,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PlayerChrome.Accent)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        AnimatedVisibility(
            visible = seekFlashDir != 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(if (seekFlashDir < 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 36.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PlayerChrome.DiscLarge)
                    .border(1.dp, PlayerChrome.Accent.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (seekFlashDir < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward30,
                    contentDescription = null,
                    tint = PlayerChrome.Accent,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        if (player != null && error == null && !isCasting) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        this.player = player
                        subtitleView?.setApplyEmbeddedStyles(false)
                        applyCaptionStyle(this, subStyle)
                        playerViewRef = this
                    }
                },
                update = {
                    it.player = player
                    it.resizeMode = resizeMode
                    applyCaptionStyle(it, subStyle)
                    playerViewRef = it
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isCasting) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.CastConnected,
                    contentDescription = null,
                    tint = PlayerChrome.Paper,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Casting to your TV",
                    color = PlayerChrome.Paper,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "EP $episode · $title",
                    color = PlayerChrome.Graphite,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!ready && error == null && !isCasting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = PlayerChrome.Accent,
            )
        }

        if (castHint != null) {
            Text(
                castHint!!,
                color = PlayerChrome.Paper,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayerChrome.Sheet)
                    .border(1.dp, PlayerChrome.SheetStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (nearEndOffer || showEndAutoplay) {
            NextEpisodePrompt(
                modifier = Modifier
                    .align(if (showEndAutoplay) Alignment.Center else Alignment.BottomStart)
                    .then(
                        if (showEndAutoplay) {
                            Modifier.padding(28.dp)
                        } else {
                            Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(start = 18.dp, bottom = 108.dp, end = 18.dp)
                        },
                    ),
                nextEpisode = episode + 1,
                ended = showEndAutoplay,
                secondsLeft = autoplaySecLeft,
                busy = nextTriggered,
                onPlayNow = { goNextEpisode() },
                onDismiss = {
                    if (showEndAutoplay) {
                        autoplayCancelled = true
                    } else {
                        nearEndDismissed = true
                    }
                },
            )
        }

        if (error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Couldn't play this source",
                    color = PlayerChrome.Paper,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error!!,
                    color = PlayerChrome.Graphite,
                    fontSize = 13.sp,
                )
            }
        }

        if (controlsLocked && controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
            ) {
                ChromeDisc(onClick = {
                    controlsLocked = false
                    controlsVisible = true
                }, size = 48.dp) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock", tint = PlayerChrome.Paper)
                }
            }
        }

        AnimatedVisibility(
            visible = !controlsLocked && (controlsVisible || error != null || panel != Panel.None),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Soft Plex-like vignettes — airy, not heavy bars.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                            ),
                        ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            color = PlayerChrome.Paper,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PlayerMetaPill("E$episode")
                            if (durationMs > 0L) {
                                PlayerMetaPill(formatDurationMinutes(durationMs))
                            }
                            PlayerMetaPill(shortQuality(qualityLabel), emphasized = true)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    if (castHelper != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PlayerChrome.Disc)
                                .border(1.dp, PlayerChrome.DiscStroke, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            CastRouteButton()
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    ChromeDisc(onClick = {
                        controlsLocked = true
                        panel = Panel.None
                        controlsVisible = false
                    }, size = 40.dp) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Lock controls", tint = PlayerChrome.Paper)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    if (onNextEpisode != null) {
                        ChromeDisc(onClick = { goNextEpisode() }, size = 40.dp) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = "Next episode",
                                tint = PlayerChrome.Paper,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    ChromeDisc(onClick = onClose, size = 40.dp) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = PlayerChrome.Paper,
                        )
                    }
                }

                if (panel == Panel.None) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        ChromeDisc(onClick = { seekBy(-10_000) }, size = 64.dp, large = true) {
                            Icon(
                                Icons.Rounded.Replay10,
                                contentDescription = "Back 10 seconds",
                                tint = PlayerChrome.Paper,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        ChromeDisc(
                            onClick = {
                                activePlayer?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                }
                                controlsVisible = true
                            },
                            size = 76.dp,
                            large = true,
                            accent = !playing,
                        ) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = if (playing) PlayerChrome.Paper else PlayerChrome.Ink,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        ChromeDisc(onClick = { seekBy(30_000) }, size = 64.dp, large = true) {
                            Icon(
                                Icons.Rounded.Forward30,
                                contentDescription = "Forward 30 seconds",
                                tint = PlayerChrome.Paper,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    val dur = durationMs.coerceAtLeast(1L).toFloat()
                    val progress = if (scrubbing) scrubValue else (positionMs / dur).coerceIn(0f, 1f)
                    val scrubMs = if (scrubbing) (scrubValue * durationMs).toLong() else positionMs

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChromeDisc(
                            onClick = {
                                panel = if (panel == Panel.Quality) Panel.None else Panel.Quality
                                controlsVisible = true
                            },
                            size = 40.dp,
                        ) {
                            Icon(
                                Icons.Rounded.Hd,
                                contentDescription = "Quality",
                                tint = if (panel == Panel.Quality) PlayerChrome.Progress else PlayerChrome.Paper,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ChromeDisc(
                            onClick = {
                                panel = if (panel == Panel.Subs || panel == Panel.Style) {
                                    Panel.None
                                } else {
                                    Panel.Subs
                                }
                                controlsVisible = true
                            },
                            size = 40.dp,
                        ) {
                            Icon(
                                Icons.Rounded.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = if (panel == Panel.Subs || panel == Panel.Style) {
                                    PlayerChrome.Progress
                                } else {
                                    PlayerChrome.Paper
                                },
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ChromeDisc(
                            onClick = {
                                panel = if (panel == Panel.Speed) Panel.None else Panel.Speed
                                controlsVisible = true
                            },
                            size = if (playbackSpeed == 1f) 40.dp else 48.dp,
                        ) {
                            if (playbackSpeed == 1f) {
                                Icon(
                                    Icons.Rounded.Speed,
                                    contentDescription = "Speed",
                                    tint = if (panel == Panel.Speed) PlayerChrome.Progress else PlayerChrome.Paper,
                                )
                            } else {
                                Text(
                                    speedLabel(playbackSpeed),
                                    color = PlayerChrome.Paper,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ChromeDisc(
                            onClick = {
                                panel = if (panel == Panel.Display) Panel.None else Panel.Display
                                controlsVisible = true
                            },
                            size = 40.dp,
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Display",
                                tint = if (panel == Panel.Display) PlayerChrome.Progress else PlayerChrome.Paper,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    GlassScrubber(
                        progress = progress,
                        durationMs = durationMs,
                        scrubbing = scrubbing,
                        onScrub = {
                            scrubbing = true
                            scrubValue = it
                            controlsVisible = true
                        },
                        onScrubFinished = {
                            activePlayer?.seekTo((scrubValue * durationMs).toLong())
                            positionMs = (scrubValue * durationMs).toLong()
                            scrubbing = false
                        },
                        formatClock = ::formatClock,
                        skipTimes = skipTimes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            formatClock(if (scrubbing) scrubMs else positionMs),
                            color = PlayerChrome.Graphite,
                            fontSize = 12.sp,
                        )
                        Text(
                            "−${formatClock((durationMs - (if (scrubbing) scrubMs else positionMs)).coerceAtLeast(0L))}",
                            color = PlayerChrome.Paper,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (panel != Panel.None) {
                    PlayerSheet(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .widthIn(max = 320.dp)
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        onDismiss = { panel = Panel.None },
                    ) {
                        when (panel) {
                            Panel.Quality -> QualitySheet(
                                videoOptions = videoOptions,
                                selectedVideoId = selectedVideoId,
                                onPickVideo = {
                                    selectedVideoId = it
                                    panel = Panel.None
                                    controlsVisible = true
                                },
                                sources = sources,
                                selectedSource = sourceIndex,
                                onPickSource = ::pickQuality,
                            )
                            Panel.Subs -> SubsSheet(
                                enabled = subsEnabled,
                                options = textOptions,
                                selectedId = selectedTextId,
                                onToggle = {
                                    subsEnabled = it
                                    if (!it) selectedTextId = "off"
                                    else if (selectedTextId == "off") selectedTextId = "auto"
                                },
                                onPick = {
                                    selectedTextId = it
                                    subsEnabled = it != "off"
                                },
                                onOpenStyle = { panel = Panel.Style },
                            )
                            Panel.Style -> StyleSheet(
                                style = subStyle,
                                onChange = { subStyle = it },
                                onBack = { panel = Panel.Subs },
                            )
                            Panel.Display -> DisplaySheet(
                                resizeMode = resizeMode,
                                immersive = immersive,
                                onResize = { resizeMode = it },
                                onImmersive = { immersive = it },
                            )
                            Panel.Speed -> SpeedSheet(
                                speed = playbackSpeed,
                                onPick = {
                                    playbackSpeed = it
                                    panel = Panel.None
                                },
                            )
                            Panel.None -> Unit
                        }
                    }
                }
            }
        }

        if (activeSkip != null && !isCasting) {
            val (kind, iv) = activeSkip
            val label = when (kind) {
                "intro" -> "Skip intro"
                "credits" -> "Skip credits"
                "recap" -> "Skip recap"
                "preview" -> "Skip preview"
                else -> "Skip"
            }
            val remaining = ((iv.endMs - positionMs) / 1000L).coerceAtLeast(0L)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 16.dp, bottom = 92.dp)
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(PlayerChrome.Accent)
                    .clickable { skipTo(iv.endMs) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = PlayerChrome.Ink,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    label,
                    color = PlayerChrome.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    "${remaining}s",
                    color = PlayerChrome.Ink.copy(alpha = 0.62f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun NextEpisodePrompt(
    modifier: Modifier = Modifier,
    nextEpisode: Int,
    ended: Boolean,
    secondsLeft: Int,
    busy: Boolean,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .shadow(16.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(PlayerChrome.Sheet)
            .border(1.dp, PlayerChrome.SheetStroke, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            if (ended) "Up next" else "Almost done",
            color = PlayerChrome.Faint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Episode $nextEpisode",
            color = PlayerChrome.Paper,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            when {
                busy -> "Loading next episode…"
                ended -> "Starting automatically in ${secondsLeft}s"
                else -> "Want to continue to the next episode?"
            },
            color = PlayerChrome.Graphite,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayerChrome.Accent)
                    .clickable(enabled = !busy, onClick = onPlayNow)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    if (ended) "Play now" else "Next episode",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayerChrome.Disc)
                    .border(1.dp, PlayerChrome.Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy, onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    if (ended) "Cancel" else "Not now",
                    color = PlayerChrome.Paper,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun PlayerSheet(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(PlayerChrome.Sheet)
            .border(1.dp, PlayerChrome.SheetStroke, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* absorb */ },
            )
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            content()
        }
    }
}

@Composable
private fun SpeedSheet(speed: Float, onPick: (Float) -> Unit) {
    SheetTitle("Speed")
    Spacer(modifier = Modifier.height(10.dp))
    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
        SheetRow(
            label = speedLabel(rate),
            selected = kotlin.math.abs(speed - rate) < 0.01f,
            onClick = { onPick(rate) },
        )
    }
}

@Composable
private fun QualitySheet(
    videoOptions: List<VideoOption>,
    selectedVideoId: String,
    onPickVideo: (String) -> Unit,
    sources: List<PlayableSource>,
    selectedSource: Int,
    onPickSource: (Int) -> Unit,
) {
    SheetTitle("Resolution")
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "Pick 1080p / 720p / … when the stream offers them",
        color = PlayerChrome.Graphite,
        fontSize = 12.sp,
    )
    Spacer(modifier = Modifier.height(10.dp))
    val resolutions = videoOptions.ifEmpty {
        listOf(VideoOption(id = "auto", label = "Auto (best available)"))
    }
    resolutions.forEach { opt ->
        SheetRow(
            label = opt.label,
            selected = selectedVideoId == opt.id ||
                (opt.id == "auto" && selectedVideoId == "auto"),
            onClick = { onPickVideo(opt.id) },
        )
    }
    if (sources.size > 1) {
        Spacer(modifier = Modifier.height(16.dp))
        SheetTitle("Source")
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Alternate mirrors for this episode",
            color = PlayerChrome.Graphite,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        sources.forEachIndexed { index, src ->
            SheetRow(
                label = src.label,
                selected = index == selectedSource,
                onClick = { onPickSource(index) },
            )
        }
    } else if (sources.size == 1) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Source · ${sources[0].label}",
            color = PlayerChrome.Graphite,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SubsSheet(
    enabled: Boolean,
    options: List<TextOption>,
    selectedId: String,
    onToggle: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    onOpenStyle: () -> Unit,
) {
    SheetTitle("Subtitles")
    Spacer(modifier = Modifier.height(10.dp))
    SheetRow(
        label = "Off",
        selected = !enabled || selectedId == "off",
        onClick = { onToggle(false) },
    )
    SheetRow(
        label = "Auto",
        selected = enabled && selectedId == "auto",
        onClick = {
            onToggle(true)
            onPick("auto")
        },
    )
    options.filter { it.id != "off" && it.id != "auto" }.forEach { opt ->
        SheetRow(
            label = opt.label,
            selected = enabled && selectedId == opt.id,
            onClick = {
                onToggle(true)
                onPick(opt.id)
            },
        )
    }
    if (options.none { it.id != "off" && it.id != "auto" }) {
        Text(
            "No external caption files on this stream — Auto uses embedded tracks when available.",
            color = PlayerChrome.Faint,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    SheetRow(label = "Customize appearance…", selected = false, onClick = onOpenStyle)
}

@Composable
private fun StyleSheet(
    style: SubStyle,
    onChange: (SubStyle) -> Unit,
    onBack: () -> Unit,
) {
    val positionLabel = when {
        style.bottomPad <= 0.08f -> "Low"
        style.bottomPad <= 0.13f -> "Mid"
        style.bottomPad <= 0.19f -> "High"
        else -> "Top"
    }
    SheetTitle("Captions")
    Text(
        "Tracks",
        color = PlayerChrome.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 4.dp, bottom = 12.dp)
            .clickable(onClick = onBack),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Size", color = PlayerChrome.Graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            String.format(Locale.US, "%.0f sp", style.sizeSp),
            color = PlayerChrome.Faint,
            fontSize = 11.sp,
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            8f to "Tiny",
            10f to "Small",
            DEFAULT_SUB_SIZE_SP to "Default",
            14f to "Large",
            18f to "XL",
            22f to "Max",
        ).forEach { (sp, label) ->
            ChoicePill(
                label = label,
                selected = kotlin.math.abs(style.sizeSp - sp) < 0.4f,
                onClick = { onChange(style.copy(sizeSp = sp)) },
            )
        }
    }
    Slider(
        value = style.sizeSp,
        onValueChange = { onChange(style.copy(sizeSp = it)) },
        valueRange = SUB_SIZE_SP_MIN..SUB_SIZE_SP_MAX,
        colors = SliderDefaults.colors(
            thumbColor = PlayerChrome.Accent,
            activeTrackColor = PlayerChrome.Accent,
            inactiveTrackColor = PlayerChrome.Faint,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Position", color = PlayerChrome.Graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(positionLabel, color = PlayerChrome.Faint, fontSize = 11.sp)
    }
    Text(
        "Drag right to raise captions",
        color = PlayerChrome.Faint,
        fontSize = 11.sp,
    )
    Slider(
        value = style.bottomPad,
        onValueChange = { onChange(style.copy(bottomPad = it)) },
        valueRange = SUB_BOTTOM_MIN..SUB_BOTTOM_MAX,
        colors = SliderDefaults.colors(
            thumbColor = PlayerChrome.Accent,
            activeTrackColor = PlayerChrome.Accent,
            inactiveTrackColor = PlayerChrome.Faint,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(10.dp))
    Text("Color", color = PlayerChrome.Graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            AndroidColor.WHITE to "White",
            AndroidColor.YELLOW to "Yellow",
            AndroidColor.CYAN to "Cyan",
            AndroidColor.rgb(255, 210, 140) to "Warm",
            AndroidColor.rgb(180, 255, 180) to "Mint",
            AndroidColor.rgb(220, 220, 255) to "Ice",
        ).forEach { (c, label) ->
            ChoicePill(
                label = label,
                selected = style.color == c,
                onClick = { onChange(style.copy(color = c)) },
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text("Background", color = PlayerChrome.Graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            SubBgMode.None to "None",
            SubBgMode.Soft to "Soft",
            SubBgMode.Blur to "Blur",
            SubBgMode.Solid to "Solid",
        ).forEach { (mode, label) ->
            ChoicePill(
                label = label,
                selected = style.bgMode == mode,
                onClick = { onChange(style.copy(bgMode = mode)) },
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text("Edge", color = PlayerChrome.Graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "Shadow",
            CaptionStyleCompat.EDGE_TYPE_OUTLINE to "Outline",
            CaptionStyleCompat.EDGE_TYPE_NONE to "None",
        ).forEach { (edge, label) ->
            ChoicePill(
                label = label,
                selected = style.edgeType == edge,
                onClick = { onChange(style.copy(edgeType = edge)) },
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    val previewBg = when (style.bgMode) {
        SubBgMode.None -> Color.Transparent
        SubBgMode.Soft -> Color.Black.copy(alpha = 0.35f)
        SubBgMode.Blur -> Color.Black.copy(alpha = 0.48f)
        SubBgMode.Solid -> Color.Black.copy(alpha = 0.82f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1A1E), Color(0xFF0A0A0C)),
                ),
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Preview · 字幕サンプル",
            color = Color(style.color),
            fontSize = style.sizeSp.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(previewBg)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun DisplaySheet(
    resizeMode: Int,
    immersive: Boolean,
    onResize: (Int) -> Unit,
    onImmersive: (Boolean) -> Unit,
) {
    SheetTitle("Display")
    Spacer(modifier = Modifier.height(10.dp))
    SheetRow(
        label = "Fit (letterbox)",
        selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
        onClick = { onResize(AspectRatioFrameLayout.RESIZE_MODE_FIT) },
    )
    SheetRow(
        label = "Fill (crop)",
        selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        onClick = { onResize(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) },
    )
    SheetRow(
        label = "Stretch",
        selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL,
        onClick = { onResize(AspectRatioFrameLayout.RESIZE_MODE_FILL) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    SheetRow(
        label = if (immersive) "System bars hidden" else "Show system bars",
        selected = immersive,
        onClick = { onImmersive(!immersive) },
    )
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        color = PlayerChrome.Accent,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
    )
}

@Composable
private fun SheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) PlayerChrome.RowSelected else Color.Transparent)
            .border(
                1.dp,
                if (selected) PlayerChrome.Accent.copy(alpha = 0.45f) else Color.Transparent,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) PlayerChrome.Paper else PlayerChrome.Graphite,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = PlayerChrome.Accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) PlayerChrome.Accent else PlayerChrome.Disc)
            .border(
                1.dp,
                if (selected) PlayerChrome.Accent else PlayerChrome.DiscStroke,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else PlayerChrome.Paper,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PlayerMetaPill(label: String, emphasized: Boolean = false) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (emphasized) PlayerChrome.Accent else PlayerChrome.Disc)
            .border(
                1.dp,
                if (emphasized) PlayerChrome.Accent else PlayerChrome.DiscStroke,
                shape,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = if (emphasized) Color.Black else PlayerChrome.Graphite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChromeDisc(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    large: Boolean = false,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(if (large) 22.dp else 14.dp)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = if (large) 16.dp else 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(shape)
            .background(
                when {
                    accent -> PlayerChrome.Accent
                    large -> Color.White.copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
            )
            .border(
                1.dp,
                if (accent) Color.White else Color.White.copy(alpha = 0.16f),
                shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@OptIn(UnstableApi::class)
private fun subtitleConfig(
    track: SubtitleTrack,
    default: Boolean = false,
): MediaItem.SubtitleConfiguration? {
    if (!track.url.startsWith("http")) return null
    val mime = track.mimeType
        ?: when {
            track.url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
            track.url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
            track.url.contains(".ass", ignoreCase = true) ||
                track.url.contains(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_VTT
        }
    return MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.url))
        .setMimeType(mime)
        .setLanguage(track.language ?: "en")
        .setLabel(track.label)
        .setSelectionFlags(if (default) C.SELECTION_FLAG_DEFAULT else 0)
        .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
        .build()
}

private data class PlayerBundle(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector,
)

@OptIn(UnstableApi::class)
private fun buildTextOptions(tracks: Tracks, external: List<SubtitleTrack>): List<TextOption> {
    val out = ArrayList<TextOption>()
    out += TextOption(id = "off", label = "Off")
    out += TextOption(id = "auto", label = "Auto")
    var externalIdx = 0
    val usedIds = HashSet<String>()
    tracks.groups.forEachIndexed { _, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (ti in 0 until group.length) {
            if (!group.isTrackSupported(ti)) continue
            val format: Format = group.getTrackFormat(ti)
            val label = format.label
                ?: format.language?.uppercase(Locale.US)
                ?: external.getOrNull(externalIdx)?.label
                ?: "Track ${out.size - 1}"
            externalIdx++
            var id = stableTextId(format.language, label)
            if (!usedIds.add(id)) {
                id = "$id#$ti"
                usedIds += id
            }
            out += TextOption(
                id = id,
                label = label,
                mediaGroup = group.mediaTrackGroup,
                trackIndex = ti,
            )
        }
    }
    return out
}

/** Stable across quality switches (group indices are not). */
private fun stableTextId(language: String?, label: String): String {
    val lang = language?.trim()?.lowercase(Locale.US).orEmpty()
    val normLabel = label.trim().lowercase(Locale.US)
    return when {
        lang.isNotBlank() && lang != "und" -> "lang:$lang|$normLabel"
        else -> "label:$normLabel"
    }
}

private fun rematchTextId(previous: String, options: List<TextOption>): String {
    if (previous == "off" || previous == "auto") return previous
    if (options.any { it.id == previous }) return previous
    val lang = previous.removePrefix("lang:").substringBefore("|").takeIf {
        previous.startsWith("lang:")
    }
    if (!lang.isNullOrBlank()) {
        options.firstOrNull { it.id.startsWith("lang:$lang") }?.id?.let { return it }
    }
    val labelKey = when {
        previous.startsWith("label:") -> previous.removePrefix("label:")
        previous.startsWith("lang:") -> previous.substringAfter("|", "")
        else -> previous
    }
    if (labelKey.isNotBlank()) {
        options.firstOrNull {
            it.id == "label:$labelKey" || it.label.equals(labelKey, ignoreCase = true)
        }?.id?.let { return it }
    }
    return "auto"
}

@OptIn(UnstableApi::class)
private fun buildVideoOptions(tracks: Tracks): List<VideoOption> {
    data class Cand(val height: Int, val width: Int, val group: TrackGroup, val index: Int, val bitrate: Int)
    val cands = ArrayList<Cand>()
    for (i in 0 until tracks.groups.size) {
        val group = tracks.groups[i]
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        val media = group.mediaTrackGroup
        for (t in 0 until group.length) {
            if (!group.isTrackSupported(t)) continue
            val format = group.getTrackFormat(t)
            val height = format.height
            if (height <= 0) continue
            cands += Cand(
                height = height,
                width = format.width.coerceAtLeast(0),
                group = media,
                index = t,
                bitrate = format.bitrate,
            )
        }
    }
    if (cands.isEmpty()) {
        return listOf(VideoOption(id = "auto", label = "Auto (best available)"))
    }
    // One row per unique height — keep the highest-bitrate variant for that height.
    val bestByHeight = cands
        .groupBy { it.height }
        .map { (_, list) -> list.maxBy { it.bitrate } }
        .sortedByDescending { it.height }
    val out = ArrayList<VideoOption>(bestByHeight.size + 1)
    out += VideoOption(id = "auto", label = "Auto (best available)")
    for (c in bestByHeight) {
        val label = when {
            c.height >= 2160 -> "2160p · 4K"
            c.height >= 1440 -> "1440p · QHD"
            c.height >= 1080 -> "1080p · Full HD"
            c.height >= 720 -> "720p · HD"
            c.height >= 480 -> "480p"
            else -> "${c.height}p"
        }
        out += VideoOption(
            id = "${c.height}p",
            label = label,
            height = c.height,
            width = c.width,
            mediaGroup = c.group,
            trackIndex = c.index,
        )
    }
    return out
}

private fun rematchVideoId(previous: String, options: List<VideoOption>): String {
    if (previous == "auto") return "auto"
    if (options.any { it.id == previous }) return previous
    val height = previous.removeSuffix("p").toIntOrNull()
    if (height != null) {
        options.firstOrNull { it.height == height }?.id?.let { return it }
        // Closest lower-or-equal height if the exact rung disappeared after a source switch.
        options.filter { it.height in 1..height }.maxByOrNull { it.height }?.id?.let { return it }
    }
    return "auto"
}

@OptIn(UnstableApi::class)
private fun applyVideoSelection(
    selector: DefaultTrackSelector,
    selectedId: String,
    options: List<VideoOption>,
) {
    val params = selector.buildUponParameters()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    if (selectedId == "auto") {
        selector.parameters = params
            .setForceHighestSupportedBitrate(false)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .build()
        return
    }
    val concrete = options.firstOrNull { it.id == selectedId && it.mediaGroup != null }
    if (concrete?.mediaGroup != null) {
        selector.parameters = params
            .setOverrideForType(
                TrackSelectionOverride(concrete.mediaGroup, listOf(concrete.trackIndex)),
            )
            .build()
        return
    }
    // Fallback: cap by height parsed from id ("1080p").
    val height = selectedId.removeSuffix("p").toIntOrNull()
    selector.parameters = if (height != null && height > 0) {
        params.setMaxVideoSize(Int.MAX_VALUE, height).build()
    } else {
        params.build()
    }
}

@OptIn(UnstableApi::class)
private fun applyTextSelection(
    selector: DefaultTrackSelector,
    selectedId: String,
    options: List<TextOption>,
    enabled: Boolean,
) {
    val params = selector.buildUponParameters()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setSelectUndeterminedTextLanguage(true)
        .setPreferredTextLanguage("en")
    if (!enabled || selectedId == "off") {
        selector.parameters = params
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        return
    }
    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    val concrete = when (selectedId) {
        "auto" -> options.firstOrNull { it.mediaGroup != null }
            ?: options.firstOrNull {
                it.id != "off" && it.id != "auto" && it.mediaGroup != null
            }
        else -> options.firstOrNull { it.id == selectedId }
    }
    val mediaGroup = concrete?.mediaGroup
    if (mediaGroup != null) {
        params.setOverrideForType(
            TrackSelectionOverride(mediaGroup, listOf(concrete.trackIndex)),
        )
    }
    selector.parameters = params.build()
}

@OptIn(UnstableApi::class)
private fun applyCaptionStyle(playerView: PlayerView?, style: SubStyle) {
    val view = playerView?.subtitleView ?: return
    view.visibility = android.view.View.VISIBLE
    // Backdrop modes approximate Netflix-style caption plates on Media3 SubtitleView.
    val (bg, window) = when (style.bgMode) {
        SubBgMode.None -> AndroidColor.TRANSPARENT to AndroidColor.TRANSPARENT
        SubBgMode.Soft -> AndroidColor.TRANSPARENT to AndroidColor.argb(100, 0, 0, 0)
        SubBgMode.Blur -> AndroidColor.argb(80, 16, 16, 18) to AndroidColor.argb(140, 0, 0, 0)
        SubBgMode.Solid -> AndroidColor.argb(200, 0, 0, 0) to AndroidColor.TRANSPARENT
    }
    view.setStyle(
        CaptionStyleCompat(
            style.color,
            bg,
            window,
            style.edgeType,
            AndroidColor.BLACK,
            Typeface.DEFAULT,
        ),
    )
    val metrics = view.resources.displayMetrics
    val sizeSp = style.sizeSp.coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX)
    // Exact sp → px. No hidden density floor that keeps captions oversized.
    val px = sizeSp * metrics.density
    view.setFixedTextSize(Cue.TEXT_SIZE_TYPE_ABSOLUTE, px)
    view.setBottomPaddingFraction(style.bottomPad.coerceIn(SUB_BOTTOM_MIN, SUB_BOTTOM_MAX))
}

private fun loadSubStyle(prefs: android.content.SharedPreferences): SubStyle {
    val version = prefs.getInt("sub_style_v", 0)
    val bgMode = when (prefs.getString("sub_bg_mode", null)) {
        SubBgMode.None.name -> SubBgMode.None
        SubBgMode.Soft.name -> SubBgMode.Soft
        SubBgMode.Blur.name -> SubBgMode.Blur
        SubBgMode.Solid.name -> SubBgMode.Solid
        else -> if (prefs.getBoolean("sub_bg", true)) SubBgMode.Soft else SubBgMode.None
    }
    // v3 resets prior oversized size defaults once; position keeps the full slider range.
    val sizeSp = when {
        version >= SUB_STYLE_VERSION && prefs.contains("sub_size_sp") ->
            prefs.getFloat("sub_size_sp", DEFAULT_SUB_SIZE_SP)
        prefs.contains("sub_size_sp") ->
            prefs.getFloat("sub_size_sp", DEFAULT_SUB_SIZE_SP).coerceAtMost(14f)
        else -> DEFAULT_SUB_SIZE_SP
    }
    // Persist Low→Top as saved. Only clamp to valid slider bounds — never rewrite Low up.
    val rawBottom = if (prefs.contains("sub_bottom")) {
        prefs.getFloat("sub_bottom", DEFAULT_SUB_BOTTOM_PAD)
    } else {
        DEFAULT_SUB_BOTTOM_PAD
    }
    val bottom = if (rawBottom.isFinite()) {
        rawBottom.coerceIn(SUB_BOTTOM_MIN, SUB_BOTTOM_MAX)
    } else {
        DEFAULT_SUB_BOTTOM_PAD
    }
    return SubStyle(
        sizeSp = sizeSp.coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX),
        bottomPad = bottom,
        color = prefs.getInt("sub_color", AndroidColor.WHITE),
        bgMode = bgMode,
        edgeType = prefs.getInt("sub_edge", CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW),
    )
}

/** Compact Netflix-like default; full Tiny→Max range is user-controlled. */
private const val SUB_STYLE_VERSION = 3
private const val DEFAULT_SUB_SIZE_SP = 10.5f
private const val SUB_SIZE_SP_MIN = 8f
private const val SUB_SIZE_SP_MAX = 24f
/** Raised so captions clear the player chrome by default. */
private const val DEFAULT_SUB_BOTTOM_PAD = 0.13f
private const val SUB_BOTTOM_MIN = 0.03f
private const val SUB_BOTTOM_MAX = 0.30f

private fun shortQuality(label: String): String {
    val m = Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(label)
    return m?.groupValues?.get(1) ?: label.take(12)
}

private fun resizeLabel(mode: Int): String = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Fill"
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
    else -> "Fit"
}

private fun speedLabel(speed: Float): String {
    return when {
        kotlin.math.abs(speed - 1f) < 0.01f -> "1×"
        speed == speed.toInt().toFloat() -> "${speed.toInt()}×"
        else -> String.format(Locale.US, "%.2g×", speed)
    }
}

private fun formatClock(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun formatDurationMinutes(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.coerceAtLeast(0L)).coerceAtLeast(1L)
    return if (minutes == 1L) "1 minute" else "$minutes minutes"
}

private const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
