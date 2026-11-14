package com.example.ui

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Welcome : Screen

    @Serializable
    data object Login : Screen
    
    @Serializable
    data object Home : Screen
    
    @Serializable
    data object Browse : Screen

    @Serializable
    data object Schedule : Screen
    
    @Serializable
    data object Watchlist : Screen
    
    @Serializable
    data class Detail(val animeId: String) : Screen
    
    @Serializable
    data class Player(
        val animeId: String,
        val malId: String,
        val seasonId: String,
        val episodeNumber: Int,
        val animeTitle: String,
        val episodeTitle: String,
        val animePoster: String
    ) : Screen
    
    @Serializable
    data object Settings : Screen

    @Serializable
    data object EditProfile : Screen

    @Serializable
    data object SelectAvatar : Screen

    @Serializable
    data object Notifications : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object Downloads : Screen

    @Serializable
    data class OfflinePlayer(val title: String, val episode: Int, val path: String) : Screen
}
