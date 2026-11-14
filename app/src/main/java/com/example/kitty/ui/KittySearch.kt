package com.example.kitty.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kitty.anilist.AiringSlot
import com.example.kitty.anilist.AniListClient
import com.example.kitty.anilist.AnimeEpisodeItem
import com.example.kitty.anilist.AnimeSummary
import com.example.kitty.theme.KittyAccent
import com.example.kitty.theme.KittyAccentGlow
import com.example.kitty.theme.KittyAtmosphere
import com.example.kitty.theme.KittyFaint
import com.example.kitty.theme.KittyGlass
import com.example.kitty.theme.KittyGlassShape
import com.example.kitty.theme.KittyGlassShapeTight
import com.example.kitty.theme.KittyGlassStrong
import com.example.kitty.theme.KittyMuted
import com.example.kitty.theme.KittyOnAccent
import com.example.kitty.theme.KittyOutline
import com.example.kitty.theme.KittyOutlineSoft
import com.example.kitty.theme.KittyPage
import com.example.kitty.theme.KittySurface
import com.example.kitty.theme.KittySurfaceElevated
import com.example.kitty.theme.KittyText
import com.example.kitty.ui.AnimatedSearchIcon
import com.example.kitty.ui.ExtensionIcon
import com.example.kitty.ui.KittyContinueCard
import com.example.kitty.ui.KittyEmptyState
import com.example.kitty.ui.KittyGenreTiles
import com.example.kitty.ui.KittyGlassHeroPager
import com.example.kitty.ui.KittyPageHeader
import com.example.kitty.ui.KittyPosterRow
import com.example.kitty.ui.KittyPosterSkeletonRow
import com.example.kitty.ui.KittyRoundIconButton
import com.example.kitty.ui.KittySectionHeading
import com.example.kitty.ui.KittyTopTenRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Adapted from AniKitty (MIT, see NOTICE).

@Composable
fun KittySearchScreen(
    aniList: AniListClient,
    onOpen: (AnimeSummary) -> Unit,
    requestedGenre: String? = null,
    onRequestedGenreConsumed: () -> Unit = {},
    listMarks: Map<Int, String> = emptyMap(),
) {
    var query by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<String?>(null) }
    var airingStatus by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<String?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var tag by remember { mutableStateOf<String?>(null) }
    var seasonYear by remember { mutableStateOf<Int?>(null) }
    var country by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf<String?>(null) }
    var asGrid by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var discover by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var recentRaw by rememberSaveable { mutableStateOf("") }
    val recent = remember(recentRaw) {
        recentRaw.split('\u001f').map { it.trim() }.filter { it.isNotEmpty() }
    }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun rememberQuery(q: String) {
        val trimmed = q.trim()
        if (trimmed.length < 2) return
        val next = (listOf(trimmed) + recent.filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(8)
        recentRaw = next.joinToString("\u001f")
    }

    fun filtersOn(): Boolean =
        format != null || airingStatus != null || season != null || genre != null ||
            tag != null || seasonYear != null || country != null || sort != null

    fun runSearch(raw: String) {
        val q = raw.trim()
        searchJob?.cancel()
        if (q.length < 2 && !filtersOn()) {
            items = emptyList()
            error = null
            loading = false
            return
        }
        searchJob = scope.launch {
            delay(350)
            loading = true
            error = null
            runCatching {
                aniList.browse(
                    query = q,
                    format = format,
                    status = airingStatus,
                    season = season,
                    genre = genre,
                    tag = tag,
                    seasonYear = seasonYear,
                    country = country,
                    sort = sort ?: if (q.length >= 2) "SEARCH_MATCH" else "POPULARITY_DESC",
                )
            }.onSuccess {
                items = it
                if (q.length >= 2 && it.isNotEmpty()) rememberQuery(q)
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { aniList.trending(perPage = 12) }
            .onSuccess { discover = it }
    }

    LaunchedEffect(requestedGenre) {
        val next = requestedGenre ?: return@LaunchedEffect
        genre = next
        runSearch(query)
        onRequestedGenreConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 12.dp),
    ) {
        KittyPageHeader(
            title = "Search",
            subtitle = "Find something to watch",
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                runSearch(it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Title, studio, or character", color = KittyFaint) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KittyText,
                unfocusedTextColor = KittyText,
                cursorColor = KittyText,
                focusedBorderColor = Color.White.copy(alpha = 0.28f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color(0xFF0C0C10),
                unfocusedContainerColor = Color(0xFF0C0C10),
            ),
            leadingIcon = {
                AnimatedSearchIcon(
                    tint = if (query.isNotBlank()) KittyAccentGlow else KittyMuted,
                    active = query.isBlank() || loading,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            runSearch("")
                        },
                    ) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search", tint = KittyMuted)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fun toggle(current: String?, value: String, assign: (String?) -> Unit) {
                assign(if (current == value) null else value)
                runSearch(query)
            }
            listOf("TV" to "TV", "MOVIE" to "Movie", "OVA" to "OVA").forEach { (value, label) ->
                KittyChip(label, selected = format == value) { toggle(format, value) { format = it } }
            }
            listOf(
                "RELEASING" to "Airing",
                "FINISHED" to "Finished",
                "NOT_YET_RELEASED" to "Upcoming",
            ).forEach { (value, label) ->
                KittyChip(label, selected = airingStatus == value) {
                    toggle(airingStatus, value) { airingStatus = it }
                }
            }
            listOf("WINTER", "SPRING", "SUMMER", "FALL").forEach { value ->
                val label = value.lowercase().replaceFirstChar { it.titlecase() }
                KittyChip(label, selected = season == value) { toggle(season, value) { season = it } }
            }
            listOf(
                "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Slice of Life",
            ).forEach { value ->
                KittyChip(value, selected = genre == value) { toggle(genre, value) { genre = it } }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fun toggle(current: String?, value: String, assign: (String?) -> Unit) {
                assign(if (current == value) null else value)
                runSearch(query)
            }
            listOf(
                null to "Match",
                "POPULARITY_DESC" to "Popular",
                "TRENDING_DESC" to "Trending",
                "SCORE_DESC" to "Score",
                "START_DATE_DESC" to "Newest",
                "FAVOURITES_DESC" to "Favourites",
            ).forEach { (value, label) ->
                KittyChip(label, selected = sort == value) {
                    sort = value
                    runSearch(query)
                }
            }
            val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            (thisYear downTo thisYear - 4).forEach { year ->
                KittyChip(year.toString(), selected = seasonYear == year) {
                    seasonYear = if (seasonYear == year) null else year
                    runSearch(query)
                }
            }
            listOf("JP" to "Japan", "KR" to "Korea", "CN" to "China").forEach { (value, label) ->
                KittyChip(label, selected = country == value) { toggle(country, value) { country = it } }
            }
            listOf("Isekai", "School", "Mecha", "Supernatural", "Psychological").forEach { value ->
                KittyChip(value, selected = tag == value) { toggle(tag, value) { tag = it } }
            }
            if (filtersOn()) {
                KittyChip("Clear", selected = true) {
                    format = null
                    airingStatus = null
                    season = null
                    genre = null
                    tag = null
                    seasonYear = null
                    country = null
                    sort = null
                    runSearch(query)
                }
            }
        }
        if (recent.isNotEmpty() && query.isBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Recent",
                color = KittyMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recent.forEach { q ->
                    KittyChip(q, selected = false) {
                        query = q
                        runSearch(q)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KittyAccent)
            }
            error != null && items.isEmpty() -> Text(error!!, color = MaterialTheme.colorScheme.error)
            items.isNotEmpty() -> Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${items.size} result${if (items.size == 1) "" else "s"}",
                        color = KittyFaint,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    KittyChip(if (asGrid) "List" else "Grid", selected = asGrid) { asGrid = !asGrid }
                }
                if (asGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        gridItems(items, key = { it.id }) { anime ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onOpen(anime)
                                    },
                            ) {
                                AsyncImage(
                                    model = anime.coverImageUrl,
                                    contentDescription = anime.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    anime.title,
                                    color = KittyText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                listMarks[anime.id]?.let { badge ->
                                    Text(
                                        badge,
                                        color = KittyFaint,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        lazyItems(items, key = { it.id }) { anime ->
                            KittySearchRow(anime, badge = listMarks[anime.id]) {
                                onOpen(anime)
                            }
                        }
                    }
                }
            }
            (query.trim().length >= 2 || filtersOn()) && items.isEmpty() && !loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(KittySurface)
                        .border(1.dp, KittyOutlineSoft, RoundedCornerShape(16.dp))
                        .padding(18.dp),
                ) {
                    Text(
                        "No titles matched “${query.trim()}”",
                        color = KittyText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Try another spelling or a shorter title.",
                        color = KittyMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            else -> {
                KittySectionHeading(
                    title = if (query.isBlank()) "Trending picks" else "Keep typing…",
                    subtitle = if (query.isBlank()) {
                        "Tap a poster to open"
                    } else {
                        "Need at least 2 characters"
                    },
                    padded = false,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    lazyItems(discover, key = { it.id }) { anime ->
                        KittySearchRow(anime, badge = listMarks[anime.id]) {
                            onOpen(anime)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KittySearchRow(
    anime: AnimeSummary,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0C0C10))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = anime.coverImageUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141416)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                anime.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                anime.format,
                anime.seasonYear?.toString(),
                anime.episodes?.let { "$it eps" },
                anime.scoreLabel(),
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(meta, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1)
            }
            if (!badge.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    badge,
                    color = KittyOnAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(KittyAccent)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun KittyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) KittyAccent else KittySurface)
            .border(1.dp, if (selected) KittyAccent else KittyOutlineSoft, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) KittyOnAccent else KittyText.copy(alpha = 0.82f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}
