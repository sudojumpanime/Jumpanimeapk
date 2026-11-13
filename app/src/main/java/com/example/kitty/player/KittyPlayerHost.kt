package com.example.kitty.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kitty.anilistIdOrNull
import com.example.kitty.servers.PlayableSource
import com.example.kitty.servers.ServerCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface KittyResolveState {
    data class Loading(val message: String) : KittyResolveState
    data class Failed(val message: String) : KittyResolveState
    data class Ready(val sources: List<PlayableSource>) : KittyResolveState
}

/**
 * Finds a playable stream for an AniList title (trying every installed source in turn)
 * and then shows AniKitty's Media3 player. Adapted from AniKitty (MIT, see NOTICE).
 */
@Composable
fun KittyPlayerHost(
    catalog: ServerCatalog,
    animeId: String,
    animeTitle: String,
    startEpisode: Int,
    lastEpisode: Int?,
    onBack: () -> Unit,
    saveProgress: suspend (episode: Int, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val anilistId = remember(animeId) { animeId.anilistIdOrNull() }

    var episode by remember { mutableIntStateOf(startEpisode.coerceAtLeast(1)) }
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<KittyResolveState>(KittyResolveState.Loading("Finding a stream…")) }

    LaunchedEffect(anilistId, episode, attempt) {
        if (anilistId == null) {
            state = KittyResolveState.Failed("This title can't be played here.")
            return@LaunchedEffect
        }
        state = KittyResolveState.Loading("Finding a stream…")
        state = try {
            val result = catalog.resolveWithFailover(
                anilistId = anilistId,
                episode = episode,
                audio = "sub",
                titleHint = animeTitle,
                onAttempt = { name, index, total ->
                    state = KittyResolveState.Loading("Trying $name (${index + 1} of $total)…")
                },
            )
            KittyResolveState.Ready(result.sources)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KittyResolveState.Failed(e.message ?: "Couldn't load this episode")
        }
    }

    val prefs = remember { context.getSharedPreferences("kitty_progress", Context.MODE_PRIVATE) }
    var lastSavedAt by remember { mutableLongStateOf(0L) }

    when (val current = state) {
        is KittyResolveState.Loading -> KittyCenteredMessage(
            message = current.message,
            showSpinner = true,
            onRetry = null,
            onBack = onBack,
        )

        is KittyResolveState.Failed -> KittyCenteredMessage(
            message = current.message,
            showSpinner = false,
            onRetry = { attempt += 1 },
            onBack = onBack,
        )

        is KittyResolveState.Ready -> {
            val posKey = "${animeId}_$episode"
            val savedPos = remember(posKey) { prefs.getLong(posKey, 0L) }
            val next: (() -> Unit)? =
                if (lastEpisode != null && episode < lastEpisode) ({ episode += 1 }) else null
            key(posKey) {
                PlayerScreen(
                    title = animeTitle,
                    episode = episode,
                    sources = current.sources,
                    anilistId = anilistId ?: 0,
                    titleHint = animeTitle,
                    loadSkip = { id, ep, hint -> catalog.anikotoSkipTimes(id, ep, hint) },
                    startPositionMs = savedPos,
                    onClose = onBack,
                    onNextEpisode = next,
                    onProgress = { pos, dur ->
                        val now = System.currentTimeMillis()
                        if (now - lastSavedAt > 5_000L) {
                            lastSavedAt = now
                            val finished = dur > 0 && pos > dur - 60_000L
                            prefs.edit().putLong(posKey, if (finished) 0L else pos).apply()
                            val ep = episode
                            scope.launch { runCatching { saveProgress(ep, pos, dur) } }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun KittyCenteredMessage(
    message: String,
    showSpinner: Boolean,
    onRetry: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(20.dp))
            }
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRetry != null) {
                    Button(onClick = onRetry) { Text("Try again") }
                }
                TextButton(onClick = onBack) { Text(if (showSpinner) "Cancel" else "Back", color = Color.White) }
            }
        }
    }
}
