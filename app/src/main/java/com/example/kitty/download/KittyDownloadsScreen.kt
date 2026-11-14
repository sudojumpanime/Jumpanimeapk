package com.example.kitty.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Lists saved / in-progress episodes. Play, retry and delete live here. */
@Composable
fun KittyDownloadsScreen(
    manager: KittyDownloadManager,
    onPlay: (KittyDownload) -> Unit,
    onBack: () -> Unit,
) {
    val items by manager.items.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color.White) }
        }
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (items.isEmpty()) {
            Text(
                text = "Nothing saved yet. Open a show and tap the download icon on an episode.",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items.sortedBy { it.animeTitle + "%05d".format(Locale.ROOT, it.episode) }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "${item.animeTitle} · Ep ${item.episode}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val statusText = when (item.status) {
                        KittyDownloadStatus.QUEUED -> "Waiting…"
                        KittyDownloadStatus.DOWNLOADING ->
                            "Downloading ${(item.progress * 100).toInt()}% · ${formatSize(item.sizeBytes)}"
                        KittyDownloadStatus.DONE -> "Saved · ${formatSize(item.sizeBytes)}"
                        KittyDownloadStatus.FAILED -> item.error ?: "Failed"
                    }
                    Text(
                        text = statusText,
                        color = if (item.status == KittyDownloadStatus.FAILED) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (item.status == KittyDownloadStatus.DOWNLOADING || item.status == KittyDownloadStatus.QUEUED) {
                        LinearProgressIndicator(
                            progress = { item.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (item.status == KittyDownloadStatus.DONE) {
                            TextButton(onClick = { onPlay(item) }) { Text("Play", color = Color.White) }
                        }
                        if (item.status == KittyDownloadStatus.FAILED) {
                            TextButton(onClick = { manager.retry(item.key) }) { Text("Retry", color = Color.White) }
                        }
                        TextButton(onClick = { manager.delete(item.key) }) {
                            Text(
                                if (item.status == KittyDownloadStatus.DONE) "Delete" else "Cancel",
                                color = Color(0xFFFF8A80),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
}
