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
    /** Stitched terrain underlay; null when the tile service is unreachable. */
    val base: Bitmap?,
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

    /**
     * Esri's World Topo basemap is keyless and public: towns, highways, water
     * and terrain shading that keep the map readable on clear days when there
     * are no echoes to look at.
     */
    private const val TOPO_ENDPOINT =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile"
    private const val HILLSHADE_ENDPOINT =
        "https://server.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile"
    private const val TOPO_ZOOM = 9
    private const val TILE_PX = 256

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

        val base = runCatching { fetchTopoBase(box) }.getOrNull()

        RadarLoop(
            frames = ordered,
            base = base,
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

    /**
     * Builds the underlay: the topo basemap tinted to the channel's night
     * palette, then hillshade multiplied over it so the Sacramento range and
     * the Tularosa Basin read as terrain, not flat fill. Cropped to the exact
     * bounds so it lines up with the echo frames pixel for pixel.
     */
    private suspend fun fetchTopoBase(box: BoundingBox): Bitmap? {
        val z = TOPO_ZOOM
        val x0 = lonToTileX(box.west, z).toInt()
        val x1 = lonToTileX(box.east, z).toInt()
        val y0 = latToTileY(box.north, z).toInt()
        val y1 = latToTileY(box.south, z).toInt()
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > 96) return null

        val topo = stitchTiles(TOPO_ENDPOINT, z, x0, x1, y0, y1) ?: return null

        // Desaturate and pull the daylight basemap down to a slate-blue night
        // map: terrain shading keeps its detail, while the basemap's own dark
        // labels stay dark enough to read against the ground.
        val night = Bitmap.createBitmap(topo.width, topo.height, Bitmap.Config.ARGB_8888)
        val matrix = android.graphics.ColorMatrix().apply {
            set(floatArrayOf(
                0.043f, 0.145f, 0.015f, 0f, 8f,
                0.060f, 0.201f, 0.020f, 0f, 10f,
                0.083f, 0.279f, 0.028f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val nightCanvas = android.graphics.Canvas(night)
        nightCanvas.drawBitmap(
            topo, 0f, 0f,
            android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            }
        )
        topo.recycle()

        // The hillshade is nearly white on flat ground and grey in shadow, so
        // amplifying the contrast before multiplying makes ridges visible.
        stitchTiles(HILLSHADE_ENDPOINT, z, x0, x1, y0, y1)?.let { relief ->
            val contrast = android.graphics.ColorMatrix().apply {
                set(floatArrayOf(
                    3.4f, 0f, 0f, 0f, -690f,
                    0f, 3.4f, 0f, 0f, -690f,
                    0f, 0f, 3.4f, 0f, -690f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            nightCanvas.drawBitmap(
                relief, 0f, 0f,
                android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(contrast)
                    xfermode = android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.MULTIPLY
                    )
                }
            )
            relief.recycle()
        }

        val scale = TILE_PX * (1 shl z) / 360.0
        val left = ((box.west + 180.0) * scale - x0 * TILE_PX).toInt().coerceIn(0, night.width - 1)
        val right = ((box.east + 180.0) * scale - x0 * TILE_PX).toInt().coerceIn(left + 1, night.width)
        val top = (latToGlobalPx(box.north, z) - y0 * TILE_PX).toInt().coerceIn(0, night.height - 1)
        val bottom = (latToGlobalPx(box.south, z) - y0 * TILE_PX).toInt().coerceIn(top + 1, night.height)
        val cropped = Bitmap.createBitmap(night, left, top, right - left, bottom - top)
        night.recycle()
        // Match the echo frame size exactly so the panel draws both 1:1.
        return Bitmap.createScaledBitmap(cropped, IMAGE_WIDTH, IMAGE_HEIGHT, true).also { cropped.recycle() }
    }

    /** Fetches one tile grid in parallel and assembles it into a flat bitmap. */
    private suspend fun stitchTiles(
        endpoint: String,
        z: Int,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int
    ): Bitmap? {
        val gate = Semaphore(6)
        val tiles = coroutineScope {
            (x0..x1).flatMap { tx ->
                (y0..y1).map { ty ->
                    async {
                        Triple(tx, ty, gate.withPermit { fetchTile(endpoint, z, ty, tx) })
                    }
                }
            }.awaitAll()
        }
        if (tiles.all { it.third == null }) return null

        val cols = x1 - x0 + 1
        val rows = y1 - y0 + 1
        val stitched = Bitmap.createBitmap(cols * TILE_PX, rows * TILE_PX, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(stitched)
        val paint = android.graphics.Paint()
        val fallback = tiles.firstNotNullOfOrNull { it.third }
        tiles.forEach { (tx, ty, tile) ->
            val drawable = tile ?: fallback ?: return@forEach
            canvas.drawBitmap(
                drawable,
                ((tx - x0) * TILE_PX).toFloat(),
                ((ty - y0) * TILE_PX).toFloat(),
                paint
            )
            if (tile != null) tile.recycle()
        }
        return stitched
    }

    /**
     * A single dropped tile shows up as a bright seam in the stitched map, so
     * each one gets a second attempt before it is written off.
     */
    private fun fetchTile(endpoint: String, z: Int, y: Int, x: Int): Bitmap? {
        repeat(2) {
            val bytes = runCatching { requestBytes("$endpoint/$z/$y/$x") }.getOrNull()
            if (bytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) return bitmap
            }
        }
        return null
    }

    private fun lonToTileX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * (1 shl z)

    private fun latToTileY(lat: Double, z: Int): Double =
        latToGlobalPx(lat, z) / TILE_PX

    private fun latToGlobalPx(lat: Double, z: Int): Double {
        val radians = Math.toRadians(lat)
        val world = TILE_PX * (1 shl z).toDouble()
        return (1.0 - Math.log(Math.tan(radians) + 1.0 / Math.cos(radians)) / Math.PI) / 2.0 * world
    }

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
