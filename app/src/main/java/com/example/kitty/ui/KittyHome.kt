package com.example.kitty.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kitty.anilist.AiringSlot
import com.example.kitty.anilist.AnimeSummary
import com.example.kitty.theme.KittyAccent
import com.example.kitty.theme.KittyAccentGlow
import com.example.kitty.theme.KittyFaint
import com.example.kitty.theme.KittyGlass
import com.example.kitty.theme.KittyGlassShape
import com.example.kitty.theme.KittyInk
import com.example.kitty.theme.KittyMuted
import com.example.kitty.theme.KittyOnAccent
import com.example.kitty.theme.KittyOutline
import com.example.kitty.theme.KittyOutlineSoft
import com.example.kitty.theme.KittySurface
import com.example.kitty.theme.KittySurfaceElevated
import com.example.kitty.theme.KittyText
import com.example.kitty.theme.PlayerChrome
import kotlinx.coroutines.delay

// Home-screen building blocks adapted from AniKitty (MIT, see NOTICE).

@Composable
fun KittyShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerShift",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(KittySurface)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    (shift - 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                    shift to KittyAccent.copy(alpha = 0.18f),
                    (shift + 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
    )
}

@Composable
fun KittySectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (padded) Modifier.padding(horizontal = 16.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = KittyText,
                letterSpacing = (-0.3).sp,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = KittyMuted,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (onMore != null) {
            Text(
                "See all",
                color = KittyAccentGlow,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onMore)
                    .border(1.dp, KittyAccent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun KittyPosterCard(
    anime: AnimeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 128.dp,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "posterPress",
    )
    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(KittySurface),
        ) {
            AsyncImage(
                model = anime.coverImageUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            anime.scoreLabel()?.let { score ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(8.dp),
                    )
                    Text(
                        score,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                    )
                }
            }
        }
        Text(
            anime.title,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = KittyText,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = KittyFaint,
            )
        } else {
            anime.format?.let { format ->
                Text(
                    format,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = KittyFaint,
                )
            }
        }
    }
}

@Composable
fun KittyPosterSkeletonRow(title: String = "Loading") {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        KittySectionHeading(title = title, subtitle = "Loading titles…")
        Spacer(modifier = Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(6) {
                Column(modifier = Modifier.width(118.dp)) {
                    KittyShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KittyShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun KittyTopTenRow(
    title: String,
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
    subtitle: String? = "Most watched right now",
) {
    val ranked = items.take(10)
    if (ranked.isEmpty()) return
    Column(modifier = Modifier.padding(top = 18.dp)) {
        KittySectionHeading(title = title, subtitle = subtitle)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(ranked.size) { index ->
                val anime = ranked[index]
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${index + 1}",
                        color = KittyAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        letterSpacing = (-1.5).sp,
                        modifier = Modifier.padding(end = 2.dp, bottom = 28.dp),
                    )
                    KittyPosterCard(
                        anime = anime,
                        onClick = { onOpen(anime) },
                        width = 108.dp,
                    )
                }
            }
        }
    }
}

@Composable
fun KittyGlassHeroPager(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
) {
    val pages = items.take(5)
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    LaunchedEffect(pages.size) {
        if (pages.size < 2) return@LaunchedEffect
        while (true) {
            delay(5000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % pages.size
                pagerState.animateScrollToPage(next)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
        ) { page ->
            val anime = pages[page]
            val shape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .clip(shape)
                    .background(Color(0xFF0F0F14))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    .clickable { onOpen(anime) },
            ) {
                AsyncImage(
                    model = anime.bannerImageUrl ?: anime.coverImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.35f },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.9f),
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(20.dp)
                        .fillMaxWidth(0.72f),
                ) {
                    Text(
                        (anime.format ?: "TV").uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        anime.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (anime.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            anime.genres.take(2).joinToString(" • "),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(KittyAccent)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = KittyOnAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play", color = KittyOnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pages.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(if (selected) 24.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) KittyAccent else Color.White.copy(alpha = 0.2f)),
                )
            }
        }
    }
}

@Composable
fun KittyGenreTiles(
    genres: List<Pair<String, String?>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    if (genres.isEmpty()) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            "Popular Genres",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(genres.size) { index ->
                val (name, image) = genres[index]
                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .aspectRatio(16f / 9f)
                        .clip(shape)
                        .background(Color(0xFF0F0F14))
                        .border(
                            1.dp,
                            if (selected == name) KittyAccent else Color.White.copy(alpha = 0.08f),
                            shape,
                        )
                        .clickable { onSelect(name) },
                ) {
                    if (!image.isNullOrBlank()) {
                        AsyncImage(
                            model = image,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.75f)),
                                ),
                            ),
                    )
                    Text(
                        name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun KittyPosterRow(
    title: String,
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
    subtitle: String? = null,
    subtitleFor: ((AnimeSummary) -> String?)? = null,
    onMore: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(top = 18.dp)) {
        KittySectionHeading(title = title, subtitle = subtitle, onMore = onMore)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { anime ->
                KittyPosterCard(
                    anime = anime,
                    onClick = { onOpen(anime) },
                    width = 118.dp,
                    subtitle = subtitleFor?.invoke(anime),
                )
            }
        }
    }
}


@Composable
fun KittyRoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(KittySurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
fun TodayAiringRail(
    slots: List<AiringSlot>,
    onMore: () -> Unit,
    onOpen: (AnimeSummary) -> Unit,
) {
    val now = System.currentTimeMillis() / 1000L
    val upcoming = slots.filter { it.airingAtEpochSec >= now - 20 * 60 }
    val shown = (if (upcoming.isNotEmpty()) upcoming else slots).take(12)
    val clock = remember {
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    }
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        KittySectionHeading(
            title = "Airing today",
            subtitle = "${shown.size} episode${if (shown.size == 1) "" else "s"}",
            onMore = onMore,
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { "${it.anime.id}-${it.episode}-${it.airingAtEpochSec}" }) { slot ->
                val soon = slot.airingAtEpochSec - now in 0..(45 * 60)
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KittySurface)
                        .border(1.dp, KittyOutlineSoft, RoundedCornerShape(16.dp))
                        .clickable { onOpen(slot.anime) }
                        .padding(8.dp),
                ) {
                    Box {
                        AsyncImage(
                            model = slot.anime.coverImageUrl,
                            contentDescription = slot.anime.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            if (soon) "Soon" else clock.format(java.util.Date(slot.airingAtEpochSec * 1000L)),
                            color = KittyOnAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(KittyAccent)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        slot.anime.title,
                        color = KittyText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Episode ${slot.episode}",
                        color = KittyFaint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun HomeWelcomeHeader(
    displayName: String,
    onRefresh: () -> Unit,
    refreshBusy: Boolean = false,
    onOpenSearch: (() -> Unit)? = null,
    onOpenAccount: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Home",
                color = KittyText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            )
            Text(
                "Welcome back, $displayName",
                color = KittyMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittyRoundIconButton(onClick = onRefresh) {
                if (refreshBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = KittyAccentGlow,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = KittyText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (onOpenAccount != null) {
                KittyRoundIconButton(onClick = onOpenAccount) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = "Account",
                        tint = KittyText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else if (onOpenSearch != null) {
                KittyRoundIconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = KittyText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun KittyContinueCard(
    title: String,
    coverUrl: String?,
    episode: Int,
    onClick: () -> Unit,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    wide: Boolean = true,
    badge: String? = null,
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0.08f
    }
    val shape = RoundedCornerShape(16.dp)
    if (wide) {
        // Split continue tile: cover + episode badge | title + progress
        Row(
            modifier = Modifier
                .width(280.dp)
                .height(112.dp)
                .clip(shape)
                .background(KittySurface)
                .border(1.dp, KittyOutlineSoft, shape)
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(96.dp),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    "EP $episode",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = KittyText,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                    )
                    if (!badge.isNullOrBlank()) {
                        Text(
                            badge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = KittyFaint,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    } else {
                        Text(
                            "Episode $episode",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = KittyFaint,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Column {
                    Text(
                        "${(progress * 100f).toInt()}% watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = KittyMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(PlayerChrome.TrackGlass),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(KittyAccent),
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0C0C10))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    listOfNotNull(badge?.takeIf { it.isNotBlank() }, "Episode $episode").joinToString(" · "),
                    maxLines = 1,
                    color = KittyMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(KittyOutlineSoft),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(KittyAccent),
                    )
                }
            }
        }
    }
}

