package com.example.data

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.model.Anime
import com.example.data.model.isMovie
import com.example.data.model.EpisodeNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class EpisodeUpdateService(
    private val context: Context,
    private val repository: JumpAnimeRepository,
    private val notificationDao: com.example.data.local.NotificationDao
) {
    private val serviceScope = CoroutineScope(Dispatchers.`IO`)

    private val _activeNotification = MutableStateFlow<EpisodeNotification?>(null)
    val activeNotification = _activeNotification.asStateFlow()

    private val _notificationsHistory = MutableStateFlow<List<EpisodeNotification>>(emptyList())
    val notificationsHistory = _notificationsHistory.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount = _unreadCount.asStateFlow()

    init {
        serviceScope.launch {
            notificationDao.getAllNotifications().collect { entities ->
                _notificationsHistory.value = entities.map { it.toModel() }
            }
        }
    }

    private fun com.example.data.local.NotificationEntity.toModel() = EpisodeNotification(
        animeId = animeId,
        animeTitle = animeTitle,
        animePoster = animePoster,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        malId = malId,
        seasonId = seasonId,
        timestamp = timestamp
    )

    private fun EpisodeNotification.toEntity() = com.example.data.local.NotificationEntity(
        animeId = animeId,
        animeTitle = animeTitle,
        animePoster = animePoster,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        malId = malId,
        seasonId = seasonId,
        timestamp = timestamp
    )

    fun dismissNotification() {
        _activeNotification.value = null
    }

    fun resetUnreadCount() {
        _unreadCount.value = 0
        repository.sessionManager.resetUnreadCount()
    }

    fun clearNotifications() {
        serviceScope.launch {
            notificationDao.deleteAll()
        }
        _unreadCount.value = 0
    }

    fun postNotification(notification: EpisodeNotification) {
        serviceScope.launch {
            try {
                notificationDao.insertNotification(notification.toEntity())
                repository.sessionManager.incrementUnreadCount()
                withContext(Dispatchers.Main) {
                    _activeNotification.value = notification
                    _unreadCount.value = repository.sessionManager.getUnreadCount()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun simulateUpdateCheck(simulateAnime: Anime? = null) {
        val isLoggedIn = repository.sessionManager.isLoggedIn()
        val isGuest = repository.sessionManager.isGuestMode()
        if (!isLoggedIn && !isGuest) return
        serviceScope.launch {
            try {
                var animeToNotify: Anime? = simulateAnime
                var episodeNum = 1
                var epTitle = ""

                if (animeToNotify == null) {
                    val watchlist = try {
                        repository.getWatchlist()
                    } catch (e: Exception) {
                        emptyList<Anime>()
                    }

                    if (watchlist.isNotEmpty()) {
                        // Pick one from tracked watchlist
                        val selected = watchlist[Random.nextInt(watchlist.size)]
                        animeToNotify = selected

                        if (selected.isMovie()) {
                            episodeNum = 1
                            epTitle = "Full Movie Released"
                        } else {
                            // Check local watchlist episode
                            val localState = repository.getLocalWatchState(selected._id)
                            val currentEp = localState?.lastEpisode ?: 0
                            episodeNum = currentEp + 1
                            epTitle = "Episode $episodeNum: Unlocking New Power"
                        }
                    } else {
                        // Fallback to trending/featured anime so the feature can always be demoed!
                        val (trending, featured) = repository.getTrendingAndFeatured(null, null)
                        val pool = trending + featured
                        if (pool.isNotEmpty()) {
                            val selected = pool[Random.nextInt(pool.size)]
                            animeToNotify = selected
                            if (selected.isMovie()) {
                                episodeNum = 1
                                epTitle = "Full Movie Released"
                            } else {
                                episodeNum = Random.nextInt(1, 13)
                                epTitle = "Episode $episodeNum: The Adventure Begins"
                            }
                        } else {
                            animeToNotify = null
                            episodeNum = 0
                            epTitle = ""
                        }
                    }
                } else {
                    if (animeToNotify.isMovie()) {
                        episodeNum = 1
                        epTitle = "Full Movie Released"
                    } else {
                        val lastEp = animeToNotify.episodesCount
                        val latestAired = if (animeToNotify.malId != null) {
                            repository.getLatestAiredEpisodeFromSchedule(animeToNotify.malId)?.first ?: 0
                        } else 0
                        
                        val baseEp = maxOf(lastEp, latestAired)
                        episodeNum = if (baseEp > 0) baseEp + 1 else Random.nextInt(1, 13)
                        epTitle = "Episode $episodeNum: Simulated Special"
                    }
                }

                if (animeToNotify != null) {
                    val seasonId = animeToNotify.seasons.firstOrNull()?._id ?: "simulated_season"
                    val notification = EpisodeNotification(
                        animeId = animeToNotify._id,
                        animeTitle = animeToNotify.title,
                        animePoster = animeToNotify.poster ?: "",
                        episodeNumber = episodeNum,
                        episodeTitle = epTitle,
                        malId = animeToNotify.malId,
                        seasonId = seasonId
                    )
                    
                    notificationDao.insertNotification(notification.toEntity())
                    
                    withContext(Dispatchers.Main) {
                        _activeNotification.value = notification
                        _unreadCount.value += 1
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun syncScheduleWithNotificationsAsync() {
        serviceScope.launch {
            syncScheduleWithNotifications()
        }
    }

    fun showSubscriptionConfirmationNotification(animeId: String, animeTitle: String, animePoster: String, episodeNumText: String) {
        if (!repository.sessionManager.isNotificationsEnabled()) return
        
        val channelId = "new_episode_channel_v2"
        val title = "Alert Scheduled"
        val message = "You will be notified as soon as $animeTitle $episodeNumText is available!"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "New Episodes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for newly released anime episodes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_CLICK", true)
            putExtra("ANIME_ID", animeId)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 
            animeId.hashCode() + 1, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(animeId.hashCode() + 1, notification)
    }

    suspend fun syncScheduleWithNotifications() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch watchlist & subscribed alert ids
                val watchlist = try {
                    repository.getWatchlist()
                } catch (e: Exception) {
                    emptyList<Anime>()
                }
                val subscribedAlertIds = repository.sessionManager.getSubscribedAlertAnimeIds()
                val allTrackedIds = (watchlist.map { it._id } + subscribedAlertIds).toSet()

                if (allTrackedIds.isEmpty()) return@withContext

                // 2. Fetch recent/upcoming airing schedules from AniList (starting from 3 days ago)
                val threeDaysAgo = (System.currentTimeMillis() - 3L * 24 * 3600 * 1000) / 1000
                
                val schedulesPage1 = try {
                    repository.getAniListUpcomingSchedules(page = 1, perPage = 50, airingAtGreater = threeDaysAgo)
                } catch (e: Exception) {
                    emptyList()
                }
                
                val schedulesPage2 = try {
                    repository.getAniListUpcomingSchedules(page = 2, perPage = 50, airingAtGreater = threeDaysAgo)
                } catch (e: Exception) {
                    emptyList()
                }

                val allSchedules = (schedulesPage1 + schedulesPage2).distinctBy { it.id }
                if (allSchedules.isEmpty()) return@withContext

                // Cache newly fetched schedules for other components
                repository.cacheSchedules(allSchedules)

                val now = System.currentTimeMillis() / 1000

                // 3. For each schedule, if it matches our tracked anime and has aired, process notifications
                for (schedule in allSchedules) {
                    val malId = schedule.media?.idMal ?: continue
                    val animeId = "mal_$malId"

                    if (allTrackedIds.contains(animeId)) {
                        // Episode has already aired or is currently airing
                        if (schedule.airingAt <= now) {
                            val episodeNum = schedule.episode
                            val lastKnown = repository.sessionManager.getLastKnownEpisode(animeId)

                            if (lastKnown == -1) {
                                // First time seeing this anime's airing status; seed to prevent old notifications spam
                                repository.sessionManager.setLastKnownEpisode(animeId, episodeNum)
                            } else if (episodeNum > lastKnown) {
                                // A new episode has aired!
                                repository.sessionManager.setLastKnownEpisode(animeId, episodeNum)

                                val animeTitle = schedule.media.title?.english 
                                    ?: schedule.media.title?.romaji 
                                    ?: "Tracked Anime"
                                val animePoster = schedule.media.coverImage?.large ?: ""
                                val epTitle = "Episode $episodeNum"

                                val notification = EpisodeNotification(
                                    animeId = animeId,
                                    animeTitle = animeTitle,
                                    animePoster = animePoster,
                                    episodeNumber = episodeNum,
                                    episodeTitle = "$epTitle is now streaming!",
                                    malId = malId.toString(),
                                    seasonId = "simulated_season",
                                    timestamp = System.currentTimeMillis()
                                )

                                // Insert to DB
                                notificationDao.insertNotification(notification.toEntity())
                                repository.sessionManager.incrementUnreadCount()

                                // Update flow states and trigger native system tray notification
                                withContext(Dispatchers.Main) {
                                    _activeNotification.value = notification
                                    _unreadCount.value = repository.sessionManager.getUnreadCount()
                                }
                                
                                showSystemNotification(
                                    animeId = animeId,
                                    animeTitle = animeTitle,
                                    animePoster = animePoster,
                                    episodeNum = episodeNum,
                                    episodeTitle = "Aired via Schedule Sync!",
                                    malId = malId.toString()
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showSystemNotification(
        animeId: String,
        animeTitle: String,
        animePoster: String,
        episodeNum: Int,
        episodeTitle: String,
        malId: String?
    ) {
        if (!repository.sessionManager.isNotificationsEnabled()) return
        
        val channelId = "new_episode_channel_v2"
        val title = "New Episode: $animeTitle"
        val message = "Episode $episodeNum - $episodeTitle is now available!"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val totalUnread = repository.sessionManager.getUnreadCount()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "New Episodes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for newly released anime episodes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_CLICK", true)
            putExtra("ANIME_ID", animeId)
            putExtra("MAL_ID", malId ?: "")
            putExtra("SEASON_ID", "simulated_season")
            putExtra("EPISODE_NUMBER", episodeNum)
            putExtra("ANIME_TITLE", animeTitle)
            putExtra("ANIME_POSTER", animePoster)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 
            animeId.hashCode(), 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setNumber(totalUnread)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(animeId.hashCode(), notification)
    }
}
