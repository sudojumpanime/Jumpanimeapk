package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.SessionManager
import com.example.ui.components.AvatarData
import com.example.ui.components.WaifuBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAvatarScreen(
    sessionManager: SessionManager,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val currentAvatar = remember { sessionManager.getAvatarUrl() }
    val avatars = remember { AvatarData.avatars }

    WaifuBackground(
        imageUrl = "https://static.wikia.nocookie.net/date-a-live/images/c/c9/Kurumi_Tokisaki_Anime.png",
        overlayAlpha = 0.95f
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = "Select avatar", 
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), // matches screenshot
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(avatars, key = { it.url }) { avatar ->
                    val isSelected = avatar.url == currentAvatar
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val imageRequest = remember(avatar.url) {
                        coil.request.ImageRequest.Builder(context)
                            .data(avatar.url)
                            .crossfade(true)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                    }
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                sessionManager.saveAvatarUrl(avatar.url)
                                Toast.makeText(context, "${avatar.name} selected!", Toast.LENGTH_SHORT).show()
                                onBackClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = avatar.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f)
                                ),
                            contentScale = ContentScale.Crop
                        )
                        
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
