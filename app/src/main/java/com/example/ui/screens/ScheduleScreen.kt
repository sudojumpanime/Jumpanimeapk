package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import coil.compose.AsyncImage
import com.example.ui.components.WaifuBackground
import com.example.ui.viewmodel.ScheduleState
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.ui.viewmodel.ScheduleAnimeUiModel
import com.example.ui.viewmodel.DateItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.shimmerEffect
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    notificationCount: Int = 0,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscribedAnimeIds by viewModel.subscribedAnimeIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        // Load schedule if it hasn't been loaded or is currently loading
        viewModel.loadSchedule()
    }

    WaifuBackground(
        imageUrl = "https://res.cloudinary.com/di0fllnd9/image/upload/v1782433692/Miku_Nakano_Wedding_nczvsv.jpg",
        overlayAlpha = 0.85f
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp, 24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Schedule",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    },
                    actions = {
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is ScheduleState.Loading -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(5) {
                            ScheduleItemSkeleton()
                        }
                    }
                }
                is ScheduleState.Error -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadSchedule(forceRefresh = true) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is ScheduleState.Success -> {
                    // 30 days / 1 month horizontal scrolling date list
                    val listState = rememberLazyListState()
                    
                    // Automatically scroll to Today on first load / initialization of Success State
                    LaunchedEffect(state.selectedIndex) {
                        if (state.dates.isNotEmpty()) {
                            // Center the list by scrolling just slightly to the left of the selected index
                            listState.animateScrollToItem(maxOf(0, state.selectedIndex - 2))
                        }
                    }

                    LazyRow(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .padding(vertical = 12.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(state.dates, key = { _, dayItem -> dayItem.dateNum }) { index, dayItem ->
                            val isSelected = state.selectedIndex == index
                            
                            Column(
                                modifier = Modifier
                                    .width(66.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.03f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { viewModel.selectDate(index) }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (dayItem.isToday) "Today" else dayItem.dayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dayItem.dateNum,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (dayItem.isToday && !isSelected) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    // Calendar Subtitle & Timezone Indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tz = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT)
                        val selectedDateText = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(state.dates[state.selectedIndex].date)
                        
                        Text(
                            text = selectedDateText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Timezone: $tz",
                            color = Color.White.copy(alpha = 0.35f),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }

                    // If Selected Date is Today, we show a red Current Time / Live indicator line!
                    if (state.dates[state.selectedIndex].isToday) {
                        val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            )
                            Text(
                                text = "Current Time: $timeString",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Anime Releases List
                    val dayItems = state.filteredAnime
                    if (dayItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No releases scheduled for this day",
                                color = Color.White.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(dayItems, key = { it.id }) { anime ->
                                val isSubscribed = subscribedAnimeIds.contains(anime.id)
                                ScheduleItemRealCard(
                                    anime = anime,
                                    isSubscribed = isSubscribed,
                                    onToggleAlert = {
                                        val nowSubscribed = viewModel.toggleAnimeAlert(anime.id, anime.title, anime.poster, anime.episodeNumText)
                                        val text = if (nowSubscribed) {
                                            "🔔 Alert set for ${anime.title}!"
                                        } else {
                                            "🔕 Alert removed for ${anime.title}."
                                        }
                                        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onClick = {
                                        onAnimeClick(anime.id.replace("mal_", ""))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun ScheduleItemRealCard(
    anime: ScheduleAnimeUiModel,
    isSubscribed: Boolean,
    onToggleAlert: () -> Unit,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Airing Time Indicator Column
            Column(
                modifier = Modifier
                    .width(66.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = anime.localAiringTimeText,
                    color = if (!anime.isAired) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (!anime.isAired) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.06f)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (!anime.isAired) "UPCOMING" else "AIRED",
                        color = if (!anime.isAired) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            val context = LocalContext.current
            val imageRequest = remember(anime.poster) {
                coil.request.ImageRequest.Builder(context)
                    .data(anime.poster)
                    .crossfade(true)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }

            // Rounded Poster Image
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp, 80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = anime.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = anime.genres.take(2).joinToString(", "),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (anime.rating != null && anime.rating > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format("%.1f", anime.rating),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!anime.episodeNumText.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = anime.episodeNumText.uppercase(java.util.Locale.getDefault()),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    
                    Text(
                        text = anime.statusText,
                        color = if (!anime.isAired) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Notification alert button - only show for upcoming releases
            if (!anime.isAired) {
                IconButton(
                    onClick = onToggleAlert
                ) {
                    Icon(
                        imageVector = if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                        contentDescription = if (isSubscribed) "Unsubscribe from alert" else "Subscribe to alert",
                        tint = if (isSubscribed) {
                            Color(0xFFFFD700) // Beautiful bright gold when subscribed!
                        } else {
                            Color.White.copy(alpha = 0.45f) // Subtle grey for upcoming unsubscribed
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}

@Composable
fun ScheduleItemSkeleton() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(66.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp, 20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(54.dp, 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(50.dp, 75.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(18.dp)
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
