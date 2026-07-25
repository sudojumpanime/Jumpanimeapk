package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.DependencyContainer
import com.example.R
import com.example.data.model.Anime

class NewEpisodeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = DependencyContainer(applicationContext)
        val repository = container.repository
        
        val isLoggedIn = repository.sessionManager.isLoggedIn()
        val isGuest = repository.sessionManager.isGuestMode()
        if (!isLoggedIn && !isGuest) return Result.success()
        if (!repository.sessionManager.isNotificationsEnabled()) return Result.success()

        return try {
            // Run the unified high-precision schedule-to-notification sync first
            try {
                container.episodeUpdateService.syncScheduleWithNotifications()
            } catch (e: Exception) {
                // Ignore failure of schedule sync so manual checks can still run
            }

            // 1. Fetch watchlist anime
            val watchlist = try {
                repository.getWatchlist()
            } catch (e: Exception) {
                emptyList()
            }

            // 2. Fetch subscribed alerts
            val subscribedAlertIds = repository.sessionManager.getSubscribedAlertAnimeIds()
            val subscribedAlertAnimes = mutableListOf<Anime>()
            
            for (id in subscribedAlertIds) {
                try {
                    // Try local cache first
                    var cached = container.database.animeDao().getById(id)
                    if (cached == null) {
                        // Fetch from API and save
                        cached = repository.getAnimeDetail(id)
                    }
                    if (cached != null) {
                        subscribedAlertAnimes.add(cached)
                    }
                } catch (e: Exception) {
                    // Ignore individual failures to check others
                }
            }

            // Combine both watchlist and subscribed alerts
            val allTrackedAnimes = (watchlist + subscribedAlertAnimes).distinctBy { it._id }

            for (anime in allTrackedAnimes) {
                try {
                    checkAnimeForNewEpisode(anime, repository, container)
                } catch (e: Exception) {
                    // Ignore individual failures to avoid stopping the worker
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun checkAnimeForNewEpisode(
        anime: Anime, 
        repository: com.example.data.JumpAnimeRepository,
        container: DependencyContainer
    ) {
        val isMal = anime._id.startsWith("mal_")
        var latestEp = 0
        var epTitle = ""
        var finalSeasonId = "simulated_season"

        if (isMal) {
            // Comply with Jikan API rate limit (3 requests per second max)
            kotlinx.coroutines.delay(1200)
            
            val realTimeAnime = repository.getAnimeDetail(anime._id)
            var epCount = realTimeAnime.episodesCount
            
            // Check AniList schedules to see if a newer episode has aired according to the high-precision schedule
            val malIdStr = anime.malId ?: anime._id.replace("mal_", "")
            val scheduleInfo = repository.getLatestAiredEpisodeFromSchedule(malIdStr)
            if (scheduleInfo != null) {
                epCount = maxOf(epCount, scheduleInfo.first)
            }
            
            latestEp = epCount
            epTitle = "Episode $latestEp"
        } else {
            val realTimeAnime = repository.getAnimeDetail(anime._id)
            val season = realTimeAnime.seasons.firstOrNull()
            if (season != null) {
                finalSeasonId = season._id
                val epList = repository.getEpisodes(season._id)
                latestEp = epList.map { it.number }.maxOrNull() ?: 0
                val latestEpObj = epList.firstOrNull { it.number == latestEp }
                epTitle = latestEpObj?.title ?: "Episode $latestEp"
            }
        }

        if (latestEp <= 0) return

        val lastKnown = repository.sessionManager.getLastKnownEpisode(anime._id)

        if (lastKnown == -1) {
            // First check, seed the last known episode to prevent notification spam
            repository.sessionManager.setLastKnownEpisode(anime._id, latestEp)
        } else if (latestEp > lastKnown) {
            // Confirmed new episode release! Update known state
            repository.sessionManager.setLastKnownEpisode(anime._id, latestEp)
            repository.sessionManager.incrementUnreadCount()

            // Save notification into local database so it shows up in Notification Center
            val episodeNotification = com.example.data.model.EpisodeNotification(
                animeId = anime._id,
                animeTitle = anime.title,
                animePoster = anime.poster ?: "",
                episodeNumber = latestEp,
                episodeTitle = epTitle,
                malId = anime.malId,
                seasonId = finalSeasonId
            )
            container.database.notificationDao().insertNotification(episodeNotification.toEntity())

            // Trigger system status bar notification
            showNotification(anime, latestEp, epTitle, finalSeasonId, repository)
        }
    }

    private fun com.example.data.model.EpisodeNotification.toEntity() = com.example.data.local.NotificationEntity(
        animeId = animeId,
        animeTitle = animeTitle,
        animePoster = animePoster,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        malId = malId,
        seasonId = seasonId,
        timestamp = timestamp
    )

    private fun showNotification(
        anime: Anime, 
        latestEp: Int, 
        episodeTitle: String, 
        seasonId: String,
        repository: com.example.data.JumpAnimeRepository
    ) {
        val channelId = "new_episode_channel_v2"
        val title = "New Episode: ${anime.title}"
        val message = "Episode $latestEp - $episodeTitle is now available!"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val totalUnread = repository.sessionManager.getUnreadCount()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "New Episodes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for newly released anime episodes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(applicationContext, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_CLICK", true)
            putExtra("ANIME_ID", anime._id)
            putExtra("MAL_ID", anime.malId ?: "")
            putExtra("SEASON_ID", seasonId)
            putExtra("EPISODE_NUMBER", latestEp)
            putExtra("ANIME_TITLE", anime.title)
            putExtra("ANIME_POSTER", anime.poster ?: "")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext, 
            anime._id.hashCode(), 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Standard info icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setNumber(totalUnread)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(anime._id.hashCode(), notification)
    }
}
