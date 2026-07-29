package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MangaRepository
import com.example.data.model.MangaDetail
import com.example.data.model.MangaLibraryEntry
import com.example.data.model.MangaPage
import com.example.data.model.MangaSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaViewModel(private val repository: MangaRepository) : ViewModel() {

    private val _browseState = MutableStateFlow<MangaBrowseState>(MangaBrowseState.Loading)
    val browseState = _browseState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _detailState = MutableStateFlow<MangaDetailState>(MangaDetailState.Idle)
    val detailState = _detailState.asStateFlow()

    private val _readerState = MutableStateFlow<MangaReaderState>(MangaReaderState.Idle)
    val readerState = _readerState.asStateFlow()

    val library = repository.getLibrary()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var searchJob: Job? = null

    init {
        loadLatest()
    }

    fun loadLatest() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _browseState.value = MangaBrowseState.Loading
            try {
                val results = repository.getLatestUpdates()
                _browseState.value = MangaBrowseState.Success(results)
            } catch (e: Exception) {
                _browseState.value = MangaBrowseState.Error("Couldn't load manga. Check your connection and try again.")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                loadLatest()
                return@launch
            }
            delay(500)
            _browseState.value = MangaBrowseState.Loading
            try {
                val results = repository.search(query)
                _browseState.value = MangaBrowseState.Success(results)
            } catch (e: Exception) {
                _browseState.value = MangaBrowseState.Error("Search failed. Try again.")
            }
        }
    }

    fun loadDetail(mangaUrl: String) {
        viewModelScope.launch {
            _detailState.value = MangaDetailState.Loading
            try {
                val detail = repository.getDetail(mangaUrl)
                val inLib = repository.isInLibrary(detail.id)
                _detailState.value = MangaDetailState.Success(detail, inLib)
            } catch (e: Exception) {
                _detailState.value = MangaDetailState.Error("Couldn't load this manga.")
            }
        }
    }

    fun toggleLibrary(detail: MangaDetail, currentlyInLibrary: Boolean) {
        viewModelScope.launch {
            if (currentlyInLibrary) {
                repository.removeFromLibrary(detail.id)
            } else {
                repository.addToLibrary(
                    MangaLibraryEntry(
                        mangaId = detail.id,
                        title = detail.title,
                        coverUrl = detail.coverUrl,
                        url = detail.url
                    )
                )
            }
            _detailState.value = MangaDetailState.Success(detail, !currentlyInLibrary)
        }
    }

    fun loadChapterPages(chapterUrl: String) {
        viewModelScope.launch {
            _readerState.value = MangaReaderState.Loading
            try {
                val pages = repository.getChapterPages(chapterUrl)
                if (pages.isEmpty()) {
                    _readerState.value = MangaReaderState.Error("No pages found for this chapter.")
                } else {
                    _readerState.value = MangaReaderState.Success(pages)
                }
            } catch (e: Exception) {
                _readerState.value = MangaReaderState.Error("Couldn't load chapter pages.")
            }
        }
    }

    fun saveProgress(
        mangaId: String, mangaTitle: String, mangaCover: String,
        chapterId: String, chapterName: String, chapterUrl: String,
        lastPageRead: Int, isRead: Boolean
    ) {
        viewModelScope.launch {
            repository.saveReadProgress(
                mangaId, mangaTitle, mangaCover, chapterId, chapterName, chapterUrl, lastPageRead, isRead
            )
        }
    }
}

sealed interface MangaBrowseState {
    data object Loading : MangaBrowseState
    data class Success(val results: List<MangaSearchResult>) : MangaBrowseState
    data class Error(val message: String) : MangaBrowseState
}

sealed interface MangaDetailState {
    data object Idle : MangaDetailState
    data object Loading : MangaDetailState
    data class Success(val detail: MangaDetail, val inLibrary: Boolean) : MangaDetailState
    data class Error(val message: String) : MangaDetailState
}

sealed interface MangaReaderState {
    data object Idle : MangaReaderState
    data object Loading : MangaReaderState
    data class Success(val pages: List<MangaPage>) : MangaReaderState
    data class Error(val message: String) : MangaReaderState
}
