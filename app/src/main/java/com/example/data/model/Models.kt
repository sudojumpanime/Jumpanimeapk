package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers

@Serializable
@Entity(tableName = "animes")
data class Anime(
    @PrimaryKey val _id: String = "",
    val title: String = "",
    val titleJapanese: String? = null,
    val poster: String? = null,
    val banner: String? = null,
    val description: String? = null,
    val rating: Double? = null,
    val totalSeasons: Int? = null,
    val type: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val trending: Boolean = false,
    val featured: Boolean = false,
    val recentlyAdded: Boolean = false,
    val episodesCount: Int = 0,
    val malId: String? = null,
    val seasons: List<Season> = emptyList(),
    val inWatchlist: Boolean = false,
    val popularity: Int = 0,
    val views: Int = 0,
    val viewsCount: Int? = null,
    val views_count: Int? = null,
    val mostPopular: Boolean = false,
    val popularThisMonth: Boolean = false,
    val popularThisWeek: Boolean = false,
    val anilab2Data: Boolean = false
)

@Serializable
data class Season(
    val _id: String = "",
    val number: Int = 1,
    val title: String? = null,
    val year: Int? = null
)

@Serializable
data class Episode(
    val _id: String = "",
    val anime: String? = null, // Backend says "anime" (ID string)
    val season: String? = null, // Backend says "season" (ID string)
    val number: Int = 1,
    val title: String? = null,
    val thumbnail: String? = null,
    val duration: String? = null,
    val streams: List<Stream> = emptyList()
)

@Serializable
data class Stream(
    val label: String = "",
    val src: String = "",
    val type: String = ""
)

@Serializable
data class AnimeListResponse(
    val animes: List<Anime>,
    val total: Int = 0,
    val page: Int = 1
)

@Serializable
data class EpisodeListResponse(
    val episodes: List<Episode>
)

@Serializable
data class EpisodeResponse(
    val episode: Episode
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val email: String,
    val isVerified: Boolean = false
)

@Serializable
data class AuthResponse(
    val token: String? = null,
    val user: User? = null,
    val error: String? = null
)

@Serializable
data class WatchlistResponse(
    val list: List<WatchlistItem>
)

@Serializable
data class WatchlistItem(
    val _id: String,
    val anime: Anime
)

@Serializable
data class PlaybackHistoryRequest(
    val animeId: String,
    val malId: String? = null,
    val seasonId: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val animeTitle: String,
    val animePoster: String,
    val progress: Float = 0f
)

@Serializable
data class ContinueWatchingItem(
    val anime: String, // ID
    val malId: String? = null,
    val season: String, // ID
    val episodeNumber: Int,
    val episodeTitle: String,
    val animeTitle: String,
    val animePoster: String,
    val watchedAt: String,
    val progress: Float = 0f
)

@Serializable
data class ContinueWatchingResponse(
    val continueWatching: List<ContinueWatchingItem>
)

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun Anime.isMovie(): Boolean {
    val typeLower = type?.lowercase() ?: ""
    val titleLower = title.lowercase()
    return typeLower == "movie" || typeLower == "special" || typeLower == "ova" || 
           titleLower.contains("movie") || titleLower.contains("film") || 
           episodesCount == 1
}

private val estimatedEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

suspend fun getEstimatedEpisodesCount(
    animeTitle: String,
    parsedCount: Int,
    animeType: String? = null,
    malId: String? = null,
    repository: com.example.data.JumpAnimeRepository? = null,
    animeStatus: String? = null
): Int {
    val cacheKey = malId ?: animeTitle
    val cachedResult = estimatedEpisodesCache[cacheKey]
    if (cachedResult != null) {
        return cachedResult
    }

    val statusLower = (animeStatus ?: "").lowercase()
    val isNotYetAired = statusLower.contains("not yet") || statusLower.contains("upcoming") || statusLower.contains("not_yet")
    if (isNotYetAired) {
        return 0
    }

    val typeLower = animeType?.lowercase() ?: ""
    val titleLower = animeTitle.lowercase()
    if (typeLower == "movie" || typeLower == "special" || typeLower == "ova" || 
        titleLower.contains("movie") || titleLower.contains("film") || 
        parsedCount == 1) {
        return 1
    }

    val isAiring = statusLower.contains("airing") || statusLower.contains("ongoing") || statusLower.contains("releasing")

    var scheduleCount = 0
    var dynamicCount = -1

    if (repository != null) {
        try {
            coroutineScope {
                val scheduleDeferred = if (malId != null) {
                    async(Dispatchers.IO) {
                        try {
                            repository.getLatestAiredEpisodeFromSchedule(malId)
                        } catch (e: Exception) {
                            null
                        }
                    }
                } else null

                val dynamicDeferred = async(Dispatchers.IO) {
                    try {
                        repository.getDynamicLatestEpisodeCount(animeTitle, malId)
                    } catch (e: Exception) {
                        -1
                    }
                }

                scheduleCount = scheduleDeferred?.await()?.first ?: 0
                dynamicCount = dynamicDeferred.await()
            }
        } catch (e: Exception) {
            // fallback
        }
    }

    val knownEstimate = when {
        titleLower.contains("one piece") -> 1168
        titleLower.contains("conan") || titleLower.contains("case closed") -> 1128
        titleLower.contains("boruto") -> 293
        titleLower.contains("naruto shippuden") -> 500
        titleLower.contains("naruto") -> 220
        titleLower.contains("bleach") && !titleLower.contains("thousand-year") -> 366
        titleLower.contains("pokemon") || titleLower.contains("pocket monsters") -> 1240
        titleLower.contains("dragon ball z") -> 291
        titleLower.contains("dragon ball super") -> 131
        titleLower.contains("dragon ball") -> 153
        titleLower.contains("gintama") -> 367
        titleLower.contains("fairy tail") -> 328
        titleLower.contains("black clover") -> 170
        titleLower.contains("hunter x hunter") -> 148
        else -> -1
    }

    val finalCount = if (isAiring) {
        // For currently airing, the number of released episodes is determined by schedule and dynamic counts.
        // We do not include the total planned parsedCount.
        val count = maxOf(scheduleCount, dynamicCount, if (knownEstimate != -1) knownEstimate else 0)
        if (count > 0) count else 1
    } else {
        val count = maxOf(parsedCount, scheduleCount, dynamicCount, if (knownEstimate != -1) knownEstimate else 0)
        if (count > 0) count else 24
    }

    estimatedEpisodesCache[cacheKey] = finalCount
    return finalCount
}

fun cleanEpisodeTitle(title: String?, episodeNum: Int, animeTitle: String? = null): String {
    if (title == null) return "Episode $episodeNum"
    var cleaned = title.trim()

    // If animeTitle is provided, strip it from the start of the title
    if (animeTitle != null) {
        val at = animeTitle.trim()
        if (cleaned.startsWith(at, ignoreCase = true)) {
            cleaned = cleaned.substring(at.length).trim()
            if (cleaned.startsWith(":") || cleaned.startsWith("-") || cleaned.startsWith(".") || cleaned.startsWith("|")) {
                cleaned = cleaned.substring(1).trim()
            }
        }
    }

    val prefix1 = "Episode $episodeNum:"
    val prefix2 = "Episode $episodeNum"
    if (cleaned.startsWith(prefix1, ignoreCase = true)) {
        cleaned = cleaned.substring(prefix1.length).trim()
    } else if (cleaned.startsWith(prefix2, ignoreCase = true)) {
        cleaned = cleaned.substring(prefix2.length).trim()
        if (cleaned.startsWith(":") || cleaned.startsWith("-") || cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim()
        }
    }

    // Also check if after cleaning the episode prefix, it starts with animeTitle again
    if (animeTitle != null) {
        val at = animeTitle.trim()
        if (cleaned.startsWith(at, ignoreCase = true)) {
            cleaned = cleaned.substring(at.length).trim()
            if (cleaned.startsWith(":") || cleaned.startsWith("-") || cleaned.startsWith(".") || cleaned.startsWith("|")) {
                cleaned = cleaned.substring(1).trim()
            }
        }
    }

    return cleaned.ifBlank { "Episode $episodeNum" }
}

@Serializable
data class CommentDto(
    val id: String = "",
    val animeId: String = "",
    val userId: String = "",
    val username: String = "User",
    val userAvatar: String = "",
    val text: String = "",
    val likesCount: Int = 0,
    val isLikedByUser: Boolean = false,
    val parentId: String? = null,
    val isAuthorAdmin: Boolean = false,
    val replyCount: Int = 0,
    val createdAt: Long = 0L
)

@Serializable
data class CommentsResponse(val comments: List<CommentDto>)

@Serializable
data class PostCommentRequest(
    val text: String,
    val parentId: String? = null,
    val username: String? = null,
    val userAvatar: String? = null
)

@Serializable
data class PostCommentResponse(val comment: CommentDto)

@Serializable
data class LikeResponse(val likesCount: Int, val isLikedByUser: Boolean)

