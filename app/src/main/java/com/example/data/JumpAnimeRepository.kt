package com.example.data

import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.ApiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

class JumpAnimeRepository(
    private val apiProvider: ApiProvider,
    private val watchDao: AnimeWatchDao,
    private val animeDao: AnimeDao,
    private val historyDao: SearchHistoryDao,
    private val customTitleDao: CustomTitleDao,
    val commentDao: CommentDao,
    val sessionManager: SessionManager
) {
    private val api get() = apiProvider.getApi()
    private val jikanApi get() = apiProvider.getJikanApi()
    private val aniListApi get() = apiProvider.getAniListApi()

    val cachedSchedules = java.util.concurrent.CopyOnWriteArrayList<com.example.data.remote.AniListAiringSchedule>()

    fun cacheSchedules(schedules: List<com.example.data.remote.AniListAiringSchedule>) {
        val existingIds = cachedSchedules.map { it.id }.toSet()
        val newSchedules = schedules.filter { it.id !in existingIds }
        cachedSchedules.addAll(newSchedules)
    }

    suspend fun getLatestAiredEpisodeFromSchedule(malId: String): Pair<Int, Long?>? {
        if (cachedSchedules.isEmpty()) {
            try {
                // Fetch upcoming schedules starting from 3 days ago to get recently aired episodes
                val startUnix = (System.currentTimeMillis() - 3L * 24 * 3600 * 1000) / 1000
                getAniListUpcomingSchedules(page = 1, perPage = 50, airingAtGreater = startUnix)
            } catch (e: Exception) {
                // Ignore
            }
        }
        val now = System.currentTimeMillis() / 1000
        var animeSchedules = cachedSchedules.filter { 
            it.media?.idMal?.toString() == malId
        }
        
        if (animeSchedules.isEmpty()) {
            try {
                val malIdInt = malId.toIntOrNull()
                if (malIdInt != null) {
                    val mediaQuery = """
                        query (${'$'}idMal: Int) {
                          Page (page: 1, perPage: 1) {
                            media (idMal: ${'$'}idMal, type: ANIME) {
                              id
                            }
                          }
                        }
                    """.trimIndent()
                    val mediaVars = buildJsonObject {
                        put("idMal", malIdInt)
                    }
                    val mediaResponse = aniListApi.searchAnimeGraphQL(
                        com.example.data.remote.AniListGraphQLSearchRequest(
                            query = mediaQuery,
                            variables = mediaVars
                        )
                    )
                    val aniListMediaId = mediaResponse.data?.Page?.media?.firstOrNull()?.id
                    if (aniListMediaId != null) {
                        val scheduleQuery = """
                            query (${'$'}mediaId: Int) {
                              Page (page: 1, perPage: 10) {
                                airingSchedules (mediaId: ${'$'}mediaId, sort: EPISODE_DESC) {
                                  id
                                  airingAt
                                  episode
                                  media {
                                    idMal
                                  }
                                }
                              }
                            }
                        """.trimIndent()
                        val scheduleVars = buildJsonObject {
                            put("mediaId", aniListMediaId)
                        }
                        val scheduleResponse = aniListApi.searchAnimeGraphQL(
                            com.example.data.remote.AniListGraphQLSearchRequest(
                                query = scheduleQuery,
                                variables = scheduleVars
                            )
                        )
                        val fetched = scheduleResponse.data?.Page?.airingSchedules ?: emptyList()
                        if (fetched.isNotEmpty()) {
                            cachedSchedules.addAll(fetched)
                            animeSchedules = fetched
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        if (animeSchedules.isEmpty()) return null
        
        // 1. If there's an upcoming schedule, say Episode N, then N-1 episodes are already released
        val upcoming = animeSchedules.filter { it.airingAt > now }.minByOrNull { it.episode }
        if (upcoming != null) {
            val releasedCount = upcoming.episode - 1
            if (releasedCount > 0) {
                return Pair(releasedCount, upcoming.airingAt)
            }
        }
        
        // 2. Otherwise, use the maximum already aired episode number from cachedSchedules
        val maxAired = animeSchedules.filter { it.airingAt <= now }.maxByOrNull { it.episode }
        return if (maxAired != null) Pair(maxAired.episode, maxAired.airingAt) else null
    }

    suspend fun getDynamicLatestEpisodeCount(title: String, malId: String?): Int {
        // 1. Try to fetch from AniList using malId
        val malIdInt = malId?.toIntOrNull()
        if (malIdInt != null) {
            try {
                val qString = """
                    query (${'$'}idMal: Int) {
                      Page (page: 1, perPage: 1) {
                        media (idMal: ${'$'}idMal, type: ANIME) {
                          status
                          episodes
                          nextAiringEpisode {
                            episode
                            airingAt
                          }
                        }
                      }
                    }
                """.trimIndent()
                val response = aniListApi.searchAnimeGraphQL(
                    com.example.data.remote.AniListGraphQLSearchRequest(
                        query = qString,
                        variables = buildJsonObject { put("idMal", malIdInt) }
                    )
                )
                val media = response.data?.Page?.media?.firstOrNull()
                if (media != null) {
                    val nextEp = media.nextAiringEpisode?.episode
                    if (nextEp != null && nextEp > 1) {
                        return nextEp - 1
                    }
                    if (media.episodes != null && media.episodes > 0) {
                        return media.episodes
                    }
                }
            } catch (e: Exception) {
                // Ignore and fall back
            }
        }

        // 2. Try to fetch from AniList using search title
        try {
            val qString = """
                query (${'$'}search: String) {
                  Page (page: 1, perPage: 1) {
                    media (search: ${'$'}search, type: ANIME) {
                      status
                      episodes
                      nextAiringEpisode {
                        episode
                        airingAt
                      }
                    }
                  }
                }
            """.trimIndent()
            val response = aniListApi.searchAnimeGraphQL(
                com.example.data.remote.AniListGraphQLSearchRequest(
                    query = qString,
                    variables = buildJsonObject { put("search", title) }
                )
            )
            val media = response.data?.Page?.media?.firstOrNull()
            if (media != null) {
                val nextEp = media.nextAiringEpisode?.episode
                if (nextEp != null && nextEp > 1) {
                    return nextEp - 1
                }
                if (media.episodes != null && media.episodes > 0) {
                    return media.episodes
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return -1
    }

    suspend fun getAniListUpcomingSchedules(page: Int = 1, perPage: Int = 50, airingAtGreater: Long): List<com.example.data.remote.AniListAiringSchedule> {
        return try {
            val query = """
                query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}airingAtGreater: Int) {
                  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    airingSchedules(airingAt_greater: ${'$'}airingAtGreater, sort: TIME) {
                      id
                      airingAt
                      episode
                      media {
                        id
                        idMal
                        title {
                          romaji
                          english
                          native
                        }
                        coverImage {
                          large
                        }
                        genres
                        averageScore
                        isAdult
                        tags {
                          name
                        }
                        status
                        type
                      }
                    }
                  }
                }
            """.trimIndent()
            
            val request = com.example.data.remote.AniListGraphQLRequest(
                query = query,
                variables = com.example.data.remote.AniListVariables(
                    page = page,
                    perPage = perPage,
                    airingAtGreater = airingAtGreater.toInt()
                )
            )
            
            val response = aniListApi.getUpcomingSchedules(request)
            val schedules = response.data?.Page?.airingSchedules ?: emptyList()
            
            // Filter out adult (Hentai) content
            val filtered = schedules.filter { schedule ->
                val media = schedule.media
                if (media != null) {
                    val isAdult = media.isAdult == true
                    val hasHentaiGenre = media.genres.any { it.equals("Hentai", ignoreCase = true) }
                    !isAdult && !hasHentaiGenre
                } else {
                    true
                }
            }
            cacheSchedules(filtered)
            filtered
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error fetching AniList schedules", e)
            e.printStackTrace()
            emptyList()
        }
    }

    private fun cleanRomajiTitle(title: String): String {
        val lower = title.lowercase().trim()
        val mapping = mapOf(
            "masamune-kun no revenge" to "Masamune-kun's Revenge",
            "masamune-kun no revenge r" to "Masamune-kun's Revenge R",
            "shingeki no kyojin" to "Attack on Titan",
            "boku no hero academia" to "My Hero Academia",
            "kimetsu no yaiba" to "Demon Slayer: Kimetsu no Yaiba",
            "gokushufudou" to "The Way of the Househusband",
            "kiseijuu" to "Parasyte",
            "yakusoku no neverland" to "The Promised Neverland",
            "kaguya-sama wa kokurasetai: tensai-tachi no renai zunousen" to "Kaguya-sama: Love is War",
            "tensei shitara slime datta ken" to "That Time I Got Reincarnated as a Slime",
            "tate no yuusha no nariagari" to "The Rising of the Shield Hero",
            "kono subarashii sekai ni shukufuku wo!" to "Konosuba",
            "re:zero kara hajimeru isekai seikatsu" to "Re:Zero - Starting Life in Another World",
            "seishun buta yarou wa bunny girl senpai no yume wo minai" to "Rascal Does Not Dream of Bunny Girl Senpai",
            "haikyuu!!" to "Haikyu!!",
            "kuroko no basket" to "Kuroko's Basketball",
            "shokugeki no souma" to "Food Wars! Shokugeki no Soma",
            "ansatsu kyoushitsu" to "Assassination Classroom",
            "mirai nikki" to "The Future Diary",
            "kaijuu 8-gou" to "Kaiju No. 8",
            "sousou no frieren" to "Frieren: Beyond Journey's End",
            "kusuriya no hitorigoto" to "The Apothecary Diaries",
            "sono bisque doll wa koi wo suru" to "My Dress-Up Darling",
            "hige wo soru. soshite joshikousei wo hirou." to "Higehiro: After Being Rejected, I Shaved and Took in a High School Runaway",
            "horimiya" to "Horimiya",
            "tokyo revengers" to "Tokyo Revengers",
            "shigatsu wa kimi no uso" to "Your Lie in April",
            "nanatsu no taizai" to "The Seven Deadly Sins",
            "rurouni kenshin" to "Rurouni Kenshin",
            "ao no exorcist" to "Blue Exorcist",
            "kakegurui" to "Kakegurui",
            "durarara!!" to "Durarara!!",
            "sen to chihiro no kamikakushi" to "Spirited Away",
            "kimi no na wa." to "Your Name.",
            "tenki no ko" to "Weathering With You",
            "koe no katachi" to "A Silent Voice",
            "byousoku 5 centimeter" to "5 Centimeters per Second",
            "tonari no totoro" to "My Neighbor Totoro",
            "mononoke hime" to "Princess Mononoke",
            "kaze no tani no nausicaa" to "Nausicaä of the Valley of the Wind",
            "hotaru no haka" to "Grave of the Fireflies",
            "howl no ugoku shiro" to "Howl's Moving Castle",
            "gake no ue no ponyo" to "Ponyo"
        )
        
        for ((romaji, english) in mapping) {
            if (lower == romaji || lower.startsWith(romaji) || romaji.startsWith(lower)) {
                return english
            }
        }
        
        var cleaned = title
        cleaned = cleaned.replace(" no ", "'s ")
        cleaned = cleaned.replace(" wa ", " ")
        cleaned = cleaned.replace(" ni ", " ")
        cleaned = cleaned.replace(" to ", " & ")
        cleaned = cleaned.replace(" kara ", " ")
        return cleaned
    }

    suspend fun getRecommendations(malId: Int): List<Anime> {
        return try {
            val response = jikanApi.getRecommendations(malId)
            val jikanList = response.data ?: emptyList()
            if (jikanList.isEmpty()) return emptyList()

            // Extract the MAL IDs of the recommendations
            val malIds = jikanList.map { it.entry.mal_id }

            // Try to resolve English titles via AniList GraphQL query
            val titleMap = mutableMapOf<Int, String>()
            try {
                val qString = """
                    query (${'$'}idMalList: [Int]) {
                      Page (page: 1, perPage: 25) {
                        media (idMal_in: ${'$'}idMalList, type: ANIME) {
                          idMal
                          title {
                            english
                            romaji
                            native
                          }
                        }
                      }
                    }
                """.trimIndent()

                val variables = buildJsonObject {
                    putJsonArray("idMalList") {
                        malIds.forEach { add(it) }
                    }
                }

                val aniListResponse = aniListApi.searchAnimeGraphQL(
                    com.example.data.remote.AniListGraphQLSearchRequest(
                        query = qString,
                        variables = variables
                    )
                )

                aniListResponse.data?.Page?.media?.forEach { media ->
                    val malIdKey = media.idMal
                    if (malIdKey != null) {
                        val title = media.title?.english ?: media.title?.romaji ?: media.title?.native
                        if (!title.isNullOrBlank()) {
                            titleMap[malIdKey] = title
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and fall back to Jikan titles
            }

            jikanList.map { rec ->
                val resolvedTitle = titleMap[rec.entry.mal_id] ?: cleanRomajiTitle(rec.entry.title)
                Anime(
                    _id = "mal_${rec.entry.mal_id}",
                    title = resolvedTitle,
                    poster = rec.entry.images?.jpg?.large_image_url ?: rec.entry.images?.jpg?.image_url,
                    malId = rec.entry.mal_id.toString()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getNextSeasonsOrRelatedParts(malId: Int, title: String): List<Anime> {
        val coreQuery = cleanFranchiseTitle(title)
        return try {
            val searchResults = searchAnime(query = coreQuery, limit = 15).animes
            searchResults.filter { it.malId != malId.toString() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun cleanFranchiseTitle(title: String): String {
        var clean = title
        val patterns = listOf(
            "\\bSeason\\s+\\d+\\b",
            "\\bPart\\s+\\d+\\b",
            "\\bCour\\s+\\d+\\b",
            "\\b(the\\s+)?final\\s+season\\b",
            "\\b(the\\s+)?final\\s+chapters\\b",
            "\\b(the\\s+)?final\\s+part\\b",
            "\\bTV\\b",
            "\\bOVA\\b",
            "\\bONA\\b",
            "\\bMovie\\b",
            "S\\d+",
            "\\d+(st|nd|rd|th)\\s+Season",
            ":\\s+.*", // Strip everything after colon
            "-\\s+.*"  // Strip everything after dash
        )
        for (pattern in patterns) {
            clean = clean.replace(Regex(pattern, RegexOption.IGNORE_CASE), " ")
        }
        clean = clean.replace(Regex("\\s+"), " ").trim()
        
        if (clean.length < 3) {
            val words = title.split(" ")
            clean = if (words.size >= 2) {
                words.take(2).joinToString(" ")
            } else {
                title
            }
        }
        return clean
    }

    suspend fun getJikanEpisodes(malId: Int): List<Episode> {
        return try {
            val response = jikanApi.getAnimeEpisodes(malId)
            val jEpisodes = response.data ?: emptyList()
            jEpisodes.map { je ->
                Episode(
                    _id = "mal_${malId}_ep_${je.mal_id}",
                    anime = "mal_$malId",
                    season = "simulated_season",
                    number = je.mal_id,
                    title = je.title ?: je.title_romanji ?: "Episode ${je.mal_id}",
                    thumbnail = null,
                    duration = "24 min"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSchedule(day: String? = null): List<Anime> {
        return try {
            val response = jikanApi.getSchedule(filter = day?.lowercase())
            response.data?.mapNotNull { jAnime ->
                val genres = (jAnime.genres + jAnime.explicit_genres + jAnime.themes + jAnime.demographics).mapNotNull { it.name }.distinct()
                val hasAdultGenre = genres.any { it.equals("Hentai", ignoreCase = true) }
                if (hasAdultGenre) return@mapNotNull null

                val titleToUse = if (!jAnime.title_english.isNullOrBlank()) jAnime.title_english else jAnime.title
                Anime(
                    _id = "mal_${jAnime.mal_id}",
                    title = titleToUse,
                    poster = jAnime.images?.jpg?.large_image_url ?: jAnime.images?.jpg?.image_url,
                    malId = jAnime.mal_id.toString(),
                    rating = jAnime.score,
                    status = jAnime.status,
                    year = jAnime.year,
                    genres = genres
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRawSchedule(
        day: String? = null,
        page: Int? = null,
        limit: Int? = null
    ): List<com.example.data.remote.JikanAnime> {
        return try {
            val response = jikanApi.getSchedule(filter = day?.lowercase(), page = page, limit = limit ?: 30)
            response.data ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun syncContinueWatchingToLocalHistory() {
        try {
            val items = api.getContinueWatching().continueWatching
            for (item in items) {
                try {
                    val animeId = item.anime
                    val existing = watchDao.getState(animeId)
                    if (existing == null || existing.lastEpisode < item.episodeNumber || (existing.lastEpisode == item.episodeNumber && existing.progress < item.progress)) {
                        watchDao.saveState(
                            AnimeWatchState(
                                animeId = animeId,
                                animeTitle = item.animeTitle,
                                animePoster = item.animePoster,
                                seasonId = item.season,
                                lastEpisode = item.episodeNumber,
                                episodeTitle = item.episodeTitle,
                                progress = item.progress,
                                timestamp = try {
                                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                        .parse(item.watchedAt)?.time ?: System.currentTimeMillis()
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }
                            )
                        )
                    }
                } catch (e: Exception) {
                    // ignore individual item sync error
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error syncing continue watching to local watch history", e)
        }
    }

    suspend fun login(email: String, password: String): AuthResponse? {
        val response = api.login(mapOf("email" to email, "password" to password))
        if (response.isSuccessful) {
            val body = response.body()
            body?.token?.let { sessionManager.saveToken(it) }
            body?.user?.let { user ->
                sessionManager.saveUsername(user.username)
                sessionManager.saveEmail(user.email)
            } ?: run {
                sessionManager.saveEmail(email)
                val userPart = email.substringBefore("@")
                sessionManager.saveUsername(userPart)
            }
            syncContinueWatchingToLocalHistory()
            return body
        } else {
            val errorMsg = try {
                val errorBodyStr = response.errorBody()?.string()
                if (!errorBodyStr.isNullOrEmpty()) {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }
                    val parsed = json.decodeFromString<AuthResponse>(errorBodyStr)
                    parsed.error ?: "Login failed ($errorBodyStr)"
                } else {
                    "Login failed with code ${response.code()}"
                }
            } catch (e: Exception) {
                "Login failed (Code: ${response.code()})"
            }
            return AuthResponse(error = errorMsg)
        }
    }

    suspend fun register(username: String, email: String, password: String, confirmPassword: String): AuthResponse? {
        val response = api.register(mapOf(
            "username" to username,
            "email" to email,
            "password" to password,
            "confirmPassword" to confirmPassword,
            "confirm_password" to confirmPassword
        ))
        if (response.isSuccessful) {
            val body = response.body()
            body?.token?.let { sessionManager.saveToken(it) }
            body?.user?.let { user ->
                sessionManager.saveUsername(user.username)
                sessionManager.saveEmail(user.email)
            } ?: run {
                sessionManager.saveEmail(email)
                sessionManager.saveUsername(username)
            }
            syncContinueWatchingToLocalHistory()
            return body
        } else {
            val errorMsg = try {
                val errorBodyStr = response.errorBody()?.string()
                if (!errorBodyStr.isNullOrEmpty()) {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }
                    val parsed = json.decodeFromString<AuthResponse>(errorBodyStr)
                    parsed.error ?: "Registration failed ($errorBodyStr)"
                } else {
                    "Registration failed with code ${response.code()}"
                }
            } catch (e: Exception) {
                "Registration failed (Code: ${response.code()})"
            }
            return AuthResponse(error = errorMsg)
        }
    }

    suspend fun resendVerification(email: String): AuthResponse? {
        val response = api.resendVerification(mapOf("email" to email))
        if (response.isSuccessful) {
            return response.body()
        } else {
            val errorMsg = try {
                val errorBodyStr = response.errorBody()?.string()
                if (!errorBodyStr.isNullOrEmpty()) {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }
                    val parsed = json.decodeFromString<AuthResponse>(errorBodyStr)
                    parsed.error ?: "Failed to resend verification email"
                } else {
                    "Failed to resend verification email"
                }
            } catch (e: Exception) {
                "Failed to resend verification email"
            }
            return AuthResponse(error = errorMsg)
        }
    }

    suspend fun getTrending(genre: String? = null): List<Anime> {
        var trendingList: List<Anime> = emptyList()

        // 1. Fetch real trending anime from the auto endpoint
        try {
            trendingList = api.getTrendingAuto().animes
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error fetching trending/auto", e)
        }

        // 2. If trending endpoint fails or returns empty, fallback to sort=rating and limit=5
        if (trendingList.isEmpty()) {
            try {
                trendingList = api.getAnimes(sort = "rating", limit = 5, genre = genre).animes
            } catch (e: Exception) {
                android.util.Log.e("JumpAnimeRepository", "Error fetching fallback trending rating limit=5", e)
            }
        }

        // Show only the first 5 results
        trendingList = trendingList.take(5)

        // Offline Caching (Room Database):
        // Whenever network fetching succeeds, save the trending items locally with trending = true.
        if (trendingList.isNotEmpty()) {
            try {
                saveAnimesLocally(trendingList, isTrending = true)
            } catch (e: Exception) {
                // Ignore caching errors
            }
        } else {
            // If completely offline, query from the local Room database
            try {
                trendingList = animeDao.getTrending().filter {
                    genre == null || it.genres.contains(genre)
                }.take(5)
            } catch (e: Exception) {
                // Ignore
            }
        }

        return trendingList
    }

    suspend fun getTrendingAndFeatured(genre: String? = null, search: String? = null): Pair<List<Anime>, List<Anime>> {
        var trendingList: List<Anime> = emptyList()

        // 1. Fetch real trending anime from the auto endpoint
        try {
            trendingList = api.getTrendingAuto().animes
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error fetching trending/auto in getTrendingAndFeatured", e)
        }

        // 2. If trending endpoint fails or returns empty, fallback strictly to sort=rating and limit=5
        if (trendingList.isEmpty()) {
            try {
                trendingList = api.getAnimes(sort = "rating", limit = 5, genre = genre, search = search).animes
            } catch (e: Exception) {
                android.util.Log.e("JumpAnimeRepository", "Error fetching fallback trending rating limit=5 in getTrendingAndFeatured", e)
            }
        }

        // Limit to first 5 results
        trendingList = trendingList.take(5)

        var featuredList: List<Anime> = emptyList()

        // 2. Try Featured
        try {
            featuredList = api.getAnimes(featured = true, genre = genre, search = search, limit = 10).animes
            if (featuredList.isEmpty()) {
                featuredList = api.getAnimes(sort = "popular", genre = genre, search = search, limit = 10).animes
            }
        } catch (e: Exception) {
            // Fallback later
        }

        // 3. Robust Primary Backend Fallback
        if (trendingList.isEmpty() && featuredList.isEmpty()) {
            try {
                val anyAnime = api.getAnimes(limit = 20, genre = genre, sort = "popular").animes
                if (anyAnime.isNotEmpty()) {
                    featuredList = anyAnime.take(10)
                    trendingList = anyAnime.drop(10).take(5)
                    if (trendingList.isEmpty()) trendingList = featuredList.take(5)
                } else {
                    // Try without genre
                    val moreAnime = api.getAnimes(limit = 20, sort = "rating").animes
                    featuredList = moreAnime.take(10)
                    trendingList = moreAnime.drop(10).take(5)
                    if (trendingList.isEmpty()) trendingList = featuredList.take(5)
                }
            } catch (e: Exception) {
                // Handled below by public API fallbacks
            }
        }

        // 4. Public API Fallbacks (AniList & Jikan) if primary backend is completely down
        if (trendingList.isEmpty() && featuredList.isEmpty()) {
            try {
                android.util.Log.d("JumpAnimeRepository", "Primary backend is down. Triggering AniList public API fallbacks...")
                val anilistTrending = getAniListCustomCategory(sort = listOf("TRENDING_DESC"), perPage = 5)
                val anilistFeatured = getAniListCustomCategory(sort = listOf("POPULARITY_DESC"), perPage = 10)
                
                if (anilistTrending.isNotEmpty() || anilistFeatured.isNotEmpty()) {
                    trendingList = anilistTrending
                    featuredList = anilistFeatured
                } else {
                    android.util.Log.d("JumpAnimeRepository", "AniList returned empty results. Triggering Jikan public API fallbacks...")
                    // Jikan Search Popularity
                    val jikanTrendingResponse = jikanApi.searchAnime(query = null, page = 1, limit = 5, orderBy = "popularity")
                    val jTrending = jikanTrendingResponse.data?.mapNotNull { jAnime ->
                        val genres = (jAnime.genres + jAnime.explicit_genres + jAnime.themes + jAnime.demographics).mapNotNull { it.name }.distinct()
                        val hasHentai = genres.any { it.equals("Hentai", ignoreCase = true) }
                        if (hasHentai) return@mapNotNull null
                        Anime(
                            _id = "mal_${jAnime.mal_id}",
                            title = if (!jAnime.title_english.isNullOrBlank()) jAnime.title_english else jAnime.title,
                            poster = jAnime.images?.jpg?.large_image_url ?: jAnime.images?.jpg?.image_url,
                            malId = jAnime.mal_id.toString(),
                            rating = jAnime.score,
                            status = jAnime.status,
                            genres = genres
                        )
                    } ?: emptyList()

                    // Jikan Search Score (Highest Rated)
                    val jikanFeaturedResponse = jikanApi.searchAnime(query = null, page = 1, limit = 10, orderBy = "score")
                    val jFeatured = jikanFeaturedResponse.data?.mapNotNull { jAnime ->
                        val genres = (jAnime.genres + jAnime.explicit_genres + jAnime.themes + jAnime.demographics).mapNotNull { it.name }.distinct()
                        val hasHentai = genres.any { it.equals("Hentai", ignoreCase = true) }
                        if (hasHentai) return@mapNotNull null
                        Anime(
                            _id = "mal_${jAnime.mal_id}",
                            title = if (!jAnime.title_english.isNullOrBlank()) jAnime.title_english else jAnime.title,
                            poster = jAnime.images?.jpg?.large_image_url ?: jAnime.images?.jpg?.image_url,
                            malId = jAnime.mal_id.toString(),
                            rating = jAnime.score,
                            status = jAnime.status,
                            genres = genres
                        )
                    } ?: emptyList()

                    if (jTrending.isNotEmpty()) trendingList = jTrending
                    if (jFeatured.isNotEmpty()) featuredList = jFeatured
                }
            } catch (e: Exception) {
                android.util.Log.e("JumpAnimeRepository", "Failed to fetch from public APIs (AniList & Jikan) fallbacks", e)
            }
        }

        // Cache if we got something
        if (search == null && genre == null) {
            if (trendingList.isNotEmpty()) saveAnimesLocally(trendingList, isTrending = true)
            if (featuredList.isNotEmpty()) saveAnimesLocally(featuredList, isFeatured = true)
        }

        // Final Offline Fallback
        if (trendingList.isEmpty() && featuredList.isEmpty()) {
            val cachedTrending = animeDao.getTrending().filter { 
                (genre == null || it.genres.contains(genre)) &&
                (search == null || it.title.contains(search ?: "", ignoreCase = true))
            }.take(5)
            val cachedFeatured = animeDao.getFeatured().filter { 
                (genre == null || it.genres.contains(genre)) &&
                (search == null || it.title.contains(search ?: "", ignoreCase = true))
            }.take(10)
            return cachedTrending to cachedFeatured
        }

        return trendingList to featuredList
    }

    private suspend fun enrichAiringAnimeEpisodes(anime: Anime): Anime {
        val statusLower = (anime.status ?: "").lowercase()
        val isAiring = statusLower.contains("airing") || statusLower.contains("ongoing") || statusLower.contains("releasing")
        if (!isAiring) {
            return anime
        }
        
        var malIdInt = anime.malId?.toIntOrNull()
        
        // If malId is null, let's search AniList by title to find the media and its idMal
        if (malIdInt == null) {
            try {
                val searchResponse = aniListApi.searchAnimeGraphQL(
                    com.example.data.remote.AniListGraphQLSearchRequest(
                        query = """
                            query (${'$'}search: String) {
                              Page (page: 1, perPage: 1) {
                                media (search: ${'$'}search, type: ANIME) {
                                  id
                                  idMal
                                  status
                                  episodes
                                  nextAiringEpisode {
                                    episode
                                    airingAt
                                  }
                                }
                              }
                            }
                        """.trimIndent(),                        variables = buildJsonObject {
                            put("search", anime.title)
                        }
                    )
                )
                val media = searchResponse.data?.Page?.media?.firstOrNull()
                if (media != null) {
                    val nextEp = media.nextAiringEpisode?.episode
                    var count = if (nextEp != null && nextEp > 1) {
                        nextEp - 1
                    } else {
                        media.episodes ?: anime.episodesCount
                    }
                    val resolvedMalId = media.idMal?.toString() ?: anime.malId
                    if (resolvedMalId != null) {
                        try {
                            val scheduleInfo = getLatestAiredEpisodeFromSchedule(resolvedMalId)
                            if (scheduleInfo != null && scheduleInfo.first > count) {
                                count = scheduleInfo.first
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    return anime.copy(
                        malId = resolvedMalId,
                        episodesCount = if (count > 0) count else anime.episodesCount
                    )
                }
            } catch (e: Exception) {
                // Ignore and fall back
            }
        }
        
        if (malIdInt == null) return anime
 
        return try {
            val qString = """
                query (${'$'}idMal: Int) {
                  Page (page: 1, perPage: 1) {
                    media (idMal: ${'$'}idMal, type: ANIME) {
                      status
                      episodes
                      nextAiringEpisode {
                        episode
                        airingAt
                      }
                    }
                  }
                }
            """.trimIndent()
            val variables = buildJsonObject {
                put("idMal", malIdInt)
            }
            val aniListResponse = aniListApi.searchAnimeGraphQL(
                com.example.data.remote.AniListGraphQLSearchRequest(
                    query = qString,
                    variables = variables
                )
            )
            val media = aniListResponse.data?.Page?.media?.firstOrNull()
            if (media != null) {
                val nextEp = media.nextAiringEpisode?.episode
                var count = if (nextEp != null && nextEp > 1) {
                    nextEp - 1
                } else {
                    media.episodes ?: anime.episodesCount
                }
                try {
                    val scheduleInfo = getLatestAiredEpisodeFromSchedule(malIdInt.toString())
                    if (scheduleInfo != null && scheduleInfo.first > count) {
                        count = scheduleInfo.first
                    }
                } catch (e: Exception) {
                    // ignore
                }
                anime.copy(episodesCount = count)
            } else {
                anime
            }
        } catch (ex: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Failed to enrich episode count from AniList", ex)
            anime
        }
    }

    suspend fun getAnimeDetail(id: String): Anime = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val isMalId = id.startsWith("mal_") || (id.all { it.isDigit() } && id.length < 12)
        if (isMalId) {
            try {
                val cleanId = id.replace("mal_", "")
                val malId = cleanId.toIntOrNull() ?: throw IllegalArgumentException("Invalid MAL ID")
                val jResponse = jikanApi.getAnimeDetail(malId)
                val jAnime = jResponse.data
                
                // Try to find if this anime exists in our backend database by title search
                var finalAnime = try {
                    val searchResponse = api.getAnimes(search = jAnime.title, limit = 5)
                    val matchedAnime = searchResponse.animes.firstOrNull { native ->
                        native.malId == jAnime.mal_id.toString() ||
                        native.title.equals(jAnime.title, ignoreCase = true)
                    }
                    if (matchedAnime != null) {
                        matchedAnime.copy(malId = matchedAnime.malId ?: jAnime.mal_id.toString())
                    } else {
                        val titleToUse = if (!jAnime.title_english.isNullOrBlank()) jAnime.title_english else jAnime.title
                        val genres = (jAnime.genres + jAnime.explicit_genres + jAnime.themes + jAnime.demographics).mapNotNull { it.name }.distinct()
                        Anime(
                            _id = "mal_${jAnime.mal_id}",
                            title = titleToUse,
                            poster = jAnime.images?.jpg?.large_image_url ?: jAnime.images?.jpg?.image_url,
                            description = jAnime.synopsis,
                            rating = jAnime.score,
                            totalSeasons = 1,
                            type = jAnime.type,
                            status = jAnime.status,
                            year = jAnime.year,
                            genres = genres,
                            episodesCount = jAnime.episodes ?: 0,
                            malId = jAnime.mal_id.toString()
                        )
                    }
                } catch (e: Exception) {
                    val titleToUse = if (!jAnime.title_english.isNullOrBlank()) jAnime.title_english else jAnime.title
                    val genres = (jAnime.genres + jAnime.explicit_genres + jAnime.themes + jAnime.demographics).mapNotNull { it.name }.distinct()
                    Anime(
                        _id = "mal_${jAnime.mal_id}",
                        title = titleToUse,
                        poster = jAnime.images?.jpg?.large_image_url ?: jAnime.images?.jpg?.image_url,
                        description = jAnime.synopsis,
                        rating = jAnime.score,
                        totalSeasons = 1,
                        type = jAnime.type,
                        status = jAnime.status,
                        year = jAnime.year,
                        genres = genres,
                        episodesCount = jAnime.episodes ?: 0,
                        malId = jAnime.mal_id.toString()
                    )
                }

                // Enrich with real-time AniList schedule if airing
                finalAnime = enrichAiringAnimeEpisodes(finalAnime)

                val existing = animeDao.getById(finalAnime._id)
                val toSave = finalAnime.copy(inWatchlist = existing?.inWatchlist ?: false)
                animeDao.insertAll(listOf(toSave))
                toSave
            } catch (e: Exception) {
                val existing = animeDao.getById(id)
                existing ?: throw e
            }
        } else {
            try {
                var anime = api.getAnimeDetail(id)
                // Enrich with real-time AniList schedule if airing
                anime = enrichAiringAnimeEpisodes(anime)
                // Check if we already have it in watchlist to preserve that flag
                val existing = animeDao.getById(id)
                val toSave = anime.copy(inWatchlist = existing?.inWatchlist ?: false)
                animeDao.insertAll(listOf(toSave))
                toSave
            } catch (e: Exception) {
                val existing = animeDao.getById(id)
                existing ?: throw e
            }
        }
    }

    suspend fun getPopular(genre: String? = null): List<Anime> {
        return try {
            api.getAnimes(sort = "popular", genre = genre, limit = 15).animes
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error fetching popular from backend. Using AniList fallback...", e)
            try {
                val list = getAniListCustomCategory(sort = listOf("POPULARITY_DESC"), perPage = 15)
                if (list.isNotEmpty() && genre == null) {
                    saveAnimesLocally(list, isMostPopular = true)
                }
                list
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getRecentlyAdded(genre: String? = null): List<Anime> {
        return try {
            val list = api.getAnimes(sort = "latest", genre = genre, limit = 15).animes
            if (list.isNotEmpty() && genre == null) {
                saveAnimesLocally(list, isRecentlyAdded = true)
            }
            list
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error fetching recently added from backend. Using AniList fallback...", e)
            try {
                val list = getAniListCustomCategory(sort = listOf("START_DATE_DESC"), perPage = 15)
                if (list.isNotEmpty()) {
                    if (genre == null) {
                        saveAnimesLocally(list, isRecentlyAdded = true)
                    }
                    list
                } else {
                    animeDao.getRecentlyAdded().filter {
                        genre == null || it.genres.contains(genre)
                    }
                }
            } catch (ex: Exception) {
                try {
                    animeDao.getRecentlyAdded().filter {
                        genre == null || it.genres.contains(genre)
                    }
                } catch (exc: Exception) {
                    emptyList()
                }
            }
        }
    }

    suspend fun getTopRated(genre: String? = null): List<Anime> {
        return try {
            api.getAnimes(sort = "rating", genre = genre, limit = 15).animes
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error fetching top rated from backend. Using AniList fallback...", e)
            try {
                getAniListCustomCategory(sort = listOf("SCORE_DESC"), perPage = 15)
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getEpisodes(seasonId: String): List<Episode> {
        return try {
            api.getEpisodes(seasonId).episodes
        } catch (e: Exception) {
            val cachedAnime = animeDao.getAll().firstOrNull { anime ->
                val hasSeason = anime.seasons.any { it._id == seasonId }
                hasSeason
            }
            if (cachedAnime != null) {
                val totalEp = if (cachedAnime.episodesCount > 0) cachedAnime.episodesCount else 12
                (1..totalEp).map { num ->
                    Episode(
                        _id = "${cachedAnime._id}_s_${seasonId}_ep_$num",
                        anime = cachedAnime._id,
                        season = seasonId,
                        number = num,
                        title = "Episode $num",
                        thumbnail = cachedAnime.poster,
                        duration = "24 min"
                    )
                }
            } else {
                throw e
            }
        }
    }

    suspend fun getEpisode(seasonId: String, epNumber: Int): Episode {
        return api.getEpisode(seasonId, epNumber).episode
    }

    suspend fun savePlaybackHistory(request: PlaybackHistoryRequest) {
        try {
            api.saveHistory(request)
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error saving playback history to API", e)
        }
        // Always save locally for guest mode and offline support
        watchDao.saveState(
            AnimeWatchState(
                animeId = request.animeId,
                animeTitle = request.animeTitle,
                animePoster = request.animePoster,
                seasonId = request.seasonId,
                lastEpisode = request.episodeNumber,
                episodeTitle = request.episodeTitle,
                progress = request.progress,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordWatchCount(animeId: String) {
        try {
            api.recordWatchCount(animeId)
        } catch (e: Exception) {
            // Ignore any errors silently
        }
    }

    fun getWatchlistFlow(): Flow<List<Anime>> = animeDao.getWatchlistFlow()

    suspend fun isInWatchlist(animeId: String): Boolean {
        return animeDao.getById(animeId)?.inWatchlist == true
    }

    suspend fun getWatchlist(): List<Anime> {
        if (sessionManager.isGuestMode() || !sessionManager.isLoggedIn()) {
            return animeDao.getWatchlist().map { it.copy(inWatchlist = true) }
        }
        return try {
            val remote = api.getWatchlist().list.map { it.anime }
            animeDao.clearWatchlistFlag()
            if (remote.isNotEmpty()) {
                remote.forEach { anime ->
                    val existing = animeDao.getById(anime._id)
                    if (existing == null) {
                        saveAnimesLocally(listOf(anime), inWatchlist = true)
                    } else {
                        // Just update the watchlist flag so we don't overwrite full details with summary
                        animeDao.updateInWatchlist(anime._id, true)
                    }
                }
            }
            val localMalList = animeDao.getWatchlist().filter { it._id.startsWith("mal_") }
            remote + localMalList
        } catch (e: Exception) {
            animeDao.getWatchlist()
        }
    }

    suspend fun getContinueWatching(): List<ContinueWatchingItem> {
        val guestOrOffline = sessionManager.isGuestMode() || !sessionManager.isLoggedIn()
        val localWatchStates = watchDao.getAllStatesList().associateBy { it.animeId }
        
        if (!guestOrOffline) {
            try {
                val items = api.getContinueWatching().continueWatching
                if (items.isNotEmpty()) {
                    // Sync each item to local database watch history if not already present or is older
                    for (item in items) {
                        try {
                            val animeId = item.anime
                            val existing = watchDao.getState(animeId)
                            if (existing == null || existing.lastEpisode < item.episodeNumber || (existing.lastEpisode == item.episodeNumber && existing.progress < item.progress)) {
                                watchDao.saveState(
                                    AnimeWatchState(
                                        animeId = animeId,
                                        animeTitle = item.animeTitle,
                                        animePoster = item.animePoster,
                                        seasonId = item.season,
                                        lastEpisode = item.episodeNumber,
                                        episodeTitle = item.episodeTitle,
                                        progress = item.progress,
                                        timestamp = try {
                                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                                .parse(item.watchedAt)?.time ?: System.currentTimeMillis()
                                        } catch (e: Exception) {
                                            System.currentTimeMillis()
                                        }
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // ignore individual item error
                        }
                    }
                    
                    // Re-fetch localWatchStates to use fresh synced data
                    val updatedLocalWatchStates = watchDao.getAllStatesList().associateBy { it.animeId }
                    return items.map { item ->
                        val localState = updatedLocalWatchStates[item.anime]
                        val progressToUse = if (localState != null && localState.lastEpisode == item.episodeNumber) {
                            localState.progress
                        } else if (item.progress > 0f) {
                            item.progress
                        } else {
                            // Simulate progress based on title/id hashCode for consistency
                            ((item.anime.hashCode().let { if (it < 0) -it else it } % 70) + 10) / 100f
                        }
                        item.copy(progress = progressToUse)
                    }
                }
            } catch (e: Exception) {
                // Fallback to local
            }
        }
        
        // Fetch from local for guest mode or as fallback
        return watchDao.getAllStatesList().map { state ->
            ContinueWatchingItem(
                anime = state.animeId,
                animeTitle = state.animeTitle ?: "Anime",
                animePoster = state.animePoster ?: "",
                season = state.seasonId ?: "",
                episodeNumber = state.lastEpisode,
                episodeTitle = state.episodeTitle ?: "Episode ${state.lastEpisode}",
                progress = state.progress,
                watchedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .format(java.util.Date(state.timestamp))
            )
        }
    }

    private fun getJikanGenreId(genreName: String): Int? {
        return when (genreName.lowercase().trim()) {
            "action" -> 1
            "adventure" -> 2
            "comedy" -> 4
            "drama" -> 8
            "fantasy" -> 10
            "horror" -> 14
            "mystery" -> 7
            "romance" -> 22
            "sci-fi" -> 24
            "slice of life" -> 36
            "sports" -> 30
            "supernatural" -> 37
            "thriller" -> 41
            "psychological" -> 40
            "mecha" -> 18
            "music" -> 19
            "school" -> 23
            "shounen" -> 27
            "shoujo" -> 25
            "seinen" -> 42
            "josei" -> 43
            "isekai" -> 62
            "ecchi" -> 9
            "historical" -> 13
            "military" -> 38
            "martial arts" -> 17
            "magic" -> 16
            "demons" -> 55
            "vampire" -> 32
            "harem" -> 35
            "cars" -> 3
            "game" -> 11
            "kids" -> 15
            "parody" -> 20
            "samurai" -> 21
            "shoujo ai" -> 26
            "shounen ai" -> 28
            "space" -> 29
            "super power" -> 31
            "yaoi" -> 33
            "yuri" -> 34
            "police" -> 39
            "dementia" -> 5
            else -> null
        }
    }

    suspend fun searchAnime(
        query: String? = null, 
        genre: String? = null,
        genres: List<String>? = null,
        type: String? = null,
        status: String? = null,
        sort: String? = null,
        limit: Int = 20,
        page: Int = 1
    ): AnimeListResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (sort?.lowercase() == "recently_added" || sort?.lowercase() == "recently added") {
            try {
                val thirtyDaysAgoUnix = (System.currentTimeMillis() - 30L * 24 * 3600 * 1000) / 1000
                val s1 = try { getAniListUpcomingSchedules(page = 1, perPage = 50, airingAtGreater = thirtyDaysAgoUnix) } catch (e: Exception) { emptyList() }
                val s2 = try { getAniListUpcomingSchedules(page = 2, perPage = 50, airingAtGreater = thirtyDaysAgoUnix) } catch (e: Exception) { emptyList() }
                val s3 = try { getAniListUpcomingSchedules(page = 3, perPage = 50, airingAtGreater = thirtyDaysAgoUnix) } catch (e: Exception) { emptyList() }
                
                val allSchedules = (s1 + s2 + s3).distinctBy { it.id }
                val nowUnix = System.currentTimeMillis() / 1000
                val sortedSchedules = allSchedules
                    .filter { it.airingAt <= nowUnix }
                    .sortedByDescending { it.airingAt }
                
                val mappedAnimeList = sortedSchedules.mapNotNull { schedule ->
                    val media = schedule.media ?: return@mapNotNull null
                    val titleToUse = if (!media.title?.english.isNullOrBlank()) media.title.english else media.title?.romaji ?: media.title?.native ?: ""
                    val mStatus = when (media.status) {
                        "RELEASING" -> "Currently Airing"
                        "FINISHED" -> "Finished Airing"
                        "NOT_YET_RELEASED" -> "Not yet aired"
                        else -> media.status ?: "Currently Airing"
                    }
                    val mappedRating = media.averageScore?.let { it / 10.0 } ?: 0.0
                    val genresAndTags = media.genres + (media.tags?.mapNotNull { it.name } ?: emptyList())
                    
                    Anime(
                        _id = "mal_${media.idMal ?: ("anilist_" + media.id)}",
                        title = titleToUse,
                        poster = media.coverImage?.large,
                        malId = media.idMal?.toString(),
                        rating = mappedRating,
                        status = mStatus,
                        year = null,
                        genres = genresAndTags,
                        type = media.type
                    )
                }.distinctBy { it.malId ?: it._id }
                
                val activeGenre = genres?.firstOrNull() ?: genre
                val selectedGenre = if (activeGenre.isNullOrBlank() || activeGenre == "All Genres" || activeGenre == "All") null else activeGenre
                
                val filteredList = mappedAnimeList.filter { item ->
                    val matchesQuery = query.isNullOrBlank() || item.title.contains(query, ignoreCase = true)
                    val matchesGenre = selectedGenre == null || item.genres.any { it.equals(selectedGenre, ignoreCase = true) }
                    val matchesType = type == null || type == "All" || item.type?.equals(type, ignoreCase = true) == true
                    val matchesStatus = status == null || status == "All" || item.status?.equals(status, ignoreCase = true) == true
                    matchesQuery && matchesGenre && matchesType && matchesStatus
                }
                
                val startIndex = (page - 1) * limit
                val paginatedList = if (startIndex < filteredList.size) {
                    filteredList.drop(startIndex).take(limit)
                } else {
                    emptyList()
                }
                
                return@withContext AnimeListResponse(
                    animes = paginatedList,
                    total = filteredList.size,
                    page = page
                )
            } catch (e: Exception) {
                android.util.Log.e("JumpAnimeRepository", "Failed recently_added schedule-based search, falling back", e)
            }
        }

        val backendList = try {
            val firstGenre = genres?.firstOrNull() ?: genre
            val backendSort = when (sort?.lowercase()) {
                "recently_added", "recently added" -> "latest"
                else -> sort
            }
            val list = api.getAnimes(search = query, genre = firstGenre, type = type, status = status, sort = backendSort, limit = limit, page = page).animes
            val selectedGenre = if (firstGenre.isNullOrBlank() || firstGenre == "All Genres") null else firstGenre
            if (selectedGenre != null) {
                list.filter { item ->
                    item.genres.any { it.equals(selectedGenre, ignoreCase = true) }
                }
            } else {
                list
            }
        } catch (e: Exception) {
            animeDao.getAll().filter { 
                (query == null || it.title.contains(query, ignoreCase = true)) &&
                (genres == null || genres.isEmpty() || genres.any { g -> it.genres.any { igName -> igName.equals(g, ignoreCase = true) } }) &&
                (genre == null || it.genres.contains(genre)) &&
                (type == null || it.type == type) &&
                (status == null || it.status == status)
            }
        }

        val jikanType = type?.lowercase()
        val jikanStatus = when (status?.lowercase()) {
            "ongoing" -> "airing"
            "completed" -> "complete"
            "upcoming" -> "upcoming"
            else -> null
        }
        
        val activeGenres = genres ?: genre?.let { listOf(it) } ?: emptyList()
        val jikanGenreIds = activeGenres.mapNotNull { getJikanGenreId(it) }
        val genresParam = if (jikanGenreIds.isNotEmpty()) jikanGenreIds.joinToString(",") else null

        val (jikanOrderBy, jikanSortOrder) = when (sort?.lowercase()) {
            "popularity", "popular" -> Pair("popularity", "asc")
            "title" -> Pair("title", "asc")
            "recently_added", "recently added", "recently_updated", "recently updated" -> Pair("start_date", "desc")
            "recently_premiered", "recently premiered" -> Pair("start_date", "desc")
            else -> Pair("popularity", "asc")
        }

        var jikanPageCount: Int? = null
        val jikanList = try {
            val jResponse = jikanApi.searchAnime(
                query = if (query.isNullOrBlank()) null else query,
                page = page,
                limit = limit,
                type = jikanType,
                status = jikanStatus,
                genres = genresParam,
                orderBy = jikanOrderBy,
                sort = jikanSortOrder
            )
            jikanPageCount = jResponse.pagination?.last_visible_page
            jResponse.data?.mapNotNull { jAnime ->
                val genres = (jAnime.genres + jAnime.explicit_genres + jAnime.themes + jAnime.demographics).mapNotNull { it.name }.distinct()
                val hasHentai = genres.any { it.equals("Hentai", ignoreCase = true) }
                if (hasHentai) return@mapNotNull null

                val titleToUse = if (!jAnime.title_english.isNullOrBlank()) jAnime.title_english else jAnime.title
                Anime(
                    _id = "mal_${jAnime.mal_id}",
                    title = titleToUse,
                    poster = jAnime.images?.jpg?.large_image_url ?: jAnime.images?.jpg?.image_url,
                    malId = jAnime.mal_id.toString(),
                    rating = jAnime.score,
                    status = jAnime.status,
                    year = jAnime.year,
                    genres = genres
                )
            } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Jikan search failed, falling back to AniList if needed", e)
            emptyList()
        }
        // Query AniList directly to merge with Jikan results
        val aniListList = try {
            val aniListApi = apiProvider.getAniListApi()
            
            val aType = when (type?.lowercase()) {
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

            // Map sorting fields to AniList GraphQLSort enum
            val aSort = when (sort?.lowercase()) {
                "popular", "popularity" -> listOf("POPULARITY_DESC")
                "title" -> listOf("TITLE_ROMAJI")
                "recently_added", "recently added", "recently updated", "recently_updated" -> listOf("START_DATE_DESC")
                "recently premiered", "recently premiered" -> listOf("START_DATE_DESC")
                else -> listOf("POPULARITY_DESC")
            }

            val queryParams = mutableListOf<String>()
            val mediaArgs = mutableListOf<String>()
            
            queryParams.add("${'$'}page: Int")
            queryParams.add("${'$'}perPage: Int")
            
            mediaArgs.add("isAdult: false")
            
            if (!query.isNullOrBlank()) {
                queryParams.add("${'$'}search: String")
                mediaArgs.add("search: ${'$'}search")
            }
            
            val firstGenre = genres?.firstOrNull() ?: genre
            val selectedGenre = if (firstGenre.isNullOrBlank() || firstGenre == "All Genres" || firstGenre == "All") null else firstGenre
            
            var isOfficialGenre = false
            if (selectedGenre != null) {
                val officialAniListGenres = setOf(
                    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", 
                    "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", 
                    "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", 
                    "Supernatural", "Thriller"
                )
                if (officialAniListGenres.any { it.equals(selectedGenre, ignoreCase = true) }) {
                    isOfficialGenre = true
                    queryParams.add("${'$'}genre: [String]")
                    mediaArgs.add("genre_in: ${'$'}genre")
                } else {
                    queryParams.add("${'$'}tag: [String]")
                    mediaArgs.add("tag_in: ${'$'}tag")
                }
            }
            
            if (aType != null) {
                queryParams.add("${'$'}type: MediaFormat")
                mediaArgs.add("format: ${'$'}type")
            }
            
            if (aStatus != null) {
                queryParams.add("${'$'}status: MediaStatus")
                mediaArgs.add("status: ${'$'}status")
            }
            
            if (aSort != null) {
                queryParams.add("${'$'}sort: [MediaSort]")
                mediaArgs.add("sort: ${'$'}sort")
            }
            
            val queryParamsString = queryParams.joinToString(", ")
            val mediaArgsString = mediaArgs.joinToString(", ")

            val qString = """
                query ($queryParamsString) {
                  Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                    media ($mediaArgsString) {
                      id
                      idMal
                      title {
                        english
                        romaji
                        native
                      }
                      coverImage {
                        large
                      }
                      genres
                      tags {
                        name
                      }
                      averageScore
                      isAdult
                      status
                      type
                      episodes
                      nextAiringEpisode {
                        episode
                        airingAt
                      }
                    }
                  }
                }
            """.trimIndent()

            val variables = buildJsonObject {
                put("page", page)
                put("perPage", limit)
                if (!query.isNullOrBlank()) {
                    put("search", query)
                }
                if (selectedGenre != null) {
                    if (isOfficialGenre) {
                        putJsonArray("genre") {
                            add(selectedGenre)
                        }
                    } else {
                        putJsonArray("tag") {
                            add(selectedGenre)
                        }
                    }
                }
                if (aType != null) {
                    put("type", aType)
                }
                if (aStatus != null) {
                    put("status", aStatus)
                }
                if (aSort != null) {
                    putJsonArray("sort") {
                        aSort.forEach { add(it) }
                    }
                }
            }

            val response = aniListApi.searchAnimeGraphQL(
                com.example.data.remote.AniListGraphQLSearchRequest(
                    query = qString,
                    variables = variables
                )
            )

            response.data?.Page?.media?.mapNotNull { media ->
                val hasHentai = media.genres.any { it.equals("Hentai", ignoreCase = true) }
                if (hasHentai || media.isAdult == true) return@mapNotNull null

                val titleToUse = if (!media.title?.english.isNullOrBlank()) media.title.english else media.title?.romaji ?: media.title?.native ?: ""
                val mStatus = when (media.status) {
                    "RELEASING" -> "Currently Airing"
                    "FINISHED" -> "Finished Airing"
                    "NOT_YET_RELEASED" -> "Not yet aired"
                    else -> media.status
                }
                val mappedRating = media.averageScore?.let { it / 10.0 } ?: 0.0
                val genresAndTags = media.genres + (media.tags?.mapNotNull { it.name } ?: emptyList())

                val isAiring = media.status == "RELEASING"
                val resolvedEpisodesCount = if (isAiring) {
                    val nextEp = media.nextAiringEpisode?.episode
                    if (nextEp != null && nextEp > 1) {
                        nextEp - 1
                    } else {
                        media.episodes ?: 0
                    }
                } else {
                    media.episodes ?: 0
                }

                Anime(
                    _id = "mal_${media.idMal ?: ("anilist_" + media.id)}",
                    title = titleToUse,
                    poster = media.coverImage?.large,
                    malId = media.idMal?.toString(),
                    rating = mappedRating,
                    status = mStatus,
                    year = null,
                    genres = genresAndTags,
                    episodesCount = resolvedEpisodesCount
                )
            } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "AniList search failed", e)
            emptyList()
        }

        // Combine Jikan and AniList results, distinct by MAL ID or unique ID
        val finalExternalList = (jikanList + aniListList).distinctBy { it.malId ?: it._id }

        val mergedList = if (query.isNullOrBlank() && (sort?.lowercase() == "popular" || sort?.lowercase() == "popularity")) {
            (finalExternalList + backendList).distinctBy { it.malId ?: it._id }
        } else {
            (backendList + finalExternalList).distinctBy { it.malId ?: it._id }
        }

        val activeGenre = genres?.firstOrNull() ?: genre
        val filterGenre = if (activeGenre.isNullOrBlank() || activeGenre == "All Genres") null else activeGenre

        val filteredMergedList = if (filterGenre != null) {
            mergedList.filter { item ->
                item.genres.any { it.equals(filterGenre, ignoreCase = true) }
            }
        } else {
            mergedList
        }

        // Determine total for pagination. Ensure we estimate enough pages so user isn't prematurely cut off
        val estimatedTotal = when {
            jikanPageCount != null -> maxOf(jikanPageCount * limit, if (finalExternalList.isNotEmpty()) (page + 1) * limit else 0)
            finalExternalList.isNotEmpty() -> (page + 1) * limit
            else -> filteredMergedList.size
        }

        return@withContext AnimeListResponse(
            animes = filteredMergedList,
            total = estimatedTotal,
            page = page
        )
    }

    suspend fun addToWatchlist(anime: Anime) {
        if (!sessionManager.isGuestMode() && sessionManager.isLoggedIn()) {
            try {
                api.addToWatchlist(anime._id)
            } catch (e: Exception) { /* ignore network error */ }
        }
        saveAnimesLocally(listOf(anime), inWatchlist = true)
    }

    suspend fun removeFromWatchlist(animeId: String) {
        if (!sessionManager.isGuestMode() && sessionManager.isLoggedIn()) {
            try {
                api.removeFromWatchlist(animeId)
            } catch (e: Exception) { /* ignore network error */ }
        }
        animeDao.removeFromWatchlist(animeId)
    }

    suspend fun syncWatchlistMetadata() {
        if (sessionManager.isGuestMode() || !sessionManager.isLoggedIn()) return
        try {
            val watchlist = getWatchlist()
            watchlist.forEach { savedAnime ->
                try {
                    val fullDetail = api.getAnimeDetail(savedAnime._id)
                    val updatedAnime = fullDetail.copy(inWatchlist = true)
                    saveAnimesLocally(listOf(updatedAnime), inWatchlist = true)
                    android.util.Log.d("MetadataSync", "Successfully synced metadata for ${savedAnime.title}, Episode count: ${updatedAnime.episodesCount}")
                } catch (e: Exception) {
                    android.util.Log.e("MetadataSync", "Failed to sync metadata for ${savedAnime.title}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MetadataSync", "Failed to sync watchlist metadata", e)
        }
    }

    suspend fun clearLocalCache() {
        animeDao.deleteAll()
        watchDao.getAllStates().first().forEach {
            // Effectively clearing states if needed, but deleteAll takes care of animes
        }
    }

    // Local Watch State
    suspend fun getAllCachedAnime(): List<Anime> = animeDao.getAll()
    suspend fun getLocalWatchState(animeId: String) = watchDao.getState(animeId)
    suspend fun saveLocalWatchState(animeId: String, episode: Int, progress: Float = 0f) {
        val existing = watchDao.getState(animeId)
        val anime = animeDao.getById(animeId)
        watchDao.saveState(
            existing?.copy(lastEpisode = episode, progress = progress, timestamp = System.currentTimeMillis())
                ?: AnimeWatchState(
                    animeId = animeId,
                    animeTitle = anime?.title ?: "Anime",
                    animePoster = anime?.poster ?: "",
                    lastEpisode = episode,
                    progress = progress,
                    timestamp = System.currentTimeMillis()
                )
        )
    }
    
    suspend fun deleteLocalWatchState(animeId: String) {
        watchDao.deleteState(animeId)
    }
    
    fun getAllLocalStates() = watchDao.getAllStates()

    // Search History
    fun getRecentSearches() = historyDao.getRecentSearches()
    suspend fun saveSearch(query: String) {
        if (query.isNotBlank()) {
            historyDao.insertSearch(SearchHistory(query.trim()))
        }
    }
    suspend fun deleteSearch(query: String) = historyDao.deleteSearch(query)

    // Custom Titles
    fun getAllCustomTitles() = customTitleDao.getAllCustomTitles()
    suspend fun addCustomTitle(title: String) = customTitleDao.insertCustomTitle(CustomTitle(title))
    suspend fun deleteCustomTitle(title: String) = customTitleDao.deleteCustomTitle(title)

    private suspend fun saveAnimesLocally(
        animes: List<Anime>,
        isTrending: Boolean? = null,
        isFeatured: Boolean? = null,
        isRecentlyAdded: Boolean? = null,
        isMostPopular: Boolean? = null,
        isPopularThisMonth: Boolean? = null,
        isPopularThisWeek: Boolean? = null,
        isAnilab2Data: Boolean? = null,
        inWatchlist: Boolean? = null
    ) {
        val toSave = animes.mapIndexed { index, anime ->
            val existing = animeDao.getById(anime._id)
            val calculatedPopularity = if (isTrending == true) {
                1000 - index
            } else {
                existing?.popularity ?: anime.popularity
            }
            anime.copy(
                inWatchlist = inWatchlist ?: existing?.inWatchlist ?: anime.inWatchlist,
                trending = isTrending ?: existing?.trending ?: anime.trending,
                featured = isFeatured ?: existing?.featured ?: anime.featured,
                recentlyAdded = isRecentlyAdded ?: existing?.recentlyAdded ?: anime.recentlyAdded,
                mostPopular = isMostPopular ?: existing?.mostPopular ?: anime.mostPopular,
                popularThisMonth = isPopularThisMonth ?: existing?.popularThisMonth ?: anime.popularThisMonth,
                popularThisWeek = isPopularThisWeek ?: existing?.popularThisWeek ?: anime.popularThisWeek,
                anilab2Data = isAnilab2Data ?: existing?.anilab2Data ?: anime.anilab2Data,
                popularity = calculatedPopularity
            )
        }
        animeDao.insertAll(toSave)
    }

    suspend fun getAniListCustomCategory(
        sort: List<String>,
        status: String? = null,
        season: String? = null,
        seasonYear: Int? = null,
        page: Int = 1,
        perPage: Int = 15
    ): List<Anime> {
        return try {
            val queryParams = mutableListOf<String>()
            val mediaArgs = mutableListOf<String>()

            queryParams.add("${'$'}page: Int")
            queryParams.add("${'$'}perPage: Int")
            
            mediaArgs.add("isAdult: false")
            mediaArgs.add("type: ANIME")

            if (status != null) {
                queryParams.add("${'$'}status: MediaStatus")
                mediaArgs.add("status: ${'$'}status")
            }
            if (season != null) {
                queryParams.add("${'$'}season: MediaSeason")
                mediaArgs.add("season: ${'$'}season")
            }
            if (seasonYear != null) {
                queryParams.add("${'$'}seasonYear: Int")
                mediaArgs.add("seasonYear: ${'$'}seasonYear")
            }
            queryParams.add("${'$'}sort: [MediaSort]")
            mediaArgs.add("sort: ${'$'}sort")

            val queryParamsString = queryParams.joinToString(", ")
            val mediaArgsString = mediaArgs.joinToString(", ")

            val qString = """
                query ($queryParamsString) {
                  Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                    media ($mediaArgsString) {
                      id
                      idMal
                      title {
                        english
                        romaji
                        native
                      }
                      coverImage {
                        large
                      }
                      genres
                      tags {
                        name
                      }
                      averageScore
                      isAdult
                      status
                      type
                      episodes
                      nextAiringEpisode {
                        episode
                        airingAt
                      }
                    }
                  }
                }
            """.trimIndent()

            val variables = buildJsonObject {
                put("page", page)
                put("perPage", perPage)
                if (status != null) {
                    put("status", status)
                }
                if (season != null) {
                    put("season", season)
                }
                if (seasonYear != null) {
                    put("seasonYear", seasonYear)
                }
                putJsonArray("sort") {
                    sort.forEach { add(it) }
                }
            }

            val response = aniListApi.searchAnimeGraphQL(
                com.example.data.remote.AniListGraphQLSearchRequest(
                    query = qString,
                    variables = variables
                )
            )

            response.data?.Page?.media?.mapNotNull { media ->
                val hasHentai = media.genres.any { it.equals("Hentai", ignoreCase = true) }
                if (hasHentai || media.isAdult == true) return@mapNotNull null

                val titleToUse = if (!media.title?.english.isNullOrBlank()) media.title.english else media.title?.romaji ?: media.title?.native ?: ""
                val mStatus = when (media.status) {
                    "RELEASING" -> "Currently Airing"
                    "FINISHED" -> "Finished Airing"
                    "NOT_YET_RELEASED" -> "Not yet aired"
                    else -> media.status
                }
                val mappedRating = media.averageScore?.let { it / 10.0 } ?: 0.0
                val genresAndTags = media.genres + (media.tags?.mapNotNull { it.name } ?: emptyList())

                val isAiring = media.status == "RELEASING"
                val resolvedEpisodesCount = if (isAiring) {
                    val nextEp = media.nextAiringEpisode?.episode
                    if (nextEp != null && nextEp > 1) {
                        nextEp - 1
                    } else {
                        media.episodes ?: 0
                    }
                } else {
                    media.episodes ?: 0
                }

                Anime(
                    _id = "mal_${media.idMal ?: ("anilist_" + media.id)}",
                    title = titleToUse,
                    poster = media.coverImage?.large,
                    malId = media.idMal?.toString(),
                    rating = mappedRating,
                    status = mStatus,
                    year = seasonYear,
                    genres = genresAndTags,
                    episodesCount = resolvedEpisodesCount
                )
            } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("JumpAnimeRepository", "Error fetching AniList custom category", e)
            emptyList()
        }
    }

    suspend fun getMostPopularCached(): List<Anime> = animeDao.getMostPopular()
    suspend fun getPopularThisMonthCached(): List<Anime> = animeDao.getPopularThisMonth()
    suspend fun getPopularThisWeekCached(): List<Anime> = animeDao.getPopularThisWeek()
    suspend fun getAnilab2DataCached(): List<Anime> = animeDao.getAnilab2Data()

    suspend fun fetchAndCacheMostPopular(): List<Anime> {
        val list = getAniListCustomCategory(sort = listOf("POPULARITY_DESC"))
        if (list.isNotEmpty()) {
            saveAnimesLocally(list, isMostPopular = true)
        }
        return list
    }

    suspend fun fetchAndCachePopularThisMonth(): List<Anime> {
        val calendar = java.util.Calendar.getInstance()
        val month = calendar.get(java.util.Calendar.MONTH)
        val year = calendar.get(java.util.Calendar.YEAR)
        val season = when (month) {
            java.util.Calendar.DECEMBER, java.util.Calendar.JANUARY, java.util.Calendar.FEBRUARY -> "WINTER"
            java.util.Calendar.MARCH, java.util.Calendar.APRIL, java.util.Calendar.MAY -> "SPRING"
            java.util.Calendar.JUNE, java.util.Calendar.JULY, java.util.Calendar.AUGUST -> "SUMMER"
            else -> "FALL"
        }
        val list = getAniListCustomCategory(sort = listOf("POPULARITY_DESC"), season = season, seasonYear = year)
        if (list.isNotEmpty()) {
            saveAnimesLocally(list, isPopularThisMonth = true)
        }
        return list
    }

    suspend fun fetchAndCachePopularThisWeek(): List<Anime> {
        val list = getAniListCustomCategory(sort = listOf("TRENDING_DESC"), status = "RELEASING")
        if (list.isNotEmpty()) {
            saveAnimesLocally(list, isPopularThisWeek = true)
        }
        return list
    }

    suspend fun fetchAndCacheAnilab2Data(): List<Anime> {
        val list = getAniListCustomCategory(sort = listOf("POPULARITY_DESC"), status = "NOT_YET_RELEASED")
        if (list.isNotEmpty()) {
            saveAnimesLocally(list, isAnilab2Data = true)
        }
        return list
    }

    // ── Comments ─────────────────────────────────────────────────────────────

    /** Returns a live Room Flow — always shows cached data while a fetch is in flight. */
    fun getComments(animeId: String): Flow<List<Comment>> =
        commentDao.getCommentsForAnime(animeId)

    /**
     * Fetches the latest comments from the server and replaces the local cache.
     * Call once on screen open. On network failure the cache is left intact.
     */
    suspend fun fetchAndCacheComments(animeId: String) {
        val response = api.getComments(animeId)
        val entities = response.comments.map { dto ->
            Comment(
                remoteId      = dto.id,
                animeId       = dto.animeId,
                userId        = dto.userId,
                username      = dto.username,
                userAvatar    = dto.userAvatar,
                text          = dto.text,
                timestamp     = dto.createdAt,
                likes         = dto.likesCount,
                isLikedByUser = dto.isLikedByUser,
                parentId      = dto.parentId,
                replyCount    = dto.replyCount,
                isAuthorAdmin = dto.isAuthorAdmin
            )
        }
        val currentUsername = sessionManager.getUsername()
        if (currentUsername != null) {
            val userIsAdmin = entities.any { it.username == currentUsername && it.isAuthorAdmin }
            if (userIsAdmin) {
                sessionManager.saveAdminStatus(true)
            }
        }
        commentDao.deleteAllForAnime(animeId)
        commentDao.insertAll(entities)
    }

    /**
     * Posts a new comment or reply to the server.
     * On success, inserts the returned comment into Room so it appears immediately.
     * @param parentId  remoteId of parent comment, or null for a top-level comment.
     */
    suspend fun addComment(animeId: String, text: String, parentId: String? = null) {
        val uName = sessionManager.getUsername() ?: "Otaku Explorer"
        val uAvatar = sessionManager.getAvatarUrl()
        val response = api.postComment(animeId, PostCommentRequest(
            text = text,
            parentId = parentId,
            username = uName,
            userAvatar = uAvatar
        ))
        if (response.isSuccessful) {
            response.body()?.comment?.let { dto ->
                if (dto.username == uName && dto.isAuthorAdmin) {
                    sessionManager.saveAdminStatus(true)
                }
                commentDao.insertComment(Comment(
                    remoteId      = dto.id,
                    animeId       = dto.animeId,
                    userId        = dto.userId,
                    username      = dto.username,
                    userAvatar    = dto.userAvatar,
                    text          = dto.text,
                    timestamp     = dto.createdAt,
                    likes         = dto.likesCount,
                    isLikedByUser = dto.isLikedByUser,
                    parentId      = dto.parentId,
                    replyCount    = 0,
                    isAuthorAdmin = dto.isAuthorAdmin
                ))
            }
        } else {
            val errorBody = response.errorBody()?.string()
            throw Exception(errorBody ?: "Failed to post comment (${response.code()})")
        }
    }

    /**
     * Deletes a comment by local Room id.
     * Looks up the remoteId, calls the server, then removes from Room on success.
     * Deleting a top-level comment also removes its cached replies.
     */
    suspend fun deleteComment(commentId: Int) {
        val comment = commentDao.getById(commentId) ?: return
        if (comment.remoteId.isNotEmpty()) {
            val response = api.deleteComment(comment.remoteId)
            if (response.isSuccessful || response.code() == 404) {
                commentDao.deleteComment(commentId)
                commentDao.deleteReplies(comment.remoteId)
            } else {
                throw Exception("Failed to delete comment (${response.code()})")
            }
        } else {
            // Locally-created comment with no server record — just remove from Room
            commentDao.deleteComment(commentId)
        }
    }

    /**
     * Toggles a like using optimistic UI: updates Room immediately, then corrects
     * with the server's authoritative counts. Reverts on network error.
     */
    suspend fun toggleCommentLike(commentId: Int, currentIsLiked: Boolean) {
        val comment = commentDao.getById(commentId) ?: return

        if (comment.remoteId.isEmpty()) {
            val delta = if (currentIsLiked) -1 else 1
            commentDao.updateLike(commentId, delta, !currentIsLiked)
            return
        }

        // Optimistic update
        val delta = if (currentIsLiked) -1 else 1
        commentDao.updateLike(commentId, delta, !currentIsLiked)

        try {
            val resp = api.likeComment(comment.remoteId)
            commentDao.updateLikeExact(commentId, resp.likesCount, resp.isLikedByUser)
        } catch (e: Exception) {
            // Revert on failure
            commentDao.updateLike(commentId, -delta, currentIsLiked)
        }
    }
}
