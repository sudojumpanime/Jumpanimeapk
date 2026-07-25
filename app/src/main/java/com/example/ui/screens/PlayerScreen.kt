package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Comment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CommentsOverlaySheet
import com.example.ui.theme.PurpleAccent
import com.example.ui.viewmodel.PlayerState
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.VideoSource
import com.example.data.model.Episode
import com.example.data.model.cleanEpisodeTitle

import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import android.app.Activity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    animeId: String,
    malId: String,
    seasonId: String,
    episodeNumber: Int,
    animeTitle: String,
    episodeTitle: String,
    animePoster: String,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val isChangingEpisode by viewModel.isChangingEpisode.collectAsStateWithLifecycle()
    val commentsList by viewModel.comments.collectAsStateWithLifecycle()
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val isLoggedIn = remember { viewModel.isLoggedIn() }
    val currentUsername = remember { viewModel.getLoggedUsername() }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var customView by remember { androidx.compose.runtime.mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { androidx.compose.runtime.mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var refreshKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var loadFailed by remember { androidx.compose.runtime.mutableStateOf(false) }
    var isAutoPlayEnabled by rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    var isAutoSkipEnabled by rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    var isAutoNextEnabled by rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }

    LaunchedEffect(uiState, selectedSource) {
        loadFailed = false
    }

    androidx.compose.runtime.LaunchedEffect(isFullscreen) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val activity = context as? Activity
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window ?: return@onDispose
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            val activity = context as? Activity
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    androidx.activity.compose.BackHandler(enabled = isFullscreen || customView != null) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        }
        isFullscreen = false
    }

    // Cleanly dismiss custom fullscreen view when changing episode or loading starts
    androidx.compose.runtime.LaunchedEffect(isChangingEpisode) {
        if (isChangingEpisode) {
            if (customView != null) {
                try {
                    customViewCallback?.onCustomViewHidden()
                } catch (e: Exception) {}
                customView = null
                customViewCallback = null
            }
        }
    }

    val currentSuccessEpisodeNumber = (uiState as? PlayerState.Success)?.episode?.number
    androidx.compose.runtime.LaunchedEffect(currentSuccessEpisodeNumber) {
        if (currentSuccessEpisodeNumber != null) {
            if (customView != null) {
                try {
                    customViewCallback?.onCustomViewHidden()
                } catch (e: Exception) {}
                customView = null
                customViewCallback = null
            }
        }
    }

    LaunchedEffect(animeId, seasonId, episodeNumber) {
        if (customView != null) {
            try {
                customViewCallback?.onCustomViewHidden()
            } catch (e: Exception) {}
            customView = null
            customViewCallback = null
        }
        viewModel.loadEpisode(animeId, malId, seasonId, episodeNumber, animeTitle, episodeTitle, animePoster)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(animeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (uiState is PlayerState.Success) {
                                val ep = (uiState as PlayerState.Success).episode
                                val displayTitle = cleanEpisodeTitle(ep.title, ep.number, animeTitle)
                                Text("Episode ${ep.number}: $displayTitle", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.9f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (val state = uiState) {
                is PlayerState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is PlayerState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error: ${state.message}", color = Color.White, modifier = Modifier.padding(16.dp))
                            Button(onClick = { viewModel.loadEpisode(animeId, malId, seasonId, episodeNumber, animeTitle, episodeTitle, animePoster) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is PlayerState.Success -> {
                    // loadFailed and refreshKey are elevated to top-level state
                    val sortedEpisodes = androidx.compose.runtime.remember(state.episodes) { state.episodes.sortedBy { it.number } }
                    var lastReportedProgress by androidx.compose.runtime.remember(state.episode.number) { androidx.compose.runtime.mutableStateOf(0f) }

                    LaunchedEffect(state.episode.number) {
                        viewModel.recordWatchCount(animeId)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))
                            .background(Color.Black)
                    ) {
                        if (loadFailed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF15181F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "This source failed to load — try another source below or reload",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            refreshKey++
                                            loadFailed = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        ),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reload Source",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reload Source", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else {
                            androidx.compose.runtime.key(selectedSource, state.episode.number, refreshKey) {
                                VideoWebView(
                                    url = getEmbedUrl(
                                        source = selectedSource,
                                        malId = state.malId,
                                        ep = state.episode.number,
                                        isAutoPlayEnabled = isAutoPlayEnabled,
                                        isAutoSkipEnabled = isAutoSkipEnabled
                                    ),
                                    refreshKey = refreshKey,
                                    initialProgress = state.initialProgress,
                                    playbackToken = "${animeId}_${state.episode.number}",
                                    isChangingEpisode = isChangingEpisode,
                                    isAutoPlayEnabled = isAutoPlayEnabled,
                                    isAutoSkipEnabled = isAutoSkipEnabled,
                                    onLoadError = { loadFailed = true },
                                    onVideoProgress = { progress ->
                                        lastReportedProgress = progress
                                        if (!isChangingEpisode) {
                                            viewModel.updateProgress(
                                                animeId = animeId,
                                                malId = state.malId,
                                                seasonId = seasonId,
                                                episodeNumber = state.episode.number,
                                                episodeTitle = state.episode.title ?: "Episode ${state.episode.number}",
                                                animeTitle = animeTitle,
                                                animePoster = animePoster,
                                                progress = progress
                                            )
                                        }
                                    },
                                    onVideoEnded = {
                                        // Only trigger auto-advance or show ending toast if the video is actually near the end (>= 85%).
                                        // This prevents third-party ad networks or tracking scripts inside iframes from firing rogue completed events mid-video!
                                        if (lastReportedProgress >= 0.85f) {
                                            val currentEpisodeNum = state.episode.number
                                            val nextEpisodeIndex = sortedEpisodes.indexOfFirst { it.number == currentEpisodeNum } + 1
                                            if (nextEpisodeIndex in sortedEpisodes.indices) {
                                                val nextEp = sortedEpisodes[nextEpisodeIndex]
                                                if (isAutoNextEnabled) {
                                                    // Load next episode instantly!
                                                    viewModel.loadEpisode(
                                                        animeId = animeId,
                                                        malId = state.malId,
                                                        seasonId = seasonId,
                                                        episodeNumber = nextEp.number,
                                                        animeTitle = animeTitle,
                                                        episodeTitle = nextEp.title ?: "Episode ${nextEp.number}",
                                                        animePoster = animePoster
                                                    )
                                                } else {
                                                    Toast.makeText(context, "Episode finished (Autoplay is Off)", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "You've finished the final episode!", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            android.util.Log.d("PlayerScreen", "Ignored premature video-ended trigger. Current progress is $lastReportedProgress (threshold is 0.85)")
                                        }
                                    },
                                    onShowCustomView = { view, callback ->
                                        customView = view
                                        customViewCallback = callback
                                        isFullscreen = true
                                    },
                                    onHideCustomView = {
                                        customViewCallback?.onCustomViewHidden()
                                        customView = null
                                        customViewCallback = null
                                        isFullscreen = false
                                    }
                                )
                            }

                            // Active offscreen prefetching of embed player WebViews is disabled to prevent background autoplay,
                            // audio leaks, and local storage contamination between different episodes.
                        }

                        if (isChangingEpisode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (!isFullscreen) {
                        val activeColor = MaterialTheme.colorScheme.primary
                        val groupSize = 100
                        val isLargeDataset = state.episodes.size > 50
                        
                        var selectedGroupIndex by remember(state.episodes) {
                            val initialGroup = (state.episode.number - 1) / groupSize
                            mutableIntStateOf(if (initialGroup in 0 until (state.episodes.size + groupSize - 1) / groupSize) initialGroup else 0)
                        }
                        
                        val totalEpisodes = state.episodes.size
                        val groupsCount = if (totalEpisodes % groupSize == 0) totalEpisodes / groupSize else (totalEpisodes / groupSize) + 1

                        val filteredEpisodes = remember(state.episodes, selectedGroupIndex, isLargeDataset) {
                            if (isLargeDataset) {
                                val startRange = selectedGroupIndex * groupSize + 1
                                val endRange = (selectedGroupIndex + 1) * groupSize
                                state.episodes.filter { it.number in startRange..endRange }
                            } else {
                                state.episodes
                            }
                        }
                        
                        val episodeChunks = remember(filteredEpisodes) {
                            filteredEpisodes.chunked(6)
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // 1. Episode Info and Control Buttons
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = cleanEpisodeTitle(state.episode.title, state.episode.number, animeTitle),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Expand Button
                                        Surface(
                                            onClick = { isFullscreen = !isFullscreen },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1E222B)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                    contentDescription = "Expand",
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Expand",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Divider
                                        Box(
                                            modifier = Modifier
                                                .height(16.dp)
                                                .width(1.dp)
                                                .background(Color.White.copy(alpha = 0.2f))
                                        )

                                        // Auto Play Button
                                        Surface(
                                            onClick = { isAutoPlayEnabled = !isAutoPlayEnabled },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAutoPlayEnabled) activeColor.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                            border = if (isAutoPlayEnabled) androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Auto Play",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isAutoPlayEnabled) activeColor else Color.Gray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Auto Next Button
                                        Surface(
                                            onClick = { isAutoNextEnabled = !isAutoNextEnabled },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAutoNextEnabled) activeColor.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                            border = if (isAutoNextEnabled) androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Auto Next",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isAutoNextEnabled) activeColor else Color.Gray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Auto Skip Button
                                        Surface(
                                            onClick = { isAutoSkipEnabled = !isAutoSkipEnabled },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAutoSkipEnabled) activeColor.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                            border = if (isAutoSkipEnabled) androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Auto Skip",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isAutoSkipEnabled) activeColor else Color.Gray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Source/Server Selector (Anilab Style below controls)
                            item {
                                SourceSelector(
                                    selectedSource = selectedSource,
                                    episodeNumber = state.episode.number,
                                    onSourceSelected = { viewModel.setSource(it) }
                                )
                            }

                            item {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.05f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            // 3. Comments Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                        .clickable { showCommentsSheet = true },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Comment,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Comments",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = commentsList.size.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        Text(
                                            "See comments",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }

                            // 4. Episode Grid Header Section
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "List of episodes",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            "${state.episodes.size} Episodes",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    if (isLargeDataset && groupsCount > 1) {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            items(groupsCount) { groupIdx ->
                                                val startNum = groupIdx * groupSize + 1
                                                val endNum = minOf((groupIdx + 1) * groupSize, totalEpisodes)
                                                val isGroupSelected = selectedGroupIndex == groupIdx
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isGroupSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E1E1E))
                                                        .border(1.dp, if (isGroupSelected) Color.Transparent else Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                                        .clickable { selectedGroupIndex = groupIdx }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "$startNum-$endNum",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isGroupSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Episode Grid Items
                            items(episodeChunks, key = { chunk -> chunk.firstOrNull()?.number ?: 0 }) { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    chunk.forEach { ep ->
                                        val isCurrent = ep.number == state.episode.number
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isCurrent) MaterialTheme.colorScheme.primary 
                                                    else Color(0xFF1E1E1E)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isCurrent) Color.Transparent else Color.White.copy(alpha = 0.05f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    if (!isCurrent) {
                                                        viewModel.loadEpisode(
                                                            animeId = animeId,
                                                            malId = state.malId,
                                                            seasonId = seasonId,
                                                            episodeNumber = ep.number,
                                                            animeTitle = animeTitle,
                                                            episodeTitle = ep.title ?: "Episode ${ep.number}",
                                                            animePoster = animePoster
                                                        )
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ep.number.toString(),
                                                fontSize = 13.sp,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    // Fill remaining space if chunk is not full
                                    repeat(6 - chunk.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
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
                heightFraction  = 0.55f,
                onDismiss       = { showCommentsSheet = false },
                onPostComment   = { text, parentId -> viewModel.postComment(animeId, text, parentId) },
                onLikeClick     = { id, isLiked -> viewModel.toggleCommentLike(id, isLiked) },
                onDeleteClick   = { id -> viewModel.deleteComment(id) }
            )
        }

        if (customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        (customView?.parent as? ViewGroup)?.removeView(customView)
                        customView!!
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
}


@Composable
fun SourceSelector(
    selectedSource: VideoSource,
    episodeNumber: Int,
    onSourceSelected: (VideoSource) -> Unit
) {
    val activeSubColor = MaterialTheme.colorScheme.primary
    val activeDubColor = MaterialTheme.colorScheme.primary
    
    val orderedSubSources = remember {
        listOf(
            VideoSource.ALT_SUB,
            VideoSource.SUB,
            VideoSource.BACKUP
        )
    }

    val orderedDubSources = remember {
        listOf(
            VideoSource.ALT_DUB,
            VideoSource.DUB
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // SUB row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(55.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ClosedCaption,
                    contentDescription = "Subtitles",
                    tint = activeSubColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "SUB:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = activeSubColor
                    )
                )
            }

            // Servers list (Scrollable row)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orderedSubSources.forEachIndexed { index, source ->
                    val isSelected = selectedSource == source
                    Surface(
                        onClick = { onSourceSelected(source) },
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) activeSubColor else Color(0xFF1E222B),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "HD-${index + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Divider between SUB & DUB
        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)

        // DUB row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(55.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Dubbing",
                    tint = activeDubColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "DUB:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = activeDubColor
                    )
                )
            }

            // Servers list (Scrollable row)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orderedDubSources.forEachIndexed { index, source ->
                    val isSelected = selectedSource == source
                    Surface(
                        onClick = { onSourceSelected(source) },
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) activeSubColor else Color(0xFF1E222B),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "HD-${index + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private class WebViewStateTracker(
    var lastLoadedUrl: String = "",
    var lastRefreshKey: Int = 0
)

fun getEmbedUrl(
    source: VideoSource,
    malId: String,
    ep: Int,
    isAutoPlayEnabled: Boolean = true,
    isAutoSkipEnabled: Boolean = true
): String {
    return when (source) {
        VideoSource.SUB -> "https://animeplay.cfd/stream/mal/$malId/$ep/sub"
        VideoSource.DUB -> "https://animeplay.cfd/stream/mal/$malId/$ep/dub"
        VideoSource.ALT_SUB -> "https://megaplay.buzz/stream/mal/$malId/$ep/sub"
        VideoSource.ALT_DUB -> "https://megaplay.buzz/stream/mal/$malId/$ep/dub"
        VideoSource.BACKUP -> "https://animeplay.cfd/stream/mal/$malId/$ep/sub"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoWebView(
    url: String, 
    refreshKey: Int = 0,
    initialProgress: Float = 0f,
    playbackToken: String,
    isChangingEpisode: Boolean = false,
    isAutoPlayEnabled: Boolean = true,
    isAutoSkipEnabled: Boolean = true,
    onLoadError: () -> Unit, 
    onVideoEnded: () -> Unit,
    onVideoProgress: (Float) -> Unit,
    onShowCustomView: (android.view.View, WebChromeClient.CustomViewCallback?) -> Unit,
    onHideCustomView: () -> Unit
) {
    val context = LocalContext.current
    var isPageLoading by remember { mutableStateOf(true) }
    
    // Solve the captured stale lambda/variable problem using rememberUpdatedState
    val currentOnVideoEnded by androidx.compose.runtime.rememberUpdatedState(onVideoEnded)
    val currentOnLoadError by androidx.compose.runtime.rememberUpdatedState(onLoadError)
    val currentOnVideoProgress by androidx.compose.runtime.rememberUpdatedState(onVideoProgress)
    val currentOnShowCustomView by androidx.compose.runtime.rememberUpdatedState(onShowCustomView)
    val currentOnHideCustomView by androidx.compose.runtime.rememberUpdatedState(onHideCustomView)
    val currentInitialProgress by androidx.compose.runtime.rememberUpdatedState(initialProgress)
    val currentPlaybackToken by androidx.compose.runtime.rememberUpdatedState(playbackToken)
    val currentUrl by androidx.compose.runtime.rememberUpdatedState(url)
    val currentIsChangingEpisode by androidx.compose.runtime.rememberUpdatedState(isChangingEpisode)
    val currentIsAutoPlayEnabled by androidx.compose.runtime.rememberUpdatedState(isAutoPlayEnabled)
    val currentIsAutoSkipEnabled by androidx.compose.runtime.rememberUpdatedState(isAutoSkipEnabled)
    var isUrlChanging by remember { mutableStateOf(false) }
    var currentLoadId by remember { mutableStateOf(1) }
    
    val stateTracker = remember { WebViewStateTracker() }
    var webViewRef by remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }

    LaunchedEffect(isAutoPlayEnabled, isAutoSkipEnabled) {
        webViewRef?.evaluateJavascript(
            """
            window.isAutoPlayEnabled = $isAutoPlayEnabled;
            window.isAutoSkipEnabled = $isAutoSkipEnabled;
            """.trimIndent(), null
        )
    }
    
    LaunchedEffect(isChangingEpisode) {
        if (isChangingEpisode) {
            webViewRef?.loadUrl("about:blank")
            stateTracker.lastLoadedUrl = "about:blank"
        }
    }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    webViewRef?.onPause()
                    webViewRef?.pauseTimers()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    webViewRef?.onResume()
                    webViewRef?.resumeTimers()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.let { webView ->
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.onPause()
                    webView.pauseTimers()
                    webView.destroy()
                } catch (e: Exception) {
                    android.util.Log.e("VideoWebView", "Error destroying WebView", e)
                }
                webViewRef = null
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                val webView = object : WebView(ctx) {
                    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                        // Keep reporting true window focus to prevent video pausing when comments sheet or keyboard is opened
                        super.onWindowFocusChanged(true)
                    }
                }
                webViewRef = webView
                webView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    setBackgroundColor(android.graphics.Color.BLACK)
                    
                    // Disable vertical and horizontal scrollbars and overscroll to prevent accidental swipe/scroll gestures inside the player
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    
                    // Hardware acceleration usually helps with black screens
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    // Enable third-party cookies for iframe support in embed players
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }

                    // Enhanced security and ad-blocking settings
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        databaseEnabled = true
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT // Optimize cache usage
                        offscreenPreRaster = true
                        allowFileAccess = true
                        allowContentAccess = true
                        
                        // Emulate a standard mobile Chrome browser to bypass WebView limits and avoid black screens
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        
                        // Block popups and multiple windows
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                    }

                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

                    // Add Javascript interface to communicate completion safely
                    class WebAppInterface {
                        @android.webkit.JavascriptInterface
                        fun triggerVideoEnded() {
                            // Empty overload for backward/generic safety to prevent javascript crashes if triggered without parameters
                            android.util.Log.d("VideoWebView", "triggerVideoEnded called without parameters - ignored for safety")
                        }

                        @android.webkit.JavascriptInterface
                        fun triggerVideoEnded(reportedToken: String) {
                            mainHandler.post {
                                if (reportedToken == currentPlaybackToken) {
                                    currentOnVideoEnded()
                                } else {
                                    android.util.Log.d("VideoWebView", "Ignored stale video-ended event for token '$reportedToken' (expected: '$currentPlaybackToken')")
                                }
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun triggerVideoProgress(progress: Float, reportedToken: String) {
                            mainHandler.post {
                                if (reportedToken == currentPlaybackToken) {
                                    currentOnVideoProgress(progress)
                                } else {
                                    android.util.Log.d("VideoWebView", "Ignored stale progress for token '$reportedToken' (expected: '$currentPlaybackToken')")
                                }
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun isAutoSkipEnabled(): Boolean {
                            return currentIsAutoSkipEnabled
                        }

                        @android.webkit.JavascriptInterface
                        fun isAutoPlayEnabled(): Boolean {
                            return currentIsAutoPlayEnabled
                        }
                    }
                    addJavascriptInterface(WebAppInterface(), "JSBridge")

                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        val documentStartScript = """
                            (function() {
                                console.log("AutoSkip & AutoResume Document Start script initialized.");
                                
                                // Track user interaction inside this frame/subframe
                                var lastUserInteractionTime = 0;
                                function registerInteraction() {
                                    lastUserInteractionTime = Date.now();
                                }
                                window.addEventListener('click', registerInteraction, { capture: true, passive: true });
                                window.addEventListener('touchstart', registerInteraction, { capture: true, passive: true });
                                window.addEventListener('mousedown', registerInteraction, { capture: true, passive: true });
                                window.addEventListener('keydown', registerInteraction, { capture: true, passive: true });

                                // Track layout and fullscreen transitions
                                var lastLayoutChangeEventTime = 0;
                                function registerLayoutChange() {
                                    lastLayoutChangeEventTime = Date.now();
                                    console.log("Layout/Fullscreen change detected.");
                                }
                                window.addEventListener('resize', registerLayoutChange, { passive: true });
                                window.addEventListener('orientationchange', registerLayoutChange, { passive: true });
                                document.addEventListener('fullscreenchange', registerLayoutChange, { passive: true });
                                document.addEventListener('webkitfullscreenchange', registerLayoutChange, { passive: true });
                                document.addEventListener('mozfullscreenchange', registerLayoutChange, { passive: true });
                                document.addEventListener('MSFullscreenChange', registerLayoutChange, { passive: true });

                                function isAutoSkipEnabled() {
                                    var isSkipEnabled = true;
                                    try {
                                        if (window.JSBridge) {
                                            if (typeof window.JSBridge.isAutoSkipEnabled === 'function') {
                                                isSkipEnabled = window.JSBridge.isAutoSkipEnabled();
                                            } else if (typeof window.JSBridge.isAutoSkipEnabled === 'boolean') {
                                                isSkipEnabled = window.JSBridge.isAutoSkipEnabled;
                                            }
                                        } else if (window.parent && window.parent.JSBridge) {
                                            if (typeof window.parent.JSBridge.isAutoSkipEnabled === 'function') {
                                                isSkipEnabled = window.parent.JSBridge.isAutoSkipEnabled();
                                            } else if (typeof window.parent.JSBridge.isAutoSkipEnabled === 'boolean') {
                                                isSkipEnabled = window.parent.JSBridge.isAutoSkipEnabled;
                                            }
                                        }
                                    } catch(e) {
                                        isSkipEnabled = true;
                                    }
                                    return isSkipEnabled;
                                }

                                function isAutoPlayEnabled() {
                                    var isPlayEnabled = true;
                                    try {
                                        if (window.JSBridge) {
                                            if (typeof window.JSBridge.isAutoPlayEnabled === 'function') {
                                                isPlayEnabled = window.JSBridge.isAutoPlayEnabled();
                                            } else if (typeof window.JSBridge.isAutoPlayEnabled === 'boolean') {
                                                isPlayEnabled = window.JSBridge.isAutoPlayEnabled;
                                            }
                                        } else if (window.parent && window.parent.JSBridge) {
                                            if (typeof window.parent.JSBridge.isAutoPlayEnabled === 'function') {
                                                isPlayEnabled = window.parent.JSBridge.isAutoPlayEnabled();
                                            } else if (typeof window.parent.JSBridge.isAutoPlayEnabled === 'boolean') {
                                                isPlayEnabled = window.parent.JSBridge.isAutoPlayEnabled;
                                            }
                                        }
                                    } catch(e) {
                                        isPlayEnabled = true;
                                    }
                                    return isPlayEnabled;
                                }

                                function clickButton(btn, label) {
                                    if (!btn || btn.hasBeenAutoSkipClicked) return;
                                    btn.hasBeenAutoSkipClicked = true;
                                    console.log("AutoSkip click triggered: " + label);
                                    
                                    // Robust click simulation by firing a complete list of interaction events
                                    var events = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];
                                    events.forEach(function(evName) {
                                        try {
                                            var ev;
                                            if (evName.startsWith('pointer')) {
                                                ev = new PointerEvent(evName, { view: window, bubbles: true, cancelable: true, isPrimary: true });
                                            } else {
                                                ev = new MouseEvent(evName, { view: window, bubbles: true, cancelable: true });
                                            }
                                            btn.dispatchEvent(ev);
                                        } catch(e) {}
                                    });
                                    try {
                                        btn.click();
                                    } catch(e) {}
                                }

                                function checkAndSkip() {
                                    if (!isAutoSkipEnabled()) return;

                                    // 1. Comprehensive case-insensitive wildcards & common player CSS Selectors
                                    var selectors = [
                                        '.skip-button',
                                        '.jw-skip',
                                        '.ani-skip',
                                        '.skip-intro',
                                        '.skip-op',
                                        '.skip-ed',
                                        '.player-skip-button',
                                        '.ani-skip-button',
                                        '.control-button.skip',
                                        '.btn-skip',
                                        'button[class*="skip" i]',
                                        'div[class*="skip" i]',
                                        'span[class*="skip" i]',
                                        'a[class*="skip" i]',
                                        '[id*="skip" i]',
                                        '[title*="skip" i]',
                                        '[aria-label*="skip" i]',
                                        '[data-skip]',
                                        '[class*="skip"]',
                                        '[id*="skip"]'
                                    ];
                                    
                                    for (var i = 0; i < selectors.length; i++) {
                                        try {
                                            var btn = document.querySelector(selectors[i]);
                                            if (btn && btn.style.display !== 'none' && !btn.disabled) {
                                                var rect = btn.getBoundingClientRect();
                                                if (rect.width > 0 && rect.height > 0) {
                                                    clickButton(btn, "CSS: " + selectors[i]);
                                                    return;
                                                }
                                            }
                                        } catch(e) {}
                                    }

                                    // 2. Intelligent Text Content Search (Matches any element with 'skip' and context labels)
                                    try {
                                        var allElements = document.querySelectorAll('button, a, [class*="skip" i], [id*="skip" i]');
                                        for (var i = 0; i < allElements.length; i++) {
                                            var el = allElements[i];
                                            var text = el.textContent ? el.textContent.trim().toLowerCase() : "";
                                            
                                            var matches = false;
                                            if (text.indexOf('skip') !== -1) {
                                                if (text === 'skip' || text === 'skip intro' || text === 'skip outro' || text === 'skip ad' ||
                                                    text.indexOf('intro') !== -1 || text.indexOf('outro') !== -1 || 
                                                    text.indexOf('op') !== -1 || text.indexOf('ed') !== -1 || 
                                                    text.indexOf('opening') !== -1 || text.indexOf('ending') !== -1 || 
                                                    text.indexOf('recap') !== -1 || text.indexOf('advertisement') !== -1) {
                                                    matches = true;
                                                }
                                            }
                                            
                                            if (matches) {
                                                var rect = el.getBoundingClientRect();
                                                if (rect.width > 0 && rect.height > 0) {
                                                    clickButton(el, "Text: " + el.textContent);
                                                    break;
                                                }
                                            }
                                        }
                                    } catch(e) {}
                                }

                                // Direct Video Element Hook & Auto Resume Monitor
                                function hookVideoElements() {
                                    var videoElements = document.querySelectorAll('video');
                                    for (var i = 0; i < videoElements.length; i++) {
                                        var v = videoElements[i];
                                        
                                        // Auto play if paused & autoplay is enabled
                                        if (isAutoPlayEnabled()) {
                                            if (v.paused && !v.dataset.hasAttemptedAutoplay) {
                                                v.dataset.hasAttemptedAutoplay = "true";
                                                v.play().catch(function(e) {});
                                            }
                                        }

                                        if (!v.hasAutoResumeListener) {
                                            v.hasAutoResumeListener = true;
                                            v.dataset.wasPlaying = "true";

                                            v.addEventListener('play', function() {
                                                v.dataset.wasPlaying = "true";
                                            });

                                            v.addEventListener('pause', function() {
                                                var timeSinceInteraction = Date.now() - lastUserInteractionTime;
                                                var timeSinceLayoutChange = Date.now() - lastLayoutChangeEventTime;
                                                console.log("Video pause event. Time since interaction: " + timeSinceInteraction + "ms, layout change: " + timeSinceLayoutChange + "ms");
                                                
                                                // Auto-resume if layout changed recently (fullscreen, resize, rotation) OR if pause happened without user interaction
                                                var isLayoutChangePause = (timeSinceLayoutChange < 2000);
                                                var isSystemPause = (timeSinceInteraction > 400);
                                                
                                                if ((isLayoutChangePause || isSystemPause) && v.dataset.wasPlaying === "true" && isAutoPlayEnabled()) {
                                                    console.log("System/Layout-initiated pause detected. Restoring playback...");
                                                    setTimeout(function() {
                                                        v.play().catch(function(e) {
                                                            console.log("Failed to auto-resume: ", e);
                                                        });
                                                    }, 50);
                                                } else if (!isLayoutChangePause && timeSinceInteraction <= 400) {
                                                    v.dataset.wasPlaying = "false";
                                                }
                                            });
                                        }

                                        // Ended listener for subframe -> topframe communication
                                        if (!v.hasEndedPostMessageListener) {
                                            v.hasEndedPostMessageListener = true;
                                            v.addEventListener('ended', function() {
                                                console.log("Video ended inside subframe. Posting message to parent.");
                                                try {
                                                    window.parent.postMessage({ type: 'custom_ended' }, '*');
                                                } catch(e) {}
                                            });
                                        }

                                        // Timeupdate progress listener for subframe -> topframe communication
                                        if (!v.hasProgressPostMessageListener) {
                                            v.hasProgressPostMessageListener = true;
                                            v.addEventListener('timeupdate', function() {
                                                if (v.duration > 0) {
                                                    var progress = v.currentTime / v.duration;
                                                    try {
                                                        window.parent.postMessage({ type: 'custom_progress', progress: progress }, '*');
                                                    } catch(e) {}
                                                }
                                            });
                                        }
                                    }
                                }

                                setInterval(checkAndSkip, 400);
                                setInterval(hookVideoElements, 500);
                            })();
                        """.trimIndent()
                        
                        androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                            this,
                            documentStartScript,
                            setOf("*")
                        )
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, urlToLoad: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, urlToLoad, favicon)
                            isPageLoading = true
                        }

                        private fun shouldBlockUrl(urlToLoad: String?): Boolean {
                            if (urlToLoad == null) return false
                            if (urlToLoad == "about:blank") return false
                            
                            val uri = try {
                                android.net.Uri.parse(urlToLoad)
                            } catch (e: Exception) {
                                null
                            }
                            val host = uri?.host?.lowercase() ?: ""
                            val path = uri?.path?.lowercase() ?: ""
                            
                            // Check whitelist of allowed streaming keywords/domains to make sure first 5 sources and iframes load
                            val whitelist = listOf(
                                "animeplay", "megaplay", "4animo", "rabbitstream", "megacloud",
                                "rapid-cloud", "vizcloud", "filemoon", "streamtape", "vidplay",
                                "mcloud", "mp4upload", "hianime", "flixcloud", "anizone",
                                "gogoplay", "gogoanime", "stream", "embed", "m3u8", "player",
                                "video", "cdn", "cloud", "static", "play"
                            )
                            if (whitelist.any { host.contains(it) || path.contains(it) || urlToLoad.lowercase().contains(it) }) {
                                return false // Do NOT block
                            }
                            
                            val currentHost = android.net.Uri.parse(currentUrl).host ?: ""
                            val isSameHost = host == currentHost || host.endsWith(".$currentHost")
                            if (isSameHost) {
                                return false // Do NOT block same host
                            }
                            
                            // Block unknown redirects that might be ads/spam
                            return true
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, urlToLoad: String?): Boolean {
                            return shouldBlockUrl(urlToLoad)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            val isMainFrame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                request.isForMainFrame
                            } else {
                                true
                            }
                            if (!isMainFrame) {
                                return false // Allow sub-frame loading, e.g., iframes inside the player
                            }
                            return shouldBlockUrl(url)
                        }

                        private fun shouldInterceptForHtmlInjection(url: String): Boolean {
                            val lower = url.lowercase()
                            if (lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".png") ||
                                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
                                lower.endsWith(".svg") || lower.endsWith(".woff") || lower.endsWith(".woff2") ||
                                lower.endsWith(".ttf") || lower.endsWith(".mp4") || lower.endsWith(".m3u8") ||
                                lower.endsWith(".ts") || lower.endsWith(".json") || lower.contains(".m3u8")
                            ) {
                                return false
                            }
                            
                            // Check if the URL belongs to our whitelisted streaming domains/keywords
                            val whitelist = listOf(
                                "animeplay", "megaplay", "4animo", "rabbitstream", "megacloud",
                                "rapid-cloud", "vizcloud", "filemoon", "streamtape", "vidplay",
                                "mcloud", "mp4upload", "hianime", "flixcloud", "anizone"
                            )
                            
                            return whitelist.any { lower.contains(it) }
                        }

                        private fun fetchAndInjectHtml(urlStr: String, requestHeaders: Map<String, String>?): android.webkit.WebResourceResponse? {
                            try {
                                val url = java.net.URL(urlStr)
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.connectTimeout = 8000
                                conn.readTimeout = 8000
                                conn.instanceFollowRedirects = true
                                
                                // Copy request headers except those we want to override
                                requestHeaders?.forEach { (key, value) ->
                                    if (key.lowercase() != "accept-encoding" && key.lowercase() != "user-agent" && key.lowercase() != "cookie") {
                                        conn.setRequestProperty(key, value)
                                    }
                                }
                                
                                // Ensure standard User-Agent is set to bypass Cloudflare and similar blockers
                                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                                
                                // Fetch and attach cookies manually because WebViewClient shouldInterceptRequest headers do NOT include Cookie header
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                val cookies = cookieManager.getCookie(urlStr)
                                if (!cookies.isNullOrEmpty()) {
                                    conn.setRequestProperty("Cookie", cookies)
                                }
                                
                                // Force Identity encoding so we receive plain HTML string instead of compressed bytes
                                conn.setRequestProperty("Accept-Encoding", "identity")
                                
                                val responseCode = conn.responseCode
                                if (responseCode >= 400) {
                                    return null
                                }
                                
                                val contentType = conn.contentType ?: "text/html"
                                if (!contentType.lowercase().contains("text/html")) {
                                    val encoding = conn.contentEncoding ?: "UTF-8"
                                    return android.webkit.WebResourceResponse(contentType, encoding, conn.inputStream)
                                }
                                
                                val encoding = conn.contentEncoding ?: "UTF-8"
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream, encoding))
                                val sb = java.lang.StringBuilder()
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    sb.append(line).append("\n")
                                }
                                reader.close()
                                
                                val originalHtml = sb.toString()
                                
                                val isAutoSkip = currentIsAutoSkipEnabled
                                val isAutoPlay = currentIsAutoPlayEnabled
                                // Javascript to inject
                                val scriptToInject = """
                                    <script type="text/javascript">
                                        (function() {
                                            var isAutoSkipEnabled = $isAutoSkip;
                                            var isAutoPlayEnabled = $isAutoPlay;
                                            
                                            // Local interaction tracking
                                            var lastUserInteractionTime = 0;
                                            function registerInteraction() {
                                                lastUserInteractionTime = Date.now();
                                            }
                                            window.addEventListener('click', registerInteraction, { capture: true, passive: true });
                                            window.addEventListener('touchstart', registerInteraction, { capture: true, passive: true });
                                            window.addEventListener('mousedown', registerInteraction, { capture: true, passive: true });
                                            window.addEventListener('keydown', registerInteraction, { capture: true, passive: true });

                                            // Local layout/fullscreen tracking
                                            var lastLayoutChangeEventTime = 0;
                                            function registerLayoutChange() {
                                                lastLayoutChangeEventTime = Date.now();
                                                console.log("Injected iframe layout/fullscreen change detected.");
                                            }
                                            window.addEventListener('resize', registerLayoutChange, { passive: true });
                                            window.addEventListener('orientationchange', registerLayoutChange, { passive: true });
                                            document.addEventListener('fullscreenchange', registerLayoutChange, { passive: true });
                                            document.addEventListener('webkitfullscreenchange', registerLayoutChange, { passive: true });
                                            document.addEventListener('mozfullscreenchange', registerLayoutChange, { passive: true });
                                            document.addEventListener('MSFullscreenChange', registerLayoutChange, { passive: true });

                                            function clickButton(btn, label) {
                                                if (!btn || btn.hasBeenAutoSkipClicked) return;
                                                btn.hasBeenAutoSkipClicked = true;
                                                console.log("AutoSkip click triggered in injected frame: " + label);
                                                
                                                var events = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];
                                                events.forEach(function(evName) {
                                                    try {
                                                        var ev;
                                                        if (evName.startsWith('pointer')) {
                                                            ev = new PointerEvent(evName, { view: window, bubbles: true, cancelable: true, isPrimary: true });
                                                        } else {
                                                            ev = new MouseEvent(evName, { view: window, bubbles: true, cancelable: true });
                                                        }
                                                        btn.dispatchEvent(ev);
                                                    } catch(e) {}
                                                });
                                                try {
                                                    btn.click();
                                                } catch(e) {}
                                            }

                                            function autoSkipIntroOutro() {
                                                if (!isAutoSkipEnabled) {
                                                    return;
                                                }
                                                
                                                // 1. Selector search
                                                var selectors = [
                                                    '.skip-button',
                                                    '.jw-skip',
                                                    '.ani-skip',
                                                    '.skip-intro',
                                                    '.skip-op',
                                                    '.skip-ed',
                                                    '.player-skip-button',
                                                    '.ani-skip-button',
                                                    '.control-button.skip',
                                                    '.btn-skip',
                                                    'button[class*="skip" i]',
                                                    'div[class*="skip" i]',
                                                    'span[class*="skip" i]',
                                                    'a[class*="skip" i]',
                                                    '[id*="skip" i]',
                                                    '[title*="skip" i]',
                                                    '[aria-label*="skip" i]',
                                                    '[data-skip]',
                                                    '[class*="skip"]',
                                                    '[id*="skip"]'
                                                ];
                                                
                                                for (var i = 0; i < selectors.length; i++) {
                                                    try {
                                                        var btn = document.querySelector(selectors[i]);
                                                        if (btn && btn.style.display !== 'none' && !btn.disabled) {
                                                            var rect = btn.getBoundingClientRect();
                                                            if (rect.width > 0 && rect.height > 0) {
                                                                clickButton(btn, "CSS: " + selectors[i]);
                                                                return;
                                                            }
                                                        }
                                                    } catch(e) {}
                                                }

                                                // 2. Text-content search (handles custom styled divs/buttons perfectly)
                                                try {
                                                    var allElements = document.querySelectorAll('button, a, [class*="skip" i], [id*="skip" i]');
                                                    for (var i = 0; i < allElements.length; i++) {
                                                        var el = allElements[i];
                                                        var text = el.textContent ? el.textContent.trim().toLowerCase() : "";
                                                        
                                                        var matches = false;
                                                        if (text.indexOf('skip') !== -1) {
                                                            if (text === 'skip' || text === 'skip intro' || text === 'skip outro' || text === 'skip ad' ||
                                                                text.indexOf('intro') !== -1 || text.indexOf('outro') !== -1 || 
                                                                text.indexOf('op') !== -1 || text.indexOf('ed') !== -1 || 
                                                                text.indexOf('opening') !== -1 || text.indexOf('ending') !== -1 || 
                                                                text.indexOf('recap') !== -1 || text.indexOf('advertisement') !== -1) {
                                                                matches = true;
                                                            }
                                                        }
                                                        
                                                        if (matches) {
                                                            var rect = el.getBoundingClientRect();
                                                            if (rect.width > 0 && rect.height > 0) {
                                                                clickButton(el, "Text: " + el.textContent);
                                                                break;
                                                            }
                                                        }
                                                    }
                                                } catch(err) {
                                                    console.error("Error in text-based skip button search", err);
                                                }
                                            }

                                            function hookVideoElements() {
                                                var videoElements = document.querySelectorAll('video');
                                                for (var i = 0; i < videoElements.length; i++) {
                                                    var v = videoElements[i];
                                                    
                                                    // Auto play if paused & autoplay is enabled
                                                    if (isAutoPlayEnabled) {
                                                        if (v.paused && !v.dataset.hasAttemptedAutoplay) {
                                                            v.dataset.hasAttemptedAutoplay = "true";
                                                            v.play().catch(function(e) {});
                                                        }
                                                    }

                                                    if (!v.hasAutoResumeListener) {
                                                        v.hasAutoResumeListener = true;
                                                        v.dataset.wasPlaying = "true";

                                                        v.addEventListener('play', function() {
                                                            v.dataset.wasPlaying = "true";
                                                        });

                                                        v.addEventListener('pause', function() {
                                                            var timeSinceInteraction = Date.now() - lastUserInteractionTime;
                                                            var timeSinceLayoutChange = Date.now() - lastLayoutChangeEventTime;
                                                            console.log("Video pause inside injected iframe. Time since interaction: " + timeSinceInteraction + "ms, layout change: " + timeSinceLayoutChange + "ms");
                                                            
                                                            var isLayoutChangePause = (timeSinceLayoutChange < 2000);
                                                            var isSystemPause = (timeSinceInteraction > 400);
                                                            
                                                            if ((isLayoutChangePause || isSystemPause) && v.dataset.wasPlaying === "true" && isAutoPlayEnabled) {
                                                                console.log("Injected iframe system/layout pause detected. Restoring playback...");
                                                                setTimeout(function() {
                                                                    v.play().catch(function(e) {
                                                                        console.log("Failed to auto-resume in injected iframe: ", e);
                                                                    });
                                                                }, 50);
                                                            } else if (!isLayoutChangePause && timeSinceInteraction <= 400) {
                                                                v.dataset.wasPlaying = "false";
                                                            }
                                                        });
                                                    }

                                                    // Ended listener for subframe -> topframe communication
                                                    if (!v.hasEndedPostMessageListener) {
                                                        v.hasEndedPostMessageListener = true;
                                                        v.addEventListener('ended', function() {
                                                            console.log("Video ended inside injected iframe. Posting message to parent.");
                                                            try {
                                                                window.parent.postMessage({ type: 'custom_ended' }, '*');
                                                            } catch(e) {}
                                                        });
                                                    }

                                                    // Timeupdate progress listener for subframe -> topframe communication
                                                    if (!v.hasProgressPostMessageListener) {
                                                        v.hasProgressPostMessageListener = true;
                                                        v.addEventListener('timeupdate', function() {
                                                            if (v.duration > 0) {
                                                                var progress = v.currentTime / v.duration;
                                                                try {
                                                                    window.parent.postMessage({ type: 'custom_progress', progress: progress }, '*');
                                                                } catch(e) {}
                                                            }
                                                        });
                                                    }
                                                }
                                            }

                                            setInterval(autoSkipIntroOutro, 400);
                                            setInterval(hookVideoElements, 500);
                                        })();
                                    </script>
                                """.trimIndent()
                                
                                val modifiedHtml = if (originalHtml.contains("<head>", ignoreCase = true)) {
                                    originalHtml.replaceFirst("<head>", "<head>\n$scriptToInject", ignoreCase = true)
                                } else if (originalHtml.contains("<body>", ignoreCase = true)) {
                                    originalHtml.replaceFirst("<body>", "<body>\n$scriptToInject", ignoreCase = true)
                                } else {
                                    originalHtml + "\n$scriptToInject"
                                }
                                
                                val stream = java.io.ByteArrayInputStream(modifiedHtml.toByteArray(charset(encoding)))
                                
                                val pureMimeType = if (contentType.contains(";")) {
                                    contentType.substringBefore(";").trim()
                                } else {
                                    contentType.trim()
                                }
                                
                                return android.webkit.WebResourceResponse(
                                    pureMimeType,
                                    encoding,
                                    responseCode,
                                    conn.responseMessage ?: "OK",
                                    mapOf("Access-Control-Allow-Origin" to "*"),
                                    stream
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("WebViewIntercept", "Error in fetchAndInjectHtml for $urlStr", e)
                                return null
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            if (request.method.equals("GET", ignoreCase = true) && shouldInterceptForHtmlInjection(url)) {
                                return fetchAndInjectHtml(url, request.requestHeaders)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageLoading = false
                            isUrlChanging = false
                            if (url == null || url == "about:blank") return
                            // Inject initial progress to restore and loadedPlaybackToken
                            val escapedToken = currentPlaybackToken.replace("'", "\\'")
                            view?.evaluateJavascript(
                                """
                                window.loadedPlaybackToken = '$escapedToken';
                                window.initialProgressToRestoreToken = '$escapedToken';
                                window.initialProgressToRestoreValue = $currentInitialProgress;
                                window.isAutoPlayEnabled = $currentIsAutoPlayEnabled;
                                window.isAutoSkipEnabled = $currentIsAutoSkipEnabled;
                                """.trimIndent(), null
                            )
                            // Periodic injector script to watch video tags and listen to postMessage events
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    // 1. Direct Video Element Hook (Same-Origin)
                                    function checkAndHook() {
                                        var v = document.querySelector('video');
                                        if (v) {
                                            if (!v.hasEndedListener) {
                                                v.hasEndedListener = true;
                                                v.addEventListener('ended', function() {
                                                    console.log("Direct video ended event triggered.");
                                                    if (window.JSBridge && window.JSBridge.triggerVideoEnded) {
                                                        window.JSBridge.triggerVideoEnded(window.loadedPlaybackToken || '');
                                                    }
                                                });
                                            }
                                            
                                            if (!v.hasTimeUpdateListener) {
                                                v.hasTimeUpdateListener = true;
                                                v.addEventListener('timeupdate', function() {
                                                    if (v.duration > 0) {
                                                        var progress = v.currentTime / v.duration;
                                                        if (window.JSBridge && window.JSBridge.triggerVideoProgress) {
                                                            window.JSBridge.triggerVideoProgress(progress, window.loadedPlaybackToken || '');
                                                        }
                                                    }
                                                });
                                            }
                                            
                                            // Auto-restore progress if available (Highly robust seek-restoration)
                                            if (!v.hasRestoredProgressInit) {
                                                v.hasRestoredProgressInit = true;
                                                var targetProgress = 0;
                                                if (window.loadedPlaybackToken && window.initialProgressToRestoreToken === window.loadedPlaybackToken) {
                                                    targetProgress = (typeof window.initialProgressToRestoreValue === 'number') ? window.initialProgressToRestoreValue : 0;
                                                }
                                                if (targetProgress < 0 || targetProgress >= 0.95) targetProgress = 0;
                                                
                                                var desiredTime = -1;
                                                var restoreAttempts = 0;
                                                
                                                var forceRestore = function() {
                                                    if (v.duration > 0) {
                                                        desiredTime = v.duration * targetProgress;
                                                        v.currentTime = desiredTime;
                                                        console.log("Forced seek to desired time: " + desiredTime);
                                                    }
                                                };
                                                
                                                v.addEventListener('loadedmetadata', forceRestore);
                                                v.addEventListener('canplay', forceRestore);
                                                v.addEventListener('play', forceRestore);
                                                
                                                var checkInterval = setInterval(function() {
                                                    if (v.duration > 0) {
                                                        desiredTime = v.duration * targetProgress;
                                                        if (Math.abs(v.currentTime - desiredTime) > 5 && restoreAttempts < 6) {
                                                            v.currentTime = desiredTime;
                                                            restoreAttempts++;
                                                            console.log("Corrected playback position to: " + desiredTime + " (Attempt " + restoreAttempts + ")");
                                                        } else {
                                                            clearInterval(checkInterval);
                                                            v.removeEventListener('loadedmetadata', forceRestore);
                                                            v.removeEventListener('canplay', forceRestore);
                                                            v.removeEventListener('play', forceRestore);
                                                        }
                                                    }
                                                }, 500);
                                                
                                                setTimeout(function() {
                                                    clearInterval(checkInterval);
                                                }, 4000);
                                            }
                                            
                                            // Auto-play / prime video at startup if paused
                                            if (window.isAutoPlayEnabled) {
                                                if (v.paused && !v.dataset.hasAttemptedAutoplay) {
                                                    v.dataset.hasAttemptedAutoplay = "true";
                                                    v.play().catch(function(e) {});
                                                    var attempts = 0;
                                                    var playInterval = setInterval(function() {
                                                        if (v.paused && attempts < 10) {
                                                            v.play().catch(function(e) {});
                                                            attempts++;
                                                        } else {
                                                            clearInterval(playInterval);
                                                        }
                                                    }, 300);
                                                }
                                            }
                                        }

                                        if (window.isAutoPlayEnabled) {
                                            // Try auto-clicking play overlay buttons, custom players, and big play icons
                                            var playButtonSelectors = [
                                                '.jw-display-icon-container',
                                                '.jw-icon-display',
                                                '.plyr__control--overlaid',
                                                '.vjs-big-play-button',
                                                '.play-button',
                                                'button.play',
                                                'div.play',
                                                '[aria-label="Play"]',
                                                '[class*="play-button"]',
                                                '[class*="play_button"]',
                                                '[id*="play-button"]'
                                            ];
                                            playButtonSelectors.forEach(function(sel) {
                                                var btn = document.querySelector(sel);
                                                if (btn && btn.style.display !== 'none' && !btn.hasBeenAutoClicked) {
                                                    btn.hasBeenAutoClicked = true;
                                                    try {
                                                        btn.click();
                                                        console.log("Simulated click on " + sel);
                                                    } catch(e) {}
                                                }
                                            });
                                        }

                                        if (window.isAutoSkipEnabled) {
                                            // Auto-click skip intro, outro, skip ad buttons if they appear in top frame
                                            var skipButtonSelectors = [
                                                '.skip-button',
                                                '.jw-skip',
                                                '.ani-skip',
                                                '.skip-intro',
                                                '.skip-op',
                                                '.skip-ed',
                                                '[class*="skip"]',
                                                '[id*="skip"]'
                                            ];
                                            skipButtonSelectors.forEach(function(sel) {
                                                var btn = document.querySelector(sel);
                                                if (btn && btn.style.display !== 'none' && !btn.hasBeenAutoSkipClicked) {
                                                    btn.hasBeenAutoSkipClicked = true;
                                                    try {
                                                        btn.click();
                                                        console.log("Simulated skip click on " + sel);
                                                    } catch(e) {}
                                                }
                                            });
                                        }
                                    }
                                    setInterval(checkAndHook, 600);
                                    
                                    // Periodically check for text-content based skip buttons in top frame / same-origin frames
                                    setInterval(function() {
                                        if (!window.isAutoSkipEnabled) return;
                                        try {
                                            var allElements = document.querySelectorAll('button, a, [class*="skip" i], [id*="skip" i]');
                                            for (var i = 0; i < allElements.length; i++) {
                                                var el = allElements[i];
                                                var text = el.textContent ? el.textContent.trim().toLowerCase() : "";
                                                if ((text === 'skip intro' || text === 'skip outro' || text === 'skip advertisement' || text.indexOf('skip intro') !== -1 || text.indexOf('skip outro') !== -1) && !el.hasBeenAutoSkipClicked) {
                                                    var rect = el.getBoundingClientRect();
                                                    if (rect.width > 0 && rect.height > 0) {
                                                        el.hasBeenAutoSkipClicked = true;
                                                        console.log("Simulated text-based skip click on: " + el.textContent);
                                                        el.click();
                                                        var clickEv = new MouseEvent('click', { view: window, bubbles: true, cancelable: true });
                                                        el.dispatchEvent(clickEv);
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch(err) {}
                                    }, 500);

                                    // Simulate mouse click on body to satisfy user interaction requirements
                                    setTimeout(function() {
                                        try {
                                            var clickEvent = new MouseEvent('click', {
                                                view: window,
                                                bubbles: true,
                                                cancelable: true
                                            });
                                            document.body.dispatchEvent(clickEvent);
                                            console.log("Simulated document body click.");
                                        } catch(e) {}
                                    }, 800);

                                    // 2. Cross-Origin PostMessage Hook (Listen to iframe events)
                                    if (!window.hasMessageListener) {
                                        window.hasMessageListener = true;
                                        window.addEventListener('message', function(event) {
                                            var data = event.data;
                                            if (data) {
                                                // Handle custom subframe message events first for perfect video state tracking
                                                if (data.type === 'custom_ended') {
                                                    console.log("Custom ended message received from subframe.");
                                                    if (window.JSBridge && window.JSBridge.triggerVideoEnded) {
                                                        window.JSBridge.triggerVideoEnded(window.loadedPlaybackToken || '');
                                                    }
                                                    return;
                                                }
                                                if (data.type === 'custom_progress') {
                                                    if (typeof data.progress === 'number' && data.progress >= 0 && data.progress <= 1) {
                                                        if (window.JSBridge && window.JSBridge.triggerVideoProgress) {
                                                            window.JSBridge.triggerVideoProgress(data.progress, window.loadedPlaybackToken || '');
                                                        }
                                                    }
                                                    return;
                                                }

                                                var strData = "";
                                                if (typeof data === 'string') {
                                                    strData = data;
                                                } else {
                                                    try {
                                                        strData = JSON.stringify(data);
                                                    } catch(e) {}
                                                }
                                                
                                                var lower = strData.toLowerCase();
                                                console.log("PostMessage event data: " + strData);
                                                // Check if event is about video ended / completed / finished from popular players
                                                if (
                                                    lower.includes('"event":"ended"') || 
                                                    lower.includes('"event":"complete"') || 
                                                    lower.includes('"event":"finish"') ||
                                                    lower.includes('"status":"ended"') ||
                                                    lower.includes('"type":"ended"') ||
                                                    (lower.includes('plyr') && lower.includes('ended')) ||
                                                    lower === 'ended' ||
                                                    lower === 'complete' ||
                                                    lower === 'finish'
                                                ) {
                                                    console.log("Video ended match detected in postMessage.");
                                                    if (window.JSBridge && window.JSBridge.triggerVideoEnded) {
                                                        window.JSBridge.triggerVideoEnded(window.loadedPlaybackToken || '');
                                                    }
                                                }
                                                
                                                // Extract progress information from postMessage events
                                                try {
                                                    var obj = (typeof data === 'object') ? data : JSON.parse(data);
                                                    var progress = -1;
                                                    
                                                    if (typeof obj.progress === 'number') {
                                                        progress = obj.progress;
                                                    } else if (typeof obj.percent === 'number') {
                                                        progress = obj.percent;
                                                        if (progress > 1) progress = progress / 100;
                                                    } else if (typeof obj.percentage === 'number') {
                                                        progress = obj.percentage;
                                                    } else if (obj.data && typeof obj.data.percentage === 'number') {
                                                        progress = obj.data.percentage;
                                                    } else if (obj.data && typeof obj.data.percent === 'number') {
                                                        progress = obj.data.percent;
                                                        if (progress > 1) progress = progress / 100;
                                                    } else if (obj.info && typeof obj.info.currentTime === 'number' && typeof obj.info.duration === 'number' && obj.info.duration > 0) {
                                                        progress = obj.info.currentTime / obj.info.duration;
                                                    } else {
                                                        var current = obj.currentTime || obj.time || obj.position || (obj.data && (obj.data.currentTime || obj.data.time || obj.data.seconds)) || (obj.value && (obj.value.currentTime || obj.value.time));
                                                        var duration = obj.duration || (obj.data && obj.data.duration) || (obj.value && obj.value.duration);
                                                        if (typeof current === 'number' && typeof duration === 'number' && duration > 0) {
                                                            progress = current / duration;
                                                        }
                                                    }
                                                    
                                                    if (progress >= 0 && progress <= 1) {
                                                        if (window.JSBridge && window.JSBridge.triggerVideoProgress) {
                                                            window.JSBridge.triggerVideoProgress(progress, window.loadedPlaybackToken || '');
                                                        }
                                                    }
                                                } catch(e) {}
                                            }
                                        });
                                    }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            val failedUrl = request?.url?.toString() ?: ""
                            val errorDesc = error?.description?.toString() ?: ""
                            val errorCode = error?.errorCode ?: 0
                            android.util.Log.e("WebViewError", "Failed URL: $failedUrl, ErrorCode: $errorCode, Error: $errorDesc")
                            
                            if (request?.isForMainFrame == true) {
                                // Ignore aborted/cancelled requests (often triggered when we block ad redirects)
                                if (errorCode == -1 || errorDesc.contains("ERR_ABORTED", ignoreCase = true) || errorDesc.contains("abort", ignoreCase = true) || errorDesc.contains("cancel", ignoreCase = true)) {
                                    return
                                }
                                
                                val failedHost = request.url?.host ?: ""
                                val targetHost = android.net.Uri.parse(currentUrl).host ?: ""
                                
                                // Only trigger load error if the main frame of the actual embed host failed
                                if (failedHost.isNotEmpty() && targetHost.isNotEmpty() && 
                                    (failedHost == targetHost || failedHost.endsWith(".$targetHost") || targetHost.endsWith(".$failedHost"))) {
                                    isPageLoading = false
                                    currentOnLoadError()
                                }
                            }
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            // Strictly return false to prevent any new window creation (popups)
                            return false
                        }

                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("WebViewConsole", "${consoleMessage?.messageLevel()}: ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                            return true
                        }

                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                            super.onShowCustomView(view, callback)
                            if (view != null) {
                                currentOnShowCustomView(view, callback)
                            }
                        }

                        override fun onHideCustomView() {
                            super.onHideCustomView()
                            if (currentIsChangingEpisode || isUrlChanging) {
                                // Ignore hide custom view call to prevent exiting fullscreen while changing episode
                                return
                            }
                            currentOnHideCustomView()
                        }
                    }
                    
                    stateTracker.lastLoadedUrl = url
                    stateTracker.lastRefreshKey = refreshKey
                    if (url == "about:blank") {
                        loadUrl("about:blank")
                    } else if (url.contains("megaplay.buzz")) {
                        loadUrl(url, mapOf("Referer" to "https://megaplay.buzz/"))
                    } else {
                        loadUrl(url)
                    }
                    requestFocus()
                }
            },
            update = { webView ->
                // Check if we need to force reload via refresh button
                val lastKey = stateTracker.lastRefreshKey
                val loadedUrl = stateTracker.lastLoadedUrl
                if (lastKey != refreshKey) {
                    isPageLoading = true
                    stateTracker.lastRefreshKey = refreshKey
                    stateTracker.lastLoadedUrl = url
                    // When explicitly refreshing, we clear session state but keep cache for speed
                    webView.clearHistory()
                    webView.reload()
                } else if (loadedUrl != url) {
                    isPageLoading = true
                    isUrlChanging = true
                    stateTracker.lastLoadedUrl = url
                    currentLoadId++
                    
                    try {
                        webView.stopLoading()
                        webView.onPause()
                    } catch (e: Exception) {}
                    
                    if (url == "about:blank") {
                        webView.loadUrl("about:blank")
                    } else {
                        try {
                            webView.onResume()
                        } catch (e: Exception) {}
                        
                        if (url.contains("megaplay.buzz")) {
                            webView.loadUrl(url, mapOf("Referer" to "https://megaplay.buzz/"))
                        } else {
                            webView.loadUrl(url)
                        }
                    }
                }
            }
        )
        
        if (isPageLoading || isChangingEpisode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PrefetchWebView(url: String) {
    Box(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f) // Invisible off-screen element
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = true // DO NOT autoplay audio/video offscreen
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        databaseEnabled = true
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT // Cache for fast load
                        offscreenPreRaster = true
                        
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    
                    webViewClient = object : WebViewClient() {
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, urlToLoad: String?): Boolean {
                            // Block popup/ad navigations in background prefetcher
                            return true
                        }
                    }
                    
                    if (url.contains("megaplay.buzz")) {
                        loadUrl(url, mapOf("Referer" to "https://megaplay.buzz/"))
                    } else {
                        loadUrl(url)
                    }
                }
            },
            update = { webView ->
                if (webView.url != url) {
                    if (url.contains("megaplay.buzz")) {
                        webView.loadUrl(url, mapOf("Referer" to "https://megaplay.buzz/"))
                    } else {
                        webView.loadUrl(url)
                    }
                }
            },
            onRelease = { webView ->
                webView.destroy()
            }
        )
    }
}
