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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import coil.compose.AsyncImage
import com.example.ui.components.ProgressiveImage
import com.example.data.model.Anime
import com.example.data.model.ContinueWatchingItem
import com.example.ui.theme.Slate100
import com.example.ui.viewmodel.HomeState
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.components.AnimeCardSkeleton
import com.example.ui.components.HeroSkeleton
import com.example.ui.components.JumpAnimeLogo
import com.example.ui.components.WaifuBackground

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.delay

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    notificationCount: Int = 0,
    onReady: () -> Unit = {},
    onAnimeClick: (String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recommendedForYou by viewModel.recommendedForYou.collectAsStateWithLifecycle()
    val recommendedBasedOn by viewModel.recommendedBasedOn.collectAsStateWithLifecycle()
    var selectedAnimeForSheet by remember { mutableStateOf<Anime?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isTopBarVisible by remember { mutableStateOf(true) }

    LaunchedEffect(lazyListState) {
        var lastScrollOffset = 0
        var lastScrollIndex = 0
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex > lastScrollIndex) {
                    isTopBarVisible = false
                } else if (currentIndex < lastScrollIndex) {
                    isTopBarVisible = true
                } else {
                    val delta = currentOffset - lastScrollOffset
                    if (delta > 15) {
                        isTopBarVisible = false
                    } else if (delta < -15) {
                        isTopBarVisible = true
                    }
                }
                lastScrollIndex = currentIndex
                lastScrollOffset = currentOffset
            }
    }

    val topBarOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isTopBarVisible) 0.dp else (-120).dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
    )

    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 10
        }
    }

    val topBarAlpha by animateFloatAsState(
        targetValue = if (isAtTop) 0f else 0.9f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "topBarAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        WaifuBackground(
            imageUrl = "https://static.wikia.nocookie.net/darling-in-the-franxx/images/6/67/Zero_Two_character_design.png",
            overlayAlpha = 0.84f
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { padding ->
        var isRefreshing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    viewModel.refresh()
                    delay(1000)
                    isRefreshing = false
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
            },
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is HomeState.Loading -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 76.dp, bottom = 32.dp)
                    ) {
                        item(contentType = "hero_skeleton") {
                            HeroSkeleton()
                        }
                        
                        item(contentType = "header") {
                            SectionHeader("Trending Now", "Loading...")
                        }
                        item(contentType = "skeleton_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(5, contentType = { "card_skeleton" }) {
                                    AnimeCardSkeleton()
                                }
                            }
                        }


                    }
                }
                is HomeState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                }
                is HomeState.Success -> {
                    LaunchedEffect(Unit) {
                        onReady()
                    }
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = if (state.searchResults != null) 76.dp else 0.dp, bottom = 32.dp)
                    ) {
                        if (state.searchResults != null) {
                            item(contentType = "header") {
                                SectionHeader("Search Results", "${state.searchResults.size} found")
                            }
                            items(state.searchResults.chunked(2), key = { pair -> pair.joinToString("-") { it._id } }, contentType = { "search_result_row" }) { pair ->
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    pair.forEach { anime ->
                                        AnimeCard(anime) { onAnimeClick(anime._id) }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            // Featured Carousel
                            if (state.featured.isNotEmpty()) {
                                item(contentType = "carousel") {
                                    AutoSlidingCarousel(
                                        items = state.featured,
                                        height = 400.dp,
                                        showIndicators = false,
                                        itemContent = { anime ->
                                            FeaturedHero(
                                                anime = anime,
                                                viewModel = viewModel,
                                                onClick = { onAnimeClick(anime._id) }
                                            )
                                        }
                                    )
                                }
                            }

                            if (state.continueWatching.isNotEmpty()) {
                                item(contentType = "header") {
                                    SectionHeader("Continue Watching", "${state.continueWatching.size} Total")
                                }
                                item(contentType = "continue_watching_row") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(state.continueWatching, key = { it.anime }, contentType = { "continue_watching_card" }) { item ->
                                            ContinueWatchingCard(item) {
                                                onContinueWatchingClick(item)
                                            }
                                        }
                                    }
                                }
                            }

                            if (recommendedForYou.isNotEmpty()) {
                                item(contentType = "header") {
                                    SectionHeader("Because you watched $recommendedBasedOn", "Recommended")
                                }
                                item(contentType = "recommended_row") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(recommendedForYou, key = { it._id }, contentType = { "anime_card" }) { anime ->
                                            AnimeCard(anime) { onAnimeClick(anime._id) }
                                        }
                                    }
                                }
                            }

                            // Trending Section
                            val trendingToShow = state.trending.take(5)
                            if (trendingToShow.isNotEmpty()) {
                                item(contentType = "header") {
                                    SectionHeader("Trending Now", "Most Watched")
                                }
                                item(contentType = "trending_row") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Row for top 3
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            trendingToShow.take(3).forEachIndexed { index, anime ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    TrendingRankedCardSmall(anime = anime, rank = index + 1) {
                                                        onAnimeClick(anime._id)
                                                    }
                                                }
                                            }
                                            val remainingSpaceTop = 3 - trendingToShow.take(3).size
                                            if (remainingSpaceTop > 0) {
                                                Spacer(modifier = Modifier.weight(remainingSpaceTop.toFloat()))
                                            }
                                        }
                                        
                                        // Row for bottom 2
                                        if (trendingToShow.size > 3) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Spacer(modifier = Modifier.weight(0.5f))
                                                val bottomTwo = trendingToShow.drop(3).take(2)
                                                bottomTwo.forEachIndexed { index, anime ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        TrendingRankedCardSmall(anime = anime, rank = index + 4) {
                                                            onAnimeClick(anime._id)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.weight(0.5f))
                                            }
                                        }
                                    }
                                }
                            }

                            // Most Popular Section
                            if (state.mostPopular.isNotEmpty()) {
                                item(contentType = "header") {
                                    SectionHeader("Most Popular", "All-Time Hits")
                                }
                                item(contentType = "most_popular_row") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(state.mostPopular, key = { it._id }, contentType = { "anime_card" }) { anime ->
                                            AnimeCard(anime) { onAnimeClick(anime._id) }
                                        }
                                    }
                                }
                            }

                            // Popular This Week Section
                            if (state.popularThisWeek.isNotEmpty()) {
                                item(contentType = "header") {
                                    SectionHeader("Popular This Week", "Trending Hits")
                                }
                                item(contentType = "popular_week_row") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(state.popularThisWeek, key = { it._id }, contentType = { "anime_card" }) { anime ->
                                            AnimeCard(anime) { onAnimeClick(anime._id) }
                                        }
                                    }
                                }
                            }

                            // Popular This Month Section
                            if (state.popularThisMonth.isNotEmpty()) {
                                item(contentType = "header") {
                                    SectionHeader("Popular This Month", "Seasonal Favorites")
                                }
                                item(contentType = "popular_month_row") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(state.popularThisMonth, key = { it._id }, contentType = { "anime_card" }) { anime ->
                                            AnimeCard(anime) { onAnimeClick(anime._id) }
                                        }
                                    }
                                }
                            }

                            // JumpAnime Spotlight (Upcoming & Anticipated)
                            if (state.anilab2Data.isNotEmpty()) {
                                item(contentType = "header") {
                                    SectionHeader("JumpAnime Spotlight", "Highly Anticipated")
                                }
                                item(contentType = "anilab2_row") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(state.anilab2Data, key = { it._id }, contentType = { "anime_card" }) { anime ->
                                            AnimeCard(anime) { onAnimeClick(anime._id) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Custom Compact Top Bar with Scroll Hide/Show
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = topBarOffset.toPx()
                }
                .drawBehind {
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F1115).copy(alpha = topBarAlpha),
                                Color(0xFF0F1115).copy(alpha = topBarAlpha * 0.85f),
                                Color.Transparent
                            )
                        )
                    )
                }
                .statusBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                JumpAnimeLogo(iconSize = 34.dp, textSize = 23f)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNotificationsClick) {
                        BadgedBox(
                            badge = {
                                if (notificationCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text(notificationCount.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Notifications", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                }
            }
        }
    }
    }
}

@Composable
fun <T> AutoSlidingCarousel(
    items: List<T>,
    height: androidx.compose.ui.unit.Dp = 300.dp,
    autoSlideInterval: Long = 5000L,
    showIndicators: Boolean = true,
    itemContent: @Composable (T) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(Unit) {
        while (true) {
            delay(autoSlideInterval)
            val nextPage = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            itemContent(items[page])
        }

        if (showIndicators) {
            // Indicators
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(items.size) { index ->
                    val color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 12.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingRankedCardSmall(anime: Anime, rank: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(2f / 3f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(Color(0xFF131313))
            ) {
                ProgressiveImage(
                    url = anime.poster,
                    contentDescription = anime.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = 240,
                    targetHeight = 360
                )

                // Beautiful ranking number overlay inside top-left corner with gradient
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "#$rank",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Category badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    val genreTag = anime.genres.firstOrNull() ?: anime.type ?: "Anime"
                    Text(
                        text = genreTag,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Text(
                text = anime.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                ),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun TrendingRankedCard(anime: Anime, rank: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .bounceClick(onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(2f / 3f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF131313))
            ) {
                ProgressiveImage(
                    url = anime.poster,
                    contentDescription = anime.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = 320,
                    targetHeight = 480
                )

                // Beautiful ranking number overlay inside top-left corner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "#$rank",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Category badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val genreTag = anime.genres.firstOrNull() ?: anime.type ?: "Anime"
                    Text(
                        text = genreTag,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Text(
                text = anime.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}


@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (subtitle != null) {
            Text(
                text = subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.Start)
        )
    }
}

@Composable
fun ContinueWatchingCard(item: ContinueWatchingItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .padding(end = 16.dp)
            .bounceClick(onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp, 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                ProgressiveImage(
                    url = item.animePoster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = 240,
                    targetHeight = 168
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.animeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Episode ${item.episodeNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate100.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                val progressValue = if (item.progress > 0f) item.progress else 0.4f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            }
        }
    }
}

@Composable
fun FeaturedHero(anime: Anime, viewModel: HomeViewModel, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        shape = RoundedCornerShape(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProgressiveImage(
                url = anime.banner ?: anime.poster,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray.copy(alpha = 0.5f)),
                contentScale = ContentScale.Crop,
                targetWidth = 1000,
                targetHeight = 600
            )
            // Premium Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Title
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 22.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags/Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!anime.type.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = anime.type.uppercase(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (anime.rating != null && anime.rating > 0) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = String.format("%.1f", anime.rating),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Metadata Row (Year, Status, Episodes)
                val statusText = anime.status ?: ""
                val yearText = anime.year?.toString() ?: ""
                val epCountText = if (anime.episodesCount > 0) "${anime.episodesCount} Episodes" else ""
                val metaList = listOf(yearText, statusText, epCountText).filter { it.isNotEmpty() }
                if (metaList.isNotEmpty()) {
                    Text(
                        text = metaList.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Genres Text
                if (anime.genres.isNotEmpty()) {
                    Text(
                        text = anime.genres.take(3).joinToString(", "),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Netflix-style row of actions: Share, Details (White pill), My List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share Column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, anime.title)
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Stream ${anime.title} on JumpAnime! Download the app at https://jumpanimedownload.pages.dev")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share anime"))
                            }
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Share",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Details Button
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Details",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // My List Column
                    var isSaved by remember { mutableStateOf(false) }
                    LaunchedEffect(anime._id) {
                        isSaved = viewModel.isInWatchlist(anime._id)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                viewModel.toggleWatchlist(anime) {
                                    isSaved = !isSaved
                                }
                            }
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "My List",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isSaved) "In List" else "My List",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .bounceClick(onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(Color(0xFF131313))
        ) {
            ProgressiveImage(
                url = anime.poster,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 240,
                targetHeight = 360
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
        Text(
            text = anime.title,
            modifier = Modifier.padding(horizontal = 2.dp),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    )
    this.then(
        Modifier
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    )
}



