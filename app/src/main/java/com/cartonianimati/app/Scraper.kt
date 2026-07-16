package com.cartonianimati.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlin.text.RegexOption

object Scraper {

    private const val BASE_URL = "https://toonitalia.xyz"

    private val REFERENCE_HOSTS = listOf(
        "wikipedia.org", "animeclick.it", "myanimelist.net", "anidb.net"
    )

    private val homeImageCache = mutableMapOf<String, String>()
    private val cacheMutex = Mutex()

    private suspend fun getDoc(url: String): org.jsoup.nodes.Document =
        withContext(Dispatchers.IO) { Jsoup.parse(NetworkModule.fetch(url)) }

    // ---------- helpers ----------

    private fun isStreamingHref(href: String?): Boolean {
        if (href.isNullOrBlank() || !href.contains("://")) return false
        if (href.contains("toonitalia.xyz")) return false
        val host = runCatching {
            Pattern.compile("https?://([^/]+)/").matcher(href).run {
                if (find()) group(1).lowercase() else ""
            }
        }.getOrDefault("")
        return !REFERENCE_HOSTS.any { host.contains(it) }
    }

    private fun hostLabel(href: String): String = runCatching {
        val m = Pattern.compile("https?://([^/]+)/").matcher(href)
        if (m.find()) m.group(1).replace("www.", "") else href
    }.getOrDefault(href)

    private fun cleanEmojiTitle(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace(Regex("[\\u0000-\\u001F\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("[\\u20E3\\u2600-\\u27BF\\u{1F000}-\\u{1FAFF}]"), "")
            .trim()
    }

    private fun cacheImage(url: String, image: String) {
        if (url.isNotBlank() && image.isNotBlank()) homeImageCache[url.trimEnd('/')] = image
    }

    private suspend fun getCachedImage(url: String): String = cacheMutex.withLock {
        homeImageCache[url.trimEnd('/')] ?: ""
    }

    private suspend fun buildHomepageCache() {
        cacheMutex.withLock { if (homeImageCache.isNotEmpty()) return }
        runCatching {
            val doc = getDoc(BASE_URL)
            val map = mutableMapOf<String, String>()
            for (item in doc.select(".item")) {
                val link = item.selectFirst("a[href]") ?: continue
                val href = link.attr("href").trimEnd('/')
                val img = item.selectFirst("img")?.attr("src") ?: ""
                if (href.isNotBlank() && img.isNotBlank()) map[href] = img
            }
            cacheMutex.withLock { homeImageCache.putAll(map) }
        }
    }

    // ---------- homepage ----------

    suspend fun scrapeHomepage(): List<CategorySection> = runCatching {
        val doc = getDoc(BASE_URL)
        val sections = mutableListOf<CategorySection>()
        for (col in doc.select(".col")) {
            val h2 = col.selectFirst("h2")
            val title = cleanEmojiTitle(h2?.text()) ?: continue
            if (title.isBlank()) continue
            val items = mutableListOf<ContentItem>()
            for (item in col.select(".item")) {
                val link = item.selectFirst("a[href]") ?: item
                val href = link.attr("href")
                val img = item.selectFirst("img")?.attr("src") ?: ""
                val itemTitle = item.selectFirst(".title")?.text()?.trim()
                    ?: link.text().trim()
                if (itemTitle.isNotBlank() && href.startsWith(BASE_URL) && href != BASE_URL) {
                    items.add(ContentItem(title = itemTitle, url = href, image = img))
                }
            }
            if (items.isNotEmpty()) sections.add(CategorySection(title, items))
        }
        if (sections.isNotEmpty()) {
            cacheMutex.withLock { homeImageCache.putAll(sections.flatMap { it.items }.associate { it.url.trimEnd('/') to it.image }.filter { it.value.isNotBlank() }) }
            sections
        } else fallbackHomepage()
    }.getOrDefault(fallbackHomepage())

    private suspend fun fallbackHomepage(): List<CategorySection> {
        val cats = scrapeCategories()
        val sections = mutableListOf<CategorySection>()
        for ((slug, label) in cats) {
            runCatching {
                val items = scrapeContentList(slug).take(30)
                if (items.isNotEmpty()) sections.add(CategorySection(label, items))
            }
        }
        return sections
    }

    // ---------- categories ----------

    fun scrapeCategories(): Map<String, String> = mapOf(
        "anime-ita" to "Anime Ita",
        "contatti" to "Anime Sub-Ita",
        "film-animazione" to "Film Animazione",
        "serie-tv" to "Serie Tv"
    )

    suspend fun scrapeContentList(categorySlug: String): List<ContentItem> = withContext(Dispatchers.IO) {
        val doc = getDoc("$BASE_URL/$categorySlug/")
        val items = mutableListOf<ContentItem>()
        val seen = mutableSetOf<String>()
        for (a in doc.select(".catlist-box ul li a[href]")) {
            val href = a.attr("href")
            val title = a.text().trim()
            if (title.isNotBlank() && href.startsWith(BASE_URL) && !seen.contains(href)) {
                seen.add(href)
                items.add(ContentItem(title = title, url = href, categorySlug = categorySlug))
            }
        }
        buildHomepageCache()
        for (item in items) {
            if (item.image.isBlank()) {
                val cached = getCachedImage(item.url)
                if (cached.isNotBlank()) {
                    val idx = items.indexOf(item)
                    if (idx >= 0) items[idx] = item.copy(image = cached)
                }
            }
        }
        // fetch missing images in parallel (limited)
        val needsFetch = items.filter { it.image.isBlank() }.take(25)
        if (needsFetch.isNotEmpty()) {
            runCatching {
                coroutineScope {
                    needsFetch.chunked(5).map { chunk ->
                        chunk.map { item ->
                            async(Dispatchers.IO) {
                                runCatching {
                                    val d = getDoc(item.url)
                                    val img = d.selectFirst("article .entry-content img")
                                        ?: d.selectFirst("article img")
                                        ?: d.selectFirst(".entry-content img")
                                    val url = img?.attr("src") ?: ""
                                    if (url.isNotBlank()) item to url else null
                                }.getOrNull()
                            }
                        }.awaitAll().filterNotNull()
                    }.flatten()
                }
            }.getOrDefault(emptyList()).forEach { (item, imgUrl) ->
                val idx = items.indexOf(item)
                if (idx >= 0) items[idx] = item.copy(image = imgUrl)
            }
        }
        items
    }

    // ---------- detail ----------

    private fun splitSegments(raw: String, labels: List<String>): List<String> {
        var clean = raw
        for (lab in labels) if (lab.isNotBlank()) clean = clean.replace(lab, " ")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        val parts = clean.split(Regex("(?<=\\s)(?=\\d{2,4}\\s*[–-]\\s)")).map { it.trim() }.filter { it.isNotBlank() }
        val merged = mutableListOf<String>()
        for (p in parts) {
            if (merged.isNotEmpty() && !Regex("^\\d{2,4}\\s*[–-]").containsMatchIn(p)) {
                merged[merged.lastIndex] = (merged.last() + " " + p).trim()
            } else merged.add(p)
        }
        if (merged.size >= 2 && !Regex("^\\d{2,4}\\s*[–-]").containsMatchIn(merged.first())) {
            merged[1] = (merged[0] + " " + merged[1]).trim()
            merged.removeAt(0)
        }
        return merged
    }

    private fun buildEpisode(segText: String, links: List<org.jsoup.nodes.Element>, allLabels: List<String>, seasonLabel: String, fallback: Int): Episode? {
        if (links.isEmpty()) return null
        val players = links.map { a ->
            val href = a.attr("href")
            PlayerLink(label = a.text().trim().ifBlank { hostLabel(href) }, url = href)
        }
        var title = segText
        title = Regex("^.*?(\\d{2,4})\\s*[–-]\\s*").replace(title, "$1 – ", RegexOption.DOT_MATCHES_ALL).trim()
        for (lab in allLabels) if (lab.isNotBlank()) title = title.replace(Regex(Pattern.quote(lab), RegexOption.IGNORE_CASE), " ")
        var m = Regex("^(\\d{2,4})\\s*[–-]\\s*(.+?)\\s*[–-]\\s").find(title)
        if (m == null) m = Regex("^(\\d{2,4})\\s*[–-]\\s*(.*)$").find(title)
        return if (m != null) {
            val num = m.groupValues[1].toIntOrNull() ?: fallback
            val t = m.groupValues[2].trim()
            Episode(
                title = if (t.isNotBlank()) "Ep. $num - $t" else "Ep. $num",
                url = players.first().url,
                number = num,
                players = players
            )
        } else {
            val cleaned = title.replace(Regex("(?i)link\\s*streaming\\s*:"), "")
                .replace(Regex("^[\\s–-]+"), "").trim()
                .ifBlank { seasonLabel.ifBlank { "Streaming" } }
            Episode(title = cleaned, url = players.first().url, number = fallback, players = players)
        }
    }

    private fun distribute(links: List<org.jsoup.nodes.Element>, n: Int): List<List<org.jsoup.nodes.Element>> {
        if (n <= 0) return emptyList()
        return List(n) { i -> links.subList(links.size * i / n, links.size * (i + 1) / n) }
    }

    suspend fun scrapeDetail(url: String): ContentItem = withContext(Dispatchers.IO) {
        val doc = getDoc(url)
        val content = doc.selectFirst("article .entry-content") ?: doc.selectFirst("article") ?: doc
        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""
        val image = content.selectFirst("img")?.attr("src") ?: ""

        // metadata
        val meta = mutableMapOf<String, String>()
        for (p in content.select("p")) {
            val t = p.text()
            if (listOf("Titolo originale", "Paese di origine", "Data di pubblicazione", "Stato Opera", "N. Episodi", "Aggiornamento episodi").any { it in t }) {
                val fields = listOf("Titolo originale", "Titoli alternativi", "Paese di origine", "Data di pubblicazione", "Stato Opera", "N. Episodi", "Aggiornamento episodi")
                val split = t.split(Regex("\\s*(" + fields.joinToString("|") { Pattern.quote(it) } + ")\\s*:\\s*"))
                for (i in 1 until split.size step 2) {
                    val f = split[i]; val v = split[i + 1].trim().trimEnd('.').trim()
                    val key = when (f) {
                        "Titolo originale", "Titoli alternativi" -> "originalTitle"
                        "Paese di origine" -> "country"
                        "Data di pubblicazione" -> "year"
                        "Stato Opera" -> "status"
                        "N. Episodi" -> "totalEpisodes"
                        "Aggiornamento episodi" -> "availableEpisodes"
                        else -> null
                    }
                    if (key != null && !meta.containsKey(key) && v.isNotBlank()) meta[key] = v
                }
                break
            }
        }

        // synopsis (between "Trama:" and next heading)
        var synopsis = ""
        var grab = false
        for (el in content.children()) {
            val name = el.tagName()
            if (name == "h2" || name == "h3") {
                if ("Trama" in el.text()) { grab = true; continue }
                if (grab) break
            }
            if (grab && name == "p") {
                val t = el.text().trim()
                if (t.isNotBlank() && "PLAYER" !in t && "http" !in t) { synopsis = t; break }
            }
        }

        // episodes: all <p> with streaming links
        val episodes = mutableListOf<Episode>()
        var currentSeason = ""
        var fallbackCounter = 1
        for (p in content.select("p")) {
            val links = p.select("a[href]").filter { isStreamingHref(it.attr("href")) }
            if (links.isEmpty()) continue
            val raw = p.text()
            if ("Fonte:" in raw) continue
            val labels = links.map { it.text().trim() } + links.map { hostLabel(it.attr("href")) }
            val segs = splitSegments(raw, labels)
            val segList = if (segs.isEmpty()) listOf(raw) else segs
            val dist = distribute(links, segList.size)
            for ((seg, segLinks) in segList.zip(dist)) {
                val ep = buildEpisode(seg, segLinks, labels, currentSeason.ifBlank { title }, fallbackCounter++)
                if (ep != null) episodes.add(ep)
            }
        }

        ContentItem(
            title = title,
            url = url,
            image = image,
            synopsis = synopsis,
            originalTitle = meta["originalTitle"] ?: "",
            country = meta["country"] ?: "",
            year = meta["year"] ?: "",
            status = meta["status"] ?: "",
            totalEpisodes = meta["totalEpisodes"] ?: "",
            availableEpisodes = meta["availableEpisodes"] ?: "",
            episodes = episodes
        )
    }

    // ---------- search ----------

    suspend fun search(query: String): List<ContentItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ContentItem>()
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val doc = getDoc("$BASE_URL/?s=$encoded")
            buildHomepageCache()
            val rawItems = mutableListOf<ContentItem>()
            for (article in doc.select("article")) {
                val h2 = article.selectFirst("h2") ?: continue
                val link = h2.selectFirst("a[href]") ?: continue
                val href = link.attr("href")
                val title = link.text().trim()
                if (title.isNotBlank() && href.startsWith(BASE_URL) &&
                    !href.contains("/?s=") && !href.contains("/category/") &&
                    !href.contains("/author/") && !href.contains("/tag/")
                ) {
                    var imageUrl = getCachedImage(href)
                    if (imageUrl.isBlank()) {
                        imageUrl = article.selectFirst("img")?.attr("src") ?: ""
                        if (imageUrl.isBlank()) {
                            imageUrl = article.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
                        }
                    }
                    rawItems.add(ContentItem(title = title, url = href, image = imageUrl))
                }
            }
            results.addAll(rawItems)
            val needsFetch = rawItems.filter { it.image.isBlank() }.take(20)
            if (needsFetch.isNotEmpty()) {
                runCatching {
                    coroutineScope {
                        needsFetch.chunked(5).map { chunk ->
                            chunk.map { item ->
                                async(Dispatchers.IO) {
                                    runCatching {
                                        val d = getDoc(item.url)
                                        val img = d.selectFirst("article .entry-content img")
                                            ?: d.selectFirst("article img")
                                            ?: d.selectFirst(".entry-content img")
                                        val url = img?.attr("src") ?: ""
                                        if (url.isNotBlank()) item to url else null
                                    }.getOrNull()
                                }
                            }.awaitAll().filterNotNull()
                        }.flatten()
                    }
                }.getOrDefault(emptyList()).forEach { (item, imgUrl) ->
                    val idx = results.indexOfFirst { it.url == item.url }
                    if (idx >= 0) results[idx] = results[idx].copy(image = imgUrl)
                }
            }
        }
        results
    }
}
