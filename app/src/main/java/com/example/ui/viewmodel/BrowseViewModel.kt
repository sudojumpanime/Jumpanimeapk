package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.model.Anime
import com.example.data.model.SearchHistory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowseViewModel(private val repository: JumpAnimeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<BrowseState>(BrowseState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedGenres = MutableStateFlow<List<String>>(emptyList())
    val selectedGenres = _selectedGenres.asStateFlow()

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType = _selectedType.asStateFlow()

    private val _selectedStatus = MutableStateFlow<String?>(null)
    val selectedStatus = _selectedStatus.asStateFlow()

    private val _selectedSort = MutableStateFlow<String>("Popular")
    val selectedSort = _selectedSort.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage = _currentPage.asStateFlow()

    private val _totalAnimes = MutableStateFlow(0)
    val totalAnimes = _totalAnimes.asStateFlow()

    val recentSearches = repository.getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var searchJob: Job? = null

    init {
        fetchAnime()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _currentPage.value = 1
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(500)
            }
            fetchAnime()
        }
    }

    fun selectGenre(genre: String?) {
        _selectedGenres.value = if (genre == "All Genres" || genre == "All" || genre == null) emptyList() else listOf(genre)
        _currentPage.value = 1
        fetchAnime()
    }

    fun selectType(type: String?) {
        _selectedType.value = if (type == "All Types" || type == "All") null else type
        _currentPage.value = 1
        fetchAnime()
    }

    fun selectStatus(status: String?) {
        _selectedStatus.value = if (status == "All Status" || status == "All") null else status
        _currentPage.value = 1
        fetchAnime()
    }

    fun selectSort(sort: String) {
        _selectedSort.value = sort
        _currentPage.value = 1
        fetchAnime()
    }

    fun applyFilters(sort: String, type: String?, status: String?, genres: List<String>) {
        _selectedSort.value = sort
        _selectedType.value = if (type == "All") null else type
        _selectedStatus.value = if (status == "All") null else status
        _selectedGenres.value = genres.filter { it != "All" }
        _currentPage.value = 1
        fetchAnime()
    }

    fun setPage(page: Int) {
        _currentPage.value = page
        fetchAnime()
    }

    fun resetFilters() {
        _selectedGenres.value = emptyList()
        _selectedType.value = null
        _selectedStatus.value = null
        _selectedSort.value = "Popular"
        _searchQuery.value = ""
        _currentPage.value = 1
        fetchAnime()
    }

    fun fetchAnime() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = BrowseState.Loading
            try {
                // Map screen sorting option names to Jikan/AniList acceptable parameters
                val mappedSort = when (_selectedSort.value.lowercase()) {
                    "popular" -> "popular"
                    "title" -> "title"
                    "recently added" -> "recently_added"
                    "recently premiered" -> "recently_premiered"
                    else -> "popular"
                }

                val response = repository.searchAnime(
                    query = _searchQuery.value.ifBlank { null },
                    genres = _selectedGenres.value.ifEmpty { null },
                    type = _selectedType.value,
                    status = _selectedStatus.value,
                    sort = mappedSort,
                    limit = 24,
                    page = _currentPage.value
                )
                _uiState.value = BrowseState.Success(response.animes.distinctBy { it._id })
                _totalAnimes.value = response.total
                
                if (_searchQuery.value.isNotBlank() && response.animes.isNotEmpty()) {
                    repository.saveSearch(_searchQuery.value)
                }
            } catch (e: Exception) {
                _uiState.value = BrowseState.Error("Unable to load search results right now. Please check your connection and try again.")
            }
        }
    }

    fun selectHistory(query: String) {
        _searchQuery.value = query
        fetchAnime()
    }

    fun deleteHistory(query: String) {
        viewModelScope.launch {
            repository.deleteSearch(query)
        }
    }
}

sealed interface BrowseState {
    data object Loading : BrowseState
    data class Success(val results: List<Anime>) : BrowseState
    data class Error(val message: String) : BrowseState
}
