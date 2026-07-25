package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.model.Episode
import com.example.data.model.getEstimatedEpisodesCount
import com.example.data.model.PlaybackHistoryRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VideoSource(val label: String) {
    SUB("AnimePlay"),
    DUB("AnimePlay"),
    ALT_SUB("MegaPlay"),
    ALT_DUB("MegaPlay"),
    BACKUP("Backup")
}

class PlayerViewModel(private val repository: JumpAnimeRepository) : ViewModel() {
    private var commentsCollectionJob: Job? = null
    private val _uiState = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedSource = MutableStateFlow(VideoSource.ALT_SUB)
    val selectedSource = _selectedSource.asStateFlow()

    private val _isChangingEpisode = MutableStateFlow(false)
    val isChangingEpisode = _isChangingEpisode.asStateFlow()

    private val prefetchedEpisodes = java.util.concurrent.ConcurrentHashMap<String, Episode>()

    fun setSource(source: VideoSource) {
        _selectedSource.value = source
    }

    fun prefetchNextEpisode(seasonId: String, episodeNumber: Int) {
        if (seasonId == "simulated_season" || seasonId.isBlank()) return
        val cacheKey = "${seasonId}_${episodeNumber}"
        if (prefetchedEpisodes.containsKey(cacheKey)) return

        viewModelScope.launch {
            try {
                val nextEpisode = repository.getEpisode(seasonId, episodeNumber)
                prefetchedEpisodes[cacheKey] = nextEpisode
                android.util.Log.d("PlayerViewModel", "Prefetch: Successfully cached episode $episodeNumber for season $seasonId")
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Prefetch: Failed to cache episode $episodeNumber: ${e.message}")
            }
        }
    }

    fun loadEpisode(
        animeId: String,
        malId: String,
        seasonId: String,
        episodeNumber: Int,
        animeTitle: String,
        episodeTitle: String,
        animePoster: String
    ) {
        loadComments(animeId)
        viewModelScope.launch {
            val isCurrentlySuccess = _uiState.value is PlayerState.Success
            if (isCurrentlySuccess) {
                _isChangingEpisode.value = true
            } else {
                _uiState.value = PlayerState.Loading
            }
            try {
                // Fetch the anime detail to ensure we have total episodes count and proper seasons
                val animeDetail = try {
                    repository.getAnimeDetail(animeId)
                } catch (e: Exception) {
                    null
                }

                val finalMalId = if (malId.isBlank()) {
                    animeDetail?.malId ?: ""
                } else {
                    malId
                }

                val finalSeasonId = if (seasonId == "simulated_season" || seasonId.isBlank()) {
                    animeDetail?.seasons?.firstOrNull()?._id ?: "simulated_season"
                } else {
                    seasonId
                }

                if (finalMalId.isBlank()) {
                    _uiState.value = PlayerState.Error("Direct malId not found for this anime. Playback unavailable.")
                    return@launch
                }

                // Load the episode detail for general info, checking the prefetch cache first
                val episode = try {
                    val cacheKey = "${finalSeasonId}_${episodeNumber}"
                    prefetchedEpisodes[cacheKey] ?: if (finalSeasonId != "simulated_season" && finalSeasonId.isNotBlank()) {
                        repository.getEpisode(finalSeasonId, episodeNumber)
                    } else {
                        Episode(_id = "", number = episodeNumber, title = episodeTitle)
                    }
                } catch (e: Exception) {
                    // Fallback to a spoofed episode if API fails but we have numbers
                    Episode(_id = "", number = episodeNumber, title = episodeTitle)
                }

                // Load all episodes for the bottom list
                val finalMalIdInt = finalMalId.toIntOrNull()
                var episodes = try {
                    if (finalSeasonId != "simulated_season" && finalSeasonId.isNotBlank()) {
                        repository.getEpisodes(finalSeasonId)
                    } else if (finalMalIdInt != null) {
                        repository.getJikanEpisodes(finalMalIdInt)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                // If loaded episodes is empty, simulate them so that the user has navigation
                val statusLower = (animeDetail?.status ?: "").lowercase()
                val isNotYetAired = statusLower.contains("not yet") || statusLower.contains("upcoming") || statusLower.contains("not_yet")
                val isAiring = statusLower.contains("airing") || statusLower.contains("ongoing") || statusLower.contains("releasing")
                
                if (isNotYetAired) {
                    episodes = emptyList()
                } else {
                    val parsedCount = animeDetail?.episodesCount ?: 0
                    val scheduleCount = if (finalMalId.isNotBlank()) {
                        repository.getLatestAiredEpisodeFromSchedule(finalMalId)?.first ?: 0
                    } else 0
                    
                    if (episodes.isEmpty()) {
                        val totalEp = getEstimatedEpisodesCount(
                            animeTitle,
                            parsedCount,
                            animeDetail?.type,
                            animeDetail?.malId ?: finalMalId,
                            repository,
                            animeDetail?.status
                        )
                        val maxEpToSimulate = maxOf(totalEp, episodeNumber)
                        
                        episodes = (1..maxEpToSimulate).map { num ->
                            Episode(
                                _id = "${animeId}_s_${finalSeasonId}_ep_$num",
                                anime = animeId,
                                season = finalSeasonId,
                                number = num,
                                title = "Episode $num",
                                thumbnail = animePoster.ifBlank { animeDetail?.poster },
                                duration = "24 min"
                            )
                        }
                    } else {
                        val maxRealNum = episodes.maxOf { it.number }
                        val totalEp = getEstimatedEpisodesCount(
                            animeTitle,
                            parsedCount,
                            animeDetail?.type,
                            animeDetail?.malId ?: finalMalId,
                            repository,
                            animeDetail?.status
                        )
                        val targetMaxEp = maxOf(totalEp, maxRealNum, episodeNumber)
                        
                        if (targetMaxEp > maxRealNum) {
                            val merged = episodes.toMutableList()
                            for (num in (maxRealNum + 1)..targetMaxEp) {
                                merged.add(
                                    Episode(
                                        _id = "mal_${animeDetail?.malId ?: ""}_ep_${num}",
                                        anime = animeId,
                                        season = finalSeasonId,
                                        number = num,
                                        title = "Episode $num",
                                        thumbnail = animePoster.ifBlank { animeDetail?.poster },
                                        duration = "24 min"
                                    )
                                )
                            }
                            episodes = merged
                        }
                    }
                }

                val savedState = repository.getLocalWatchState(animeId)
                val initialProgress = if (savedState != null && savedState.lastEpisode == episodeNumber) {
                    savedState.progress
                } else {
                    0f
                }

                _uiState.value = PlayerState.Success(episode, episodes, finalMalId, initialProgress)

                // Prefetch the next episode's metadata in the background
                try {
                    val currentEpisodeNum = episodeNumber
                    val sortedEpisodes = episodes.sortedBy { it.number }
                    val nextEpisodeIndex = sortedEpisodes.indexOfFirst { it.number == currentEpisodeNum } + 1
                    if (nextEpisodeIndex in sortedEpisodes.indices) {
                        val nextEp = sortedEpisodes[nextEpisodeIndex]
                        prefetchNextEpisode(finalSeasonId, nextEp.number)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error triggering metadata prefetch: ${e.message}")
                }
                
                // Save history
                repository.savePlaybackHistory(
                    PlaybackHistoryRequest(
                        animeId = animeId,
                        malId = finalMalId,
                        seasonId = finalSeasonId,
                        episodeNumber = episodeNumber,
                        episodeTitle = episodeTitle,
                        animeTitle = animeTitle,
                        animePoster = animePoster
                    )
                )
                
                // Also update local watch state
                repository.saveLocalWatchState(animeId, episodeNumber)

            } catch (e: Exception) {
                _uiState.value = PlayerState.Error("Unable to load the episode right now. Please try again.")
            } finally {
                _isChangingEpisode.value = false
            }
        }
    }

    private var lastSavedProgress = -1f
    private var lastSaveTime = 0L

    fun updateProgress(
        animeId: String,
        malId: String,
        seasonId: String,
        episodeNumber: Int,
        episodeTitle: String,
        animeTitle: String,
        animePoster: String,
        progress: Float
    ) {
        val currentTime = System.currentTimeMillis()
        // Save at most once every 3 seconds to avoid overloading SQLite/API, or immediately if progress reaches 100% or is exactly 0%
        if (progress != lastSavedProgress && (progress >= 0.99f || progress <= 0.01f || (currentTime - lastSaveTime > 3000))) {
            lastSavedProgress = progress
            lastSaveTime = currentTime
            
            viewModelScope.launch {
                try {
                    repository.savePlaybackHistory(
                        PlaybackHistoryRequest(
                            animeId = animeId,
                            malId = malId,
                            seasonId = seasonId,
                            episodeNumber = episodeNumber,
                            episodeTitle = episodeTitle,
                            animeTitle = animeTitle,
                            animePoster = animePoster,
                            progress = progress
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error saving progress in updateProgress: ${e.message}")
                }
            }
        }
    }

    private val _comments = MutableStateFlow<List<com.example.data.local.Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments = _isLoadingComments.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent = _errorEvent.asStateFlow()

    fun clearErrorEvent() {
        _errorEvent.value = null
    }

    fun isLoggedIn(): Boolean = repository.sessionManager.isLoggedIn()
    fun getLoggedUsername(): String = repository.sessionManager.getUsername() ?: "Guest"
    fun isAdmin(): Boolean = repository.sessionManager.isAdmin()
    fun isPreloadEnabled(): Boolean = repository.sessionManager.isPreloadEnabled()

    fun loadComments(animeId: String) {
        commentsCollectionJob?.cancel()
        // Coroutine 1: observe Room cache live
        commentsCollectionJob = viewModelScope.launch {
            repository.getComments(animeId).collect {
                _comments.value = it
            }
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

    fun recordWatchCount(animeId: String) {
        viewModelScope.launch {
            repository.recordWatchCount(animeId)
        }
    }
}

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Success(val episode: Episode, val episodes: List<Episode>, val malId: String, val initialProgress: Float = 0f) : PlayerState
    data class Error(val message: String) : PlayerState
}
