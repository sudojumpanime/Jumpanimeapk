package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kitty.anilist.AnimePerson
import com.example.kitty.anilist.AnimeSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extra title details AniKitty shows (adapted from AniKitty, MIT — see NOTICE):
 * studio / season / duration facts, trailer, characters, staff and outside links.
 * Recommendations and related seasons reuse the screen's existing rows.
 */
@Composable
fun KittyDetailSections(detail: AnimeSummary) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // No app can open it — ignore.
        }
    }

    val facts = ArrayList<Pair<String, String>>()
    if (detail.studios.isNotEmpty()) facts.add("Studio" to detail.studios.joinToString(", "))
    detail.seasonLabel()?.let { facts.add("Season" to it) }
    detail.durationMinutes?.let { facts.add("Episode length" to "$it min") }
    detail.romajiTitle?.let { facts.add("Romaji" to it) }
    detail.nativeTitle?.let { facts.add("Native" to it) }
    detail.popularity?.let { facts.add("Followers" to String.format(Locale.getDefault(), "%,d", it)) }
    detail.nextAiring?.let {
        val whenText = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(it.airingAtEpochSec * 1000L))
        facts.add("Next episode" to "Ep ${it.episode} · $whenText")
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (facts.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((label, value) in facts) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.width(110.dp)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        val trailerUrl = detail.trailer?.watchUrl()
        if (trailerUrl != null) {
            Text(
                text = "▶  Watch trailer",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { openUrl(trailerUrl) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        KittyPeopleRow("Characters", detail.characters)
        KittyPeopleRow("Staff", detail.staff)

        if (detail.externalLinks.isNotEmpty()) {
            Text(
                text = "Links",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(detail.externalLinks) { link ->
                    Text(
                        text = link.site,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { openUrl(link.url) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KittyPeopleRow(title: String, people: List<AnimePerson>) {
    if (people.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(people) { person ->
            Column(
                modifier = Modifier.width(84.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProgressiveImage(
                    url = person.imageUrl,
                    contentDescription = person.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    targetWidth = 160,
                    targetHeight = 160
                )
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                person.role?.let {
                    Text(
                        text = it.lowercase().replaceFirstChar { c -> c.titlecase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
