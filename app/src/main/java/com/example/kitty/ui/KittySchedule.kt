package com.example.kitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kitty.anilist.AiringSlot
import com.example.kitty.anilist.AniListClient
import com.example.kitty.anilist.AnimeSummary
import com.example.kitty.theme.KittyAccent
import com.example.kitty.theme.KittyFaint
import com.example.kitty.theme.KittyMuted
import com.example.kitty.theme.KittyOnAccent
import com.example.kitty.theme.KittyOutlineSoft
import com.example.kitty.theme.KittySurface
import com.example.kitty.theme.KittyText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Adapted from AniKitty (MIT, see NOTICE).

@Composable
fun ScheduleFeed(
    aniList: AniListClient,
    onBack: () -> Unit,
    onOpen: (AnimeSummary) -> Unit,
    showBack: Boolean = true,
    bottomInset: Dp = 12.dp,
    followedEpisodes: Map<Int, Int> = emptyMap(),
) {
    val days = remember { upcomingDays(5) }
    var selected by remember { mutableIntStateOf(0) }
    var slots by remember { mutableStateOf<List<AiringSlot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val clock = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var onlyMine by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            aniList.airingSchedule(
                fromSec = days.first().timeInMillis / 1000L,
                toSec = days.last().clone().let { end ->
                    (end as Calendar).add(Calendar.DAY_OF_YEAR, 1)
                    end.timeInMillis / 1000L
                },
            )
        }.onSuccess { slots = it }
            .onFailure { error = it.message ?: "Could not load the schedule" }
        loading = false
    }

    val dayStart = days[selected].timeInMillis
    val dayEnd = dayStart + 24L * 60L * 60L * 1000L
    val visible = slots.filter { slot ->
        val ms = slot.airingAtEpochSec * 1000L
        val onDay = ms in dayStart until dayEnd
        val mine = !onlyMine || followedEpisodes.containsKey(slot.anime.id)
        onDay && mine
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = bottomInset),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = KittyText)
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(
                    "Airing schedule",
                    color = KittyText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Next five days",
                    color = KittyMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (followedEpisodes.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                val shape = RoundedCornerShape(999.dp)
                Text(
                    if (onlyMine) "My list" else "All airing",
                    color = if (onlyMine) KittyOnAccent else KittyText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (onlyMine) KittyAccent else KittySurface)
                        .border(1.dp, if (onlyMine) KittyAccent else KittyOutlineSoft, shape)
                        .clickable { onlyMine = !onlyMine }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(KittySurface)
                .border(1.dp, KittyOutlineSoft, RoundedCornerShape(16.dp))
                .padding(4.dp),
        ) {
            days.forEachIndexed { index, day ->
                val on = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) KittyAccent else KittySurface)
                        .clickable { selected = index }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        day.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                            .orEmpty()
                            .uppercase(Locale.getDefault()),
                        color = if (on) KittyOnAccent else KittyFaint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        day.get(Calendar.DAY_OF_MONTH).toString(),
                        color = if (on) KittyOnAccent else KittyText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KittyAccent)
            }
            error != null && slots.isEmpty() -> Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            visible.isEmpty() -> Text(
                if (onlyMine) "Nothing from your list airs this day." else "Nothing airing this day.",
                color = KittyMuted,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { "${it.anime.id}-${it.episode}-${it.airingAtEpochSec}" }) { slot ->
                    ScheduleRow(
                        slot,
                        clock.format(Date(slot.airingAtEpochSec * 1000L)),
                        catchUpLabel(slot.episode, followedEpisodes[slot.anime.id]),
                        onOpen,
                    )
                }
            }
        }
    }
}

/** [watchedEpisode] is episodes finished. 0 means on the list but nothing finished, so episode 1 is New. */
private fun catchUpLabel(airingEpisode: Int, watchedEpisode: Int?): String? {
    if (watchedEpisode == null) return null
    val behind = airingEpisode - watchedEpisode.coerceAtLeast(0)
    return when {
        behind > 1 -> "$behind behind"
        behind == 1 -> "New"
        else -> "On list"
    }
}

@Composable
private fun ScheduleRow(
    slot: AiringSlot,
    timeLabel: String,
    listLabel: String?,
    onOpen: (AnimeSummary) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KittySurface)
            .border(1.dp, KittyOutlineSoft, RoundedCornerShape(16.dp))
            .clickable { onOpen(slot.anime) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = slot.anime.coverImageUrl,
            contentDescription = slot.anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(48.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KittyOutlineSoft),
        )
        Spacer(modifier = Modifier.width(12.dp))
        val now = System.currentTimeMillis() / 1000L
        val soon = slot.airingAtEpochSec - now in 0..(45 * 60)
        val aired = slot.airingAtEpochSec < now
        Column(modifier = Modifier.weight(1f)) {
            Text(
                slot.anime.title,
                color = KittyText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Episode ${slot.episode}",
                color = KittyMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!listLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listLabel,
                    color = KittyOnAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(KittyAccent)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (soon) {
                Text(
                    "Soon",
                    color = KittyOnAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(KittyAccent)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                timeLabel,
                color = if (aired) KittyFaint else KittyText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (aired) KittySurface else KittyAccent.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun upcomingDays(count: Int): List<Calendar> {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return List(count) { offset ->
        (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
    }
}
