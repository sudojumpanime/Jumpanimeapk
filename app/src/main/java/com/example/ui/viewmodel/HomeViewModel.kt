package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.model.Anime
import com.example.data.model.ContinueWatchingItem
import com.example.data.local.AnimeWatchState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

class HomeViewModel(private val repository: JumpAnimeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeState>(HomeState.Loading)
    val uiState = _uiState.asStateFlow()

    val isInitialLoadCompleted = MutableStateFlow(false)

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _recommendedForYou = MutableStateFlow<List<Anime>>(emptyList())
    val recommendedForYou = _recommendedForYou.asStateFlow()

    private val _recommendedBasedOn = MutableStateFlow<String>("")
    val recommendedBasedOn = _recommendedBasedOn.asStateFlow()

    private var fetchJob: Job? = null

    init {
        refresh()
    }

    fun setGenre(genre: String?) {
        _selectedGenre.value = genre
        refresh()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        // Debounce search query changes to make typing snappy and save api calls
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            delay(300)
            performFetch()
        }
    }

    fun refresh() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            performFetch()
        }
    }

    private suspend fun performFetch() {
        val currentState = _uiState.value
        val hasData = currentState is HomeState.Success
        
        // 1. Instantly load local cache if we do not already have success data
        if (!hasData) {
            try {
                val continueWatching = try {
                    repository.getContinueWatching()
                        .distinctBy { it.anime }
                        .sortedByDescending { it.watchedAt }
                } catch (e: Exception) {
                    emptyList()
                }
                val localStates = repository.getAllLocalStates().first().associateBy({ it.animeId }, { it.lastEpisode })
                
                // Read local cached lists
                val cachedTrending = repository.getAllCachedAnime().filter { it.trending }.take(5)
                val cachedFeatured = repository.getAllCachedAnime().filter { it.featured }.take(10)
                val cachedRecently = repository.getAllCachedAnime().filter { it.recentlyAdded }.take(15)
                val cachedMostPopular = repository.getMostPopularCached().take(15)
                val cachedPopularThisMonth = repository.getPopularThisMonthCached().take(15)
                val cachedPopularThisWeek = repository.getPopularThisWeekCached().take(15)
                val cachedAnilab2Data = repository.getAnilab2DataCached().take(15)
                
                if (cachedTrending.isNotEmpty() || cachedFeatured.isNotEmpty() || cachedRecently.isNotEmpty() ||
                    cachedMostPopular.isNotEmpty() || cachedPopularThisMonth.isNotEmpty() || cachedPopularThisWeek.isNotEmpty() || cachedAnilab2Data.isNotEmpty()) {
                    _uiState.value = HomeState.Success(
                        trending = cachedTrending,
                        featured = cachedFeatured,
                        recentlyAdded = cachedRecently,
                        mostPopular = cachedMostPopular,
                        popularThisMonth = cachedPopularThisMonth,
                        popularThisWeek = cachedPopularThisWeek,
                        anilab2Data = cachedAnilab2Data,
                        continueWatching = continueWatching,
                        watchStates = localStates,
                        searchResults = null
                    )
                } else {
                    _uiState.value = HomeState.Loading
                }
            } catch (e: Exception) {
                _uiState.value = HomeState.Loading
            }
        }
        
        // 2. Perform background/online network fetch and update in-place smoothly
        try {
            val search = if (searchQuery.value.isNotBlank()) searchQuery.value.trim() else null
            val continueWatching = try {
                repository.getContinueWatching()
                    .distinctBy { it.anime }
                    .sortedByDescending { it.watchedAt }
            } catch (e: Exception) {
                emptyList()
            }
            
            // Load recommendations based on the most recently watched anime
            try {
                val mostRecent = continueWatching.firstOrNull()
                if (mostRecent != null) {
                    var malIdInt = mostRecent.malId?.toIntOrNull()
                        ?: mostRecent.anime.replace("mal_", "").toIntOrNull()
                    if (malIdInt == null) {
                        try {
                            val detail = repository.getAnimeDetail(mostRecent.anime)
                            malIdInt = detail.malId?.toIntOrNull()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    if (malIdInt != null) {
                        val recs = repository.getRecommendations(malIdInt)
                        _recommendedForYou.value = recs
                        _recommendedBasedOn.value = mostRecent.animeTitle
                    } else {
                        _recommendedForYou.value = emptyList()
                        _recommendedBasedOn.value = ""
                    }
                } else {
                    _recommendedForYou.value = emptyList()
                    _recommendedBasedOn.value = ""
                }
            } catch (e: Exception) {
                _recommendedForYou.value = emptyList()
                _recommendedBasedOn.value = ""
            }

            val localStates = repository.getAllLocalStates().first().associateBy({ it.animeId }, { it.lastEpisode })
            
            if (search != null) {
                // First, instantly find and show matching local cached anime
                val localMatches = try {
                    repository.getAllCachedAnime().filter { 
                        it.title.contains(search, ignoreCase = true) || 
                        (it.description != null && it.description.contains(search, ignoreCase = true))
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                
                if (localMatches.isNotEmpty()) {
                    _uiState.value = HomeState.Success(
                        trending = emptyList(),
                        featured = emptyList(),
                        recentlyAdded = emptyList(),
                        mostPopular = emptyList(),
                        popularThisMonth = emptyList(),
                        popularThisWeek = emptyList(),
                        anilab2Data = emptyList(),
                        continueWatching = continueWatching,
                        watchStates = localStates,
                        searchResults = localMatches.distinctBy { it._id }
                    )
                }

                val response = repository.searchAnime(search, selectedGenre.value)
                _uiState.value = HomeState.Success(
                    trending = emptyList(),
                    featured = emptyList(),
                    recentlyAdded = emptyList(),
                    mostPopular = emptyList(),
                    popularThisMonth = emptyList(),
                    popularThisWeek = emptyList(),
                    anilab2Data = emptyList(),
                    continueWatching = continueWatching,
                    watchStates = localStates,
                    searchResults = response.animes.distinctBy { it._id }
                )
            } else {
                // Fetch in parallel using async coroutines for maximum load speed
                val trendingJob = viewModelScope.async {
                    try { repository.getTrendingAndFeatured(selectedGenre.value, null) } catch (e: Exception) { Pair(emptyList<Anime>(), emptyList<Anime>()) }
                }
                val recentlyAddedJob = viewModelScope.async {
                    try { repository.getRecentlyAdded(selectedGenre.value) } catch (e: Exception) { emptyList<Anime>() }
                }
                val mostPopularJob = viewModelScope.async {
                    try { repository.fetchAndCacheMostPopular() } catch (e: Exception) { emptyList<Anime>() }
                }
                val popularThisMonthJob = viewModelScope.async {
                    try { repository.fetchAndCachePopularThisMonth() } catch (e: Exception) { emptyList<Anime>() }
                }
                val popularThisWeekJob = viewModelScope.async {
                    try { repository.fetchAndCachePopularThisWeek() } catch (e: Exception) { emptyList<Anime>() }
                }
                val anilab2DataJob = viewModelScope.async {
                    try { repository.fetchAndCacheAnilab2Data() } catch (e: Exception) { emptyList<Anime>() }
                }

                val (trending, featured) = trendingJob.await()
                val recentlyAdded = recentlyAddedJob.await()
                val mostPopular = mostPopularJob.await()
                val popularThisMonth = popularThisMonthJob.await()
                val popularThisWeek = popularThisWeekJob.await()
                val anilab2Data = anilab2DataJob.await()
                
                _uiState.value = HomeState.Success(
                    trending = trending.distinctBy { it._id },
                    featured = featured.distinctBy { it._id },
                    recentlyAdded = recentlyAdded.distinctBy { it._id },
                    mostPopular = mostPopular.distinctBy { it._id },
                    popularThisMonth = popularThisMonth.distinctBy { it._id },
                    popularThisWeek = popularThisWeek.distinctBy { it._id },
                    anilab2Data = anilab2Data.distinctBy { it._id },
                    continueWatching = continueWatching,
                    watchStates = localStates,
                    searchResults = null
                )
            }
        } catch (e: Exception) {
            // Only show full-screen error if we don't have any data loaded from cache
            if (_uiState.value !is HomeState.Success) {
                _uiState.value = HomeState.Error("Unable to load content right now. Please refresh or try again.")
            }
        } finally {
            isInitialLoadCompleted.value = true
        }
    }

    fun markAsWatched(animeId: String, episode: Int) {
        viewModelScope.launch {
            try {
                repository.saveLocalWatchState(animeId, episode)
                refresh()
            } catch (e: Exception) {
            }
        }
    }

    fun toggleWatchlist(anime: Anime, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                if (repository.isInWatchlist(anime._id)) {
                    repository.removeFromWatchlist(anime._id)
                } else {
                    repository.addToWatchlist(anime)
                }
                onComplete()
                refresh()
            } catch (e: Exception) {
            }
        }
    }

    suspend fun isInWatchlist(animeId: String): Boolean {
        return repository.isInWatchlist(animeId)
    }
}

sealed interface HomeState {
    data object Loading : HomeState
    data class Success(
        val trending: List<Anime>,
        val featured: List<Anime>,
        val recentlyAdded: List<Anime>,
        val mostPopular: List<Anime>,
        val popularThisMonth: List<Anime>,
        val popularThisWeek: List<Anime>,
        val anilab2Data: List<Anime>,
        val continueWatching: List<ContinueWatchingItem>,
        val watchStates: Map<String, Int> = emptyMap(),
        val searchResults: List<Anime>? = null
    ) : HomeState
    data class Error(val message: String) : HomeState
}
