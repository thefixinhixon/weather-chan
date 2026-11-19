package com.localonthe8s.tv.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One reflectivity image in the loop, plus how old it is. */
data class RadarFrameImage(val image: Bitmap, val ageLabel: String)

/**
 * A loop of recent NEXRAD frames for one market, ordered oldest first so the panel
 * can play it forward the way the cable radar loop did.
 */
data class RadarLoop(
    val frames: List<RadarFrameImage>,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val asOfLabel: String,
    val echoesInRange: Boolean
) {
    fun xFraction(longitude: Double): Float = ((longitude - west) / (east - west)).toFloat()

    fun yFraction(latitude: Double): Float = ((north - latitude) / (north - south)).toFloat()
}

/**
 * Real NEXRAD base reflectivity as an animation, from the Iowa State mesonet mirror
 * of NOAA's mosaic (keyless, public domain). The service exposes the recent past as
 * five-minute offset layers, so the loop is one GetMap per frame — the newest layer
 * plus m05m … m35m — all for the market's exact lat/lon box.
 */
object RadarFeed {

    private const val WMS_ENDPOINT =
        "https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0r.cgi"

    private const val BASE_LAYER = "nexrad-n0r"

    /** 16:9 to match the panel, so the mosaic is never stretched or cropped. */
    private const val IMAGE_WIDTH = 1280
    private const val IMAGE_HEIGHT = 720

    /**
     * Degrees across the map (~210 mi E-W, ~140 mi N-S at 33N): wide enough to
     * hold all of Lincoln County plus the Sacramento mountains and the Tularosa
     * Basin, instead of a tight crop on one town.
     */
    private const val LON_SPAN = 3.6

    /** Lincoln County centroid — used as the map center for county towns. */
    private const val COUNTY_LAT = 33.62
    private const val COUNTY_LON = -105.65

    private const val FRAME_STEP_MINUTES = 5
    private const val FRAME_COUNT = 8

    private data class BoundingBox(
        val west: Double,
        val south: Double,
        val east: Double,
        val north: Double
    )

    suspend fun load(place: Place): RadarLoop = withContext(Dispatchers.IO) {
        val latSpan = LON_SPAN * IMAGE_HEIGHT / IMAGE_WIDTH
        // County towns share one county-wide view so Lincoln County reads as a
        // whole; anywhere else stays centered on the tuned market.
        val centerLat = if (isCountyTown(place)) COUNTY_LAT else place.latitude
        val centerLon = if (isCountyTown(place)) COUNTY_LON else place.longitude
        val box = BoundingBox(
            west = centerLon - LON_SPAN / 2,
            east = centerLon + LON_SPAN / 2,
            south = centerLat - latSpan / 2,
            north = centerLat + latSpan / 2
        )

        val gate = Semaphore(4)
        val fetched = coroutineScope {
            (0 until FRAME_COUNT).map { index ->
                async { index to gate.withPermit { fetchFrame(index, box) } }
            }.awaitAll()
        }.mapNotNull { (index, frame) -> frame?.let { index to it } }

        if (fetched.isEmpty()) throw IOException("Radar frames unreachable")

        // index 0 is the newest frame, so descending index is oldest first.
        val ordered = fetched.sortedByDescending { it.first }.map { it.second }
        val newest = fetched.first { it.first == 0 }.second
            ?: ordered.last()

        RadarLoop(
            frames = ordered,
            west = box.west,
            south = box.south,
            east = box.east,
            north = box.north,
            asOfLabel = asOfLabel(place.timezone),
            echoesInRange = newest.image.opaqueFraction() > 0.001f
        )
    }

    private fun fetchFrame(index: Int, box: BoundingBox): RadarFrameImage? = runCatching {
        val layer = if (index == 0) {
            BASE_LAYER
        } else {
            String.format(Locale.US, "%s-m%02dm", BASE_LAYER, index * FRAME_STEP_MINUTES)
        }
        val bytes = requestBytes(buildUrl(layer, box))
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            // Screen density scaling would break the lat/lon mapping of the image.
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Radar frame did not decode")
        RadarFrameImage(bitmap, ageLabel(index * FRAME_STEP_MINUTES))
    }.getOrNull()

    private fun buildUrl(layer: String, box: BoundingBox): String = buildString {
        append(WMS_ENDPOINT)
        append("?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap")
        append("&LAYERS=").append(layer)
        append("&STYLES=&FORMAT=image/png&TRANSPARENT=true")
        append("&SRS=EPSG:4326")
        append("&BBOX=")
        append(coordinate(box.west)).append(',')
        append(coordinate(box.south)).append(',')
        append(coordinate(box.east)).append(',')
        append(coordinate(box.north))
        append("&WIDTH=").append(IMAGE_WIDTH)
        append("&HEIGHT=").append(IMAGE_HEIGHT)
    }

    /** Share of pixels carrying reflectivity; a clear frame is almost fully transparent. */
    private fun Bitmap.opaqueFraction(): Float {
        val stride = 4
        var painted = 0
        var total = 0
        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                if (Color.alpha(getPixel(x, y)) > 24) painted++
                total++
                y += stride
            }
            x += stride
        }
        return if (total == 0) 0f else painted.toFloat() / total
    }

    private fun requestBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LocalOnThe8s/1.0 (Android TV)")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Radar feed responded $status")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /** Ruidoso, Alto, Capitan and Carrizozo all sit inside Lincoln County. */
    private fun isCountyTown(place: Place): Boolean {
        val name = place.name.lowercase(Locale.US)
        if (name in setOf("ruidoso", "alto", "capitan", "carrizozo",
                "ruidoso downs", "lincoln", "hondo", "nogal")) return true
        val dLat = kotlin.math.abs(place.latitude - COUNTY_LAT)
        val dLon = kotlin.math.abs(place.longitude - COUNTY_LON)
        return dLat < 0.6 && dLon < 0.9
    }

    private fun coordinate(value: Double): String = String.format(Locale.US, "%.4f", value)

    private fun ageLabel(minutes: Int): String =
        if (minutes == 0) "NOW" else "-$minutes MIN"

    private fun asOfLabel(timezone: String): String =
        SimpleDateFormat("h:mm a", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(timezone) }
            .format(Date())
            .uppercase(Locale.US)
}
