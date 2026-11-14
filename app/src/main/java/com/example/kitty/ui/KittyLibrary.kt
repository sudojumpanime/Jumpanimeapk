package com.example.kitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.data.JumpAnimeRepository
import com.example.kitty.KittyCatalog
import com.example.kitty.kittyId
import com.example.kitty.theme.KittyAccent
import com.example.kitty.theme.KittyFaint
import com.example.kitty.theme.KittyMuted
import com.example.kitty.theme.KittyOnAccent
import com.example.kitty.theme.KittyOutlineSoft
import com.example.kitty.theme.KittySurface
import com.example.kitty.theme.KittyText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KittyLibraryItem(
    val id: String,
    val title: String,
    val poster: String?,
    val status: String,
    val progress: Int = 0,
    val total: Int = 0,
)

/** The user's AniList lists (plus Favourites). Falls back to the on-device list when not signed in. */
class KittyLibraryViewModel(
    private val catalog: KittyCatalog,
    private val repository: JumpAnimeRepository,
) : ViewModel() {
    private val _items = MutableStateFlow<List<KittyLibraryItem>>(emptyList())
    val items: StateFlow<List<KittyLibraryItem>> = _items.asStateFlow()
    private val _favourites = MutableStateFlow<List<KittyLibraryItem>>(emptyList())
    val favourites: StateFlow<List<KittyLibraryItem>> = _favourites.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            val token = catalog.account.accessToken
            val userId = catalog.account.userId
            if (token != null && userId > 0) {
                runCatching { catalog.client.mediaListEntries(token, userId) }
                    .onSuccess { entries ->
                        catalog.remember(entries.map { it.media })
                        _items.value = entries.map {
                            KittyLibraryItem(
                                id = kittyId(it.media.id),
                                title = it.media.title,
                                poster = it.media.coverImageUrl,
                                status = it.status,
                                progress = it.progress,
                                total = it.media.releasedEpisodeCount(),
                            )
                        }
                    }
                runCatching { catalog.client.favourites(token) }
                    .onSuccess { favs ->
                        catalog.remember(favs)
                        _favourites.value = favs.map {
                            KittyLibraryItem(kittyId(it.id), it.title, it.coverImageUrl, "FAVOURITE")
                        }
                    }
            } else {
                _favourites.value = emptyList()
                _items.value = runCatching { repository.getWatchlist() }.getOrDefault(emptyList()).map {
                    KittyLibraryItem(it._id, it.title, it.poster, "PLANNING")
                }
            }
            _loading.value = false
        }
    }
}

@Composable
fun KittyLibraryScreen(
    viewModel: KittyLibraryViewModel,
    catalog: KittyCatalog,
    filter: String,
    onFilter: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val linkedName by catalog.account.linkedName.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Reload when the user signs in or out.
    androidx.compose.runtime.LaunchedEffect(linkedName) { viewModel.load() }

    fun matches(status: String, f: String) = when (f) {
        "watching" -> status == "CURRENT" || status == "REPEATING"
        "plan" -> status == "PLANNING"
        "paused" -> status == "PAUSED"
        "done" -> status == "COMPLETED"
        "dropped" -> status == "DROPPED"
        else -> true
    }
    val shown = if (filter == "favourites") favourites else items.filter { matches(it.status, filter) }
    val order = listOf("CURRENT", "REPEATING", "PLANNING", "PAUSED", "COMPLETED", "DROPPED")
    val sorted = shown.sortedBy { order.indexOf(it.status).let { i -> if (i < 0) 99 else i } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            KittyPageHeader(
                title = "Library",
                subtitle = if (linkedName != null) "Your AniList, in one place." else "Sign in with AniList to see all your lists.",
                trailing = {
                    Button(
                        onClick = { if (linkedName == null) catalog.account.startSignIn(context) else viewModel.load() },
                        colors = ButtonDefaults.buttonColors(containerColor = KittyAccent, contentColor = KittyOnAccent),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(if (linkedName == null) "Sign in" else "Sync", fontWeight = FontWeight.Bold) }
                },
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "all" to "All", "watching" to "Watching", "plan" to "Plan", "paused" to "Paused",
                    "done" to "Done", "dropped" to "Dropped", "favourites" to "Favourites",
                ).forEach { (id, label) ->
                    KittyChip(label, selected = filter == id) { onFilter(id) }
                }
            }
        }
        when {
            loading && items.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KittyAccent)
                }
            }
            sorted.isEmpty() -> item {
                KittyEmptyState(
                    title = "Nothing in this list",
                    body = "Add a show to your list, or set it to Watching, and it lands here.",
                )
            }
            else -> items(sorted, key = { "${it.status}-${it.id}" }) { item ->
                LibraryRow(item, onClick = { onOpen(item.id) })
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "CURRENT", "REPEATING" -> "Watching"
    "PLANNING" -> "Plan to watch"
    "PAUSED" -> "Paused"
    "COMPLETED" -> "Completed"
    "DROPPED" -> "Dropped"
    "FAVOURITE" -> "Favourite"
    else -> status
}

@Composable
private fun LibraryRow(item: KittyLibraryItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0C0C10))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(KittySurface),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val progress = if (item.total > 0) "${item.progress}/${item.total} eps" else if (item.progress > 0) "${item.progress} eps" else null
            Text(
                listOfNotNull(statusLabel(item.status), progress).joinToString(" · "),
                color = KittyMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}
