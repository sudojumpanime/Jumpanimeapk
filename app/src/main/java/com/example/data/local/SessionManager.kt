package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val normalPrefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_UNREAD_COUNT = "unread_notification_count"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_GUEST = "is_guest"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_PRELOAD_ENABLED = "preload_enabled"
        private const val DEFAULT_BASE_URL = "https://jumpanime-backend-production.up.railway.app"
    }

    fun saveGuestMode(isGuest: Boolean) {
        normalPrefs.edit().putBoolean(KEY_IS_GUEST, isGuest).apply()
    }

    fun isGuestMode(): Boolean {
        return normalPrefs.getBoolean(KEY_IS_GUEST, false)
    }

    fun saveToken(token: String) {
        securePrefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return securePrefs.getString(KEY_TOKEN, null)
    }

    fun saveAvatarUrl(url: String) {
        normalPrefs.edit().putString(KEY_AVATAR_URL, url).apply()
    }

    fun getAvatarUrl(): String {
        val url = normalPrefs.getString(KEY_AVATAR_URL, null)
        return if (url.isNullOrBlank()) {
            val username = getUsername() ?: "Guest"
            "https://ui-avatars.com/api/?name=${username}&background=FF2A43&color=fff&size=128"
        } else {
            url
        }
    }

    fun saveUsername(username: String) {
        normalPrefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? {
        return normalPrefs.getString(KEY_USERNAME, null)
    }

    fun saveEmail(email: String) {
        normalPrefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun getEmail(): String? {
        return normalPrefs.getString(KEY_EMAIL, null)
    }

    fun clearToken() {
        securePrefs.edit().remove(KEY_TOKEN).apply()
        normalPrefs.edit().remove(KEY_USERNAME).remove(KEY_EMAIL).remove(KEY_IS_GUEST).apply()
    }

    fun getBaseUrl(): String {
        return normalPrefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun saveBaseUrl(url: String) {
        normalPrefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    fun isNotificationsEnabled(): Boolean {
        return normalPrefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        normalPrefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getSubscribedAlertAnimeIds(): Set<String> {
        return normalPrefs.getStringSet("subscribed_alerts", emptySet()) ?: emptySet()
    }

    fun toggleAnimeAlertSubscription(animeId: String): Boolean {
        val current = normalPrefs.getStringSet("subscribed_alerts", emptySet())?.toMutableSet() ?: mutableSetOf()
        val isSubscribedNow = if (current.contains(animeId)) {
            current.remove(animeId)
            false
        } else {
            current.add(animeId)
            true
        }
        normalPrefs.edit().putStringSet("subscribed_alerts", current).apply()
        return isSubscribedNow
    }

    fun getUnreadCount(): Int = normalPrefs.getInt(KEY_UNREAD_COUNT, 0)
    
    fun incrementUnreadCount() {
        val current = getUnreadCount()
        normalPrefs.edit().putInt(KEY_UNREAD_COUNT, current + 1).apply()
    }

    fun resetUnreadCount() {
        normalPrefs.edit().putInt(KEY_UNREAD_COUNT, 0).apply()
    }

    fun getLastKnownEpisode(animeId: String): Int {
        return normalPrefs.getInt("last_known_ep_$animeId", -1)
    }

    fun setLastKnownEpisode(animeId: String, episodeNum: Int) {
        normalPrefs.edit().putInt("last_known_ep_$animeId", episodeNum).apply()
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun isPreloadEnabled(): Boolean {
        return normalPrefs.getBoolean(KEY_PRELOAD_ENABLED, true)
    }

    fun setPreloadEnabled(enabled: Boolean) {
        normalPrefs.edit().putBoolean(KEY_PRELOAD_ENABLED, enabled).apply()
    }

    fun getSelectedTheme(): String {
        return normalPrefs.getString("selected_theme", "RED") ?: "RED"
    }

    fun setSelectedTheme(theme: String) {
        normalPrefs.edit().putString("selected_theme", theme).apply()
    }

    fun getLastDismissedUpdateVersion(): String {
        return normalPrefs.getString("last_dismissed_update_version", "") ?: ""
    }

    fun setLastDismissedUpdateVersion(version: String) {
        normalPrefs.edit().putString("last_dismissed_update_version", version).apply()
    }

    fun saveAdminStatus(isAdmin: Boolean) {
        normalPrefs.edit().putBoolean("is_admin", isAdmin).apply()
    }

    fun isAdmin(): Boolean {
        val uName = getUsername() ?: return false
        return normalPrefs.getBoolean("is_admin", false) || uName.contains("admin", ignoreCase = true)
    }
}
