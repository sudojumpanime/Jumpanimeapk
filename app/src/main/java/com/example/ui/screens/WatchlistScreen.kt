package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.WaifuBackground
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.R
import com.example.data.model.Anime
import com.example.data.local.AnimeWatchState
import com.example.ui.theme.Slate100
import com.example.ui.viewmodel.WatchlistState
import com.example.ui.viewmodel.WatchlistViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.shimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onAnimeClick: (String) -> Unit,
    onResumeClick: (AnimeWatchState) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val view = LocalView.current
    val pagerState = rememberPagerState(pageCount = { 2 })

    WaifuBackground(
        imageUrl = "https://res.cloudinary.com/di0fllnd9/image/upload/v1782433691/Boa_Hancock_rabquc.jpg",
        overlayAlpha = 0.85f
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.watchlist_title), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            var isRefreshing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch {
                        viewModel.loadWatchlist()
                        delay(1000)
                        isRefreshing = false
                    }
                },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            text = { Text(stringResource(R.string.tab_official), fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("tab_official")
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            text = { Text(stringResource(R.string.tab_custom), fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("tab_custom")
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        if (page == 0) {
                            when (val state = uiState) {
                                is WatchlistState.Loading -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(12.dp)
                                    ) {
                                        items(5) {
                                            WatchlistItemSkeleton()
                                        }
                                    }
                                }
                                is WatchlistState.Error -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(state.message, color = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.loadWatchlist() }
                                        ) {
                                            Text("Retry")
                                        }
                                    }
                                }
                                is WatchlistState.Success -> {
                                    if (state.list.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    "Your Watchlist is Empty",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White.copy(alpha = 0.4f)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "Add animes to track them here",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(12.dp)
                                        ) {
                                            items(state.list, key = { it._id }) { anime ->
                                                WatchlistItemCard(
                                                    anime = anime,
                                                    onDismiss = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                        viewModel.removeFromWatchlist(anime._id)
                                                    },
                                                    onClick = { 
                                                        onAnimeClick(anime._id)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            WatchHistoryComponent(
                                watchHistory = watchHistory,
                                onAnimeClick = onAnimeClick,
                                onResumeClick = onResumeClick,
                                onDeleteClick = { id ->
                                    viewModel.deleteWatchHistoryItem(id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchHistoryComponent(
    watchHistory: List<AnimeWatchState>,
    onAnimeClick: (String) -> Unit,
    onResumeClick: (AnimeWatchState) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    if (watchHistory.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Watch History Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Start watching anime episodes to automatically track your progress here!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(watchHistory.size, key = { index -> watchHistory[index].animeId }) { index ->
                val state = watchHistory[index]
                WatchHistoryItemCard(
                    state = state,
                    index = index,
                    onAnimeClick = onAnimeClick,
                    onResumeClick = onResumeClick,
                    onDeleteClick = onDeleteClick
                )
            }
        }
    }
}

@Composable
fun WatchHistoryItemCard(
    state: AnimeWatchState,
    index: Int,
    onAnimeClick: (String) -> Unit,
    onResumeClick: (AnimeWatchState) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(state.animePoster) {
        if (!state.animePoster.isNullOrEmpty()) {
            coil.request.ImageRequest.Builder(context)
                .data(state.animePoster)
                .crossfade(true)
                .size(240, 360) // High-performance resizing for small card
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
        } else null
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "cardScale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) { onAnimeClick(state.animeId) }
            .testTag("custom_item_card_$index"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp, 96.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = state.animeTitle ?: "Unknown Anime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Episode ${state.lastEpisode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (!state.episodeTitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.episodeTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                val progressValue = if (state.progress > 0f) state.progress else 0.4f
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    Text(
                        text = "${(progressValue * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate100.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable { onResumeClick(state) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Resume Playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            IconButton(
                onClick = { onDeleteClick(state.animeId) },
                modifier = Modifier.testTag("delete_custom_item_$index")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun WatchlistItemCard(
    anime: Anime,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(anime.poster) {
        coil.request.ImageRequest.Builder(context)
            .data(anime.poster)
            .crossfade(true)
            .size(240, 360) // High-performance resizing for small card
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "cardScale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp, 96.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                        Text(
                            anime.type ?: "TV",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = anime.status ?: "Airing",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate100.copy(alpha = 0.6f)
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("delete_watchlist_item_${anime._id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun WatchlistItemSkeleton() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp, 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                )
            }
        }
    }
}
