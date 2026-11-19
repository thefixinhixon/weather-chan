package com.localonthe8s.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Keyless reader for public Google Calendar .ics feeds, plus the bundled
 * `local-events.ics` base calendar that ships in assets. Paste the "Public
 * address in iCal format" (Calendar settings -> Integrate calendar) into the
 * lineup screen and those events merge with the bundled file and the
 * DiscoverRuidoso slideshow. No API key, no account — just static .ics.
 *
 * Google exports recurring events as one VEVENT with an RRULE (weekly Movie
 * Day, Thursday Bingo...) plus EXDATEs and RECURRENCE-ID overrides, so the
 * parser expands recurrences instead of showing them once.
 */
object GoogleCalendar {

    private const val HORIZON_DAYS = 180
    private const val ASSET_NAME = "local-events.ics"

    private var bundled: List<CommunityEvent>? = null

    /** The shipped base calendar; empty when the asset is missing or unreadable. */
    suspend fun loadBundled(context: Context): List<CommunityEvent> = withContext(Dispatchers.IO) {
        bundled?.let { return@withContext it }
        val parsed = runCatching {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            parse(text)
        }.getOrDefault(emptyList())
        bundled = parsed
        parsed
    }

    suspend fun fetch(icsUrl: String): List<CommunityEvent> = withContext(Dispatchers.IO) {
        if (icsUrl.isBlank()) return@withContext emptyList()
        parse(httpGetText(icsUrl.trim()))
    }

    private fun todayStart(): Date {
        val denver = TimeZone.getTimeZone("America/Denver")
        val cal = Calendar.getInstance(denver)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun parse(ics: String): List<CommunityEvent> {
        // Unfold RFC 5545 line continuations, then split into VEVENT blocks.
        val unfolded = ics.replace("\r\n ", "").replace("\n ", "")
        val raws = unfolded.split("BEGIN:VEVENT").drop(1).mapNotNull { block ->
            parseBlock(block.substringBefore("END:VEVENT"))
        }
        // Overrides (RECURRENCE-ID) attach to their base UID; standalone base
        // events expand on their own.
        val byUid = raws.groupBy { it.uid }
        val out = ArrayList<CommunityEvent>()
        byUid.forEach { (_, group) ->
            val base = group.firstOrNull { it.recurrenceId == null } ?: return@forEach
            out += expand(base, group.filter { it.recurrenceId != null })
        }
        return out
            .sortedWith(compareBy({ it.startsOn }, { it.timeLabel }, { it.title }))
            .distinctBy { it.id }
            .take(EventsFeed.MAX_EVENTS)
    }

    private data class RawEvent(
        val uid: String,
        val summary: String?,
        val location: String?,
        val description: String?,
        val startRaw: String,
        val endRaw: String?,
        val rrule: String?,
        val exdates: List<String>,
        val recurrenceId: String?
    )

    private fun parseBlock(body: String): RawEvent? {
        val params = mutableMapOf<String, String>()
        // Property params (TZID=..., VALUE=...) live before the colon; keep the
        // full left side so datetimes can be re-parsed with their zone.
        val full = mutableMapOf<String, String>()
        var key = ""
        val value = StringBuilder()
        var fullKey = ""
        val fullValue = StringBuilder()
        body.lineSequence().forEach { line ->
            val clean = line.trimEnd('\r')
            if (clean.isEmpty()) return@forEach
            val colon = clean.indexOf(':')
            val looksNew = colon > 0 &&
                clean.substringBefore(';').substringBefore(':').all { it.isUpperCase() || it == '-' }
            if (looksNew) {
                if (key.isNotEmpty()) {
                    params[key] = value.toString()
                    full[fullKey] = fullValue.toString()
                }
                val namePart = clean.substring(0, colon)
                key = namePart.substringBefore(';')
                fullKey = namePart
                value.clear()
                value.append(clean.substring(colon + 1))
                fullValue.clear()
                fullValue.append(clean.substring(colon + 1))
            } else if (key.isNotEmpty()) {
                value.append(clean)
                fullValue.append(clean)
            }
        }
        if (key.isNotEmpty()) {
            params[key] = value.toString()
            full[fullKey] = fullValue.toString()
        }
        val startEntry = full.entries.firstOrNull { it.key.startsWith("DTSTART") }
            ?: return null
        val endEntry = full.entries.firstOrNull { it.key.startsWith("DTEND") }
        // EXDATE can appear multiple times and hold comma-separated values.
        val exdates = full.entries
            .filter { it.key.startsWith("EXDATE") }
            .flatMap { (name, values) -> values.split(',').map { v -> "$name:$v" } }
        val recurrenceEntry = full.entries.firstOrNull { it.key.startsWith("RECURRENCE-ID") }
        return RawEvent(
            uid = params["UID"]?.trim().orEmpty().ifBlank { return null },
            summary = params["SUMMARY"]?.trim(),
            location = params["LOCATION"]?.trim(),
            description = params["DESCRIPTION"]?.trim(),
            startRaw = "${startEntry.key}:${startEntry.value}",
            endRaw = endEntry?.let { "${it.key}:${it.value}" },
            rrule = params["RRULE"]?.trim(),
            exdates = exdates,
            recurrenceId = recurrenceEntry?.let { "${it.key}:${it.value}" }
        )
    }

    private data class Moment(val date: Date, val tz: TimeZone, val allDay: Boolean)

    private fun expand(base: RawEvent, overrides: List<RawEvent>): List<CommunityEvent> {
        val start = parseMoment(base.startRaw) ?: return emptyList()
        val end = base.endRaw?.let { parseMoment(it) }
        val durationMillis = if (end != null) {
            (end.date.time - start.date.time).coerceAtLeast(0L)
        } else if (start.allDay) {
            24L * 60 * 60 * 1000
        } else {
            0L
        }
        val exdateMillis = base.exdates.mapNotNull { parseMoment(it)?.date?.time }.toSet()
        val overrideByMillis = overrides.mapNotNull { override ->
            val id = override.recurrenceId?.let { parseMoment(it) } ?: return@mapNotNull null
            id.date.time to override
        }.toMap()

        val rule = base.rrule?.let { parseRule(it) }
        val starts = if (rule != null && rule.freq == "WEEKLY") {
            expandWeekly(start, rule)
        } else {
            listOf(start.date.time)
        }
        val todayMillis = todayStart().time
        return starts.mapNotNull { occurrence ->
            if (exdateMillis.contains(occurrence)) return@mapNotNull null
            // Drop instances that already ended (keeps multi-day spans crossing today).
            if (occurrence + durationMillis < todayMillis && !start.allDay) return@mapNotNull null
            if (start.allDay && occurrence < todayMillis) return@mapNotNull null
            val effective = overrideByMillis[occurrence]
            val summary = (effective?.summary ?: base.summary)?.takeIf { !it.isNullOrBlank() }
                ?: return@mapNotNull null
            buildEvent(
                summary = summary,
                location = effective?.location ?: base.location,
                description = effective?.description ?: base.description,
                uid = base.uid,
                startMillis = occurrence,
                durationMillis = durationMillis,
                tz = start.tz,
                allDay = start.allDay
            )
        }
    }

    private data class Rule(
        val freq: String,
        val byDay: Set<Int>?,
        val interval: Int,
        val until: Date?,
        val count: Int?
    )

    private fun parseRule(raw: String): Rule? {
        val map = raw.split(';').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0].trim().uppercase(Locale.US) to parts[1].trim() else null
        }.toMap()
        val freq = map["FREQ"]?.uppercase(Locale.US) ?: return null
        val dayMap = mapOf(
            "SU" to Calendar.SUNDAY, "MO" to Calendar.MONDAY, "TU" to Calendar.TUESDAY,
            "WE" to Calendar.WEDNESDAY, "TH" to Calendar.THURSDAY,
            "FR" to Calendar.FRIDAY, "SA" to Calendar.SATURDAY
        )
        val byDay = map["BYDAY"]?.split(',')
            ?.mapNotNull { dayMap[it.trim().uppercase(Locale.US)] }
            ?.toSet()
        val until = map["UNTIL"]?.let { parseMoment("UNTIL:$it")?.date }
        return Rule(
            freq = freq,
            byDay = byDay?.takeIf { it.isNotEmpty() },
            interval = map["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            until = until,
            count = map["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
        )
    }

    private fun expandWeekly(start: Moment, rule: Rule): List<Long> {
        val days = rule.byDay ?: setOf(
            Calendar.getInstance(start.tz).apply { time = start.date }.get(Calendar.DAY_OF_WEEK)
        )
        // Weeks are counted from the WKST week holding DTSTART (RFC default Monday).
        val weekStart = Calendar.getInstance(start.tz).apply {
            time = start.date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.timeInMillis
        val horizon = start.date.time + HORIZON_DAYS * 24L * 60 * 60 * 1000
        val out = ArrayList<Long>()
        var dayOffset = 0
        val cursor = Calendar.getInstance(start.tz)
        while (out.size < (rule.count ?: Int.MAX_VALUE) && dayOffset <= HORIZON_DAYS + 7) {
            cursor.timeInMillis = start.date.time + dayOffset * 24L * 60 * 60 * 1000
            // Normalize through the tz calendar so DST transitions stay on the wall time.
            val normalized = Calendar.getInstance(start.tz).apply {
                timeInMillis = cursor.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val occurrence = Calendar.getInstance(start.tz).apply {
                timeInMillis = normalized.timeInMillis
                val wall = Calendar.getInstance(start.tz).apply { time = start.date }
                set(Calendar.HOUR_OF_DAY, wall.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, wall.get(Calendar.MINUTE))
                set(Calendar.SECOND, wall.get(Calendar.SECOND))
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (occurrence >= start.date.time && occurrence <= horizon) {
                if (rule.until != null && Date(occurrence).after(rule.until)) break
                val weekIndex = ((normalized.timeInMillis - weekStart) / (7L * 24 * 60 * 60 * 1000)).toInt()
                if (normalized.get(Calendar.DAY_OF_WEEK) in days && weekIndex % rule.interval == 0) {
                    out += occurrence
                }
            }
            if (rule.until != null && Date(occurrence).after(rule.until)) break
            dayOffset++
        }
        return out
    }

    private fun buildEvent(
        summary: String,
        location: String?,
        description: String?,
        uid: String,
        startMillis: Long,
        durationMillis: Long,
        tz: TimeZone,
        allDay: Boolean
    ): CommunityEvent {
        val denver = TimeZone.getTimeZone("America/Denver")
        val start = Date(startMillis)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = denver }
        val day = dayFmt.format(start)
        val monthFmt = SimpleDateFormat("MMM", Locale.US).apply { timeZone = denver }
        val dayNumFmt = SimpleDateFormat("dd", Locale.US).apply { timeZone = denver }
        val dateFmt = SimpleDateFormat("EEEE, MMM d", Locale.US).apply { timeZone = denver }
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = denver }
        val endDate = Date(startMillis + durationMillis)
        val timeLabel = when {
            allDay -> "ALL DAY"
            durationMillis > 0 &&
                dayFmt.format(endDate) == day -> "${timeFmt.format(start)} - ${timeFmt.format(endDate)}"
            else -> timeFmt.format(start)
        }.uppercase(Locale.US)

        val cleanLocation = cleanText(location)
            ?.takeIf { it.isNotBlank() } ?: "RUIDOSO, NM"
        val cleanDescription = cleanText(description).orEmpty().take(420)
        return CommunityEvent(
            id = "gcal-$uid-$day",
            title = cleanText(summary).orEmpty(),
            monthLabel = monthFmt.format(start).uppercase(Locale.US).take(3),
            dayLabel = dayNumFmt.format(start).trimStart('0').ifBlank { "1" },
            dateLabel = dateFmt.format(start).uppercase(Locale.US),
            timeLabel = timeLabel,
            venue = cleanLocation,
            category = "MY CALENDAR",
            description = cleanDescription.ifBlank { "ADDED FROM THE LOCAL CALENDAR • ALL TIMES MOUNTAIN" },
            artworkUrl = null,
            startsOn = day
        )
    }

    private fun parseMoment(raw: String): Moment? {
        val zone = if (raw.contains("TZID=")) {
            raw.substringAfter("TZID=").substringBefore(':').substringBefore(';')
        } else null
        val value = raw.substringAfterLast(':')
        val allDay = raw.contains("VALUE=DATE") || (value.length == 8 && value.all { it.isDigit() })
        val patterns = listOf("yyyyMMdd'T'HHmmss", "yyyyMMdd")
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.isLenient = false
                fmt.timeZone = when {
                    value.endsWith('Z') -> TimeZone.getTimeZone("UTC")
                    zone != null -> TimeZone.getTimeZone(zone)
                    pattern == "yyyyMMdd" -> TimeZone.getTimeZone("America/Denver")
                    else -> TimeZone.getTimeZone("America/Denver")
                }
                val parsed = fmt.parse(if (value.endsWith('Z')) value else value.trimEnd('Z'))
                    ?: continue
                return Moment(parsed, fmt.timeZone, allDay)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private val TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("\\s+")

    private fun cleanText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw
            .replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        text = TAG.replace(text, " ")
        return text.replace(WHITESPACE, " ").trim().ifBlank { null }
    }

    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/calendar,text/plain,*/*")
            setRequestProperty("User-Agent", EventsFeed.BROWSER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw java.io.IOException("Calendar responded $status")
            return connection.inputStream.readBytes().toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
