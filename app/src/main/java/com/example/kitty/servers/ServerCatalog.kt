package com.example.kitty.servers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.kitty.extensions.ExtensionInstaller
import com.example.kitty.extensions.ExtensionManifest
import com.example.kitty.extensions.ExtensionRepository
import com.example.kitty.extensions.ExtensionStoreClient
import com.example.kitty.extensions.HttpExtensionResolver
import com.example.kitty.skip.SkipTimes
import com.example.kitty.extensions.StoreIndex

/**
 * Stream sources = optional bundled resolver + user-installed HTTP extensions.
 * Auth VPS and Media3 playback stay first-party; scrapers move to extensions.
 */
class ServerCatalog(context: Context) {
    private val prefs = context.getSharedPreferences("anikitty_servers", Context.MODE_PRIVATE)
    private val resolveCache = ResolveCache(context)
    private val megaPlayCrypto = MegaPlayCrypto(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val extensions = ExtensionRepository(context)
    val installer = ExtensionInstaller(extensions)
    val storeClient = ExtensionStoreClient()

    private val bundled: List<StreamResolver> = listOf(
        AnikotoServer(cache = resolveCache, crypto = megaPlayCrypto),
        GotakuServer(cache = resolveCache),
        AniPmServer(cache = resolveCache),
    )

    private val _resolvers = MutableStateFlow(buildResolvers(extensions.installed.value))
    val resolvers: StateFlow<List<StreamResolver>> = _resolvers.asStateFlow()

    private val _selectedId = MutableStateFlow(
        prefs.getString(KEY_SELECTED, AnikotoServer.ID) ?: AnikotoServer.ID,
    )
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    val servers: List<StreamServer> get() = _resolvers.value.map { it.server }

    init {
        scope.launch {
            extensions.installed.collect { installed ->
                _resolvers.value = buildResolvers(installed)
                ensureSelectionValid()
            }
        }
    }

    fun select(id: String) {
        if (_resolvers.value.none { it.server.id == id }) return
        prefs.edit().putString(KEY_SELECTED, id).apply()
        _selectedId.update { id }
    }

    /**
     * Intro and credits for any playing provider. The timestamps always come
     * from Anikoto's episode page, not from a separate skip service.
     */
    suspend fun anikotoSkipTimes(
        anilistId: Int,
        episode: Int,
        titleHint: String?,
    ): SkipTimes {
        val anikoto = bundled.filterIsInstance<AnikotoServer>().firstOrNull()
            ?: return SkipTimes()
        return anikoto.loadSkipTimes(anilistId, episode, titleHint)
    }

    fun selectedResolver(): StreamResolver {
        val list = _resolvers.value
        return list.firstOrNull { it.server.id == _selectedId.value }
            ?: list.firstOrNull()
            ?: error("No stream extensions installed")
    }

    /**
     * Resolve using the selected installed provider first, then automatically
     * try every other **installed** provider until one returns sources.
     * On success via a fallback, selects that provider so the next Play prefers it.
     */
    suspend fun resolveWithFailover(
        anilistId: Int,
        episode: Int,
        audio: String = "sub",
        titleHint: String? = null,
        onAttempt: ((serverName: String, index: Int, total: Int) -> Unit)? = null,
    ): ResolveFailoverResult {
        val list = _resolvers.value
        if (list.isEmpty()) error("No stream extensions installed")
        val preferredId = _selectedId.value
        val ordered = buildList {
            list.firstOrNull { it.server.id == preferredId }?.let { add(it) }
            list.filterNot { it.server.id == preferredId }.forEach { add(it) }
        }
        val errors = ArrayList<String>()
        for ((index, resolver) in ordered.withIndex()) {
            onAttempt?.invoke(resolver.server.name, index, ordered.size)
            val sources = runCatching {
                resolver.resolve(anilistId, episode, audio, titleHint)
            }.getOrElse { err ->
                val msg = err.message?.takeIf { it.isNotBlank() } ?: err::class.java.simpleName
                errors += "${resolver.server.name}: $msg"
                null
            }
            if (sources != null && sources.isNotEmpty()) {
                val usedFallback = resolver.server.id != preferredId
                if (usedFallback) {
                    select(resolver.server.id)
                }
                return ResolveFailoverResult(
                    sources = sources,
                    server = resolver.server,
                    usedFallback = usedFallback,
                    attempted = errors + "${resolver.server.name}: ok",
                )
            }
            if (sources != null) {
                errors += "${resolver.server.name}: empty sources"
            }
        }
        error(
            buildString {
                append("All installed providers failed")
                if (errors.isNotEmpty()) {
                    append(" — ")
                    append(errors.joinToString(" · "))
                }
            },
        )
    }

    fun uninstallExtension(id: String) {
        if (bundled.any { it.server.id == id }) return
        extensions.remove(id)
        ensureSelectionValid()
    }

    suspend fun installFromGithub(url: String): ExtensionManifest =
        installer.installFromGithub(url)

    suspend fun installStoreListing(
        github: String,
        manifestPath: String?,
    ): ExtensionManifest {
        // Nested monorepo paths: prefer tree URL so locator hits the right raw file.
        val input = if (!manifestPath.isNullOrBlank() &&
            github.contains("github.com") &&
            !github.contains("/tree/") &&
            !github.contains("/blob/")
        ) {
            val base = github.trimEnd('/')
            "$base/tree/main/${manifestPath.removeSuffix("/anikitty-extension.json").trim('/')}"
        } else {
            github
        }
        return installer.installFromGithub(input)
    }

    suspend fun fetchStore(): StoreIndex = storeClient.fetchIndex()

    private fun buildResolvers(installed: List<ExtensionManifest>): List<StreamResolver> {
        val fromExt = installed.map { HttpExtensionResolver(it) }
        // Bundled Anikoto remains available until users rely fully on BYO extensions.
        val bundledIds = bundled.map { it.server.id }.toSet()
        val extras = fromExt.filterNot { it.server.id in bundledIds }
        return bundled + extras
    }

    private fun ensureSelectionValid() {
        val list = _resolvers.value
        if (list.isEmpty()) return
        if (list.none { it.server.id == _selectedId.value }) {
            select(list.first().server.id)
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected_server_id"
    }
}
