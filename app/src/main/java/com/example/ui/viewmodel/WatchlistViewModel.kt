package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.model.Anime
import com.example.data.local.AnimeWatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

class WatchlistViewModel(private val repository: JumpAnimeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<WatchlistState>(WatchlistState.Loading)
    val uiState = _uiState.asStateFlow()

    val isInitialLoadCompleted = MutableStateFlow(false)

    init {
        observeWatchlist()
        observeWatchHistory()
    }

    private fun observeWatchHistory() {
        viewModelScope.launch {
            repository.getAllLocalStates().collect { list ->
                _watchHistory.value = list
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            combine(
                repository.getWatchlistFlow(),
                repository.getAllLocalStates()
            ) { list, states ->
                val localStatesMap = states.associateBy({ it.animeId }, { it.lastEpisode })
                WatchlistState.Success(list, localStatesMap)
            }.onStart {
                refreshWatchlist()
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun refreshWatchlist() {
        viewModelScope.launch {
            try {
                repository.getWatchlist()
            } catch (e: Exception) {
                if (_uiState.value is WatchlistState.Loading) {
                    _uiState.value = WatchlistState.Error("Unable to load watchlist right now. Please try again.")
                }
            } finally {
                isInitialLoadCompleted.value = true
            }
        }
    }

    fun loadWatchlist() = refreshWatchlist()

    private val _watchHistory = MutableStateFlow<List<AnimeWatchState>>(emptyList())
    val watchHistory = _watchHistory.asStateFlow()

    fun deleteWatchHistoryItem(animeId: String) {
        viewModelScope.launch {
            repository.deleteLocalWatchState(animeId)
        }
    }

    private val _selectedAnimeDetail = MutableStateFlow<Anime?>(null)
    val selectedAnimeDetail = _selectedAnimeDetail.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading = _isDetailLoading.asStateFlow()

    fun fetchDetailAndSet(anime: Anime) {
        _selectedAnimeDetail.value = anime // Set summary first
        viewModelScope.launch {
            _isDetailLoading.value = true
            try {
                val full = repository.getAnimeDetail(anime._id)
                _selectedAnimeDetail.value = full
            } catch (e: Exception) {
                // If it fails, we keep the summary
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun clearDetail() {
        _selectedAnimeDetail.value = null
        _isDetailLoading.value = false
    }

    fun removeFromWatchlist(animeId: String) {
        viewModelScope.launch {
            try {
                repository.removeFromWatchlist(animeId)
                loadWatchlist()
            } catch (e: Exception) {
            }
        }
    }

    fun markAsWatched(animeId: String, episode: Int) {
        viewModelScope.launch {
            try {
                repository.saveLocalWatchState(animeId, episode)
                // Update local state and refresh
                loadWatchlist()
            } catch (e: Exception) {
            }
        }
    }
}

sealed interface WatchlistState {
    data object Loading : WatchlistState
    data class Success(
        val list: List<Anime>,
        val watchStates: Map<String, Int> = emptyMap()
    ) : WatchlistState
    data class Error(val message: String) : WatchlistState
}
