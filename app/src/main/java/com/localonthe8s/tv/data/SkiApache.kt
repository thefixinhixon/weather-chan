package com.localonthe8s.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** One Ski Apache lift with its reported status. */
data class LiftStatus(
    val name: String,
    val status: String,
    val open: Boolean
)

/** A resort activity (gondola, ziptour, rentals…) with its reported status. */
data class ActivityStatus(
    val name: String,
    val status: String
)

/** The live Ski Apache conditions digest for one report cycle. */
data class SkiReport(
    val updatedLabel: String,
    val past24: String,
    val past48: String,
    val baseDepth: String,
    val seasonTotal: String,
    val openTrails: String,
    val openLifts: String,
    val road: String,
    val lifts: List<LiftStatus>,
    val activities: List<ActivityStatus>
)

/**
 * Keyless reader for Ski Apache's live mountain conditions. The trail-status
 * page carries a static conditions bar (report time, 24/48h snow, base depth,
 * season total, open counts, activity statuses) plus a plain lifts table, and
 * BrownRice serves a fresh mountain-cam still at a fixed snapshot URL. No API
 * key, no account — just static HTML and a JPEG, same as the other feeds.
 */
object SkiApache {

    const val STATUS_URL = "https://skiapache.com/mountain-info/trail-status/"
    const val SNAPSHOT_URL = "https://player.brownrice.com/snapshot/apache"

    private val UPDATED = Regex("Last Updated:\\s*(.+?)\\s*Past 24 Hours:", RegexOption.IGNORE_CASE)
    private val PAST_24 = Regex("Past 24 Hours:\\s*(\\d+)\\s*\"", RegexOption.IGNORE_CASE)
    private val PAST_48 = Regex("Past 48 Hours:\\s*(\\d+)\\s*\"", RegexOption.IGNORE_CASE)
    private val BASE = Regex("Base Depth:\\s*(\\d+)\\s*\"", RegexOption.IGNORE_CASE)
    private val SEASON = Regex("Season Total:\\s*(\\d+)\\s*\"", RegexOption.IGNORE_CASE)
    private val TRAILS_OPEN = Regex("Trails:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val LIFTS_OPEN = Regex("Lifts:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val ACTIVITY =
        Regex("(ZipTour|Gondola|Mountain Bike|Hiking|Bike Rentals):\\s*([A-Za-z]+)", RegexOption.IGNORE_CASE)
    private val ROAD = Regex("Road Conditions\\s*([A-Za-z]+)", RegexOption.IGNORE_CASE)
    private val ROW = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL)

    private val TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("\\s+")

    suspend fun fetch(): SkiReport = withContext(Dispatchers.IO) {
        val html = httpGetText(STATUS_URL)
        val text = clean(html)

        // The conditions bar is printed twice on the page (desktop + mobile
        // header), so dedupe by activity name keeping the first reading.
        val activities = ACTIVITY.findAll(text).map { match ->
            ActivityStatus(
                name = match.groupValues[1].trim().uppercase(),
                status = match.groupValues[2].trim().uppercase()
            )
        }.distinctBy { it.name }.toList()

        SkiReport(
            updatedLabel = UPDATED.find(text)?.groupValues?.get(1)?.trim().orEmpty(),
            past24 = PAST_24.find(text)?.groupValues?.get(1).orEmpty(),
            past48 = PAST_48.find(text)?.groupValues?.get(1).orEmpty(),
            baseDepth = BASE.find(text)?.groupValues?.get(1).orEmpty(),
            seasonTotal = SEASON.find(text)?.groupValues?.get(1).orEmpty(),
            openTrails = TRAILS_OPEN.find(text)?.groupValues?.get(1).orEmpty(),
            openLifts = LIFTS_OPEN.find(text)?.groupValues?.get(1).orEmpty(),
            road = ROAD.find(text)?.groupValues?.get(1)?.trim().orEmpty(),
            lifts = parseLifts(html),
            activities = activities
        )
    }

    /**
     * The lifts table sits between its header and the trails section; each row
     * is name + status once the markup is stripped.
     */
    private fun parseLifts(html: String): List<LiftStatus> {
        val start = html.indexOf("Lift Name", ignoreCase = true)
        if (start < 0) return emptyList()
        val end = html.indexOf("Currently all trails", ignoreCase = true, startIndex = start)
            .let { if (it < 0) html.length else it }
        val section = html.substring(start, end)
        return ROW.findAll(section).mapNotNull { row ->
            val cells = CELL.findAll(row.groupValues[1]).map { clean(it.groupValues[1]) }.toList()
            if (cells.size < 2) return@mapNotNull null
            val name = cells.first()
            val status = cells.last()
            if (name.equals("Lift Name", ignoreCase = true)) return@mapNotNull null
            if (name.isBlank() || status.isBlank()) return@mapNotNull null
            LiftStatus(
                name = name.uppercase(),
                status = status.uppercase(),
                open = status.contains("open", ignoreCase = true)
            )
        }.toList()
    }

    private fun clean(raw: String): String {
        var text = TAG.replace(raw, " ")
        text = text.replace("&amp;", "&").replace("&nbsp;", " ").replace("&#038;", "&")
        return WHITESPACE.replace(text, " ").trim()
    }

    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html")
            setRequestProperty("User-Agent", EventsFeed.BROWSER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
