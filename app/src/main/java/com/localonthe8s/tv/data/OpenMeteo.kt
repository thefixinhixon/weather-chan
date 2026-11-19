package com.localonthe8s.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Keyless Open-Meteo client. Everything here runs on [Dispatchers.IO] and is
 * defensive: a malformed feed throws, the caller keeps the last good snapshot.
 */
object OpenMeteo {

    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

    private val COMPASS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    suspend fun fetch(place: Place): WeatherSnapshot = withContext(Dispatchers.IO) {
        val body = httpGet(forecastUrl(place))
        val root = JSONObject(body)

        val current = root.optJSONObject("current")
            ?: throw IOException("Feed returned no current conditions")
        val hourly = root.optJSONObject("hourly")
        val daily = root.optJSONObject("daily")
        val observedRaw = current.optString("time", "")

        val hourTimes = stringList(hourly?.optJSONArray("time"))
        val hourKey = observedRaw.take(13)
        val firstHour = hourTimes.indexOfFirst { it.take(13) >= hourKey }.let { if (it < 0) 0 else it }
        val hourlyTemps = doubleList(hourly?.optJSONArray("temperature_2m"))
        val hourlyPrecip = intList(hourly?.optJSONArray("precipitation_probability"))
        val hourlyCodes = intList(hourly?.optJSONArray("weather_code"))

        val hourlyPoints = (firstHour until minOf(firstHour + 12, hourTimes.size)).map { i ->
            HourlyPoint(
                label = clockLabel(hourTimes[i], "h a"),
                tempF = hourlyTemps.getOrNull(i)?.roundToInt() ?: 0,
                precipChance = hourlyPrecip.getOrNull(i) ?: 0,
                code = hourlyCodes.getOrNull(i) ?: 0
            )
        }

        val dayTimes = stringList(daily?.optJSONArray("time"))
        val highs = doubleList(daily?.optJSONArray("temperature_2m_max"))
        val lows = doubleList(daily?.optJSONArray("temperature_2m_min"))
        val dayPrecip = intList(daily?.optJSONArray("precipitation_probability_max"))
        val dayCodes = intList(daily?.optJSONArray("weather_code"))
        val sunrises = stringList(daily?.optJSONArray("sunrise"))
        val sunsets = stringList(daily?.optJSONArray("sunset"))

        val dailyPoints = dayTimes.indices.map { i ->
            DailyPoint(
                label = dayLabel(dayTimes[i]),
                highF = highs.getOrNull(i)?.roundToInt() ?: 0,
                lowF = lows.getOrNull(i)?.roundToInt() ?: 0,
                precipChance = dayPrecip.getOrNull(i) ?: 0,
                code = dayCodes.getOrNull(i) ?: 0
            )
        }

        val sunriseRaw = sunrises.firstOrNull().orEmpty()
        val sunsetRaw = sunsets.firstOrNull().orEmpty()

        WeatherSnapshot(
            place = place,
            current = CurrentConditions(
                tempF = current.optDouble("temperature_2m", 0.0).roundToInt(),
                feelsLikeF = current.optDouble("apparent_temperature", 0.0).roundToInt(),
                humidity = current.optInt("relative_humidity_2m", 0),
                windMph = current.optDouble("wind_speed_10m", 0.0).roundToInt(),
                windDir = compass(current.optDouble("wind_direction_10m", 0.0)),
                pressureInHg = formatInHg(current.optDouble("pressure_msl", 0.0)),
                cloudCover = current.optInt("cloud_cover", 0),
                code = current.optInt("weather_code", 0),
                isDay = current.optInt("is_day", 1) == 1,
                observedAtLabel = clockLabel(observedRaw, "h:mm a")
            ),
            hourly = hourlyPoints,
            daily = dailyPoints,
            observations = runCatching { regional(place) }.getOrDefault(emptyList()),
            sunrise = clockLabel(sunriseRaw, "h:mm a"),
            sunset = clockLabel(sunsetRaw, "h:mm a"),
            daylight = daylightLabel(sunriseRaw, sunsetRaw),
            timezoneAbbrev = root.optString("timezone_abbreviation", "LOCAL"),
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    /** IANA zone for a coordinate, so a ZIP market's clock matches its own time. */
    suspend fun timezoneAt(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        val body = httpGet(
            "$FORECAST_URL?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m&timezone=auto"
        )
        JSONObject(body).optString("timezone").ifBlank { "UTC" }
    }

    private fun forecastUrl(place: Place): String = buildString {
        append(FORECAST_URL)
        append("?latitude=").append(place.latitude)
        append("&longitude=").append(place.longitude)
        append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,")
        append("cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,weather_code")
        append("&hourly=temperature_2m,precipitation_probability,weather_code")
        append("&daily=weather_code,temperature_2m_max,temperature_2m_min,")
        append("precipitation_probability_max,sunrise,sunset")
        append("&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch")
        append("&timezone=auto&forecast_days=7")
    }

    /**
     * One request for four points 35 miles out in each cardinal direction, so the
     * regional panel is real data rather than invented city names.
     */
    private fun regional(place: Place): List<Observation> {
        val offsets = listOf(
            Triple("35 MI N", 0.5, 0.0),
            Triple("35 MI E", 0.0, 0.62),
            Triple("35 MI S", -0.5, 0.0),
            Triple("35 MI W", 0.0, -0.62)
        )
        val lats = offsets.joinToString(",") { String.format(Locale.US, "%.4f", place.latitude + it.second) }
        val lons = offsets.joinToString(",") { String.format(Locale.US, "%.4f", place.longitude + it.third) }
        val body = httpGet(
            "$FORECAST_URL?latitude=$lats&longitude=$lons" +
                "&current=temperature_2m,weather_code&temperature_unit=fahrenheit&timezone=auto"
        )
        val array = JSONArray(body)

        return offsets.mapIndexedNotNull { index, offset ->
            val point = array.optJSONObject(index) ?: return@mapIndexedNotNull null
            val conditions = point.optJSONObject("current") ?: return@mapIndexedNotNull null
            Observation(
                label = offset.first,
                tempF = conditions.optDouble("temperature_2m", 0.0).roundToInt(),
                code = conditions.optInt("weather_code", 0)
            )
        }
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LocalOnThe8s/1.0 (Android TV)")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                // Open-Meteo explains refusals in the body; keep it so the on-screen
                // SIGNAL LOST panel can say something more useful than a bare code.
                val detail = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                    ?.trim()
                    ?.replace(Regex("\\s+"), " ")
                    ?.take(160)
                throw IOException(
                    if (detail.isNullOrBlank()) "Feed responded $status"
                    else "Feed responded $status — $detail"
                )
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index -> array.optString(index, "") }
    }

    private fun doubleList(array: JSONArray?): List<Double?> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            if (array.isNull(index)) null else array.optDouble(index, 0.0)
        }
    }

    private fun intList(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index -> array.optInt(index, 0) }
    }

    private fun compass(degrees: Double): String {
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return COMPASS[(normalized / 22.5).roundToInt() % 16]
    }

    private fun formatInHg(hPa: Double): String =
        String.format(Locale.US, "%.2f", hPa * 0.02953)

    private fun parseIso(raw: String): Date? {
        if (raw.isBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(raw)
        }.getOrNull()
    }

    // Times arrive as the market's wall clock; parse and format them in UTC so the
    // display never shifts by the device's own offset.
    private fun clockLabel(raw: String, pattern: String): String {
        val date = parseIso(raw) ?: return "--"
        return SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date).uppercase(Locale.US)
    }

    private fun dayLabel(raw: String): String {
        val date = parseIso("${raw}T12:00") ?: return "---"
        val zone = TimeZone.getTimeZone("UTC")
        val day = SimpleDateFormat("EEE", Locale.US).apply { timeZone = zone }
            .format(date).uppercase(Locale.US)
        val stamp = SimpleDateFormat("M/d", Locale.US).apply { timeZone = zone }.format(date)
        return "$day $stamp"
    }

    private fun daylightLabel(sunriseRaw: String, sunsetRaw: String): String {
        val sunrise = parseIso(sunriseRaw) ?: return "--"
        val sunset = parseIso(sunsetRaw) ?: return "--"
        val minutes = ((sunset.time - sunrise.time) / 60_000L).coerceAtLeast(0L)
        return "${minutes / 60}H ${minutes % 60}M"
    }
}
