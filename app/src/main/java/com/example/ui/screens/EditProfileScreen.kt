package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.SessionManager
import com.example.ui.components.WaifuBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    sessionManager: SessionManager,
    onBackClick: () -> Unit,
    onSelectAvatarClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Read state from SessionManager
    val isGuest = remember { sessionManager.isGuestMode() }
    var username by remember { mutableStateOf(sessionManager.getUsername() ?: "Otaku Explorer") }
    val email = remember { sessionManager.getEmail() ?: "email@jumpanime.com" }
    val avatarUrl = remember { mutableStateOf(sessionManager.getAvatarUrl()) }
    
    // Refresh avatar URL when returning
    LaunchedEffect(Unit) {
        avatarUrl.value = sessionManager.getAvatarUrl()
    }

    WaifuBackground(
        imageUrl = "https://static.wikia.nocookie.net/date-a-live/images/c/c9/Kurumi_Tokisaki_Anime.png",
        overlayAlpha = 0.9f
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = "Edit profile", 
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // CIRCULAR AVATAR CONTAINER (CUSTOMIZATION DISABLED FOR v1.1.1)
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clickable {
                                Toast.makeText(context, "Avatar customization is temporarily disabled in v1.1.1", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer circular glow
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(
                                    width = 3.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = avatarUrl.value,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    // Username input field (DISABLED FOR v1.1.1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Username changing is temporarily disabled in v1.1.1", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {},
                            label = { Text("Username", color = Color.White.copy(alpha = 0.4f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.White.copy(alpha = 0.5f),
                                disabledBorderColor = Color.White.copy(alpha = 0.1f),
                                disabledContainerColor = Color.White.copy(alpha = 0.03f),
                                disabledLabelColor = Color.White.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f)
                                )
                            },
                            singleLine = true,
                            enabled = false
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Email input field (Read-only/Disabled as in screenshot)
                    OutlinedTextField(
                        value = if (isGuest) "guest@jumpanime.com" else email,
                        onValueChange = {},
                        label = { Text("Email", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White.copy(alpha = 0.4f),
                            disabledBorderColor = Color.White.copy(alpha = 0.1f),
                            disabledContainerColor = Color.White.copy(alpha = 0.02f),
                            disabledLabelColor = Color.White.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f)
                            )
                        },
                        singleLine = true,
                        enabled = false
                    )
                    
                    if (isGuest) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You are currently logged in as Guest. Please log in to view profile details.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Profile customization is temporarily disabled in v1.1.1.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
                
                // UPDATE BUTTON AT BOTTOM (matches screenshot)
                Button(
                    onClick = {
                        if (isGuest) {
                            Toast.makeText(context, "Guest cannot save changes", Toast.LENGTH_SHORT).show()
                        } else if (username.isBlank()) {
                            Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                        } else {
                            sessionManager.saveUsername(username)
                            Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            onBackClick()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isGuest || username.isNotBlank()
                ) {
                    Text(
                        text = "Update",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
