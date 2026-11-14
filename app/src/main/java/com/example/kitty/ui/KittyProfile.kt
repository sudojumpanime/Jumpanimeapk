package com.example.kitty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.kitty.anilist.AniListViewer
import com.example.kitty.theme.KittyAccent
import com.example.kitty.theme.KittyFaint
import com.example.kitty.theme.KittyMuted
import com.example.kitty.theme.KittyOnAccent
import com.example.kitty.theme.KittyOutlineSoft
import com.example.kitty.theme.KittySurface
import com.example.kitty.theme.KittySurfaceElevated
import com.example.kitty.theme.KittyText

private fun AniListViewer.daysWatched() = minutesWatched / 1440
private fun AniListViewer.scoreText() = if (meanScore <= 0.0) "—" else meanScore.toInt().toString()

@Composable
private fun ProfileAvatar(name: String, avatarUrl: String?, size: Int, ringColor: Color = Color.Transparent) {
    val shape = CircleShape
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .border(2.dp, ringColor, shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .background(KittyAccent)
                .border(2.dp, ringColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase().ifBlank { "?" },
                color = KittyOnAccent,
                fontWeight = FontWeight.Black,
                fontSize = (size / 3).sp,
            )
        }
    }
}

@Composable
private fun RowScope.ProfileStat(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = KittyText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(label, color = KittyMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Small card shown when you tap the avatar on Home: a quick look, with a button to the full Account tab. */
@Composable
fun KittyProfilePopup(
    profile: AniListViewer?,
    onDismiss: () -> Unit,
    onViewAccount: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(KittySurface)
                .border(1.dp, KittyOutlineSoft, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (profile == null) {
                ProfileAvatar("?", null, 64)
                Spacer(Modifier.height(12.dp))
                Text("Not signed in", color = KittyText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sign in with AniList to sync your lists and write comments.",
                    color = KittyMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onDismiss(); onSignIn() },
                    colors = ButtonDefaults.buttonColors(containerColor = KittyAccent, contentColor = KittyOnAccent),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign in with AniList", fontWeight = FontWeight.Bold) }
            } else {
                ProfileAvatar(profile.name, profile.avatarUrl, 64)
                Spacer(Modifier.height(10.dp))
                Text(profile.name, color = KittyText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("on AniList", color = KittyFaint, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ProfileStat(profile.animeCount.toString(), "Anime")
                    ProfileStat(profile.episodesWatched.toString(), "Episodes")
                    ProfileStat(profile.daysWatched().toString(), "Days")
                    ProfileStat(profile.scoreText(), "Score")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${profile.watching} watching · ${profile.planning} planned · ${profile.completed} completed",
                    color = KittyMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onDismiss(); onViewAccount() },
                    colors = ButtonDefaults.buttonColors(containerColor = KittyAccent, contentColor = KittyOnAccent),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("View account", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDismiss(); onSignOut() },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, KittyOutlineSoft),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KittyMuted),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign out", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Banner + avatar + stats, as on AniKitty's Account tab. */
@Composable
fun KittyProfileCard(
    profile: AniListViewer,
    onOpenList: (String) -> Unit = {},
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KittySurface)
            .border(1.dp, KittyOutlineSoft, shape),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            if (!profile.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(KittySurfaceElevated))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-28).dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ProfileAvatar(profile.name, profile.avatarUrl, 72, ringColor = KittySurface)
            Column(modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                Text(profile.name, color = KittyText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${profile.name}", color = KittyMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(
            modifier = Modifier
                .offset(y = (-12).dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 14.dp),
        ) {
            if (!profile.about.isNullOrBlank()) {
                Text(profile.about.orEmpty(), color = KittyMuted, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ProfileStat(profile.animeCount.toString(), "Anime")
                ProfileStat(profile.episodesWatched.toString(), "Episodes")
                ProfileStat(profile.daysWatched().toString(), "Days")
                ProfileStat(profile.scoreText(), "Score")
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ProfileStat(profile.watching.toString(), "Watching") { onOpenList("watching") }
                ProfileStat(profile.completed.toString(), "Completed") { onOpenList("done") }
                ProfileStat(profile.planning.toString(), "Planning") { onOpenList("plan") }
                ProfileStat(profile.paused.toString(), "Paused") { onOpenList("paused") }
                ProfileStat(profile.dropped.toString(), "Dropped") { onOpenList("dropped") }
            }
        }
    }
}
