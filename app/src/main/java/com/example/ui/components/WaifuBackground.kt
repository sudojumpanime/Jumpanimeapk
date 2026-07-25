package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun WaifuBackground(
    imageUrl: String,
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 0.82f,
    content: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build()
    }
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Waifu Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Elegant gradient and semi-transparent dark overlay to keep UI elements highly readable and stylish
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F1115).copy(alpha = overlayAlpha - 0.15f),
                            Color(0xFF0F1115).copy(alpha = overlayAlpha),
                            Color(0xFF0F1115).copy(alpha = overlayAlpha + 0.08f)
                        )
                    )
                )
        )
        content()
    }
}
