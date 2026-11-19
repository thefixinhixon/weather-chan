package com.localonthe8s.tv.data

import android.content.Context
import com.localonthe8s.tv.audio.RadioStation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the market playlist, the tuned market, the station, the music
 * preference and the optional Google Calendar link. The playlist is the user's
 * own channel lineup: it starts as the built-in presets and then lives in
 * SharedPreferences so adds and removals stick.
 */
class LocationStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var place: Place?
        get() {
            val name = prefs.getString(KEY_NAME, null) ?: return null
            return Place(
                name = name,
                region = prefs.getString(KEY_REGION, "").orEmpty(),
                latitude = coordinate(KEY_LAT_BITS, KEY_LAT),
                longitude = coordinate(KEY_LON_BITS, KEY_LON),
                timezone = prefs.getString(KEY_TZ, "UTC") ?: "UTC"
            )
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_NAME)
                editor.remove(KEY_REGION)
                editor.remove(KEY_LAT)
                editor.remove(KEY_LON)
                editor.remove(KEY_LAT_BITS)
                editor.remove(KEY_LON_BITS)
                editor.remove(KEY_TZ)
            } else {
                editor.putString(KEY_NAME, value.name)
                editor.putString(KEY_REGION, value.region)
                // Exact doubles: the tuned market has to compare equal to its lineup
                // entry, so it cannot go through a float and lose the last digits.
                editor.putLong(KEY_LAT_BITS, value.latitude.toRawBits())
                editor.putLong(KEY_LON_BITS, value.longitude.toRawBits())
                editor.putFloat(KEY_LAT, value.latitude.toFloat())
                editor.putFloat(KEY_LON, value.longitude.toFloat())
                editor.putString(KEY_TZ, value.timezone)
            }
            editor.apply()
        }

    /** The editable channel lineup; falls back to the presets until it is edited. */
    var playlist: List<Place>
        get() = prefs.getString(KEY_PLAYLIST, null)?.let { decode(it) } ?: PRESETS
        set(value) {
            prefs.edit().putString(KEY_PLAYLIST, encode(value)).apply()
        }

    var musicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MUSIC, value).apply()
        }

    var station: RadioStation
        get() = RadioStation.fromName(prefs.getString(KEY_STATION, null))
        set(value) {
            prefs.edit().putString(KEY_STATION, value.name).apply()
        }

    /** Public Google Calendar .ics URL merged into the community calendar (empty = off). */
    var calendarUrl: String
        get() = prefs.getString(KEY_CALENDAR_URL, "").orEmpty().trim()
        set(value) {
            prefs.edit().putString(KEY_CALENDAR_URL, value.trim()).apply()
        }

    /** True when the viewer has never edited the lineup (still on the seed presets). */
    fun isDefaultPlaylist(): Boolean = !prefs.contains(KEY_PLAYLIST)

    /** Reads an exact coordinate, falling back to the float-only key of older builds. */
    private fun coordinate(bitsKey: String, floatKey: String): Double =
        if (prefs.contains(bitsKey)) {
            java.lang.Double.longBitsToDouble(prefs.getLong(bitsKey, 0L))
        } else {
            prefs.getFloat(floatKey, 0f).toDouble()
        }

    private fun encode(list: List<Place>): String {
        val array = JSONArray()
        list.forEach { place ->
            array.put(
                JSONObject()
                    .put("name", place.name)
                    .put("region", place.region)
                    .put("lat", place.latitude)
                    .put("lon", place.longitude)
                    .put("tz", place.timezone)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<Place>? = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name")
            if (name.isBlank()) return@mapNotNull null
            Place(
                name = name,
                region = item.optString("region"),
                latitude = item.optDouble("lat", 0.0),
                longitude = item.optDouble("lon", 0.0),
                timezone = item.optString("tz", "UTC").ifBlank { "UTC" }
            )
        }.ifEmpty { null }
    }.getOrNull()

    companion object {
        private const val PREFS = "local_on_the_8s"
        private const val KEY_NAME = "place_name"
        private const val KEY_REGION = "place_region"
        private const val KEY_LAT = "place_lat"
        private const val KEY_LON = "place_lon"
        private const val KEY_LAT_BITS = "place_lat_bits"
        private const val KEY_LON_BITS = "place_lon_bits"
        private const val KEY_TZ = "place_tz"
        private const val KEY_MUSIC = "music_enabled"
        private const val KEY_PLAYLIST = "market_playlist"
        private const val KEY_STATION = "radio_station"
        private const val KEY_CALENDAR_URL = "calendar_ics_url"

        /** Seed lineup: the Ruidoso area + the wider region. Used until the viewer edits it. */
        val PRESETS: List<Place> = listOf(
            Place("Ruidoso", "New Mexico, US", 33.3314, -105.2855, "America/Denver"),
            Place("Alto", "New Mexico, US", 33.3906, -105.6775, "America/Denver"),
            Place("Capitan", "New Mexico, US", 33.5456, -105.5767, "America/Denver"),
            Place("Carrizozo", "New Mexico, US", 33.6420, -105.8770, "America/Denver"),
            Place("Alamogordo", "New Mexico, US", 32.8995, -106.3075, "America/Denver"),
            Place("Roswell", "New Mexico, US", 33.3943, -104.5230, "America/Denver"),
            Place("El Paso", "Texas, US", 31.7619, -106.4850, "America/Denver"),
            Place("Albuquerque", "New Mexico, US", 35.0844, -106.6504, "America/Denver")
        )

        fun indexOf(lineup: List<Place>, place: Place?): Int {
            if (place == null) return -1
            return lineup.indexOfFirst { sameMarket(it, place) }
        }

        /**
         * Same market, not same object: the tuned market is rebuilt from storage on
         * every launch, so coordinates are compared with a tolerance rather than
         * exactly. Without this a ZIP market never matches its own lineup row and
         * the lineup shows no ON AIR badge.
         */
        fun sameMarket(a: Place?, b: Place?): Boolean {
            if (a == null || b == null) return false
            return a.name.equals(b.name, ignoreCase = true) &&
                a.region.equals(b.region, ignoreCase = true) &&
                kotlin.math.abs(a.latitude - b.latitude) < 1e-4 &&
                kotlin.math.abs(a.longitude - b.longitude) < 1e-4
        }

        /** Next/previous market in the lineup, wrapping at both ends. */
        fun cycle(lineup: List<Place>, place: Place?, delta: Int): Place? {
            if (lineup.isEmpty()) return place
            val index = indexOf(lineup, place)
            if (index < 0) return lineup.first()
            val wrapped = ((index + delta) % lineup.size + lineup.size) % lineup.size
            return lineup[wrapped]
        }
    }
}
