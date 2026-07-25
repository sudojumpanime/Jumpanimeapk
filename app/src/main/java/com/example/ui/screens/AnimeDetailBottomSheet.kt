package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Anime
import com.example.ui.theme.Slate100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailBottomSheet(
    anime: Anime,
    lastEpisode: Int?,
    isLoading: Boolean = false,
    onDismissRequest: () -> Unit,
    onFullDetailsClick: (String) -> Unit,
    onMarkAsWatched: (Anime, Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Image Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = anime.banner ?: anime.poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Linear gradient fade overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceVariant),
                                startY = 100f
                            )
                        )
                )
                
                // Poster small floating
                AsyncImage(
                    model = anime.poster,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 12.dp)
                        .size(70.dp, 105.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Determine total episodes once for use in multiple places
                val totalEpisodes = if (anime.episodesCount > 0) anime.episodesCount else if (anime.seasons.isNotEmpty()) 12 else 0

                // Genres row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    anime.genres.take(3).forEach { genre ->
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                genre,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Title and Meta Info
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", anime.rating ?: 0.0),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                    }

                    Text(
                        text = "${anime.type ?: "TV"} • ${anime.status ?: "Airing"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate100.copy(alpha = 0.6f)
                    )

                    // Total Episodes label
                    if (totalEpisodes > 0) {
                        Badge(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "$totalEpisodes Episodes",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Continue Watching Progress Percentage
                val percentage = if (totalEpisodes > 0) {
                    lastEpisode?.let { ((it.toFloat() / totalEpisodes.toFloat()) * 100).toInt().coerceIn(0, 100) } ?: 0
                } else 0
                
                val progress = if (totalEpisodes > 0) {
                    lastEpisode?.let { it.toFloat() / totalEpisodes.toFloat() } ?: 0f
                } else 0f
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Watch Progress",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (totalEpisodes > 0) "$percentage% completed" else "Progress N/A",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                when {
                                    lastEpisode != null && totalEpisodes > 0 -> "Episode $lastEpisode of $totalEpisodes"
                                    lastEpisode != null -> "Episode $lastEpisode"
                                    else -> "Not started yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate100.copy(alpha = 0.7f)
                            )
                            
                            val nextEpisode = (lastEpisode ?: 0) + 1
                            if (totalEpisodes > 0 && nextEpisode <= totalEpisodes) {
                                Button(
                                    onClick = { onMarkAsWatched(anime, nextEpisode) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (lastEpisode == null) "Start Watching" else "Mark Ep $nextEpisode Watched",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (totalEpisodes > 0) {
                                AssistChip(
                                    onClick = { },
                                    label = { Text("Completed", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary)
                                )
                            } else if (totalEpisodes == 0 && lastEpisode == null) {
                                 // Option to mark first if total unknown
                                 Button(
                                    onClick = { onMarkAsWatched(anime, 1) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Start Watching", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                // Synopsis Heading
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = anime.description ?: "No description available for this anime series.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate100.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 8.dp),
                    lineHeight = 20.sp,
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))

                // Episodes Heading
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (totalEpisodes > 0) "${lastEpisode ?: 0} / $totalEpisodes Watched" else "Tracking Progress",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (totalEpisodes > 0) {
                    val itemsPerRow = 5
                    val episodesRange = (1..totalEpisodes).toList()
                    val rows = episodesRange.chunked(itemsPerRow)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rows.forEach { rowEpisodes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowEpisodes.forEach { epNum ->
                                    val isWatched = lastEpisode != null && epNum <= lastEpisode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isWatched) MaterialTheme.colorScheme.primary
                                                else Color.White.copy(alpha = 0.05f)
                                            )
                                            .clickable {
                                                if (isWatched) {
                                                    // Toggle off: mark previous episode as last watched
                                                    onMarkAsWatched(anime, epNum - 1)
                                                } else {
                                                    // Toggle on: mark this episode as watched
                                                    onMarkAsWatched(anime, epNum)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = epNum.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isWatched) Color.White else Slate100.copy(alpha = 0.8f)
                                            )
                                            if (isWatched) {
                                                Text(
                                                    text = "✓",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowEpisodes.size < itemsPerRow) {
                                    for (i in 0 until (itemsPerRow - rowEpisodes.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No episodes listed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate100.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onFullDetailsClick(anime._id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Full Details", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
