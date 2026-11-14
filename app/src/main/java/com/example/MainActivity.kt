package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.mutableStateOf
import com.example.ui.theme.JumpAnimeTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import com.example.kitty.anilistIdOrNull
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.work.*
import com.example.ui.Screen
import com.example.ui.screens.*
import com.example.ui.viewmodel.*
import com.example.data.local.AnimeWatchState
import com.example.worker.NewEpisodeWorker
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.InAppNotificationBanner
import com.example.ui.components.JumpAnimeSplashScreen

class MainActivity : ComponentActivity() {
    private lateinit var container: DependencyContainer

    private var currentIntent = mutableStateOf<android.content.Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        container = (application as JumpAnimeApplication).container
        currentIntent.value = intent
        handleAniListCallback(intent)
        
        lifecycleScope.launch(Dispatchers.IO) {
            container.repository.syncWatchlistMetadata()
            try {
                container.repository.syncContinueWatchingToLocalHistory()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                container.episodeUpdateService.syncScheduleWithNotifications()
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        setupWorkManager()
        enableEdgeToEdge()
        
        setContent {
            val themeNameState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(container.sessionManager.getSelectedTheme()) }
            JumpAnimeTheme(themeName = themeNameState.value) {
                JumpAnimeApp(
                    container = container, 
                    initialIntent = currentIntent.value,
                    themeNameState = themeNameState,
                    onIntentProcessed = { currentIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleAniListCallback(intent)) return
        currentIntent.value = intent
    }

    /** Finishes AniList sign-in when the browser redirects back to jumpanime://anilist. */
    private fun handleAniListCallback(intent: android.content.Intent?): Boolean {
        val account = container.kitty.account
        if (!account.matchesCallback(intent)) return false
        val token = account.tokenFromCallback(intent?.data)
        if (token == null) {
            android.widget.Toast.makeText(this, "AniList sign-in didn't complete", android.widget.Toast.LENGTH_LONG).show()
            return true
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val viewer = container.kitty.client.viewer(token)
            launch(Dispatchers.Main) {
                if (viewer != null) {
                    account.save(token, viewer)
                    android.widget.Toast.makeText(this@MainActivity, "Signed in to AniList as ${viewer.name}", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(this@MainActivity, "Couldn't read your AniList profile", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        return true
    }

    private fun setupWorkManager() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<NewEpisodeWorker>(
            4, TimeUnit.HOURS // Every few hours
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "new_episode_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

private fun getTabIndex(destination: androidx.navigation.NavDestination?): Int? {
    if (destination == null) return null
    return when {
        destination.hasRoute<Screen.Home>() -> 0
        destination.hasRoute<Screen.Schedule>() -> 1
        destination.hasRoute<Screen.Watchlist>() -> 2
        destination.hasRoute<Screen.Settings>() -> 3
        else -> null
    }
}

@Composable
fun JumpAnimeApp(
    container: DependencyContainer, 
    initialIntent: android.content.Intent? = null,
    themeNameState: androidx.compose.runtime.MutableState<String> = androidx.compose.runtime.mutableStateOf("RED"),
    onIntentProcessed: () -> Unit = {},
    onReady: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Activity-scoped Shared ViewModels for seamless tab switching and instant load
    val homeViewModel: HomeViewModel = viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity,
        factory = ViewModelFactory(container.repository)
    )
    val scheduleViewModel: ScheduleViewModel = viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity,
        factory = ViewModelFactory(container.repository, container.episodeUpdateService)
    )
    val watchlistViewModel: WatchlistViewModel = viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity,
        factory = ViewModelFactory(container.repository)
    )

    val homeLoadDone by homeViewModel.isInitialLoadCompleted.collectAsStateWithLifecycle()
    val scheduleLoadDone by scheduleViewModel.isInitialLoadCompleted.collectAsStateWithLifecycle()
    val watchlistLoadDone by watchlistViewModel.isInitialLoadCompleted.collectAsStateWithLifecycle()

    var lastDismissedVersion by androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf(container.sessionManager.getLastDismissedUpdateVersion()) 
    }
    var hasDismissedDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val updateState by com.example.utils.UpdateManager.updateState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Wait 4 seconds to check in background after splash screen clears
        kotlinx.coroutines.delay(4000)
        try {
            com.example.utils.UpdateManager.checkForUpdates(context)
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeApp", "Auto update check failed", e)
        }
    }
    
    var showInAppSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val handleReady = {
        showInAppSplash = false
        onReady()
    }
    
    // Dismiss splash screen ONLY when all core features have loaded their initial data
    androidx.compose.runtime.LaunchedEffect(homeLoadDone, scheduleLoadDone, watchlistLoadDone) {
        if (homeLoadDone && scheduleLoadDone && watchlistLoadDone) {
            handleReady()
        }
    }

    // Safeguard timeout to ensure splash screen always dismisses after max 8 seconds
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8000)
        handleReady()
    }
    val startDestination = when {
        container.sessionManager.isLoggedIn() -> Screen.Home
        container.sessionManager.isGuestMode() -> Screen.Home
        else -> Screen.Welcome
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.run {
        hasRoute<Screen.Home>() || hasRoute<Screen.Browse>() || hasRoute<Screen.Schedule>() || hasRoute<Screen.Watchlist>() || hasRoute<Screen.Settings>()
    } == true

    val activeNotification by container.episodeUpdateService.activeNotification.collectAsStateWithLifecycle()
    val unreadCount by container.episodeUpdateService.unreadCount.collectAsStateWithLifecycle()

    // Handle System Notification Intent & Pinned Home Screen Shortcuts
    androidx.compose.runtime.LaunchedEffect(initialIntent) {
        if (initialIntent?.getBooleanExtra("NOTIFICATION_CLICK", false) == true) {
            val animeId = initialIntent.getStringExtra("ANIME_ID") ?: ""
            val malId = initialIntent.getStringExtra("MAL_ID") ?: ""
            val seasonId = initialIntent.getStringExtra("SEASON_ID") ?: "simulated_season"
            val animeTitle = initialIntent.getStringExtra("ANIME_TITLE") ?: "Anime Update"
            val episodeNumber = initialIntent.getIntExtra("EPISODE_NUMBER", 1)
            val animePoster = initialIntent.getStringExtra("ANIME_POSTER") ?: ""

            if (animeId.isNotBlank()) {
                navController.navigate(
                    Screen.Player(
                        animeId = animeId,
                        malId = malId,
                        seasonId = seasonId,
                        episodeNumber = episodeNumber,
                        animeTitle = animeTitle,
                        episodeTitle = "Episode $episodeNumber",
                        animePoster = animePoster
                    )
                )
            } else {
                navController.navigate(Screen.Notifications)
            }
            onIntentProcessed()
        } else if (initialIntent?.getBooleanExtra("PINNED_CLICK", false) == true) {
            val animeId = initialIntent.getStringExtra("ANIME_ID") ?: ""
            if (animeId.isNotBlank()) {
                navController.navigate(Screen.Detail(animeId))
            }
            onIntentProcessed()
        }
    }

    androidx.compose.runtime.LaunchedEffect(activeNotification) {
        if (activeNotification != null) {
            kotlinx.coroutines.delay(6000)
            container.episodeUpdateService.dismissNotification()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute<Screen.Home>() == true,
                            onClick = {
                                navController.navigate(Screen.Home) {
                                    popUpTo(Screen.Home) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(androidx.compose.material.icons.Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute<Screen.Schedule>() == true,
                            onClick = {
                                navController.navigate(Screen.Schedule) {
                                    popUpTo(Screen.Home) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Schedule") },
                            label = { Text("Schedule") }
                        )
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute<Screen.Watchlist>() == true,
                            onClick = {
                                navController.navigate(Screen.Watchlist) {
                                    popUpTo(Screen.Home) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = "My List") },
                            label = { Text("My List") }
                        )
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute<Screen.Settings>() == true,
                            onClick = {
                                navController.navigate(Screen.Settings) {
                                    popUpTo(Screen.Home) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                            label = { Text("Profile") }
                        )
                    }
                }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                enterTransition = {
                    val initialTab = getTabIndex(initialState.destination)
                    val targetTab = getTabIndex(targetState.destination)
                    if (initialTab != null && targetTab != null) {
                        if (targetTab > initialTab) {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250))
                        }
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(250))
                    }
                },
                exitTransition = {
                    val initialTab = getTabIndex(initialState.destination)
                    val targetTab = getTabIndex(targetState.destination)
                    if (initialTab != null && targetTab != null) {
                        if (targetTab > initialTab) {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        }
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    }
                },
                popEnterTransition = {
                    val initialTab = getTabIndex(initialState.destination)
                    val targetTab = getTabIndex(targetState.destination)
                    if (initialTab != null && targetTab != null) {
                        if (targetTab > initialTab) {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250))
                        }
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(250))
                    }
                },
                popExitTransition = {
                    val initialTab = getTabIndex(initialState.destination)
                    val targetTab = getTabIndex(targetState.destination)
                    if (initialTab != null && targetTab != null) {
                        if (targetTab > initialTab) {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        }
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    }
                }
            ) {
                composable<Screen.Welcome> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        handleReady()
                    }
                    WelcomeScreen(
                        sessionManager = container.sessionManager,
                        onLoginClick = { navController.navigate(Screen.Login) },
                        onGuestClick = {
                            navController.navigate(Screen.Home) {
                                popUpTo(Screen.Welcome) { inclusive = true }
                            }
                        }
                    )
                }

                composable<Screen.Login> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        handleReady()
                    }
                    val viewModel: AuthViewModel = viewModel(factory = ViewModelFactory(container.repository))
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = {
                            container.sessionManager.saveGuestMode(false)
                            scope.launch {
                                container.repository.clearLocalCache()
                                try {
                                    container.repository.syncWatchlistMetadata()
                                } catch (e: Exception) { /* ignore */ }
                                homeViewModel.refresh()
                            }
                            navController.navigate(Screen.Home) {
                                popUpTo(Screen.Login) { inclusive = true }
                            }
                        }
                    )
                }
                
                composable<Screen.Home> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        notificationCount = unreadCount,
                        onReady = handleReady,
                        onAnimeClick = { id -> navController.navigate(Screen.Detail(id)) },
                        onContinueWatchingClick = { item ->
                            navController.navigate(
                                Screen.Player(
                                    animeId = item.anime,
                                    malId = item.malId ?: "",
                                    seasonId = item.season,
                                    episodeNumber = item.episodeNumber,
                                    animeTitle = item.animeTitle,
                                    episodeTitle = item.episodeTitle,
                                    animePoster = item.animePoster
                                )
                            )
                        },
                        onSearchClick = { navController.navigate(Screen.Browse) },
                        onNotificationsClick = { navController.navigate(Screen.Notifications) }
                    )
                }

                composable<Screen.Browse> {
                    val viewModel: BrowseViewModel = viewModel(factory = ViewModelFactory(container.repository))
                    BrowseScreen(
                        viewModel = viewModel,
                        onAnimeClick = { id -> navController.navigate(Screen.Detail(id)) },
                        onBackClick = {
                            navController.navigate(Screen.Home) {
                                popUpTo(Screen.Home) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable<Screen.Schedule> {
                    ScheduleScreen(
                        viewModel = scheduleViewModel,
                        notificationCount = unreadCount,
                        onAnimeClick = { id -> navController.navigate(Screen.Detail(id)) },
                        onSearchClick = { navController.navigate(Screen.Browse) },
                        onNotificationsClick = { navController.navigate(Screen.Notifications) }
                    )
                }

                composable<Screen.Detail> { backStackEntry ->
                    val detail: Screen.Detail = backStackEntry.toRoute()
                    val viewModel: DetailViewModel = viewModel(factory = ViewModelFactory(container.repository))
                    DetailScreen(
                        animeId = detail.animeId,
                        viewModel = viewModel,
                        sessionManager = container.sessionManager,
                        onBackClick = { navController.popBackStack() },
                        onEpisodeClick = { anime, episode ->
                            navController.navigate(
                                Screen.Player(
                                    animeId = anime._id,
                                    malId = anime.malId ?: "",
                                    seasonId = episode.season ?: "",
                                    episodeNumber = episode.number,
                                    animeTitle = anime.title,
                                    episodeTitle = episode.title ?: "Episode ${episode.number}",
                                    animePoster = anime.poster ?: ""
                                )
                            )
                        },
                        onAnimeClick = { id -> navController.navigate(Screen.Detail(id)) },
                        onDownloadEpisode = { anime, episode ->
                            val aniListId = anime._id.anilistIdOrNull()
                            if (aniListId != null) {
                                container.downloads.enqueue(anime._id, aniListId, anime.title, anime.poster, episode.number)
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "Added Episode ${episode.number} to Downloads",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }

                composable<Screen.Settings> {
                    SettingsScreen(
                        sessionManager = container.sessionManager,
                        repository = container.repository,
                        updateService = container.episodeUpdateService,
                        onBackClick = { navController.popBackStack() },
                        onLogout = {
                            navController.navigate(Screen.Login) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onEditProfileClick = {
                            navController.navigate(Screen.EditProfile)
                        },
                        onStreamSourcesClick = {
                            navController.navigate(Screen.Extensions)
                        },
                        onDownloadsClick = {
                            navController.navigate(Screen.Downloads)
                        },
                        onThemeChange = { theme ->
                            themeNameState.value = theme
                        }
                    )
                }

                composable<Screen.EditProfile> {
                    EditProfileScreen(
                        sessionManager = container.sessionManager,
                        onBackClick = { navController.popBackStack() },
                        onSelectAvatarClick = {
                            navController.navigate(Screen.SelectAvatar)
                        }
                    )
                }

                composable<Screen.SelectAvatar> {
                    SelectAvatarScreen(
                        sessionManager = container.sessionManager,
                        onBackClick = { navController.popBackStack() }
                    )
                }
                
                composable<Screen.Watchlist> {
                    WatchlistScreen(
                        viewModel = watchlistViewModel,
                        onAnimeClick = { id -> navController.navigate(Screen.Detail(id)) },
                        onResumeClick = { state ->
                            navController.navigate(
                                Screen.Player(
                                    animeId = state.animeId,
                                    malId = "",
                                    seasonId = state.seasonId ?: "simulated_season",
                                    episodeNumber = state.lastEpisode,
                                    animeTitle = state.animeTitle ?: "",
                                    episodeTitle = state.episodeTitle ?: "Episode ${state.lastEpisode}",
                                    animePoster = state.animePoster ?: ""
                                )
                            )
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable<Screen.Downloads> {
                    com.example.kitty.download.KittyDownloadsScreen(
                        manager = container.downloads,
                        onPlay = { item ->
                            val path = item.filePath
                            if (path != null) {
                                navController.navigate(Screen.OfflinePlayer(item.animeTitle, item.episode, path))
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<Screen.OfflinePlayer> { backStackEntry ->
                    val offline: Screen.OfflinePlayer = backStackEntry.toRoute()
                    com.example.kitty.player.KittyOfflinePlayer(
                        title = offline.title,
                        episode = offline.episode,
                        path = offline.path,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<Screen.Extensions> {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .statusBarsPadding()
                    ) {
                        androidx.compose.material3.TextButton(onClick = { navController.popBackStack() }) {
                            Text("← Back", color = androidx.compose.ui.graphics.Color.White)
                        }
                        com.example.kitty.ui.ExtensionStoreFeed(catalog = container.streamServers)
                    }
                }

                composable<Screen.Notifications> {
                    // Reset unread count when viewing center
                    container.episodeUpdateService.resetUnreadCount()
                    
                    NotificationCenterScreen(
                        updateService = container.episodeUpdateService,
                        onBackClick = { navController.popBackStack() },
                        onNotificationClick = { notification ->
                            navController.navigate(
                                Screen.Player(
                                    animeId = notification.animeId,
                                    malId = notification.malId ?: "",
                                    seasonId = notification.seasonId ?: "simulated_season",
                                    episodeNumber = notification.episodeNumber,
                                    animeTitle = notification.animeTitle,
                                    episodeTitle = notification.episodeTitle,
                                    animePoster = notification.animePoster
                                )
                            )
                        }
                    )
                }

                composable<Screen.Player> { backStackEntry ->
                    val player: Screen.Player = backStackEntry.toRoute()
                    if (player.animeId.startsWith(com.example.kitty.KITTY_ID_PREFIX)) {
                        // AniList titles play through the Media3 player ported from AniKitty.
                        val totalEpisodes = player.animeId.anilistIdOrNull()
                            ?.let { container.kitty.cached(it)?.releasedEpisodeCount() }
                            ?.takeIf { it > 0 }
                        com.example.kitty.player.KittyPlayerHost(
                            catalog = container.streamServers,
                            animeId = player.animeId,
                            animeTitle = player.animeTitle,
                            startEpisode = player.episodeNumber,
                            lastEpisode = totalEpisodes,
                            onBack = { navController.popBackStack() },
                            saveProgress = { episode, positionMs, durationMs ->
                                val fraction = if (durationMs > 0) {
                                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                container.repository.saveLocalWatchState(player.animeId, episode, fraction)
                            }
                        )
                    } else {
                        val viewModel: PlayerViewModel = viewModel(factory = ViewModelFactory(container.repository))
                        PlayerScreen(
                            viewModel = viewModel,
                            animeId = player.animeId,
                            malId = player.malId,
                            seasonId = player.seasonId,
                            episodeNumber = player.episodeNumber,
                            animeTitle = player.animeTitle,
                            episodeTitle = player.episodeTitle,
                            animePoster = player.animePoster,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // Custom Splash/Loading Screen overlay
        AnimatedVisibility(
            visible = showInAppSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(durationMillis = 600)),
            modifier = Modifier.zIndex(100f)
        ) {
            JumpAnimeSplashScreen()
        }

        // Overlay Banner
        AnimatedVisibility(
            visible = activeNotification != null && (container.sessionManager.isLoggedIn() || container.sessionManager.isGuestMode()),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(99f)
        ) {
            activeNotification?.let { notification ->
                InAppNotificationBanner(
                    notification = notification,
                    onDismiss = { container.episodeUpdateService.dismissNotification() },
                    onWatchNow = {
                        container.episodeUpdateService.dismissNotification()
                        navController.navigate(
                            Screen.Player(
                                animeId = notification.animeId,
                                malId = notification.malId ?: "",
                                seasonId = notification.seasonId ?: "simulated_season",
                                episodeNumber = notification.episodeNumber,
                                animeTitle = notification.animeTitle,
                                episodeTitle = notification.episodeTitle,
                                animePoster = notification.animePoster
                            )
                        )
                    }
                )
            }
        }

        // Automatic One-time Update Popup Dialog
        if (updateState is com.example.utils.UpdateState.UpdateAvailable) {
            val available = updateState as com.example.utils.UpdateState.UpdateAvailable
            if (available.tagName != lastDismissedVersion && !hasDismissedDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        hasDismissedDialog = true
                        container.sessionManager.setLastDismissedUpdateVersion(available.tagName)
                        lastDismissedVersion = available.tagName
                        com.example.utils.UpdateManager.resetState()
                    },
                    title = {
                        Text(
                            text = "New Version Available: ${available.tagName}",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(
                                text = "A new update of JumpAnime is ready. Would you like to update now?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                            )
                            if (available.body.isNotEmpty()) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Release Notes:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.Card(
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 120.dp)
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = available.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                hasDismissedDialog = true
                                container.sessionManager.setLastDismissedUpdateVersion(available.tagName)
                                lastDismissedVersion = available.tagName
                                scope.launch {
                                    com.example.utils.UpdateManager.downloadAndInstallApk(context, available.downloadUrl, available.fileName)
                                }
                            }
                        ) {
                            Text("Update Now")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                hasDismissedDialog = true
                                container.sessionManager.setLastDismissedUpdateVersion(available.tagName)
                                lastDismissedVersion = available.tagName
                                com.example.utils.UpdateManager.resetState()
                            }
                        ) {
                            Text("Later")
                        }
                    },
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    textContentColor = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

class ViewModelFactory(
    private val repository: com.example.data.JumpAnimeRepository,
    private val episodeUpdateService: com.example.data.EpisodeUpdateService? = null
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repository) as T
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository) as T
            modelClass.isAssignableFrom(BrowseViewModel::class.java) -> BrowseViewModel(repository) as T
            modelClass.isAssignableFrom(DetailViewModel::class.java) -> DetailViewModel(repository) as T
            modelClass.isAssignableFrom(WatchlistViewModel::class.java) -> WatchlistViewModel(repository) as T
            modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(repository) as T
            modelClass.isAssignableFrom(ScheduleViewModel::class.java) -> ScheduleViewModel(repository, episodeUpdateService) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
