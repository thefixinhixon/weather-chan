package com.localonthe8s.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a US ZIP code into a market using two keyless services: zippopotam.us for
 * the place, state and coordinates, then Open-Meteo for the IANA timezone so the
 * broadcast clock matches the market.
 */
object ZipLookup {

    suspend fun resolve(zip: String): Place = withContext(Dispatchers.IO) {
        val trimmed = zip.trim()
        if (trimmed.length != 5 || trimmed.any { !it.isDigit() }) {
            throw IOException("Enter a five-digit ZIP")
        }

        val root = JSONObject(request("https://api.zippopotam.us/us/$trimmed"))
        val match = root.optJSONArray("places")?.optJSONObject(0)
            ?: throw IOException("ZIP $trimmed has no place on file")

        val city = match.optString("place name").ifBlank { trimmed }
        val state = match.optString("state abbreviation").ifBlank { "US" }
        val latitude = match.optDouble("latitude", Double.NaN)
        val longitude = match.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) {
            throw IOException("ZIP $trimmed has no coordinates")
        }

        Place(
            name = city,
            region = "$state $trimmed",
            latitude = latitude,
            longitude = longitude,
            timezone = OpenMeteo.timezoneAt(latitude, longitude)
        )
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LocalOnThe8s/1.0 (Android TV)")
        }
        try {
            val status = connection.responseCode
            if (status == 404) throw IOException("No US ZIP code with that number")
            if (status !in 200..299) throw IOException("ZIP lookup responded $status")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
