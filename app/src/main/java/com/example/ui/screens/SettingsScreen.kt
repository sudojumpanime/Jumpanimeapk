package com.example.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.JumpAnimeRepository
import com.example.data.local.SessionManager
import com.example.data.model.Anime
import com.example.data.model.isMovie
import com.example.ui.components.WaifuBackground
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler

import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    repository: JumpAnimeRepository,
    updateService: com.example.data.EpisodeUpdateService,
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    onEditProfileClick: () -> Unit,
    onThemeChange: (String) -> Unit = {},
    onStreamSourcesClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Retrieve saved profile parameters
    val isGuest = remember { sessionManager.isGuestMode() }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        username = if (isGuest) "Guest" else sessionManager.getUsername() ?: "Otaku Explorer"
        email = if (isGuest) "" else sessionManager.getEmail() ?: "email@jumpanime.com"
        avatarUrl = sessionManager.getAvatarUrl()
    }
    var notificationsEnabled by remember { mutableStateOf(sessionManager.isNotificationsEnabled()) }
    var preloadEnabled by remember { mutableStateOf(sessionManager.isPreloadEnabled()) }
    val recentSearchesState by repository.getRecentSearches().collectAsStateWithLifecycle(initialValue = emptyList())
    var isSimulating by remember { mutableStateOf(false) }
    var isOptimizingCache by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var activeSubPage by remember { mutableStateOf<String?>(null) }
    val updateState by com.example.utils.UpdateManager.updateState.collectAsStateWithLifecycle()
    var hasCheckedUpdatesInSettings by remember { mutableStateOf(false) }

    // Live statistics from repository
    var watchlistCount by remember { mutableIntStateOf(0) }
    var playedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val list = repository.getWatchlist()
            watchlistCount = list.size
        } catch (e: Exception) {
            watchlistCount = 0
        }
        try {
            repository.getAllLocalStates().collect { states ->
                playedCount = states.size
            }
        } catch (e: Exception) {
            playedCount = 0
        }
    }

    LaunchedEffect(activeSubPage) {
        if (activeSubPage != null) {
            scrollState.scrollTo(0)
        }
    }

    // Push permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification alerts enabled successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Banners or push sound may be suppressed without permission.", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled) {
            checkAndRequestPermission()
        }
    }

    if (activeSubPage != null) {
        BackHandler {
            activeSubPage = null
        }
    }

    WaifuBackground(
        imageUrl = "https://static.wikia.nocookie.net/date-a-live/images/c/c9/Kurumi_Tokisaki_Anime.png",
        overlayAlpha = 0.85f
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                val currentTitle = when (activeSubPage) {
                    null -> "My Profile & Settings"
                    "ACCOUNT" -> "Account Details"
                    "APPEARANCE" -> "Appearance & Styling"
                    "STREAMING" -> "Player & Alerts"
                    "STORAGE" -> "Cache & Performance"
                    else -> "Settings"
                }
                TopAppBar(
                    title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (activeSubPage != null) {
                                    activeSubPage = null
                                } else {
                                    onBackClick()
                                }
                            }
                        ) {
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
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .verticalScroll(scrollState)
                    .padding(bottom = 32.dp)
            ) {
            if (activeSubPage == null) {
                // PROFILE HERO CONTAINER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Modern Rounded Avatar Box with Click feedback
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .background(Color.Black.copy(alpha = 0.2f))
                            .clickable { onEditProfileClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = avatarUrl.ifBlank { "https://ui-avatars.com/api/?name=${username.ifBlank { "Guest" }}&background=FF2A43&color=fff&size=128" },
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username text
                    Text(
                        text = username.ifBlank { "Otaku Explorer" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Email text
                    if (email.isNotEmpty()) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onEditProfileClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // AniList account: lists and progress sync
            run {
                val aniAccount = repository.kitty.account
                val aniName by aniAccount.linkedName.collectAsStateWithLifecycle()
                val aniContext = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        if (aniName == null) aniAccount.startSignIn(aniContext) else aniAccount.clear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (aniName == null) "Sign in with AniList" else "AniList: $aniName · Sign out",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stream sources / extensions (AniKitty-style store)
            androidx.compose.material3.OutlinedButton(
                onClick = onStreamSourcesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Stream sources & extensions", fontWeight = FontWeight.Bold)
            }
            androidx.compose.material3.OutlinedButton(
                onClick = onDownloadsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Downloads", fontWeight = FontWeight.Bold)
            }

            // MINI VISUAL STATS DASHBOARD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stat 1
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$watchlistCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Saved List",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                // Stat 2
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$playedCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Anime Watched",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            }

            // PROFILE PREFERENCES SECTIONS
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {

                // Category 1: Account
                if (activeSubPage == null) {
                    SettingsCategoryCard(
                        title = "Account Details",
                        subtitle = if (isGuest) "Logged in as Guest" else "Manage display name and email",
                        icon = Icons.Default.Person,
                        isExpanded = false,
                        onHeaderClick = { activeSubPage = "ACCOUNT" }
                    ) {
                        // Empty inside
                    }
                } else if (activeSubPage == "ACCOUNT") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (isGuest) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "You are currently browsing as a Guest. Sign in to sync your watchlist, add comments, and personalize your experience.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                repository.clearLocalCache()
                                                sessionManager.saveGuestMode(false)
                                                onLogout()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sign In Now", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ProfileItemRow(
                                        icon = Icons.Default.Person,
                                        title = "Display Name",
                                        subtitle = username,
                                        showChevron = true,
                                        onClick = { onEditProfileClick() }
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                    ProfileItemRow(
                                        icon = Icons.Default.Email,
                                        title = "Primary Account Email",
                                        subtitle = email,
                                        showChevron = false,
                                        onClick = {}
                                    )
                                }
                            }
                        }
                    }
                }

                // Category 2: Appearance
                if (activeSubPage == null) {
                    SettingsCategoryCard(
                        title = "Appearance & Styling",
                        subtitle = "Customize app color theme",
                        icon = Icons.Default.Palette,
                        isExpanded = false,
                        onHeaderClick = { activeSubPage = "APPEARANCE" }
                    ) {
                        // Empty inside
                    }
                } else if (activeSubPage == "APPEARANCE") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Choose your favorite interface accent color. Changes apply instantly across all screens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            var selectedThemeKey by remember { mutableStateOf(sessionManager.getSelectedTheme()) }
                            
                            val themesList = remember(selectedThemeKey) {
                                val list = mutableListOf(
                                    Triple("BLUE", Color(0xFF00B0FF), "Blue"),
                                    Triple("RED", Color(0xFFFF2A43), "Red"),
                                    Triple("GREEN", Color(0xFF00E676), "Green"),
                                    Triple("PURPLE", Color(0xFFD500F9), "Purple"),
                                    Triple("ORANGE", Color(0xFFFF6D00), "Orange"),
                                    Triple("PINK", Color(0xFFFF4081), "Pink")
                                )
                                if (selectedThemeKey.startsWith("#")) {
                                    try {
                                        val cleaned = selectedThemeKey.substring(1)
                                        val colorLong = cleaned.toLong(16)
                                        val color = if (cleaned.length == 6) Color(0xFF000000 or colorLong) else Color(colorLong)
                                        list.add(Triple(selectedThemeKey, color, "Custom"))
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                }
                                list
                            }
                            
                            // Parse current RGB values based on current selection
                            val initialRgb = remember(selectedThemeKey) {
                                if (selectedThemeKey.startsWith("#")) {
                                    val cleaned = selectedThemeKey.removePrefix("#").trim()
                                    if (cleaned.length == 6) {
                                        try {
                                            Triple(
                                                cleaned.substring(0, 2).toInt(16),
                                                cleaned.substring(2, 4).toInt(16),
                                                cleaned.substring(4, 6).toInt(16)
                                            )
                                        } catch (e: Exception) {
                                            Triple(255, 42, 67)
                                        }
                                    } else {
                                        Triple(255, 42, 67)
                                    }
                                } else {
                                    when (selectedThemeKey.uppercase()) {
                                        "BLUE" -> Triple(0, 176, 255)
                                        "RED" -> Triple(255, 42, 67)
                                        "GREEN" -> Triple(0, 230, 118)
                                        "PURPLE" -> Triple(213, 0, 249)
                                        "ORANGE" -> Triple(255, 109, 0)
                                        "PINK" -> Triple(255, 64, 129)
                                        else -> Triple(255, 42, 67)
                                    }
                                }
                            }

                            var customRed by remember(initialRgb) { mutableStateOf(initialRgb.first) }
                            var customGreen by remember(initialRgb) { mutableStateOf(initialRgb.second) }
                            var customBlue by remember(initialRgb) { mutableStateOf(initialRgb.third) }

                            val customColorHex = remember(customRed, customGreen, customBlue) {
                                val rHex = customRed.toString(16).padStart(2, '0').uppercase()
                                val gHex = customGreen.toString(16).padStart(2, '0').uppercase()
                                val bHex = customBlue.toString(16).padStart(2, '0').uppercase()
                                "#$rHex$gHex$bHex"
                            }

                            var hexInputText by remember(customColorHex) { mutableStateOf(customColorHex) }

                            // Display themes in an adaptive flow or row
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                themesList.forEach { (key, color, label) ->
                                    val isSelected = selectedThemeKey == key
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable {
                                                selectedThemeKey = key
                                                sessionManager.setSelectedTheme(key)
                                                onThemeChange(key)
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (isSelected) 3.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = if ((color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.6) Color.Black else Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.1f),
                                thickness = 1.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Full Range Color Picker",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Slide to select any custom color or type a hex code directly below. The whole app theme updates dynamically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Row with Hex TextField and a beautiful live color preview block
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Live Preview Block
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(customRed, customGreen, customBlue))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = if ((customRed * 0.299 + customGreen * 0.587 + customBlue * 0.114) > 150) Color.Black else Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                // Hex Input
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = hexInputText,
                                        onValueChange = { newValue ->
                                            if (newValue.length <= 7) {
                                                hexInputText = newValue
                                                // Auto-parse if valid 7-character hex (e.g., #FF2A43)
                                                if (newValue.startsWith("#") && newValue.length == 7) {
                                                    try {
                                                        val cleaned = newValue.removePrefix("#")
                                                        val r = cleaned.substring(0, 2).toInt(16)
                                                        val g = cleaned.substring(2, 4).toInt(16)
                                                        val b = cleaned.substring(4, 6).toInt(16)
                                                        customRed = r
                                                        customGreen = g
                                                        customBlue = b
                                                        
                                                        selectedThemeKey = newValue
                                                        sessionManager.setSelectedTheme(newValue)
                                                        onThemeChange(newValue)
                                                    } catch (e: Exception) {
                                                        // Invalid hex format, don't update sliders yet
                                                    }
                                                }
                                            }
                                        },
                                        label = { Text("Hex Color Code", color = Color.White.copy(alpha = 0.6f)) },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedLabelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // R, G, B Sliders
                            ColorSlider(
                                label = "Red",
                                value = customRed,
                                color = Color.Red,
                                onValueChange = { newValue ->
                                    customRed = newValue
                                    val newHex = run {
                                        val rHex = newValue.toString(16).padStart(2, '0').uppercase()
                                        val gHex = customGreen.toString(16).padStart(2, '0').uppercase()
                                        val bHex = customBlue.toString(16).padStart(2, '0').uppercase()
                                        "#$rHex$gHex$bHex"
                                    }
                                    selectedThemeKey = newHex
                                    sessionManager.setSelectedTheme(newHex)
                                    onThemeChange(newHex)
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ColorSlider(
                                label = "Green",
                                value = customGreen,
                                color = Color.Green,
                                onValueChange = { newValue ->
                                    customGreen = newValue
                                    val newHex = run {
                                        val rHex = customRed.toString(16).padStart(2, '0').uppercase()
                                        val gHex = newValue.toString(16).padStart(2, '0').uppercase()
                                        val bHex = customBlue.toString(16).padStart(2, '0').uppercase()
                                        "#$rHex$gHex$bHex"
                                    }
                                    selectedThemeKey = newHex
                                    sessionManager.setSelectedTheme(newHex)
                                    onThemeChange(newHex)
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ColorSlider(
                                label = "Blue",
                                value = customBlue,
                                color = Color.Blue,
                                onValueChange = { newValue ->
                                    customBlue = newValue
                                    val newHex = run {
                                        val rHex = customRed.toString(16).padStart(2, '0').uppercase()
                                        val gHex = customGreen.toString(16).padStart(2, '0').uppercase()
                                        val bHex = newValue.toString(16).padStart(2, '0').uppercase()
                                        "#$rHex$gHex$bHex"
                                    }
                                    selectedThemeKey = newHex
                                    sessionManager.setSelectedTheme(newHex)
                                    onThemeChange(newHex)
                                }
                            )
                        }
                    }
                }

                if (activeSubPage == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Category 3: Player & Alerts
                if (activeSubPage == null) {
                    SettingsCategoryCard(
                        title = "Player & Alerts",
                        subtitle = "Episode release notifications and preloading",
                        icon = Icons.Default.Tune,
                        isExpanded = false,
                        onHeaderClick = { activeSubPage = "STREAMING" }
                    ) {
                        // Empty inside
                    }
                } else if (activeSubPage == "STREAMING") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Item 1: Episode Alerts Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Episode Release Alerts",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Notification only when new verified episodes are imported",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                Switch(
                                    checked = notificationsEnabled,
                                    onCheckedChange = { isChecked ->
                                        notificationsEnabled = isChecked
                                        sessionManager.setNotificationsEnabled(isChecked)
                                    }
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Item 2: Preload next episode Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Preload Next Episode",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Prefetch content in background for instant playback transition",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                Switch(
                                    checked = preloadEnabled,
                                    onCheckedChange = { isChecked ->
                                        preloadEnabled = isChecked
                                        sessionManager.setPreloadEnabled(isChecked)
                                    }
                                )
                            }

                            // Simulated alert trigger option (visible only if alerts enabled and in debug build)
                            if (com.example.BuildConfig.DEBUG && notificationsEnabled) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isSimulating = true
                                            scope.launch {
                                                try {
                                                    val watchlist = repository.getWatchlist()
                                                    val target: Anime = if (watchlist.isEmpty()) {
                                                        Toast.makeText(context, "Saved list is empty. Simulating One Piece...", Toast.LENGTH_SHORT).show()
                                                        // Create a dummy One Piece Anime object for simulation
                                                        Anime(
                                                            _id = "677d2427f7a77a94250e64c5",
                                                            title = "One Piece",
                                                            poster = "https://cdn.myanimelist.net/images/anime/1244/138851l.jpg",
                                                            malId = "21",
                                                            seasons = listOf(com.example.data.model.Season(_id = "677d2427f7a77a94250e64c8", number = 1))
                                                        )
                                                    } else {
                                                        watchlist.random()
                                                    }

                                                    val notificationMsg = if (target.isMovie()) {
                                                        "The full movie is now available to stream!"
                                                    } else {
                                                        "New episode matching your preference is now fully imported!"
                                                    }
                                                    showLocalPushNotification(
                                                        context,
                                                        target,
                                                        notificationMsg
                                                    )
                                                    updateService.simulateUpdateCheck(target)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Simulation error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isSimulating = false
                                                }
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isSimulating) "Posting Simulated Release Alert..." else "Trigger Push Alert Simulation",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeSubPage == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Category 4: Cache & Performance
                if (activeSubPage == null) {
                    SettingsCategoryCard(
                        title = "Cache & Performance",
                        subtitle = "Manage storage and optimize database size",
                        icon = Icons.Default.Storage,
                        isExpanded = false,
                        onHeaderClick = { activeSubPage = "STORAGE" }
                    ) {
                        // Empty inside
                    }
                } else if (activeSubPage == "STORAGE") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Clear Offline Search History",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    "Remove search terms cached on device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }

                            TextButton(
                                onClick = {
                                    isOptimizingCache = true
                                    scope.launch {
                                        try {
                                            recentSearchesState.forEach { item ->
                                                repository.deleteSearch(item.query)
                                            }
                                            kotlinx.coroutines.delay(800)
                                            Toast.makeText(context, "Search history cleared!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isOptimizingCache = false
                                        }
                                    }
                                },
                                enabled = !isOptimizingCache && recentSearchesState.isNotEmpty()
                            ) {
                                Text(
                                    text = if (isOptimizingCache) "Purging..." else "Purge Now",
                                    fontWeight = FontWeight.Bold,
                                    color = if (recentSearchesState.isEmpty()) Color.Gray else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (activeSubPage == null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // SECTION 4: VERSION & UPDATES
                Text(
                    text = "App Version & Updates",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                val packageInfo = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (e: Exception) {
                        null
                    }
                }
                val currentVersionName = packageInfo?.versionName ?: "1.0.0"

                ProfileCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "JumpAnime v$currentVersionName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    "Keep your application up to date",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }

                            when (updateState) {
                                is com.example.utils.UpdateState.Checking -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                is com.example.utils.UpdateState.Downloading -> {
                                    Text(
                                        "Downloading...",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                    )
                                }
                                else -> {
                                    TextButton(
                                        onClick = {
                                            hasCheckedUpdatesInSettings = true
                                            scope.launch {
                                                com.example.utils.UpdateManager.checkForUpdates(context)
                                            }
                                        }
                                    ) {
                                        Text(
                                            "Check",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }

                        when (val state = updateState) {
                            is com.example.utils.UpdateState.NoUpdate -> {
                                // Handled via UpToDateDialog below, not inline at the bottom
                            }
                            is com.example.utils.UpdateState.Downloading -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Downloading update...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "${(state.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                            is com.example.utils.UpdateState.Success -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2D3A).copy(alpha = 0.6f)),
                                    border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = null,
                                            tint = Color(0xFF00B0FF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Download complete!",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(
                                                "Tap install to update your app.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                com.example.utils.UpdateManager.triggerInstallation(context, state.apkFile)
                                            }
                                        ) {
                                            Text("Install", fontWeight = FontWeight.Bold, color = Color(0xFF00B0FF))
                                        }
                                    }
                                }
                            }
                            is com.example.utils.UpdateState.Error -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1E1E).copy(alpha = 0.5f)),
                                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = Color(0xFFEF5350),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Failed to check/download update:",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFEF5350)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            state.message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextButton(
                                            onClick = { com.example.utils.UpdateManager.resetState() },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350)),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // If update is available, prompt user using a Dialog
                if (updateState is com.example.utils.UpdateState.UpdateAvailable) {
                    val available = updateState as com.example.utils.UpdateState.UpdateAvailable
                    AlertDialog(
                        onDismissRequest = { com.example.utils.UpdateManager.resetState() },
                        title = {
                            Text("New version tag found: ${available.tagName}")
                        },
                        text = {
                            Column {
                                Text(
                                    "A new update is available. Do you want to download and install it now?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                if (available.body.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Release Notes:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 150.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                available.body,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val url = available.downloadUrl
                                    val name = available.fileName
                                    scope.launch {
                                        com.example.utils.UpdateManager.downloadAndInstallApk(context, url, name)
                                    }
                                }
                            ) {
                                Text("Download & Install")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { com.example.utils.UpdateManager.resetState() }) {
                                Text("Later")
                            }
                        },
                        containerColor = Color(0xFF1E1E1E),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
                }

                // If update is NOT needed (already up to date) and user pressed check button, show a very cool dialog
                if (updateState is com.example.utils.UpdateState.NoUpdate && hasCheckedUpdatesInSettings) {
                    UpToDateDialog(
                        onDismiss = {
                            hasCheckedUpdatesInSettings = false
                            com.example.utils.UpdateManager.resetState()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // SECTION 5: SESSION ACCOUNT SIGNOUT (DANGER ROW)
                Button(
                    onClick = {
                        scope.launch {
                            repository.clearLocalCache()
                            if (isGuest) {
                                sessionManager.saveGuestMode(false)
                            } else {
                                sessionManager.clearToken()
                            }
                            onLogout()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGuest) MaterialTheme.colorScheme.primary else Color(0xFFE94560),
                        contentColor = if (isGuest) Color.Black else Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isGuest) Icons.Default.Login else Icons.Default.ExitToApp,
                        contentDescription = "Logout icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isGuest) "Sign In to JumpAnime" else "Sign Out & Clear Session",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                }
            }
        }
    }
}
}

@Composable
fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.03f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Navigate",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 12.dp))
                    content()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.03f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        content = content
    )
}

@Composable
fun ProfileItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = showChevron, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Interact",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun showLocalPushNotification(context: Context, anime: Anime, message: String) {
    val channelId = "new_episode_channel_v2"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "New Episodes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notification channel for newly released anime episodes"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val seasonId = anime.seasons.firstOrNull()?._id ?: "simulated_season"
    val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("NOTIFICATION_CLICK", true)
        putExtra("ANIME_ID", anime._id)
        putExtra("MAL_ID", anime.malId ?: "")
        putExtra("SEASON_ID", seasonId)
        putExtra("EPISODE_NUMBER", 1) // Just simulate Ep 1 for local push
        putExtra("ANIME_TITLE", anime.title)
        putExtra("ANIME_POSTER", anime.poster ?: "")
    }

    // Increment unread count for Badge testing
    val tempContainer = (context.applicationContext as com.example.JumpAnimeApplication).container
    tempContainer.repository.sessionManager.incrementUnreadCount()
    val unreadCount = tempContainer.repository.sessionManager.getUnreadCount()

    val pendingIntent = android.app.PendingIntent.getActivity(
        context, 
        anime._id.hashCode(), 
        intent, 
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(anime.title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setNumber(unreadCount)
        .setAutoCancel(true)
        .build()

    try {
        notificationManager.notify(anime._id.hashCode(), notification)
        Toast.makeText(context, "New episode release alert posted!", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "${anime.title}: $message", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun UpToDateDialog(
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top Right Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Elegant Check Circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success check",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "JumpAnime is up to date",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You are currently running the latest version of JumpAnime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ColorSlider(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.width(80.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.5f),
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}
