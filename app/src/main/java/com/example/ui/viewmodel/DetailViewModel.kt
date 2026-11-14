package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.local.Comment
import com.example.data.model.Anime
import com.example.data.model.Episode
import com.example.data.model.getEstimatedEpisodesCount
import com.example.kitty.anilistIdOrNull
import com.example.kitty.toAnime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: JumpAnimeRepository) : ViewModel() {
    private var commentsCollectionJob: Job? = null
    private val _uiState = MutableStateFlow<DetailState>(DetailState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isInWatchlist = MutableStateFlow(false)
    val isInWatchlist = _isInWatchlist.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments = _isLoadingComments.asStateFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes = _isLoadingEpisodes.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent = _errorEvent.asStateFlow()

    fun clearErrorEvent() {
        _errorEvent.value = null
    }

    fun isLoggedIn(): Boolean = repository.kitty.account.isLinked
    fun getLoggedUsername(): String = repository.kitty.account.username ?: "Guest"
    fun isAdmin(): Boolean = repository.sessionManager.isAdmin()

    fun loadComments(animeId: String) {
        commentsCollectionJob?.cancel()
        // Coroutine 1: observe Room cache live
        commentsCollectionJob = viewModelScope.launch {
            repository.getComments(animeId).collectLatest { _comments.value = it }
        }
        // Coroutine 2: fetch fresh data from server
        viewModelScope.launch {
            _isLoadingComments.value = true
            try {
                repository.fetchAndCacheComments(animeId)
            } catch (e: Exception) {
                // Network failure — cached data still shown
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun postComment(animeId: String, text: String, parentId: String? = null) {
        viewModelScope.launch {
            try {
                repository.addComment(animeId, text, parentId)
            } catch (e: Exception) {
                _errorEvent.value = "Couldn't post comment: ${e.message ?: "check your connection"}"
            }
        }
    }

    fun deleteComment(commentId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteComment(commentId)
            } catch (e: Exception) {
                _errorEvent.value = "Couldn't delete comment: ${e.message ?: "check your connection"}"
            }
        }
    }

    fun toggleCommentLike(commentId: Int, isLiked: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleCommentLike(commentId, isLiked)
            } catch (e: Exception) {
                _errorEvent.value = "Couldn't update like"
            }
        }
    }

    private suspend fun populateJikanOrSimulatedEpisodes(anime: Anime) {
        _isLoadingEpisodes.value = true
        try {
            if (anime._id.startsWith(com.example.kitty.KITTY_ID_PREFIX)) {
                _episodes.value = repository.kitty.episodesFor(anime._id)
                return
            }
            val malId = anime.malId?.toIntOrNull()
            val realJikanEpisodes = if (malId != null) repository.getJikanEpisodes(malId) else emptyList()
            val totalEp = getEstimatedEpisodesCount(anime.title, anime.episodesCount, anime.type, anime.malId, repository, anime.status)
            val statusLower = (anime.status ?: "").lowercase()
            val isNotYetAired = statusLower.contains("not yet") || statusLower.contains("upcoming") || statusLower.contains("not_yet")
            val isAiring = statusLower.contains("airing") || statusLower.contains("ongoing") || statusLower.contains("releasing")

            if (isNotYetAired) {
                _episodes.value = emptyList()
                return
            }

            if (realJikanEpisodes.isNotEmpty()) {
                val maxRealNum = realJikanEpisodes.maxOf { it.number }
                val parsedCount = anime.episodesCount
                val scheduleCount = if (malId != null) {
                    repository.getLatestAiredEpisodeFromSchedule(malId.toString())?.first ?: 0
                } else 0

                val targetMaxEp = if (isAiring) {
                    val limit = if (scheduleCount > 0) scheduleCount else maxRealNum
                    maxOf(limit, maxRealNum)
                } else {
                    val limit = if (parsedCount > maxRealNum) parsedCount else maxRealNum
                    maxOf(limit, maxRealNum)
                }

                val mergedEpisodes = realJikanEpisodes.toMutableList()
                if (targetMaxEp > maxRealNum) {
                    for (num in (maxRealNum + 1)..targetMaxEp) {
                        mergedEpisodes.add(
                            Episode(
                                _id = "mal_${malId}_ep_${num}",
                                anime = anime._id,
                                season = "simulated_season",
                                number = num,
                                title = "Episode $num",
                                thumbnail = anime.poster,
                                duration = "24 min"
                            )
                        )
                    }
                }
                _episodes.value = mergedEpisodes
            } else {
                val maxEpToSimulate = totalEp

                val simulated = (1..maxEpToSimulate).map { num ->
                    Episode(
                        _id = "${anime._id}_s_simulated_ep_$num",
                        anime = anime._id,
                        season = "simulated_season",
                        number = num,
                        title = "Episode $num",
                        thumbnail = anime.poster,
                        duration = "24 min"
                    )
                }
                _episodes.value = simulated
            }
        } finally {
            _isLoadingEpisodes.value = false
        }
    }

    fun loadAnime(animeId: String, onUnavailable: (() -> Unit)? = null) {
        val currentState = _uiState.value
        if (currentState is DetailState.Success && currentState.anime._id == animeId) {
            viewModelScope.launch {
                try {
                    val anime = repository.getAnimeDetail(animeId)
                    val watchState = repository.getLocalWatchState(animeId)
                    _uiState.value = DetailState.Success(anime, watchState?.lastEpisode)
                    checkWatchlist(anime._id)
                    loadComments(anime._id)
                    val firstSeasonId = anime.seasons.firstOrNull()?._id
                    if (firstSeasonId != null) {
                        loadEpisodes(firstSeasonId, anime)
                    } else {
                        populateJikanOrSimulatedEpisodes(anime)
                    }
                } catch (e: Exception) {
                    // ignore background errors if we already have data
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = DetailState.Loading
            try {
                val anime = repository.getAnimeDetail(animeId)
                val watchState = repository.getLocalWatchState(animeId)
                _uiState.value = DetailState.Success(anime, watchState?.lastEpisode)
                checkWatchlist(anime._id)
                loadComments(anime._id)

                // If this anime has seasons, let's load episodes for the first season
                val firstSeasonId = anime.seasons.firstOrNull()?._id
                if (firstSeasonId != null) {
                    loadEpisodes(firstSeasonId, anime)
                } else {
                    populateJikanOrSimulatedEpisodes(anime)
                }
            } catch (e: Exception) {
                if (onUnavailable != null) {
                    onUnavailable()
                } else {
                    _uiState.value = DetailState.Error("Unable to load details right now. Please try again.")
                }
            }
        }
    }

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes = _episodes.asStateFlow()

    private val _recommendations = MutableStateFlow<List<Anime>>(emptyList())
    val recommendations = _recommendations.asStateFlow()

    private val _nextSeasons = MutableStateFlow<List<Anime>>(emptyList())
    val nextSeasons = _nextSeasons.asStateFlow()

    /** AniList detail (studios, staff, characters, trailer ...) for "al_" titles. */
    private val _kittyDetail = MutableStateFlow<com.example.kitty.anilist.AnimeSummary?>(null)
    val kittyDetail = _kittyDetail.asStateFlow()

    fun loadKittyExtras(animeId: String) {
        val anilistId = animeId.anilistIdOrNull() ?: return
        viewModelScope.launch {
            val detail = try { repository.kitty.detail(anilistId) } catch (e: Exception) { null } ?: return@launch
            _kittyDetail.value = detail
            _recommendations.value = detail.recommendations.map { rec -> rec.toAnime() }
            _nextSeasons.value = repository.kitty.related(detail)
        }
    }

    fun loadEpisodes(seasonId: String, anime: Anime? = null) {
        _isLoadingEpisodes.value = true
        viewModelScope.launch {
            try {
                val realEpisodes = repository.getEpisodes(seasonId)
                val currentAnime = anime ?: (uiState.value as? DetailState.Success)?.anime
                if (currentAnime != null) {
                    val malId = currentAnime.malId
                    val statusLower = (currentAnime.status ?: "").lowercase()
                    val isNotYetAired = statusLower.contains("not yet") || statusLower.contains("upcoming") || statusLower.contains("not_yet")
                    val isAiring = statusLower.contains("airing") || statusLower.contains("ongoing") || statusLower.contains("releasing")

                    if (isNotYetAired) {
                        _episodes.value = emptyList()
                    } else {
                        val parsedCount = currentAnime.episodesCount
                        val scheduleCount = if (malId != null) {
                            repository.getLatestAiredEpisodeFromSchedule(malId)?.first ?: 0
                        } else 0

                        if (isAiring) {
                            if (realEpisodes.isEmpty()) {
                                val totalEp = getEstimatedEpisodesCount(
                                    currentAnime.title,
                                    parsedCount,
                                    currentAnime.type,
                                    currentAnime.malId,
                                    repository,
                                    currentAnime.status
                                )
                                _episodes.value = (1..totalEp).map { num ->
                                    Episode(
                                        _id = "${currentAnime._id}_s_${seasonId}_ep_$num",
                                        anime = currentAnime._id,
                                        season = seasonId,
                                        number = num,
                                        title = "Episode $num",
                                        thumbnail = currentAnime.poster,
                                        duration = "24 min"
                                    )
                                }
                            } else {
                                _episodes.value = realEpisodes
                            }
                        } else {
                            if (realEpisodes.isEmpty()) {
                                val totalEp = getEstimatedEpisodesCount(
                                    currentAnime.title,
                                    parsedCount,
                                    currentAnime.type,
                                    currentAnime.malId,
                                    repository,
                                    currentAnime.status
                                )
                                val maxEpToSimulate = totalEp

                                _episodes.value = (1..maxEpToSimulate).map { num ->
                                    Episode(
                                        _id = "${currentAnime._id}_s_${seasonId}_ep_$num",
                                        anime = currentAnime._id,
                                        season = seasonId,
                                        number = num,
                                        title = "Episode $num",
                                        thumbnail = currentAnime.poster,
                                        duration = "24 min"
                                    )
                                }
                            } else {
                                val maxRealNum = realEpisodes.maxOf { it.number }
                                val totalEp = getEstimatedEpisodesCount(
                                    currentAnime.title,
                                    parsedCount,
                                    currentAnime.type,
                                    currentAnime.malId,
                                    repository,
                                    currentAnime.status
                                )
                                val targetMaxEp = maxOf(totalEp, maxRealNum)

                                if (targetMaxEp > maxRealNum) {
                                    val merged = realEpisodes.toMutableList()
                                    for (num in (maxRealNum + 1)..targetMaxEp) {
                                        merged.add(
                                            Episode(
                                                _id = "mal_${currentAnime.malId ?: ""}_ep_${num}",
                                                anime = currentAnime._id,
                                                season = seasonId,
                                                number = num,
                                                title = "Episode $num",
                                                thumbnail = currentAnime.poster,
                                                duration = "24 min"
                                            )
                                        )
                                    }
                                    _episodes.value = merged
                                } else {
                                    _episodes.value = realEpisodes
                                }
                            }
                        }
                    }
                } else {
                    _episodes.value = realEpisodes
                }
            } catch (e: Exception) {
                _episodes.value = emptyList()
            } finally {
                _isLoadingEpisodes.value = false
            }
        }
    }

    fun loadRecommendations(malId: Int) {
        viewModelScope.launch {
            try {
                _recommendations.value = repository.getRecommendations(malId)
            } catch (e: Exception) {
                _recommendations.value = emptyList()
            }
        }
    }

    fun loadNextSeasons(malId: Int, title: String) {
        viewModelScope.launch {
            try {
                _nextSeasons.value = repository.getNextSeasonsOrRelatedParts(malId, title)
            } catch (e: Exception) {
                _nextSeasons.value = emptyList()
            }
        }
    }

    private fun checkWatchlist(animeId: String) {
        viewModelScope.launch {
            try {
                _isInWatchlist.value = repository.isInWatchlist(animeId)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun toggleWatchlist(anime: Anime) {
        viewModelScope.launch {
            try {
                if (_isInWatchlist.value) {
                    repository.removeFromWatchlist(anime._id)
                    _isInWatchlist.value = false
                } else {
                    repository.addToWatchlist(anime)
                    _isInWatchlist.value = true
                }
            } catch (e: Exception) {
                _errorEvent.value = "Couldn't update watchlist, check your connection"
            }
        }
    }

    fun markAsWatched(animeId: String, episode: Int) {
        viewModelScope.launch {
            try {
                repository.saveLocalWatchState(animeId, episode)
                // Reload to update UI
                val currentState = _uiState.value
                if (currentState is DetailState.Success) {
                    _uiState.value = currentState.copy(lastEpisode = episode)
                }
            } catch (e: Exception) {
            }
        }
    }
}

sealed interface DetailState {
    data object Loading : DetailState
    data class Success(val anime: Anime, val lastEpisode: Int? = null) : DetailState
    data class Error(val message: String) : DetailState
}
