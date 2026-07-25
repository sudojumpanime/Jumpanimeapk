package com.example.data.model

data class EpisodeNotification(
    val animeId: String,
    val animeTitle: String,
    val animePoster: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val malId: String? = null,
    val seasonId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
