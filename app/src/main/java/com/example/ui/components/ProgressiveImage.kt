package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * A highly optimized progressive image loader.
 * Uses standard AsyncImage (avoiding slow SubcomposeAsyncImage pass and expensive real-time blur modifiers)
 * to achieve 60/120 FPS buttery-smooth scrolling on both physical devices and emulators.
 */
@Composable
fun ProgressiveImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidth: Int = 240,
    targetHeight: Int = 360
) {
    val context = LocalContext.current
    
    val mainRequest = remember(url, targetWidth, targetHeight) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(250) // Hardware-accelerated smooth crossfade animation
            .size(targetWidth, targetHeight)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier.background(Color(0xFF1E1E1E)), // Solid slate backdrop acts as lightweight placeholder
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = mainRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
    }
}


