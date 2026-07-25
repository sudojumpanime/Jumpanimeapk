package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.model.Anime
import com.example.data.remote.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed interface ScheduleState {
    data object Loading : ScheduleState
    data class Success(
        val dates: List<DateItem>,
        val selectedIndex: Int,
        val filteredAnime: List<ScheduleAnimeUiModel>
    ) : ScheduleState
    data class Error(val message: String) : ScheduleState
}

data class DateItem(
    val dayName: String, // e.g. "Mon"
    val dateNum: String, // e.g. "21"
    val isToday: Boolean,
    val date: Date
)

data class ScheduleAnimeUiModel(
    val id: String,
    val title: String,
    val poster: String?,
    val rating: Double?,
    val genres: List<String>,
    val localAiringTimeText: String, // formatted time like "17:30"
    val statusText: String, // "Aired 2h ago" or "Airing in 3h" etc.
    val isAired: Boolean,
    val episodeNumText: String? = null
)

class ScheduleViewModel(
    private val repository: JumpAnimeRepository,
    private val episodeUpdateService: com.example.data.EpisodeUpdateService? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<ScheduleState>(ScheduleState.Loading)
    val uiState = _uiState.asStateFlow()

    val isInitialLoadCompleted = MutableStateFlow(false)

    private val _subscribedAnimeIds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedAnimeIds = _subscribedAnimeIds.asStateFlow()

    private val allAniListSchedules = java.util.concurrent.CopyOnWriteArrayList<AniListAiringSchedule>()
    private val datesList = mutableListOf<DateItem>()
    private var selectedDateIndex = 3 // Center (Today) out of [ -3 days to +10 days ] (total 14 days)

    init {
        generateDates()
        loadSubscribedAnimeIds()
    }

    fun loadSubscribedAnimeIds() {
        _subscribedAnimeIds.value = repository.sessionManager.getSubscribedAlertAnimeIds()
    }

    fun toggleAnimeAlert(animeId: String, title: String, poster: String?, episodeNumText: String?): Boolean {
        val isSubscribedNow = repository.sessionManager.toggleAnimeAlertSubscription(animeId)
        
        if (isSubscribedNow) {
            val digits = episodeNumText?.filter { it.isDigit() } ?: ""
            val num = digits.toIntOrNull() ?: 1
            if (num > 0) {
                // Seed last known episode as (num - 1) so when 'num' airs, NewEpisodeWorker identifies it as a new release!
                repository.sessionManager.setLastKnownEpisode(animeId, num - 1)
            }
            
            // Post immediate system notification to confirm the schedule subscription
            episodeUpdateService?.showSubscriptionConfirmationNotification(
                animeId = animeId,
                animeTitle = title,
                animePoster = poster ?: "",
                episodeNumText = episodeNumText ?: "New Episode"
            )
            
            // Instantly trigger sync in background to check if the episode already aired
            episodeUpdateService?.syncScheduleWithNotificationsAsync()
        }
        
        loadSubscribedAnimeIds()
        return isSubscribedNow
    }

    private fun generateDates() {
        datesList.clear()
        // Generate from 3 days ago to 10 days in the future (total 14 days centering Today)
        for (i in -3..10) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val dayName = SimpleDateFormat("E", Locale.getDefault()).format(cal.time) // "Mon", "Tue" etc.
            val dateNum = SimpleDateFormat("dd", Locale.getDefault()).format(cal.time) // "21", "22" etc.
            val isToday = i == 0
            datesList.add(DateItem(dayName, dateNum, isToday, cal.time))
        }
    }

    fun loadSchedule(forceRefresh: Boolean = false) {
        if (!forceRefresh && allAniListSchedules.isNotEmpty()) {
            updateFilteredList()
            isInitialLoadCompleted.value = true
            return
        }
        
        viewModelScope.launch {
            if (allAniListSchedules.isEmpty()) {
                _uiState.value = ScheduleState.Loading
            }
            try {
                if (datesList.isEmpty()) {
                    generateDates()
                }
                
                // Fetch schedules starting from 3 days ago (in seconds) to cover our entire datesList range
                val startUnix = (System.currentTimeMillis() - 3L * 24 * 3600 * 1000) / 1000
                
                // Fetch page 1 (50 schedules)
                var fetchedSomething = false
                try {
                    val p1 = repository.getAniListUpcomingSchedules(page = 1, perPage = 50, airingAtGreater = startUnix)
                    if (p1.isNotEmpty()) {
                        fetchedSomething = true
                        if (forceRefresh) {
                            allAniListSchedules.clear()
                        }
                        allAniListSchedules.addAll(p1)
                    }
                } catch (e: Exception) {
                    // ignore page 1 error
                }
                
                updateFilteredList()
                
                // Load page 2 & 3 in the background for completeness
                if (fetchedSomething) {
                    viewModelScope.launch {
                        for (page in 2..6) {
                            try {
                                kotlinx.coroutines.delay(800)
                                val pNext = repository.getAniListUpcomingSchedules(page = page, perPage = 50, airingAtGreater = startUnix)
                                if (pNext.isNotEmpty()) {
                                    allAniListSchedules.addAll(pNext)
                                    updateFilteredList()
                                } else {
                                    break
                                }
                            } catch (e: Exception) {
                                break
                            }
                        }
                    }
                } else {
                    // Fallback to empty if absolutely nothing was fetched
                    if (allAniListSchedules.isEmpty()) {
                        _uiState.value = ScheduleState.Error("Unable to load the schedule right now. Please try again later.")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ScheduleState.Error("Unable to load the schedule right now. Please check your connection and try again.")
            } finally {
                isInitialLoadCompleted.value = true
            }
        }
    }

    fun selectDate(index: Int) {
        if (index in datesList.indices) {
            selectedDateIndex = index
            updateFilteredList()
        }
    }

    private fun updateFilteredList() {
        if (datesList.isEmpty()) return
        val targetDateItem = datesList[selectedDateIndex]
        val targetDate = targetDateItem.date
        val now = Date()

        val targetCal = Calendar.getInstance().apply { time = targetDate }
        val targetYear = targetCal.get(Calendar.YEAR)
        val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)

        val allUniqueSchedules = allAniListSchedules.distinctBy { it.id }

        val filtered = allUniqueSchedules.mapNotNull { schedule ->
            val localAiringDate = Date(schedule.airingAt * 1000)
            val airingCal = Calendar.getInstance().apply { time = localAiringDate }
            
            val isSameDay = airingCal.get(Calendar.YEAR) == targetYear && 
                            airingCal.get(Calendar.DAY_OF_YEAR) == targetDayOfYear
                            
            if (!isSameDay) return@mapNotNull null

            val isAired = localAiringDate.before(now)
            
            val englishTitle = schedule.media?.title?.english
            val romajiTitle = schedule.media?.title?.romaji
            val nativeTitle = schedule.media?.title?.native
            val titleToUse = if (!englishTitle.isNullOrBlank()) englishTitle 
                             else if (!romajiTitle.isNullOrBlank()) romajiTitle 
                             else (nativeTitle ?: "Unknown Anime")
            
            val ratingScore = schedule.media?.averageScore?.let { it.toDouble() / 10.0 }
            
            ScheduleAnimeUiModel(
                id = "mal_${schedule.media?.idMal ?: schedule.media?.id}",
                title = titleToUse,
                poster = schedule.media?.coverImage?.large,
                rating = ratingScore,
                genres = schedule.media?.genres ?: emptyList(),
                localAiringTimeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(localAiringDate),
                statusText = getAiringStatusText(localAiringDate, now),
                isAired = isAired,
                episodeNumText = "Episode ${schedule.episode}"
            )
        }.sortedBy { it.localAiringTimeText } // sort chronologically
         .distinctBy { it.id }

        _uiState.value = ScheduleState.Success(
            dates = datesList,
            selectedIndex = selectedDateIndex,
            filteredAnime = filtered
        )
    }

    private fun getDayDifference(date1: Date, date2: Date): Int {
        val cal1 = Calendar.getInstance().apply { 
            time = date1
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val cal2 = Calendar.getInstance().apply { 
            time = date2
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMs = cal1.timeInMillis - cal2.timeInMillis
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun getAiringStatusText(airingDate: Date, now: Date): String {
        val dayDiff = getDayDifference(airingDate, now)
        return when {
            dayDiff == 0 -> {
                val diffMs = airingDate.time - now.time
                val isAired = diffMs <= 0
                val absDiffMs = Math.abs(diffMs)
                val absDiffMin = absDiffMs / (60 * 1000)
                val absDiffHours = absDiffMin / 60
                val absDiffMins = absDiffMin % 60
                
                if (isAired) {
                    if (absDiffHours > 0) "Aired ${absDiffHours}h ${absDiffMins}m ago"
                    else "Aired ${absDiffMin}m ago"
                } else {
                    if (absDiffHours > 0) "Upcoming in ${absDiffHours}h ${absDiffMins}m"
                    else "Upcoming in ${absDiffMin}m"
                }
            }
            dayDiff == -1 -> "Aired yesterday"
            dayDiff < -1 -> "Aired ${Math.abs(dayDiff)} days ago"
            dayDiff == 1 -> "Upcoming tomorrow"
            dayDiff > 1 -> "Upcoming in $dayDiff days"
            else -> ""
        }
    }
}
