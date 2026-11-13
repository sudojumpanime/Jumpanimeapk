package com.example.kitty.extensions

/**
 * Installed / store-listed AniKitty stream extension (apiVersion 1).
 * Auth VPS + Media3 stay in the APK; extensions only resolve playable URLs.
 */
data class ExtensionManifest(
    val apiVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val homepage: String? = null,
    val baseUrl: String,
    val resolvePath: String = DEFAULT_RESOLVE_PATH,
    /** Required HTTPS icon of the site this extension scrapes / fronts. */
    val iconUrl: String,
    /** Public site origin shown in the Store (usually the scraped host). */
    val siteUrl: String? = null,
    val nsfw: Boolean = false,
    /** GitHub repo URL this was installed from (if any). */
    val sourceGithub: String? = null,
    /** Raw manifest URL used for updates. */
    val manifestUrl: String? = null,
) {
    fun resolveEndpoint(): String = baseUrl.trimEnd('/') + "/" + resolvePath.trimStart('/')

    companion object {
        const val SUPPORTED_API = 1
        const val DEFAULT_RESOLVE_PATH = "/v1/resolve"
    }
}

data class StoreListing(
    val id: String,
    val name: String,
    val description: String,
    val github: String,
    val author: String = "",
    val featured: Boolean = false,
    val manifestPath: String? = null,
    /** Site icon shown in the Store grid (required for new listings). */
    val iconUrl: String? = null,
    val siteUrl: String? = null,
)

data class StoreIndex(
    val apiVersion: Int,
    val updatedAt: String?,
    val extensions: List<StoreListing>,
    /** Which index URL served this payload (for Store status chrome). */
    val sourceUrl: String? = null,
)
