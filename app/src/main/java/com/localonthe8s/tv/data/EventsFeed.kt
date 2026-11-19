package com.localonthe8s.tv.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One listing from the DiscoverRuidoso community calendar. */
data class CommunityEvent(
    val id: String,
    val title: String,
    val monthLabel: String,
    val dayLabel: String,
    val dateLabel: String,
    val timeLabel: String,
    val venue: String,
    val category: String?,
    val description: String,
    val artworkUrl: String?,
    val startsOn: String
)

/**
 * Keyless reader for the DiscoverRuidoso.com events calendar. The site is a plain
 * Squarespace event list: every listing is an `<article class="eventlist-event">`
 * carrying a date tag, a title link, a venue line, an excerpt and a thumbnail, so
 * a handful of anchored expressions is enough and no extra dependency is needed.
 */
object EventsFeed {

    const val CALENDAR_URL = "https://www.discoverruidoso.com/calendar-of-events"
    const val MAX_EVENTS = 60

    private val ARTICLE = Regex("""<article class="eventlist-event[^"]*">""")
    private val IMAGE = Regex("""data-src="([^"]+)"""")
    private val TITLE = Regex("""class="eventlist-title-link"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val SLUG = Regex("""href="(/calendar-of-events/[^"?#]+)"""")
    private val ISO_DATE = Regex("""class="event-date" datetime="([^"]+)"""")
    private val DATE_LABEL = Regex("""class="event-date"[^>]*>(.*?)</time>""", RegexOption.DOT_MATCHES_ALL)
    private val MONTH = Regex("""datetag-startdate--month">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val DAY = Regex("""datetag-startdate--day">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val START_TIME = Regex("""class="event-time-12hr-start"[^>]*>(.*?)</time>""", RegexOption.DOT_MATCHES_ALL)
    private val END_TIME = Regex("""class="event-time-12hr-end"[^>]*>(.*?)</time>""", RegexOption.DOT_MATCHES_ALL)
    // The venue class sits mid-attribute, not at the front of it, so anchor loosely.
    private val VENUE = Regex("""class="[^"]*eventlist-meta-address[^"]*"[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
    private val CATEGORIES = Regex("""class="eventlist-cats">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val EXCERPT = Regex("""class="eventlist-excerpt">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    private val TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("\\s+")
    private val NUMERIC_ENTITY = Regex("&#(\\d+);")

    suspend fun fetch(): List<CommunityEvent> = withContext(Dispatchers.IO) {
        val html = httpGetText(CALENDAR_URL)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val starts = ARTICLE.findAll(html).map { it.range.first }.toList()

        val events = starts.mapIndexedNotNull { index, start ->
            val close = html.indexOf("</article>", start).let { if (it < 0) html.length else it + 10 }
            val next = if (index + 1 < starts.size) starts[index + 1] else html.length
            val block = html.substring(start, minOf(close, next))

            val title = clean(TITLE.first(block)) ?: return@mapIndexedNotNull null
            val startsOn = ISO_DATE.first(block) ?: return@mapIndexedNotNull null
            if (startsOn < today) return@mapIndexedNotNull null

            val startTime = clean(START_TIME.first(block))
            val endTime = clean(END_TIME.first(block))
            CommunityEvent(
                id = SLUG.first(block) ?: title,
                title = title,
                monthLabel = clean(MONTH.first(block)).orEmpty().take(3).uppercase(Locale.US),
                dayLabel = clean(DAY.first(block)).orEmpty(),
                dateLabel = clean(DATE_LABEL.first(block)).orEmpty().uppercase(Locale.US),
                timeLabel = when {
                    startTime == null -> "ALL DAY"
                    endTime == null -> startTime
                    else -> "$startTime - $endTime"
                },
                venue = clean(VENUE.first(block))
                    ?.replace("(map)", "", ignoreCase = true)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "RUIDOSO, NM",
                category = clean(CATEGORIES.first(block))
                    ?.split(',')
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                description = clean(EXCERPT.first(block)).orEmpty().take(420),
                artworkUrl = IMAGE.first(block),
                startsOn = startsOn
            )
        }

        events
            .sortedBy { it.startsOn }
            .distinctBy { it.id }
            .take(MAX_EVENTS)
    }

    private fun Regex.first(source: String): String? =
        find(source)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    private fun clean(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val flattened = TAG.replace(unescape(raw), " ")
        return flattened.replace(WHITESPACE, " ").trim().ifBlank { null }
    }

    private fun unescape(raw: String): String {
        var text = raw
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&ldquo;", "\"").replace("&rdquo;", "\"")
            .replace("&lsquo;", "'").replace("&rsquo;", "'")
            .replace("&ndash;", "-").replace("&mdash;", "-")
            .replace("&hellip;", "...")
        text = NUMERIC_ENTITY.replace(text) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        return text
    }

    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html")
            setRequestProperty("User-Agent", BROWSER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw java.io.IOException("Calendar responded $status")
            return connection.inputStream.readBytes().toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    internal const val BROWSER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; Google TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36 LocalOnThe8s/1.0"
}

/**
 * Event thumbnails off the Squarespace CDN, decoded once and kept for the next few
 * slides. The full-size originals are several megapixels, so the CDN is asked for a
 * screen-sized render instead.
 */
object EventArtwork {

    private const val MAX_CACHED = 6
    private const val RENDER_WIDTH = 1500

    private val cache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > MAX_CACHED
    }

    fun cached(url: String): Bitmap? = synchronized(cache) { cache[url] }

    suspend fun load(url: String): Bitmap? {
        cached(url)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(render(url)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    setRequestProperty("Accept", "image/*")
                    setRequestProperty("User-Agent", EventsFeed.BROWSER_AGENT)
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        } ?: return null
        synchronized(cache) { cache[url] = bitmap }
        return bitmap
    }

    private fun render(url: String): String =
        if (url.contains("format=")) url else "$url?format=${RENDER_WIDTH}w"
}
