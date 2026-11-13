package com.example.kitty

import android.content.Context
import com.example.data.model.Anime
import com.example.data.model.Episode
import com.example.data.model.Season
import com.example.kitty.anilist.AniListClient
import com.example.kitty.anilist.AnimeSummary
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapted from AniKitty (MIT, https://github.com/fampep/anikitty) — see NOTICE.
 *
 * AniList is the source for catalog, search, detail and episode lists.
 * Shows from here carry an "al_<anilistId>" id so they never collide with
 * backend ids or the "mal_<id>" ids JumpAnime already uses.
 */
const val KITTY_ID_PREFIX = "al_"

fun kittyId(anilistId: Int): String = "$KITTY_ID_PREFIX$anilistId"

fun String.anilistIdOrNull(): Int? =
    if (startsWith(KITTY_ID_PREFIX)) removePrefix(KITTY_ID_PREFIX).toIntOrNull()?.takeIf { it > 0 } else null

private fun statusText(raw: String?): String? = when (raw?.uppercase()) {
    "RELEASING" -> "Currently Airing"
    "FINISHED" -> "Finished Airing"
    "NOT_YET_RELEASED" -> "Upcoming"
    "CANCELLED" -> "Cancelled"
    "HIATUS" -> "Hiatus"
    else -> raw
}

private fun typeText(raw: String?): String? = when (raw?.uppercase()) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV"
    "MOVIE" -> "Movie"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "SPECIAL" -> "Special"
    "MUSIC" -> "Music"
    else -> raw
}

/** AniList show -> the model the rest of JumpAnime's screens already understand. */
fun AnimeSummary.toAnime(): Anime {
    val animeId = kittyId(this.id)
    return Anime(
        _id = animeId,
        title = title,
        titleJapanese = nativeTitle,
        poster = coverImageUrl,
        banner = bannerImageUrl,
        description = description,
        rating = averageScore?.let { it / 10.0 },
        totalSeasons = 1,
        type = typeText(format),
        status = statusText(status),
        year = seasonYear,
        genres = genres,
        episodesCount = releasedEpisodeCount(),
        malId = malId?.toString(),
        // Empty on purpose: the detail screen then builds episodes from AniList
        // (see KittyCatalog.episodesFor) instead of asking the old backend.
        seasons = emptyList(),
        popularity = popularity ?: 0,
    )
}

/** Episode rows for the existing episode list UI. */
fun AnimeSummary.toEpisodes(animeId: String): List<Episode> {
    if (status.equals("NOT_YET_RELEASED", ignoreCase = true)) return emptyList()
    return episodeItems().map { item ->
        Episode(
            _id = "${animeId}_ep_${item.number}",
            anime = animeId,
            season = "${animeId}_s1",
            number = item.number,
            title = item.title,
            thumbnail = item.thumbnailUrl,
            duration = durationMinutes?.let { "$it min" },
        )
    }
}

class KittyCatalog(context: Context) {
    val client = AniListClient(context.applicationContext)

    /** Everything seen this session. Detail payloads are never replaced by thinner list payloads. */
    private val summaries = ConcurrentHashMap<Int, AnimeSummary>()

    fun remember(list: List<AnimeSummary>): List<AnimeSummary> {
        for (item in list) {
            summaries.merge(item.id, item) { old, new ->
                if (old.detailLoaded && !new.detailLoaded) old else new
            }
        }
        return list
    }

    fun cached(anilistId: Int): AnimeSummary? = summaries[anilistId]

    /** Full detail (studios, staff, characters, relations, trailer, recommendations, episodes). */
    suspend fun detail(anilistId: Int, force: Boolean = false): AnimeSummary? {
        val have = summaries[anilistId]
        if (!force && have?.detailLoaded == true) return have
        val fresh = client.media(anilistId) ?: return have
        summaries[anilistId] = fresh
        return fresh
    }

    suspend fun episodesFor(animeId: String): List<Episode> {
        val id = animeId.anilistIdOrNull() ?: return emptyList()
        return detail(id)?.toEpisodes(animeId).orEmpty()
    }

    suspend fun trending(perPage: Int, page: Int = 1): List<Anime> =
        remember(client.trending(page = page, perPage = perPage)).map { it.toAnime() }

    suspend fun popular(perPage: Int, page: Int = 1): List<Anime> =
        remember(client.popular(page = page, perPage = perPage)).map { it.toAnime() }

    suspend fun topRated(perPage: Int, page: Int = 1): List<Anime> =
        remember(client.browse(sort = "SCORE_DESC", page = page, perPage = perPage)).map { it.toAnime() }

    suspend fun recent(perPage: Int, page: Int = 1): List<Anime> =
        remember(
            client.browse(status = "RELEASING", sort = "START_DATE_DESC", page = page, perPage = perPage),
        ).map { it.toAnime() }

    /**
     * Search / browse using the filter names JumpAnime's screens already use
     * ("Currently Airing", "Movie", "Recently added" ...).
     */
    suspend fun search(
        query: String?,
        genre: String?,
        type: String?,
        status: String?,
        sort: String?,
        page: Int,
        perPage: Int,
    ): List<Anime> {
        val format = when (type?.lowercase()) {
            "tv" -> "TV"
            "movie" -> "MOVIE"
            "ova" -> "OVA"
            "ona" -> "ONA"
            "special" -> "SPECIAL"
            "music" -> "MUSIC"
            else -> null
        }
        val aStatus = when (status?.lowercase()) {
            "ongoing", "currently airing" -> "RELEASING"
            "completed", "finished airing" -> "FINISHED"
            "upcoming" -> "NOT_YET_RELEASED"
            else -> null
        }
        val aSort = when (sort?.lowercase()) {
            "title" -> "TITLE_ROMAJI"
            "recently_added", "recently added", "recently updated", "recently_updated",
            "recently premiered", "latest" -> "START_DATE_DESC"
            "rating" -> "SCORE_DESC"
            else -> if (query.isNullOrBlank()) "POPULARITY_DESC" else "SEARCH_MATCH"
        }
        val g = genre?.takeIf { it.isNotBlank() && it != "All" && it != "All Genres" }
        val isGenre = g != null && g in OFFICIAL_GENRES
        return remember(
            client.browse(
                query = query.orEmpty(),
                format = format,
                status = aStatus,
                genre = if (isGenre) g else null,
                tag = if (g != null && !isGenre) g else null,
                sort = aSort,
                page = page,
                perPage = perPage,
            ),
        ).map { it.toAnime() }
    }

    private companion object {
        val OFFICIAL_GENRES = setOf(
            "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
            "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
            "Supernatural", "Thriller",
        )
    }
}
