package com.example.kitty.anilist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * AniList sign-in + token storage. Adapted from AniKitty (MIT, see NOTICE).
 * Implicit grant: the token comes back in the redirect fragment, so no client secret is needed.
 */
class AniListAccount(context: Context) {
    private val appContext = context.applicationContext

    private val prefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                appContext,
                "jumpanime_anilist",
                MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            appContext.getSharedPreferences("jumpanime_anilist_plain", Context.MODE_PRIVATE)
        }
    }

    val accessToken: String? get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    val userId: Int get() = prefs.getInt(KEY_USER_ID, 0)
    val username: String? get() = prefs.getString(KEY_USERNAME, null)
    /** Profile + list counts, shown on Home's avatar popup and the Account tab. */
    val profile = kotlinx.coroutines.flow.MutableStateFlow<AniListViewer?>(null)

    /** Signed-in AniList username, or null. Screens observe this. */
    val linkedName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        linkedName.value = if (accessToken != null && userId > 0) username else null
        profile.value = readProfile()
    }

    val isLinked: Boolean get() = accessToken != null && userId > 0

    private fun readProfile(): AniListViewer? {
        if (!isLinked) return null
        return AniListViewer(
            id = userId,
            name = username.orEmpty(),
            avatarUrl = prefs.getString(KEY_AVATAR, null),
            bannerUrl = prefs.getString(KEY_BANNER, null),
            about = prefs.getString(KEY_ABOUT, null),
            animeCount = prefs.getInt(KEY_ANIME_COUNT, 0),
            episodesWatched = prefs.getInt(KEY_EPISODES, 0),
            minutesWatched = prefs.getInt(KEY_MINUTES, 0),
            meanScore = prefs.getFloat(KEY_SCORE, 0f).toDouble(),
            watching = prefs.getInt(KEY_WATCHING, 0),
            completed = prefs.getInt(KEY_COMPLETED, 0),
            planning = prefs.getInt(KEY_PLANNING, 0),
            paused = prefs.getInt(KEY_PAUSED, 0),
            dropped = prefs.getInt(KEY_DROPPED, 0),
        )
    }

    fun save(token: String, viewer: AniListViewer) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, viewer.id)
            .putString(KEY_USERNAME, viewer.name)
            .putString(KEY_AVATAR, viewer.avatarUrl)
            .putString(KEY_BANNER, viewer.bannerUrl)
            .putString(KEY_ABOUT, viewer.about)
            .putInt(KEY_ANIME_COUNT, viewer.animeCount)
            .putInt(KEY_EPISODES, viewer.episodesWatched)
            .putInt(KEY_MINUTES, viewer.minutesWatched)
            .putFloat(KEY_SCORE, viewer.meanScore.toFloat())
            .putInt(KEY_WATCHING, viewer.watching)
            .putInt(KEY_COMPLETED, viewer.completed)
            .putInt(KEY_PLANNING, viewer.planning)
            .putInt(KEY_PAUSED, viewer.paused)
            .putInt(KEY_DROPPED, viewer.dropped)
            .apply()
        linkedName.value = viewer.name
        profile.value = viewer
    }

    /** Re-reads the profile and counts from AniList (keeps the stored token). */
    suspend fun refreshProfile(client: AniListClient) {
        val token = accessToken ?: return
        client.viewer(token)?.let { save(token, it) }
    }

    fun clear() {
        prefs.edit().clear().apply()
        linkedName.value = null
        profile.value = null
    }

    /** Opens AniList's sign-in page in a Custom Tab. */
    fun startSignIn(context: Context) {
        val nonce = UUID.randomUUID().toString()
        appContext.getSharedPreferences(OAUTH_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .putString(KEY_NONCE, nonce)
            .apply()
        val url = Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("state", nonce)
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, url)
    }

    fun matchesCallback(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return data.scheme == "jumpanime" && data.host == "anilist"
    }

    /** Reads the token out of `jumpanime://anilist#access_token=...`. Null if it isn't a valid, recent sign-in. */
    fun tokenFromCallback(uri: Uri?): String? {
        if (uri == null) return null
        val sp = appContext.getSharedPreferences(OAUTH_PREFS, Context.MODE_PRIVATE)
        val startedAt = sp.getLong(KEY_STARTED_AT, 0L)
        val nonce = sp.getString(KEY_NONCE, null)
        sp.edit().remove(KEY_STARTED_AT).remove(KEY_NONCE).apply()
        if (nonce.isNullOrBlank() || System.currentTimeMillis() - startedAt !in 0..TTL_MS) return null
        val map = HashMap<String, String>()
        for (raw in listOf(uri.encodedFragment, uri.encodedQuery)) {
            raw?.split('&')?.forEach { part ->
                val i = part.indexOf('=')
                if (i > 0) map[part.substring(0, i)] = Uri.decode(part.substring(i + 1))
            }
        }
        if (map["error"] != null) return null
        val state = map["state"]
        if (!state.isNullOrBlank() && state != nonce) return null
        return map["access_token"]?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val CLIENT_ID = "56832"
        private const val AUTHORIZE = "https://anilist.co/api/v2/oauth/authorize"
        private const val OAUTH_PREFS = "jumpanime_anilist_oauth"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_NONCE = "nonce"
        private const val TTL_MS = 10 * 60_000L
        private const val KEY_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_BANNER = "banner"
        private const val KEY_ABOUT = "about"
        private const val KEY_ANIME_COUNT = "anime_count"
        private const val KEY_EPISODES = "episodes_watched"
        private const val KEY_MINUTES = "minutes_watched"
        private const val KEY_SCORE = "mean_score"
        private const val KEY_WATCHING = "watching_count"
        private const val KEY_COMPLETED = "completed_count"
        private const val KEY_PLANNING = "planning_count"
        private const val KEY_PAUSED = "paused_count"
        private const val KEY_DROPPED = "dropped_count"
    }
}
