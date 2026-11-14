package com.example.kitty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.model.ContinueWatchingItem
import com.example.kitty.KittyCatalog
import com.example.kitty.anilist.AiringSlot
import com.example.kitty.anilist.AnimeSummary
import com.example.kitty.kittyId
import com.example.kitty.theme.KittyInk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Home data, loaded the AniKitty way: three AniList feeds fetched in parallel and
 * shown as each arrives (AniListClient caches them, so reopening is instant).
 */
class KittyHomeViewModel(
    private val catalog: KittyCatalog,
    private val repository: JumpAnimeRepository,
) : ViewModel() {
    private val _trending = MutableStateFlow<List<AnimeSummary>>(emptyList())
    val trending: StateFlow<List<AnimeSummary>> = _trending.asStateFlow()
    private val _popular = MutableStateFlow<List<AnimeSummary>>(emptyList())
    val popular: StateFlow<List<AnimeSummary>> = _popular.asStateFlow()
    private val _seasonal = MutableStateFlow<List<AnimeSummary>>(emptyList())
    val seasonal: StateFlow<List<AnimeSummary>> = _seasonal.asStateFlow()
    private val _airingToday = MutableStateFlow<List<AiringSlot>>(emptyList())
    val airingToday: StateFlow<List<AiringSlot>> = _airingToday.asStateFlow()
    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** True once the first feed has arrived (or failed), so the splash screen can go away. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var busy = false

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    fun reloadContinueWatching() {
        viewModelScope.launch {
            runCatching { repository.getContinueWatching() }
                .onSuccess { _continueWatching.value = it.take(12) }
        }
    }

    private fun load(force: Boolean) {
        if (busy) return
        busy = true
        _error.value = null
        if (force) _refreshing.value = true else _loading.value = true
        if (force) catalog.client.invalidateCache()
        reloadContinueWatching()

        val pending = java.util.concurrent.atomic.AtomicInteger(3)
        fun done() {
            if (pending.decrementAndGet() == 0) {
                _loading.value = false
                _refreshing.value = false
                _ready.value = true
                busy = false
            }
        }
        fun feed(block: suspend () -> List<AnimeSummary>, sink: MutableStateFlow<List<AnimeSummary>>, isMain: Boolean) {
            viewModelScope.launch {
                runCatching { block() }
                    .onSuccess { sink.value = catalog.remember(it) }
                    .onFailure { if (isMain) _error.value = it.message ?: "Failed to load" }
                if (isMain) {
                    _loading.value = false
                    _ready.value = true
                }
                done()
            }
        }
        feed({ catalog.client.trending(forceRefresh = force) }, _trending, isMain = true)
        feed({ catalog.client.popular(forceRefresh = force) }, _popular, isMain = false)
        feed({ catalog.client.seasonal(forceRefresh = force) }, _seasonal, isMain = false)

        viewModelScope.launch {
            val from = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis / 1000L
            _airingToday.value = runCatching {
                catalog.client.airingSchedule(from, from + 24 * 60 * 60)
            }.getOrDefault(emptyList())
        }
    }
}

/** JumpAnime's Home, laid out like AniKitty's: hero, genres, airing today, trending, top 10, most popular, continue watching. */
@Composable
fun KittyHomeScreen(
    viewModel: KittyHomeViewModel,
    catalog: KittyCatalog,
    displayName: String,
    onAnimeClick: (String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onSearchClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
    val trending by viewModel.trending.collectAsStateWithLifecycle()
    val popular by viewModel.popular.collectAsStateWithLifecycle()
    val seasonal by viewModel.seasonal.collectAsStateWithLifecycle()
    val airingToday by viewModel.airingToday.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    fun open(anime: AnimeSummary) {
        catalog.remember(listOf(anime))
        onAnimeClick(kittyId(anime.id))
    }

    val busy = loading || refreshing

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading && trending.isEmpty() -> LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    HomeWelcomeHeader(
                        displayName = displayName,
                        onRefresh = { viewModel.refresh() },
                        refreshBusy = busy,
                        onOpenSearch = onSearchClick,
                    )
                }
                item { KittyPosterSkeletonRow("Trending Anime") }
                item { KittyPosterSkeletonRow("Most popular") }
            }

            error != null && trending.isEmpty() -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                HomeWelcomeHeader(
                    displayName = displayName,
                    onRefresh = { viewModel.refresh() },
                    refreshBusy = busy,
                    onOpenSearch = onSearchClick,
                )
                Spacer(modifier = Modifier.height(28.dp))
                KittyEmptyState(
                    title = "Couldn’t load the catalog",
                    body = error ?: "Check your connection and try again.",
                    actionLabel = "Retry",
                    onAction = { viewModel.refresh() },
                )
            }

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    HomeWelcomeHeader(
                        displayName = displayName,
                        onRefresh = { viewModel.refresh() },
                        refreshBusy = busy,
                        onOpenSearch = onSearchClick,
                    )
                }
                item { KittyGlassHeroPager(items = trending, onOpen = ::open) }
                item {
                    val tiles = listOf("Action", "Romance", "Fantasy", "Comedy", "Drama", "Adventure")
                        .map { name ->
                            name to (trending + popular + seasonal)
                                .firstOrNull { name in it.genres }
                                ?.let { it.bannerImageUrl ?: it.coverImageUrl }
                        }
                    KittyGenreTiles(
                        genres = tiles,
                        selected = null,
                        onSelect = { onSearchClick() },
                    )
                }
                if (airingToday.isNotEmpty()) {
                    item {
                        TodayAiringRail(slots = airingToday, onMore = onScheduleClick, onOpen = ::open)
                    }
                }
                item {
                    KittyPosterRow(title = "Trending Anime", items = trending, onMore = onSearchClick, onOpen = ::open)
                }
                item { KittyTopTenRow(title = "Top 10", items = popular, onOpen = ::open) }
                item {
                    KittyPosterRow(title = "Most popular", items = popular, onMore = onSearchClick, onOpen = ::open)
                }
                if (seasonal.isNotEmpty()) {
                    item {
                        KittyPosterRow(title = "Top airing now", items = seasonal, onMore = onSearchClick, onOpen = ::open)
                    }
                }
                if (continueWatching.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                            KittySectionHeading(title = "Continue watching")
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(continueWatching) { entry ->
                                    KittyContinueCard(
                                        title = entry.animeTitle,
                                        coverUrl = entry.animePoster,
                                        episode = entry.episodeNumber,
                                        positionMs = (entry.progress.coerceIn(0f, 1f) * 1000f).toLong(),
                                        durationMs = 1000L,
                                        badge = "Resume",
                                        onClick = { onContinueWatchingClick(entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
