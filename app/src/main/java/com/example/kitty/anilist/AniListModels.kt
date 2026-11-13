package com.example.kitty.anilist

data class AnimeTrailer(
    val id: String,
    val site: String,
    val thumbnailUrl: String? = null,
) {
    fun watchUrl(): String? = when (site.lowercase()) {
        "youtube" -> "https://www.youtube.com/watch?v=$id"
        "dailymotion" -> "https://www.dailymotion.com/video/$id"
        else -> null
    }
}

data class AnimeExternalLink(
    val site: String,
    val url: String,
)

data class AnimeStreamingEpisode(
    val title: String,
    val thumbnailUrl: String? = null,
    val url: String? = null,
    val site: String? = null,
)

/** Resolved episode row for the Plex-style selector. */
data class AnimeEpisodeItem(
    val number: Int,
    val title: String,
    val thumbnailUrl: String? = null,
    val site: String? = null,
)

data class AnimeRelation(
    val id: Int,
    val title: String,
    val coverImageUrl: String?,
    val relationType: String,
    val format: String? = null,
    val episodes: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
) {
    /** AniList airing season label (e.g. "Fall 2024"). */
    fun seasonLabel(): String? {
        val s = season?.lowercase()?.replaceFirstChar { it.titlecase() }
        return when {
            s != null && seasonYear != null -> "$s $seasonYear"
            seasonYear != null -> seasonYear.toString()
            s != null -> s
            else -> null
        }
    }
}

data class AnimePerson(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val role: String?,
)

data class NextAiring(
    val episode: Int,
    val airingAtEpochSec: Long,
)

/** The signed-in viewer's AniList list row for one show. Score is 0–100. */
data class AnimeListState(
    val status: String,
    val progress: Int,
    val score: Int,
)

data class AiringSlot(
    val anime: AnimeSummary,
    val episode: Int,
    val airingAtEpochSec: Long,
)

data class AnimeSummary(
    val id: Int,
    val title: String,
    val coverImageUrl: String?,
    val bannerImageUrl: String? = null,
    val episodes: Int?,
    val format: String?,
    val averageScore: Int?,
    val description: String? = null,
    /** AniList romaji — often matches Anikoto page titles better than English. */
    val romajiTitle: String? = null,
    val nativeTitle: String? = null,
    /** MyAnimeList id, kept with local watch history. */
    val malId: Int? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val durationMinutes: Int? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val trailer: AnimeTrailer? = null,
    val externalLinks: List<AnimeExternalLink> = emptyList(),
    val relations: List<AnimeRelation> = emptyList(),
    val streamingEpisodes: List<AnimeStreamingEpisode> = emptyList(),
    val characters: List<AnimePerson> = emptyList(),
    val staff: List<AnimePerson> = emptyList(),
    val recommendations: List<AnimeSummary> = emptyList(),
    val nextAiring: NextAiring? = null,
    val listState: AnimeListState? = null,
    val isFavourite: Boolean = false,
    /** True when this payload came from the richer Media(id) query. */
    val detailLoaded: Boolean = false,
) {
    /** Titles to try when mapping a watch page / search (romaji first — matches Anikoto). */
    fun searchTitles(): List<String> =
        listOfNotNull(romajiTitle, title, nativeTitle)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun seasonLabel(): String? {
        val s = season?.lowercase()?.replaceFirstChar { it.titlecase() }
        return when {
            s != null && seasonYear != null -> "$s $seasonYear"
            seasonYear != null -> seasonYear.toString()
            s != null -> s
            else -> null
        }
    }

    fun statusLabel(): String? = when (status?.uppercase()) {
        "FINISHED" -> "Finished"
        "RELEASING" -> "Airing"
        "NOT_YET_RELEASED" -> "Upcoming"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> status?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
    }

    fun scoreLabel(): String? = averageScore?.let { String.format("%.1f", it / 10f) }

    /**
     * Season switcher from AniList only: this media + direct PREQUEL/SEQUEL TV/ONA entries.
     * Side stories / alternatives / movies stay in [relations] — not mixed into seasons.
     */
    fun seasonEntries(): List<AnimeRelation> {
        val self = AnimeRelation(
            id = id,
            title = title,
            coverImageUrl = coverImageUrl,
            relationType = "Current",
            format = format,
            episodes = episodes,
            season = season,
            seasonYear = seasonYear,
        )
        val seasonFormats = setOf("TV", "TV_SHORT", "ONA")
        val chain = relations.filter { rel ->
            val fmt = rel.format?.uppercase()
            val kind = rel.relationType.uppercase().replace(' ', '_')
            (fmt == null || fmt in seasonFormats) && kind in setOf("PREQUEL", "SEQUEL")
        }
        return (listOf(self) + chain)
            .distinctBy { it.id }
            .sortedWith(
                compareBy<AnimeRelation> { it.seasonYear ?: Int.MAX_VALUE }
                    .thenBy { it.id },
            )
    }

    /** Related row excludes titles already shown as season chips. */
    fun relatedOutsideSeasons(): List<AnimeRelation> {
        val seasonIds = seasonEntries().map { it.id }.toSet()
        return relations.filterNot { it.id in seasonIds }
    }

    /**
     * Episodes AniList knows about.
     * Finished shows use [episodes]. Airing shows leave that null, so the count is
     * one less than [nextAiring] (the next episode has not aired yet).
     */
    fun releasedEpisodeCount(): Int {
        val listed = episodes?.takeIf { it > 0 } ?: 0
        val aired = nextAiring?.episode?.minus(1)?.takeIf { it > 0 } ?: 0
        return maxOf(listed, aired)
    }

    /** Episode list for this media. Titles and thumbs come from AniList streaming episodes when present. */
    fun episodeItems(fallbackCover: String? = coverImageUrl): List<AnimeEpisodeItem> {
        val byNumber = LinkedHashMap<Int, AnimeStreamingEpisode>()
        streamingEpisodes.forEachIndexed { index, ep ->
            val n = parseStreamingEpisodeNumber(ep.title) ?: (index + 1)
            byNumber.putIfAbsent(n, ep)
        }
        val maxFromStream = byNumber.keys.maxOrNull() ?: 0
        val max = maxOf(releasedEpisodeCount(), maxFromStream)
            .coerceAtLeast(1)
            .coerceAtMost(12_000)
        return (1..max).map { n ->
            val stream = byNumber[n]
            AnimeEpisodeItem(
                number = n,
                title = cleanStreamingEpisodeTitle(stream?.title) ?: "Episode $n",
                thumbnailUrl = stream?.thumbnailUrl ?: fallbackCover,
                site = stream?.site,
            )
        }
    }
}

internal fun parseStreamingEpisodeNumber(raw: String): Int? {
    val patterns = listOf(
        Regex("""(?i)episode\s*(\d+)"""),
        Regex("""(?i)\bep\.?\s*(\d+)"""),
        Regex("""(?i)^e(\d+)\b"""),
        Regex("""#\s*(\d+)"""),
    )
    for (p in patterns) {
        val m = p.find(raw) ?: continue
        return m.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
    }
    return null
}

internal fun cleanStreamingEpisodeTitle(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw
        .replace(Regex("""(?i)^episode\s*\d+\s*[-–:|]\s*"""), "")
        .replace(Regex("""(?i)^ep\.?\s*\d+\s*[-–:|]\s*"""), "")
        .trim()
        .takeIf { it.isNotBlank() && !it.equals(raw, ignoreCase = true) }
        ?: raw.trim().takeIf { it.isNotBlank() }
}

data class AniListViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val about: String? = null,
    val animeCount: Int = 0,
    val episodesWatched: Int = 0,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0,
    val watching: Int = 0,
    val completed: Int = 0,
    val planning: Int = 0,
    val paused: Int = 0,
    val dropped: Int = 0,
)

data class AniListMediaListEntry(
    val status: String,
    val progress: Int,
    val media: AnimeSummary,
)
