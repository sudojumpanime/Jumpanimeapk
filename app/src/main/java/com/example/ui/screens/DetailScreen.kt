package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.ProgressiveImage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ArrowUpward
import android.content.Intent
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import com.example.data.local.SessionManager
import com.example.data.model.Anime
import com.example.data.model.Episode
import com.example.data.model.cleanEpisodeTitle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CommentsOverlaySheet
import com.example.ui.viewmodel.DetailState
import com.example.ui.viewmodel.DetailViewModel
import com.example.ui.components.shimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    animeId: String,
    viewModel: DetailViewModel,
    sessionManager: SessionManager,
    onBackClick: () -> Unit,
    onEpisodeClick: (Anime, Episode) -> Unit,
    onDownloadEpisode: (Anime, Episode) -> Unit = { _, _ -> },
    onAnimeClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isInWatchlist by viewModel.isInWatchlist.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val isLoadingEpisodes by viewModel.isLoadingEpisodes.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val nextSeasons by viewModel.nextSeasons.collectAsStateWithLifecycle()
    val kittyDetail by viewModel.kittyDetail.collectAsStateWithLifecycle()
    val commentsList by viewModel.comments.collectAsStateWithLifecycle()
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val isLoggedIn = remember { viewModel.isLoggedIn() }
    val currentUsername = remember { viewModel.getLoggedUsername() }
    val view = LocalView.current
    val context = LocalContext.current
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var episodeSearchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedRangeIndex by remember { mutableIntStateOf(0) }
    var newCommentText by remember { mutableStateOf("") }
    var showCommentsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(animeId) {
        viewModel.loadAnime(animeId)
    }

    LaunchedEffect(uiState, selectedSeasonIndex) {
        val state = uiState
        if (state is DetailState.Success) {
            val seasonId = state.anime.seasons.getOrNull(selectedSeasonIndex)?._id
            if (seasonId != null) {
                viewModel.loadEpisodes(seasonId)
            }
            if (state.anime._id.startsWith(com.example.kitty.KITTY_ID_PREFIX)) {
                viewModel.loadKittyExtras(state.anime._id)
            } else {
                state.anime.malId?.toIntOrNull()?.let {
                    viewModel.loadRecommendations(it)
                    viewModel.loadNextSeasons(it, state.anime.title)
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()

    LaunchedEffect(errorEvent) {
        errorEvent?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearErrorEvent()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color(0xFF0F1115) // Dark background from image
        ) { padding ->
        var isRefreshing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    viewModel.loadAnime(animeId)
                    delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is DetailState.Loading -> DetailScreenSkeleton(onBackClick = onBackClick)
                is DetailState.Error -> Text(state.message, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                is DetailState.Success -> {
                    val anime = state.anime
                    var showDownloadDialog by remember { mutableStateOf(false) }
                    
                    val filteredEpisodes = remember(episodes, episodeSearchQuery) {
                        episodes.filter { 
                            it.title?.contains(episodeSearchQuery, ignoreCase = true) == true || 
                            it.number.toString().contains(episodeSearchQuery)
                        }
                    }
                    
                    val chunkedEpisodes = remember(filteredEpisodes) {
                        filteredEpisodes.chunked(50)
                    }

                    val ranges = remember(chunkedEpisodes) {
                        chunkedEpisodes.mapIndexed { index, list ->
                            if (list.isEmpty()) "" else "${list.first().number}-${list.last().number}"
                        }.filter { it.isNotEmpty() }
                    }
                    
                    val displayEpisodes = remember(chunkedEpisodes, selectedRangeIndex) {
                        (chunkedEpisodes.getOrNull(selectedRangeIndex) ?: chunkedEpisodes.firstOrNull() ?: emptyList()).distinctBy { it._id }
                    }

                    LaunchedEffect(filteredEpisodes) {
                        selectedRangeIndex = 0
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Hero Section
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                                ProgressiveImage(
                                    url = anime.banner ?: anime.poster,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    targetWidth = 1000,
                                    targetHeight = 600
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color(0xFF0F1115)),
                                                startY = 200f
                                            )
                                        )
                                )
                                
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }

                                var showMenu by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp)
                                ) {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More options",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier.background(Color(0xFF1C1F26))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Download via Magnet", color = Color.White) },
                                            onClick = {
                                                showMenu = false
                                                showDownloadDialog = true
                                            },
                                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Pin to Home Screen", color = Color.White) },
                                            onClick = {
                                                showMenu = false
                                                com.example.utils.ShortcutUtils.pinAnimeWithPoster(context, scope, anime._id, anime.title, anime.poster)
                                            },
                                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                        )
                                    }
                                }

                                // Overlapping Poster
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 20.dp, bottom = 20.dp)
                                        .size(120.dp, 170.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                ) {
                                    ProgressiveImage(
                                        url = anime.poster,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        targetWidth = 240,
                                        targetHeight = 360
                                    )
                                }
                            }
                        }

                        // Title and Tags
                        item {
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Text(
                                    text = anime.title.uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item { InfoTag("Action") } // Static tags for now or map from anime.genres if available
                                    item { InfoTag(anime.type ?: "TV") }
                                    item { InfoTag(anime.status ?: "Ongoing") }
                                    item { InfoTag(anime.year?.toString() ?: "N/A") }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Info Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                                ) {
                                    InfoItem("Rating", "★ ${anime.rating ?: "N/A"}")
                                    InfoItem(
                                        "Seasons",
                                        if (anime.seasons.isNotEmpty()) anime.seasons.size.toString() else (anime.totalSeasons ?: 1).toString()
                                    )
                                    InfoItem("Status", anime.status ?: "Unknown")
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Synopsis
                                Text(
                                    text = anime.description ?: "No description available.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.LightGray.copy(alpha = 0.7f),
                                    maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSynopsisExpanded) "Read less ▲" else "Read more ▼",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { 
                                            // Play first episode
                                            if (episodes.isNotEmpty()) {
                                                onEpisodeClick(anime, episodes.first())
                                            }
                                        },
                                        modifier = Modifier.height(40.dp).weight(1.2f),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Watch Now", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                    }

                                    Button(
                                        onClick = { 
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.toggleWatchlist(anime)
                                        },
                                        modifier = Modifier.height(40.dp).weight(1.1f),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isInWatchlist) MaterialTheme.colorScheme.secondaryContainer else Color(0xFF1C1F26)
                                        )
                                    ) {
                                        Icon(if (isInWatchlist) Icons.Default.Check else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isInWatchlist) "In List" else "My List", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, anime.title)
                                                putExtra(Intent.EXTRA_TEXT, "Stream ${anime.title} on JumpAnime! Download the app at https://jumpanimedownload.pages.dev")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share anime"))
                                        },
                                        modifier = Modifier.height(40.dp).weight(0.9f),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1C1F26)
                                        )
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, maxLines = 1, softWrap = false)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Episode Search Toggle and Range Filter Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (isSearchExpanded) {
                                        OutlinedTextField(
                                            value = episodeSearchQuery,
                                            onValueChange = { episodeSearchQuery = it },
                                            placeholder = { Text("Search episodes...", color = Color.Gray, fontSize = 14.sp) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF1C1F26),
                                                unfocusedContainerColor = Color(0xFF1C1F26),
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary
                                            ),
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                        )
                                    } else {
                                        // Choose episode section (Range Filter) shifted in front of search icon
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (ranges.size > 1) {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    itemsIndexed(ranges, key = { index, range -> range }) { index, range ->
                                                        val isSelected = selectedRangeIndex == index
                                                        Surface(
                                                            onClick = { selectedRangeIndex = index },
                                                            modifier = Modifier.height(36.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1C1F26),
                                                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                                        ) {
                                                            Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    "EPS: $range", 
                                                                    color = if (isSelected) Color.White else Color.Gray,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    IconButton(
                                        onClick = { 
                                            isSearchExpanded = !isSearchExpanded 
                                            if (!isSearchExpanded) {
                                                episodeSearchQuery = "" // Reset search query when toggling off
                                            }
                                        },
                                        modifier = Modifier.background(Color(0xFF1C1F26), RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Toggle Search",
                                            tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Episodes Horizontal List
                        item {
                            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                Text(
                                    text = "Episodes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                                
                                if (isLoadingEpisodes) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(5) {
                                            Column(modifier = Modifier.width(80.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .height(115.dp)
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .shimmerEffect()
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .height(10.dp)
                                                        .fillMaxWidth(0.6f)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .shimmerEffect()
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .height(8.dp)
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .shimmerEffect()
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(displayEpisodes, key = { it._id }) { episode ->
                                            EpisodeCard(
                                                episode = episode,
                                                fallbackPosterUrl = anime.poster,
                                                animeTitle = anime.title,
                                                onDownload = if (anime._id.startsWith(com.example.kitty.KITTY_ID_PREFIX)) {
                                                    { onDownloadEpisode(anime, episode) }
                                                } else null
                                            ) {
                                                onEpisodeClick(anime, episode)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AniKitty-style extras: studio, trailer, characters, staff, links
                        val kittyExtras = kittyDetail
                        if (kittyExtras != null && com.example.kitty.kittyId(kittyExtras.id) == anime._id) {
                            item {
                                com.example.ui.components.KittyDetailSections(kittyExtras)
                            }
                        }

                        // Related Anime (More Like This)
                        if (recommendations.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                                    Text(
                                        text = "More Like This",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                    
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(recommendations, key = { it._id }) { recAnime ->
                                            Column(
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .clickable {
                                                        onAnimeClick(recAnime._id.replace("mal_", ""))
                                                    }
                                            ) {
                                                ProgressiveImage(
                                                    url = recAnime.poster,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .height(170.dp)
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color.DarkGray),
                                                    contentScale = ContentScale.Crop,
                                                    targetWidth = 240,
                                                    targetHeight = 360
                                                )
                                                Text(
                                                    text = recAnime.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Related Anime (Next Seasons & Related Parts)
                        if (nextSeasons.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                                    Text(
                                        text = "Next Seasons & Related Parts",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                    
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(nextSeasons, key = { it._id }) { relAnime ->
                                            Column(
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .clickable {
                                                        onAnimeClick(relAnime._id.replace("mal_", ""))
                                                    }
                                            ) {
                                                ProgressiveImage(
                                                    url = relAnime.poster,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .height(170.dp)
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color.DarkGray),
                                                    contentScale = ContentScale.Crop,
                                                    targetWidth = 240,
                                                    targetHeight = 360
                                                )
                                                Text(
                                                    text = relAnime.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Comments Section (Anilab Style: Clickable Card)
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .clickable { showCommentsSheet = true },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF14171C)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Comment,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Comments",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = commentsList.size.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = "View All",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    if (commentsList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val latestComment = commentsList.last()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            ProgressiveImage(
                                                url = latestComment.userAvatar,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Gray.copy(alpha = 0.2f)),
                                                contentScale = ContentScale.Crop,
                                                targetWidth = 48,
                                                targetHeight = 48
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = latestComment.username,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                                Text(
                                                    text = latestComment.text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Be the first to share your thoughts!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }

                    if (showDownloadDialog) {
                        var torrents by remember { mutableStateOf<List<com.example.utils.TorrentItem>?>(null) }
                        
                        LaunchedEffect(anime.title) {
                            torrents = com.example.utils.NyaaService.searchTorrents(anime.title)
                        }
                        
                        AlertDialog(
                            onDismissRequest = { showDownloadDialog = false },
                            title = { Text("Available Downloads (Nyra)", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                val list = torrents
                                if (list == null) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(150.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                } else if (list.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(150.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No torrents found.", color = Color.Gray)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 400.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(list, key = { it.magnet }) { torrent ->
                                            Card(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(torrent.magnet))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "No torrent downloader installed. Please install a torrent client like Flud or 1DM.", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                                    Text(
                                                        text = torrent.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Size: ${torrent.size}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.LightGray
                                                        )
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowUpward,
                                                                contentDescription = "Seeders",
                                                                tint = Color.Green,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = torrent.seeders.toString(),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color.Green
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showDownloadDialog = false }) {
                                    Text("Close", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            containerColor = Color(0xFF0F1115)
                        )
                    }
                }
            }
        }

        if (showCommentsSheet) {
            CommentsOverlaySheet(
                commentsList    = commentsList,
                isLoggedIn      = viewModel.isLoggedIn(),
                isAdmin         = viewModel.isAdmin() || commentsList.any { it.username == viewModel.getLoggedUsername() && it.isAuthorAdmin },
                isLoading       = isLoadingComments,
                currentUsername = viewModel.getLoggedUsername(),
                onDismiss       = { showCommentsSheet = false },
                onPostComment   = { text, parentId -> viewModel.postComment((uiState as? DetailState.Success)?.anime?._id ?: animeId, text, parentId) },
                onLikeClick     = { id, isLiked -> viewModel.toggleCommentLike(id, isLiked) },
                onDeleteClick   = { id -> viewModel.deleteComment(id) }
            )
        }
    }
}
}

@Composable
fun EpisodeCard(episode: Episode, fallbackPosterUrl: String?, animeTitle: String? = null, onDownload: (() -> Unit)? = null, onClick: () -> Unit) {
    val imageUrl = if (!episode.thumbnail.isNullOrEmpty()) episode.thumbnail else fallbackPosterUrl
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .height(115.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF131313))
        ) {
            ProgressiveImage(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 240,
                targetHeight = 345
            )
            if (onDownload != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable { onDownload() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download episode", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            // Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            text = "Ep ${episode.number}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = cleanEpisodeTitle(episode.title, episode.number, animeTitle),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun InfoTag(text: String) {
    Surface(
        color = Color(0xFF1C1F26),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
    }
}

@Composable
fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 140.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            ProgressiveImage(
                url = episode.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 280,
                targetHeight = 160
            )
            // Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            val displayTitle = cleanEpisodeTitle(episode.title, episode.number)
            Text(
                text = "Ep ${episode.number}: $displayTitle",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.duration != null) {
                Text(
                    text = episode.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

fun formatCommentTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@Composable
fun CommentItemRow(
    comment: com.example.data.local.Comment,
    canDelete: Boolean,
    onLikeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Row 1: Avatar + Username + Delete Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressiveImage(
                url = comment.userAvatar,
                contentDescription = comment.username,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.2f)),
                contentScale = ContentScale.Crop,
                targetWidth = 76,
                targetHeight = 76
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = comment.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            if (canDelete) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete comment",
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Comment Text (Full-width underneath the header)
        Text(
            text = comment.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Row 3: Action Buttons (Like with count, Reply button, and Date)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Like button and count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onLikeClick
                )
            ) {
                Icon(
                    imageVector = if (comment.isLikedByUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (comment.isLikedByUser) "Liked" else "Like",
                    tint = if (comment.isLikedByUser) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = comment.likes.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (comment.isLikedByUser) MaterialTheme.colorScheme.primary else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            // Reply button (visual action)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Visual action only */ }
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reply",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            // Date/Timestamp
            Text(
                text = formatCommentTimestamp(comment.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun DetailScreenSkeleton(onBackClick: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Backdrop Hero
        item {
            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0F1115)),
                                startY = 200f
                            )
                        )
                )
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                // Overlapping Poster
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 20.dp)
                        .size(120.dp, 170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                        .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                )
            }
        }

        // Title and Tags
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .shimmerEffect()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    repeat(3) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .size(45.dp, 14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(60.dp, 18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .shimmerEffect()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Synopsis lines
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 2) 0.6f else 1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

