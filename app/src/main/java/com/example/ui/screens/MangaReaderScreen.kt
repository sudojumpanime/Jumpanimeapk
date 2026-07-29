package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MangaChapter
import com.example.data.model.MangaDetail
import com.example.data.model.MangaPage
import com.example.ui.theme.Slate100
import com.example.ui.viewmodel.MangaReaderState
import com.example.ui.viewmodel.MangaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    viewModel: MangaViewModel,
    mangaDetail: MangaDetail,
    chapter: MangaChapter,
    onBackClick: () -> Unit
) {
    val readerState by viewModel.readerState.collectAsStateWithLifecycle()
    var currentPage by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(chapter.url) {
        viewModel.loadChapterPages(chapter.url)
        currentPage = 0
    }

    LaunchedEffect(currentPage) {
        val state = readerState
        if (state is MangaReaderState.Success) {
            viewModel.saveProgress(
                mangaId = mangaDetail.id,
                mangaTitle = mangaDetail.title,
                mangaCover = mangaDetail.coverUrl,
                chapterId = chapter.id,
                chapterName = chapter.name,
                chapterUrl = chapter.url,
                lastPageRead = currentPage,
                isRead = currentPage >= state.pages.size - 1
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        chapter.name,
                        color = Color.White,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        }
    ) { padding ->
        when (val state = readerState) {
            is MangaReaderState.Loading, MangaReaderState.Idle -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is MangaReaderState.Error -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadChapterPages(chapter.url) }) { Text("Retry") }
                    }
                }
            }
            is MangaReaderState.Success -> {
                val pages = state.pages
                val context = LocalContext.current

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(Color.Black),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(pages, key = { it.index }) { page ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(page.imageUrl)
                                .build(),
                            contentDescription = "Page ${page.index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            "End of ${chapter.name}",
                            color = Slate100.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Track current page based on scroll
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .collect { index ->
                            currentPage = index
                        }
                }
            }
        }
    }
}
