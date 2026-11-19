package com.localonthe8s.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One state outline: rings of (lon, lat) pairs plus its bounding box. */
data class StateOutline(
    val rings: List<FloatArray>,
    val west: Float,
    val south: Float,
    val east: Float,
    val north: Float
) {
    fun intersects(w: Double, s: Double, e: Double, n: Double): Boolean =
        east >= w && west <= e && north >= s && south <= n
}

/**
 * State borders from a bundled public-domain GeoJSON (US Census boundaries via the
 * PublicaMundi MappingAPI). Bundled rather than fetched so the radar map keeps its
 * geography even when the network is down, and parsed once per process.
 */
object StateBorders {

    private var cached: List<StateOutline>? = null

    suspend fun load(context: Context): List<StateOutline> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        val parsed = runCatching {
            val text = context.assets.open("us-states.json").bufferedReader().use { it.readText() }
            parse(text)
        }.getOrDefault(emptyList())
        cached = parsed
        parsed
    }

    private fun parse(text: String): List<StateOutline> {
        val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
        val outlines = ArrayList<StateOutline>(features.length())

        for (index in 0 until features.length()) {
            val geometry = features.optJSONObject(index)?.optJSONObject("geometry") ?: continue
            val rings = ArrayList<FloatArray>()
            when (geometry.optString("type")) {
                "Polygon" -> geometry.optJSONArray("coordinates")?.let { collectRings(it, rings) }
                "MultiPolygon" -> {
                    val polygons = geometry.optJSONArray("coordinates") ?: continue
                    for (p in 0 until polygons.length()) {
                        polygons.optJSONArray(p)?.let { collectRings(it, rings) }
                    }
                }
            }
            if (rings.isEmpty()) continue

            var west = Float.MAX_VALUE
            var south = Float.MAX_VALUE
            var east = -Float.MAX_VALUE
            var north = -Float.MAX_VALUE
            rings.forEach { ring ->
                var i = 0
                while (i + 1 < ring.size) {
                    val lon = ring[i]
                    val lat = ring[i + 1]
                    if (lon < west) west = lon
                    if (lon > east) east = lon
                    if (lat < south) south = lat
                    if (lat > north) north = lat
                    i += 2
                }
            }
            outlines.add(StateOutline(rings, west, south, east, north))
        }
        return outlines
    }

    /** A polygon's coordinates are an array of rings; each ring is an array of [lon, lat]. */
    private fun collectRings(polygon: JSONArray, into: MutableList<FloatArray>) {
        for (r in 0 until polygon.length()) {
            val ring = polygon.optJSONArray(r) ?: continue
            if (ring.length() < 2) continue
            val flat = FloatArray(ring.length() * 2)
            for (i in 0 until ring.length()) {
                val point = ring.optJSONArray(i) ?: continue
                flat[i * 2] = point.optDouble(0).toFloat()
                flat[i * 2 + 1] = point.optDouble(1).toFloat()
            }
            into.add(flat)
        }
    }
}
