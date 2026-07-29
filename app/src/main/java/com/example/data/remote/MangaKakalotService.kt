package com.example.data.remote

import com.example.data.model.MangaChapter
import com.example.data.model.MangaDetail
import com.example.data.model.MangaPage
import com.example.data.model.MangaSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class MangaKakalotService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://chapmanganato.to"
    private val searchBaseUrl = "https://chapmanganato.to/search/story/"

    private suspend fun fetchDoc(url: String): Document {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", baseUrl)
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
        val body = resp.body?.string() ?: throw RuntimeException("Empty response")
        Jsoup.parse(body)
    }

    suspend fun search(query: String, page: Int = 1): List<MangaSearchResult> = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase().replace("\\s+".toRegex(), "_")
        val url = if (page > 1) "$searchBaseUrl$normalized?page=$page" else "$searchBaseUrl$normalized"
        val doc = fetchDoc(url)

        // MangaKakalot/Manganato search results
        val results = mutableListOf<MangaSearchResult>()
        val storyItems = doc.select(".search-story-item, .story_item, .item")
        if (storyItems.isNotEmpty()) {
            for (item in storyItems) {
                val link = item.selectFirst("a[href]") ?: continue
                val title = item.selectFirst(".item-title, .story_item_right h3, h3 a, .item-right h3")?.text()
                    ?: link.text()
                val cover = item.selectFirst("img")?.absUrl("src") ?: ""
                val mangaUrl = link.absUrl("href")
                val id = mangaUrl.substringAfterLast("/").removeSuffix("/")
                if (mangaUrl.isNotBlank()) {
                    results.add(MangaSearchResult(id, title, cover, mangaUrl))
                }
            }
        }

        // Fallback: generic link parsing if selectors don't match
        if (results.isEmpty()) {
            val links = doc.select("a[href*='/manga/'], a[href*='-story-']")
            val seen = mutableSetOf<String>()
            for (link in links) {
                val mangaUrl = link.absUrl("href")
                if (mangaUrl in seen) continue
                seen.add(mangaUrl)
                val title = link.text().ifBlank { mangaUrl.substringAfterLast("/").replace("-", " ") }
                val id = mangaUrl.substringAfterLast("/").removeSuffix("/")
                results.add(MangaSearchResult(id, title, "", mangaUrl))
            }
        }

        results.distinctBy { it.id }
    }

    suspend fun getLatestUpdates(page: Int = 1): List<MangaSearchResult> = withContext(Dispatchers.IO) {
        val url = if (page > 1) "$baseUrl?page=$page" else baseUrl
        val doc = fetchDoc(url)
        val results = mutableListOf<MangaSearchResult>()

        // Manganato homepage latest updates
        val items = doc.select(".panel_latest_update .item, .latest_updates .item, .content-home .item")
        for (item in items) {
            val link = item.selectFirst("a[href]") ?: continue
            val mangaUrl = link.absUrl("href")
            val title = item.selectFirst("h3, .title, .item-title")?.text() ?: link.text()
            val cover = item.selectFirst("img")?.absUrl("src") ?: ""
            val id = mangaUrl.substringAfterLast("/").removeSuffix("/")
            if (mangaUrl.isNotBlank()) {
                results.add(MangaSearchResult(id, title, cover, mangaUrl))
            }
        }

        // Fallback
        if (results.isEmpty()) {
            val links = doc.select("a[href*='/manga/']")
            val seen = mutableSetOf<String>()
            for (link in links) {
                val mangaUrl = link.absUrl("href")
                if (mangaUrl in seen) continue
                seen.add(mangaUrl)
                val id = mangaUrl.substringAfterLast("/").removeSuffix("/")
                val title = link.text().ifBlank { id.replace("-", " ") }
                results.add(MangaSearchResult(id, title, "", mangaUrl))
            }
        }

        results.distinctBy { it.id }
    }

    suspend fun getDetail(mangaUrl: String): MangaDetail = withContext(Dispatchers.IO) {
        val doc = fetchDoc(mangaUrl)

        val title = doc.selectFirst("h1, h2.story-info-right h1, .panel-story-info .info-right h1")?.text()
            ?: mangaUrl.substringAfterLast("/").replace("-", " ")

        val cover = doc.selectFirst(".info-left img, .story-info-left img, .panel-story-info img, img[src*='cover']")?.absUrl("src") ?: ""

        val description = doc.selectFirst(".panel-story-info-description, #noidungm, .story-info-right p, .info-right p")?.text() ?: ""

        val author = doc.selectFirst(".table-info tr:has(td:contains(Author)) td:last-child, .info-right li:has(span:contains(Author)) span+span, li:contains(Author)")?.text()
            ?.replace("Author : ", "")?.replace("Author:", "")?.trim() ?: ""

        val status = doc.selectFirst(".table-info tr:has(td:contains(Status)) td:last-child, .info-right li:has(span:contains(Status)) span+span, li:contains(Status)")?.text()
            ?.replace("Status : ", "")?.replace("Status:", "")?.trim() ?: ""

        val genres = doc.select(".table-info tr:has(td:contains(Genres)) td:last-child a, .info-right li:has(span:contains(Genres)) a").map { it.text() }
            .ifEmpty { doc.select(".genres a, .panel-story-info a[href*='genre']").map { it.text() } }

        // Chapter list
        val chapters = mutableListOf<MangaChapter>()
        val chapterRows = doc.select(".panel-story-chapter-list .row, .chapter-list .row, .row-content-chapter li, ul.row-content-chapter li")
        if (chapterRows.isNotEmpty()) {
            for (row in chapterRows) {
                val link = row.selectFirst("a") ?: continue
                val chapterUrl = link.absUrl("href")
                val name = link.text().trim()
                val date = row.selectFirst(".chapter-time, span.chapter-time, time")?.text() ?: ""
                val id = chapterUrl.substringAfterLast("/").removeSuffix("/")
                chapters.add(MangaChapter(id, name, chapterUrl, date))
            }
        }

        // Fallback: any links that look like chapters
        if (chapters.isEmpty()) {
            val chapterLinks = doc.select("a[href*='chapter'], a[href*='-chapter-']")
            for (link in chapterLinks) {
                val chapterUrl = link.absUrl("href")
                val name = link.text().trim()
                val id = chapterUrl.substringAfterLast("/").removeSuffix("/")
                chapters.add(MangaChapter(id, name, chapterUrl))
            }
        }

        val mangaId = mangaUrl.substringAfterLast("/").removeSuffix("/")
        MangaDetail(mangaId, title, cover, description, author, status, genres, mangaUrl, chapters.reversed())
    }

    suspend fun getChapterPages(chapterUrl: String): List<MangaPage> = withContext(Dispatchers.IO) {
        val doc = fetchDoc(chapterUrl)
        val pages = mutableListOf<MangaPage>()

        // Manganato reader: images in .container-chapter-reader img
        val images = doc.select(".container-chapter-reader img, .reading-detail img, .page-chapter img")
        for ((index, img) in images.withIndex()) {
            val src = img.absUrl("src").ifBlank { img.absUrl("data-src") }.ifBlank { img.attr("data-src") }
            if (src.isNotBlank()) {
                pages.add(MangaPage(index, src))
            }
        }

        // Fallback: any img tags with data-src (lazy loaded)
        if (pages.isEmpty()) {
            val lazyImages = doc.select("img[data-src], img[data-lazy-src]")
            for ((index, img) in lazyImages.withIndex()) {
                val src = img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
                if (src.isNotBlank() && (src.contains("manga") || src.contains("chapter"))) {
                    pages.add(MangaPage(index, src))
                }
            }
        }

        pages
    }

    fun buildImageRequest(url: String, referer: String): Request {
        return Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", referer)
            .build()
    }
}
