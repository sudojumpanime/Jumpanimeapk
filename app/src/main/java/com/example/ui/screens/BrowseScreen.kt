package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
import com.example.ui.viewmodel.BrowseState
import com.example.ui.viewmodel.BrowseViewModel
import com.example.ui.theme.Slate100
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AnimeCardSkeleton
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onAnimeClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedGenres by viewModel.selectedGenres.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val totalAnimes by viewModel.totalAnimes.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()

    var showFilterDialog by remember { mutableStateOf(false) }

    val genres = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery", 
        "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller", 
        "Psychological", "Mecha", "Music", "School", "Shounen", "Shoujo", "Seinen", 
        "Josei", "Isekai", "Ecchi", "Historical", "Military", "Martial Arts", "Magic", 
        "Demons", "Vampire", "Harem"
    )
    val types = listOf("TV", "Movie", "OVA", "Special")
    val statuses = listOf("Ongoing", "Completed", "Upcoming")

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = Color.White
                            )
                        }
                        Text(
                            "Browse",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search anime...", color = Slate100.copy(alpha = 0.4f)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                unfocusedLeadingIconColor = Slate100.copy(alpha = 0.4f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate100.copy(alpha = 0.4f))
                                    }
                                }
                            } else null,
                            singleLine = true
                        )

                        IconButton(
                            onClick = { showFilterDialog = true },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                if (searchQuery.isEmpty() && recentSearches.isNotEmpty()) {
                    Text(
                        "Recent Searches",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    
                    com.example.ui.screens.RecentSearchesList(
                        searches = recentSearches,
                        onSearchClick = { viewModel.selectHistory(it) },
                        onDeleteClick = { viewModel.deleteHistory(it) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }


            }
        }
    ) { padding ->
        var isRefreshing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    viewModel.fetchAnime()
                    delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is BrowseState.Loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(12) {
                            AnimeCardSkeleton(width = Dp.Unspecified, height = 155.dp, showText = false)
                        }
                    }
                }
                is BrowseState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is BrowseState.Success -> {
                    if (state.results.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (searchQuery.isNotEmpty()) "No results found for \"$searchQuery\"" else "No anime found",
                                color = Slate100.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(span = { GridItemSpan(3) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val headerTitle = when {
                                        searchQuery.isNotEmpty() -> "Search Results"
                                        selectedGenres.isNotEmpty() -> {
                                            if (selectedGenres.size == 1) "${selectedGenres.first()} Anime"
                                            else "Filtered Anime"
                                        }
                                        selectedStatus != null -> "$selectedStatus Anime"
                                        selectedType != null -> "$selectedType Anime"
                                        else -> when (selectedSort) {
                                            "Popular" -> "Popular Anime"
                                            "Recently added" -> "Recently Added"
                                            "Recently premiered" -> "Recently Premiered"
                                            "Title" -> "Anime by Title"
                                            else -> "Anime"
                                        }
                                    }
                                    Text(
                                        headerTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                }
                            }

                            items(state.results, key = { it._id }, contentType = { "anime_grid_card" }) { anime ->
                                AnimeGridCard(anime = anime) {
                                    onAnimeClick(anime._id)
                                }
                            }
                            
                            item(span = { GridItemSpan(3) }) {
                                val total = totalAnimes
                                val page = currentPage
                                val totalPages = (total + 23) / 24

                                if (totalPages > 1) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { if (page > 1) viewModel.setPage(page - 1) },
                                            enabled = page > 1
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Previous",
                                                tint = if (page > 1) Color.White else Color.White.copy(alpha = 0.2f)
                                            )
                                        }
                                        
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White.copy(alpha = 0.05f),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            Text(
                                                "Page $page of $totalPages",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White
                                            )
                                        }

                                        IconButton(
                                            onClick = { if (page < totalPages) viewModel.setPage(page + 1) },
                                            enabled = page < totalPages
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Next",
                                                tint = if (page < totalPages) Color.White else Color.White.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        if (showFilterDialog) {
            SortAndFilterOverlay(
                currentSort = selectedSort,
                currentType = selectedType,
                currentStatus = selectedStatus,
                currentGenres = selectedGenres,
                onDismiss = { showFilterDialog = false },
                onApply = { sort, type, status, genres ->
                    viewModel.applyFilters(sort, type, status, genres)
                    showFilterDialog = false
                }
            )
        }
    }
}

@Composable
fun SortAndFilterOverlay(
    currentSort: String,
    currentType: String?,
    currentStatus: String?,
    currentGenres: List<String>,
    onDismiss: () -> Unit,
    onApply: (sort: String, type: String?, status: String?, genres: List<String>) -> Unit
) {
    var tempSort by remember { mutableStateOf(currentSort) }
    var tempType by remember { mutableStateOf(currentType ?: "All") }
    var tempStatus by remember { mutableStateOf(currentStatus ?: "All") }
    var tempGenres by remember { mutableStateOf(currentGenres) }

    val sortOptions = listOf("Popular", "Title", "Recently added", "Recently premiered")
    val typeOptions = listOf("All", "Movie", "TV", "OVA", "ONA", "Special", "Music")
    val statusOptions = listOf("All", "Finished Airing", "Currently Airing", "Upcoming")
    val genreOptions = listOf(
        "All", "Action", "Adventure", "Cars", "Comedy", "Dementia", "Demons", "Mystery", "Drama", 
        "Ecchi", "Fantasy", "Game", "Historical", "Horror", "Kids", "Magic", 
        "Martial Arts", "Mecha", "Music", "Parody", "Samurai", "Romance", "School", 
        "Sci-Fi", "Shoujo", "Shoujo Ai", "Shounen", "Shounen Ai", "Space", "Sports", 
        "Super Power", "Vampire", "Yaoi", "Yuri", "Harem", "Slice of Life", "Supernatural", 
        "Military", "Police", "Psychological", "Thriller", "Seinen", "Josei", "Isekai"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C12))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Sort & Filter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Options content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Sort Section
                    item {
                        FilterSection(
                            title = "Sort",
                            options = sortOptions,
                            selectedOption = tempSort,
                            onOptionSelected = { tempSort = it }
                        )
                    }

                    // Type Section
                    item {
                        FilterSection(
                            title = "Type",
                            options = typeOptions,
                            selectedOption = tempType,
                            onOptionSelected = { tempType = it }
                        )
                    }

                    // Status Section
                    item {
                        FilterSection(
                            title = "Status",
                            options = statusOptions,
                            selectedOption = tempStatus,
                            onOptionSelected = { tempStatus = it }
                        )
                    }

                    // Genre Section
                    item {
                        MultiFilterSection(
                            title = "Genre (Select Multiple)",
                            options = genreOptions,
                            selectedOptions = tempGenres,
                            onOptionToggled = { option ->
                                if (option == "All") {
                                    tempGenres = emptyList()
                                } else {
                                    tempGenres = if (tempGenres.contains(option)) {
                                        tempGenres - option
                                    } else {
                                        tempGenres + option
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Buttons Bar
            Surface(
                color = Color(0xFF13131A),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    Button(
                        onClick = {
                            tempSort = "Popular"
                            tempType = "All"
                            tempStatus = "All"
                            tempGenres = emptyList()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            "Reset",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Apply Button
                    Button(
                        onClick = {
                            onApply(tempSort, tempType, tempStatus, tempGenres)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            "Apply",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSection(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                        )
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiFilterSection(
    title: String,
    options: List<String>,
    selectedOptions: List<String>,
    onOptionToggled: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                val isSelected = if (option == "All") selectedOptions.isEmpty() else selectedOptions.contains(option)
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                        )
                        .clickable { onOptionToggled(option) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun RecentSearchesList(
    searches: List<com.example.data.model.SearchHistory>,
    onSearchClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(searches, key = { it.query }) { history ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSearchClick(history.query) },
                color = Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Slate100.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        history.query,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Slate100.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onDeleteClick(history.query) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterPill(
    label: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.05f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (label.startsWith("All ")) Slate100.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary
                )
                Text(
                    "▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate100.copy(alpha = 0.3f)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF1E1E1E))
                .widthIn(min = 120.dp, max = 240.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            option, 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == label) MaterialTheme.colorScheme.primary else Color.White
                        ) 
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AnimeGridCard(anime: com.example.data.model.Anime, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "cardScale"
    )
    val imageRequest = remember(anime.poster) {
        coil.request.ImageRequest.Builder(context)
            .data(anime.poster)
            .crossfade(true)
            .size(300, 450) // High-performance resizing for grid card
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(Color(0xFF131313))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Rating overlay badge at bottom-right corner, dynamic color from settings
            if (anime.rating != null && anime.rating > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp)
                        )
                        val ratingFormatted = if (anime.rating % 1.0 == 0.0) {
                            anime.rating.toInt().toString()
                        } else {
                            anime.rating.toString()
                        }
                        Text(
                            text = ratingFormatted,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Optional Type overlay badge at top-left corner
            if (!anime.type.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = anime.type.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title text directly below the image, exactly like the screenshot
        Text(
            text = anime.title,
            modifier = Modifier
                .padding(horizontal = 2.dp),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            lineHeight = 15.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}
