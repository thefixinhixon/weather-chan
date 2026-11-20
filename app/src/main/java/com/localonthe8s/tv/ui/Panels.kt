package com.localonthe8s.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import com.localonthe8s.tv.data.DailyPoint
import com.localonthe8s.tv.data.HourlyPoint
import com.localonthe8s.tv.data.Observation
import com.localonthe8s.tv.data.OpenMeteo
import com.localonthe8s.tv.data.Ruidoso
import com.localonthe8s.tv.data.SkiApache
import com.localonthe8s.tv.data.SkiReport
import com.localonthe8s.tv.data.StateBorders
import com.localonthe8s.tv.data.StateOutline
import com.localonthe8s.tv.data.WeatherSnapshot
import com.localonthe8s.tv.data.describe
import com.localonthe8s.tv.data.glyphFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun OpeningPanel(place: com.localonthe8s.tv.data.Place, snapshot: WeatherSnapshot?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {            Column(horizontalAlignment = Alignment.CenterHorizontally) {                Text(
                    text = "THE GREENERY ROOM",
                    style = Type.Brand.copy(fontSize = 58.sp, letterSpacing = 8.sp)
                )
                Spacer(Modifier.height(6.dp))
                Text("RUIDOSO LOCAL", style = Type.Kicker.copy(letterSpacing = 6.sp))
            Spacer(Modifier.height(18.dp))
            Text(place.displayName.uppercase(), style = Type.TempMid.copy(color = Palette.Cyan))
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .width(420.dp)
                    .height(2.dp)
                    .background(Palette.Amber)
            )
            Spacer(Modifier.height(24.dp))
            Text("EVENTS FIRST • THEN LOCAL WEATHER", style = Type.Kicker)
            Spacer(Modifier.height(8.dp))
            if (snapshot != null) {
                Text(
                    text = "${snapshot.current.tempF}° ${describe(snapshot.current.code).uppercase()}",
                    style = Type.Body
                )
            } else {
                Text("ACQUIRING REGIONAL DATA", style = Type.BodyDim)
            }
        }
    }
}

@Composable
fun CurrentPanel(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    val current = snapshot.current
    PanelFrame(
        title = "CURRENT CONDITIONS",
        caption = "OBSERVED ${current.observedAtLabel} • ${snapshot.place.name.uppercase()}",
        modifier = modifier
    ) {
        // The hero block plus the almanac strip is taller than the panel on a
        // 1080p/320dpi set. Keeping the temperature at 104sp and letting
        // SpaceBetween pin the strip to the bottom keeps every value on screen.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(glyphFor(current.code), 100.dp)
                Spacer(Modifier.width(28.dp))
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${current.tempF}", style = Type.Hero.copy(fontSize = 104.sp))
                        Text("°", style = Type.Hero.copy(fontSize = 104.sp, color = Palette.Amber))
                    }
                    Text(describe(current.code).uppercase(), style = Type.TempMid.copy(color = Palette.Cyan))
                    Spacer(Modifier.height(6.dp))
                    Text("FEELS LIKE ${current.feelsLikeF}°", style = Type.BodyDim)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("WIND", style = Type.Small)
                    Text("${current.windDir} ${current.windMph} MPH", style = Type.TempMid)
                    Spacer(Modifier.height(10.dp))
                    Text("HUMIDITY ${current.humidity}% • CLOUDS ${current.cloudCover}%", style = Type.BodyDim)
                    Text("PRESSURE ${current.pressureInHg} IN", style = Type.BodyDim)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                StatCard("SUNRISE", snapshot.sunrise, Modifier.weight(1f))
                StatCard("SUNSET", snapshot.sunset, Modifier.weight(1f))
                StatCard("DAYLIGHT", snapshot.daylight, Modifier.weight(1f))
                StatCard("ZONE", snapshot.timezoneAbbrev, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(end = 12.dp)
            .background(Palette.Navy.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, style = Type.Small)
        Spacer(Modifier.height(4.dp))
        Text(value.uppercase(), style = Type.Body.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun ObservationsPanel(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    PanelFrame(
        title = "REGIONAL OBSERVATIONS",
        caption = "AUTOMATED READINGS ±35 MI • ${snapshot.place.name.uppercase()}",
        modifier = modifier
    ) {
        val rows = snapshot.observations.chunked(2)
        if (rows.isEmpty()) {
            Text("REGIONAL FEED UNAVAILABLE. LOCAL CONDITIONS REMAIN CURRENT.", style = Type.BodyDim)
        } else {
            Column(Modifier.fillMaxSize()) {
                rows.forEachIndexed { index, row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { observation ->
                            ObservationCard(
                                observation = observation,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(end = 12.dp)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    if (index < rows.lastIndex) Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Palette.Navy.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Line.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WeatherGlyph(glyphFor(observation.code), 56.dp)
        Spacer(Modifier.width(20.dp))
        Column {
            Text(observation.label, style = Type.Kicker)
            Text("${observation.tempF}°", style = Type.TempLarge)
            Text(describe(observation.code).uppercase(), style = Type.Small)
        }
    }
}

@Composable
fun SevenDayPanel(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    val days = snapshot.daily
    PanelFrame(
        title = "7-DAY OUTLOOK",
        caption = "${snapshot.place.name.uppercase()} • ISSUED ${snapshot.timezoneAbbrev}",
        modifier = modifier
    ) {
        if (days.isEmpty()) {
            Text("OUTLOOK PACKAGE NOT YET RECEIVED.", style = Type.BodyDim)
        } else {
            val weekLow = days.minOf { it.lowF }
            val weekHigh = days.maxOf { it.highF }
            val span = (weekHigh - weekLow).coerceAtLeast(1)
            Column(Modifier.fillMaxSize()) {
                days.forEach { day ->
                    DailyRow(day, weekLow, span)
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun DailyRow(day: DailyPoint, weekLow: Int, span: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.label,
            style = Type.Body.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(122.dp)
        )
        WeatherGlyph(glyphFor(day.code), 30.dp)
        Text(
            text = "${day.precipChance}%",
            style = Type.Small,
            modifier = Modifier.width(70.dp).padding(start = 12.dp)
        )
        Text(
            text = "${day.lowF}°",
            style = Type.BodyDim,
            textAlign = TextAlign.End,
            modifier = Modifier.width(54.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .padding(horizontal = 14.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(Palette.Line.copy(alpha = 0.35f), cornerRadius = corner)
                val startFraction = (day.lowF - weekLow) / span.toFloat()
                val endFraction = (day.highF - weekLow) / span.toFloat()
                val x = size.width * startFraction
                val width = (size.width * (endFraction - startFraction)).coerceAtLeast(6f)
                drawRoundRect(
                    color = Palette.Amber,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = corner
                )
            }
        }
        Text(
            text = "${day.highF}°",
            style = Type.Body.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(54.dp)
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.Line.copy(alpha = 0.3f))
    )
}

@Composable
fun HourlyPanel(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    PanelFrame(
        title = "HOURLY FORECAST",
        caption = "NEXT ${snapshot.hourly.take(10).size} HOURS • ${snapshot.place.name.uppercase()}",
        modifier = modifier
    ) {
        val hours = snapshot.hourly.take(10)
        if (hours.isEmpty()) {
            Text("HOURLY PACKAGE NOT YET RECEIVED.", style = Type.BodyDim)
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                hours.forEach { hour -> HourlyColumn(hour, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun HourlyColumn(hour: HourlyPoint, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(hour.label, style = Type.Small.copy(color = Palette.Cyan))
        Spacer(Modifier.height(12.dp))
        WeatherGlyph(glyphFor(hour.code), 44.dp)
        Spacer(Modifier.height(12.dp))
        Text("${hour.tempF}°", style = Type.TempMid)
        Spacer(Modifier.height(6.dp))
        Text("${hour.precipChance}%", style = Type.Small)
    }
}

/**
 * Real Doppler as an animation: the last eight NEXRAD frames loop oldest-to-newest
 * and hold on the latest, over a projected graticule, state lines and 25/50 mile
 * range rings. Falls back to the bare map when the echo feed is down.
 */
private const val FRAME_HOLD_MILLIS = 420L
private const val LOOP_PAUSE_MILLIS = 1500L

/** Approximate Lincoln County bounds drawn on the Doppler map. */
private const val COUNTY_WEST = -106.05
private const val COUNTY_EAST = -105.0
private const val COUNTY_SOUTH = 33.3
private const val COUNTY_NORTH = 33.98

private data class CountyTown(val name: String, val lat: Double, val lon: Double)

private val COUNTY_TOWNS = listOf(
    CountyTown("RUIDOSO", 33.3314, -105.2855),
    CountyTown("ALTO", 33.3906, -105.6775),
    CountyTown("CAPITAN", 33.5456, -105.5767),
    CountyTown("CARRIZOZO", 33.6420, -105.8770),
    CountyTown("CLOUDCROFT", 32.9552, -105.7443),
    CountyTown("ALAMOGORDO", 32.8995, -106.3075),
    CountyTown("ROSWELL", 33.3943, -104.5230)
)

private data class Landmark(val name: String, val lat: Double, val lon: Double)

private val MAP_LANDMARKS = listOf(
    Landmark("SIERRA BLANCA 11,973'", 33.3747, -105.8088),
    Landmark("BONITO LAKE", 33.4556, -105.7297),
    Landmark("VALLEY OF FIRES", 33.3200, -105.7150),
    Landmark("WHITE SANDS", 32.7797, -106.1714)
)

@Composable
fun RadarPanel(snapshot: WeatherSnapshot, radar: RadarUiState, modifier: Modifier = Modifier) {
    val place = snapshot.place
    val loop = (radar as? RadarUiState.Ready)?.loop
    val context = LocalContext.current
    var borders by remember { mutableStateOf<List<StateOutline>>(emptyList()) }
    var frameIndex by remember(loop) { mutableStateOf(0) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(Unit) { borders = StateBorders.load(context) }

    // Cable-style loop: step forward through the frames, then hold on the newest.
    LaunchedEffect(loop) {
        val frames = loop?.frames ?: return@LaunchedEffect
        if (frames.size == 1) return@LaunchedEffect
        while (true) {
            for (index in frames.indices) {
                frameIndex = index
                delay(FRAME_HOLD_MILLIS)
            }
            frameIndex = frames.lastIndex
            delay(LOOP_PAUSE_MILLIS)
        }
    }

    val shown = loop?.frames?.getOrNull(frameIndex) ?: loop?.frames?.lastOrNull()
    val peakPrecip = snapshot.hourly.take(6).maxOfOrNull { it.precipChance } ?: 0
    val caption = when {
        loop != null -> "NEXRAD LOOP ${loop.asOfLabel} • ${place.name.uppercase()}"
        radar is RadarUiState.Failed -> "ECHO FEED UNAVAILABLE • ${place.name.uppercase()}"
        else -> "LINKING TO RADAR • ${place.name.uppercase()}"
    }

    PanelFrame(
        title = "REGIONAL DOPPLER",
        caption = caption,
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val mid = Offset(size.width / 2f, size.height / 2f)

                drawRect(Palette.Navy.copy(alpha = 0.5f))

                loop?.let { radarFrame ->
                    val frameImage = shown?.image ?: radarFrame.frames.lastOrNull()?.image
                    if (frameImage == null) return@let
                    // Terrain sits under the echoes so the map reads on clear days.
                    radarFrame.base?.let { terrain ->
                        val terrainBitmap = terrain.asImageBitmap()
                        drawImage(
                            image = terrainBitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(terrainBitmap.width, terrainBitmap.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium
                        )
                        // Slight lift so the tinted terrain stays behind the echoes.
                        drawRect(Palette.Ink.copy(alpha = 0.04f))
                    }
                    val bitmap = frameImage.asImageBitmap()
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Medium
                    )

                    // State lines sit above the echoes so a storm never hides them.
                    val borderColor = Palette.Cyan.copy(alpha = 0.34f)
                    borders.forEach { outline ->
                        if (outline.intersects(
                                radarFrame.west, radarFrame.south,
                                radarFrame.east, radarFrame.north
                            )
                        ) {
                            outline.rings.forEach { ring ->
                                var i = 0
                                while (i + 3 < ring.size) {
                                    val x1 = radarFrame.xFraction(ring[i].toDouble()) * size.width
                                    val y1 = radarFrame.yFraction(ring[i + 1].toDouble()) * size.height
                                    val x2 = radarFrame.xFraction(ring[i + 2].toDouble()) * size.width
                                    val y2 = radarFrame.yFraction(ring[i + 3].toDouble()) * size.height
                                    drawLine(borderColor, Offset(x1, y1), Offset(x2, y2), 1.3f)
                                    i += 2
                                }
                            }
                        }
                    }

                    // Whole-degree graticule so the picture reads as a map.
                    val grid = Palette.Cyan.copy(alpha = 0.10f)
                    var lon = ceil(radarFrame.west)
                    while (lon <= radarFrame.east) {
                        val x = radarFrame.xFraction(lon) * size.width
                        drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                        lon += 1.0
                    }
                    var lat = floor(radarFrame.north)
                    while (lat >= radarFrame.south) {
                        val y = radarFrame.yFraction(lat) * size.height
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                        lat -= 1.0
                    }

                    val milesAcross = (radarFrame.east - radarFrame.west) * 69.0 *
                        cos(Math.toRadians(place.latitude))
                    val pxPerMile = (size.width / milesAcross.coerceAtLeast(1.0)).toFloat()
                    // Rings follow the tuned town even when the map is county-centered.
                    val town = Offset(
                        radarFrame.xFraction(place.longitude) * size.width,
                        radarFrame.yFraction(place.latitude) * size.height
                    )
                    listOf(25, 50).forEach { miles ->
                        drawCircle(
                            color = Palette.Cyan.copy(alpha = 0.20f),
                            radius = pxPerMile * miles,
                            center = town,
                            style = Stroke(width = 1.2f)
                        )
                    }

                    // Lincoln County box so the view reads as a county, not one town.
                    if (radarFrame.west < COUNTY_EAST && radarFrame.east > COUNTY_WEST &&
                        radarFrame.south < COUNTY_NORTH && radarFrame.north > COUNTY_SOUTH
                    ) {
                        val cx1 = radarFrame.xFraction(COUNTY_WEST) * size.width
                        val cx2 = radarFrame.xFraction(COUNTY_EAST) * size.width
                        val cy1 = radarFrame.yFraction(COUNTY_NORTH) * size.height
                        val cy2 = radarFrame.yFraction(COUNTY_SOUTH) * size.height
                        drawRect(
                            color = Palette.Amber.copy(alpha = 0.45f),
                            topLeft = Offset(cx1, cy1),
                            size = Size(cx2 - cx1, cy2 - cy1),
                            style = Stroke(width = 1.6f)
                        )
                    }

                    // Nearby towns and landmarks give the map its detail. A
                    // label is dropped when it would land on one already
                    // drawn, so dense clusters (Ruidoso/Alto/Ruidoso Downs)
                    // stay legible instead of stacking words on each other.
                    val placed = mutableListOf<Rect>()
                    fun label(text: String, style: TextStyle, center: Offset, above: Boolean): Boolean {
                        val layout = textMeasurer.measure(text, style)
                        val w = layout.size.width.toFloat()
                        val h = layout.size.height.toFloat()
                        val top = if (above) center.y - h - 10f else center.y + 8f
                        // Keep the whole word on the map when its dot sits near
                        // an edge, instead of slicing towns like ALAMOGORDO.
                        val left = (center.x - w / 2f - 6f)
                            .coerceIn(0f, (size.width - w - 12f).coerceAtLeast(0f))
                        val rect = Rect(
                            left = left,
                            top = top - 3f,
                            right = left + w + 12f,
                            bottom = top + h + 3f
                        )
                        if (placed.any { it.overlaps(rect) }) return false
                        drawText(
                            textMeasurer = textMeasurer,
                            text = text,
                            topLeft = Offset(rect.left + 6f, top),
                            style = style
                        )
                        placed.add(rect)
                        return true
                    }

                    val townStyle = TextStyle(
                        color = Palette.White.copy(alpha = 0.92f),
                        fontSize = (size.width / 58f).toSp()
                    )
                    // The tuned town claims its label first, so neighboring
                    // towns yield instead of colliding with the readout.
                    val tuned = Offset(
                        radarFrame.xFraction(place.longitude) * size.width,
                        radarFrame.yFraction(place.latitude) * size.height
                    )
                    label(
                        place.name.uppercase(),
                        townStyle.copy(color = Palette.Amber, fontWeight = FontWeight.Bold),
                        tuned,
                        above = false
                    )
                    // Peaks, lakes and monuments come before plain town names:
                    // they are the landmark layer the map is here to carry.
                    val landmarkStyle = TextStyle(
                        color = Palette.Amber.copy(alpha = 0.92f),
                        fontSize = (size.width / 72f).toSp()
                    )
                    MAP_LANDMARKS.forEach { mark ->
                        if (mark.lon in radarFrame.west..radarFrame.east &&
                            mark.lat in radarFrame.south..radarFrame.north
                        ) {
                            val at = Offset(
                                radarFrame.xFraction(mark.lon) * size.width,
                                radarFrame.yFraction(mark.lat) * size.height
                            )
                            label(mark.name, landmarkStyle, at, above = true) ||
                                label(mark.name, landmarkStyle, at, above = false)
                        }
                    }
                    COUNTY_TOWNS.forEach { townMark ->
                        if (townMark.lon in radarFrame.west..radarFrame.east &&
                            townMark.lat in radarFrame.south..radarFrame.north
                        ) {
                            val dot = Offset(
                                radarFrame.xFraction(townMark.lon) * size.width,
                                radarFrame.yFraction(townMark.lat) * size.height
                            )
                            drawCircle(Palette.White.copy(alpha = 0.9f), 3f, dot)
                            // The tuned town already carries the crosshair readout.
                            if (!townMark.name.equals(place.name, ignoreCase = true)) {
                                label(townMark.name, townStyle, dot, above = false) ||
                                    label(townMark.name, townStyle, dot, above = true)
                            }
                        }
                    }
                }

                // Crosshair stays on the tuned town; county label sits mid-map.
                val townPt = loop?.let {
                    Offset(
                        it.xFraction(place.longitude) * size.width,
                        it.yFraction(place.latitude) * size.height
                    )
                } ?: mid
                val arm = 14f
                drawLine(Palette.Amber, Offset(townPt.x - arm, townPt.y), Offset(townPt.x + arm, townPt.y), 2f)
                drawLine(Palette.Amber, Offset(townPt.x, townPt.y - arm), Offset(townPt.x, townPt.y + arm), 2f)
                drawCircle(Palette.Amber, 3.5f, townPt)
            }

            Text(
                "N",
                style = Type.Small.copy(color = Palette.Green),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
            )
            // Only name the county when the view actually covers it.
            val countyInView = loop != null && loop.west < COUNTY_EAST && loop.east > COUNTY_WEST &&
                loop.south < COUNTY_NORTH && loop.north > COUNTY_SOUTH
            if (countyInView) {
                Text(
                    "LINCOLN COUNTY",
                    style = Type.Small.copy(color = Palette.Amber),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
                )
            }
            if (shown != null) {
                Text(
                    text = "LOOP ${shown.ageLabel}",
                    style = Type.Small.copy(color = Palette.Amber),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp)
                )
            }
            Text(
                "${peakPrecip}% PEAK PRECIP • RINGS 25/50 MI • TERRAIN ESRI",
                style = Type.Small,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp)
            )
            when {
                radar is RadarUiState.Failed -> Text(
                    "RADAR FEED UNREACHABLE • RETRYING",
                    style = Type.Small.copy(color = Palette.Red),
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 10.dp, start = 12.dp)
                )
                loop != null -> Text(
                    if (loop.echoesInRange) "ECHOES IN RANGE • NEXRAD" else "NO ECHOES IN RANGE • NEXRAD",
                    style = Type.Small.copy(color = if (loop.echoesInRange) Palette.Green else Palette.Muted),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
fun AlmanacPanel(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    val phase = remember { moonPhase(System.currentTimeMillis()) }
    PanelFrame(
        title = "ALMANAC",
        caption = "${snapshot.place.displayName.uppercase()} • ${snapshot.timezoneAbbrev}",
        modifier = modifier
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AlmanacRow("SUNRISE", snapshot.sunrise)
                AlmanacRow("SUNSET", snapshot.sunset)
                AlmanacRow("DAYLIGHT", snapshot.daylight)
                AlmanacRow("MOON", phase.label)
                AlmanacRow("ILLUMINATION", "${(phase.illumination * 100).roundToInt()}%")
            }
            MoonGlyph(phase, 150.dp)
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text("BROADCAST NOTES", style = Type.Kicker)
                Spacer(Modifier.height(14.dp))
                Text("STRAIGHT MET DATA. NO STORM FATIGUE.", style = Type.Body)
                Spacer(Modifier.height(10.dp))
                Text("EVENTS RUN FIRST • WEATHER FOLLOWS FOR EACH TOWN.", style = Type.BodyDim)
                Spacer(Modifier.height(8.dp))
                Text("SMOOTH JAZZ CONTINUES OVER THE RADAR LOOP.", style = Type.BodyDim)
                Spacer(Modifier.height(8.dp))
                Text("THE GREENERY ROOM • RUIDOSO, NM", style = Type.Small)
            }
        }
    }
}

@Composable
private fun AlmanacRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, style = Type.Small.copy(color = Palette.Cyan), modifier = Modifier.width(150.dp))
        Text(value.uppercase(), style = Type.Body.copy(fontWeight = FontWeight.Bold))
    }
}

private data class MoonPhase(
    val label: String,
    val illumination: Float,
    val waxing: Boolean
)

private fun moonPhase(millis: Long): MoonPhase {
    val synodic = 29.530588853
    val referenceNewMoon = 947_182_440_000.0
    val days = (millis - referenceNewMoon) / 86_400_000.0
    var fraction = (days % synodic) / synodic
    if (fraction < 0) fraction += 1.0

    val illumination = ((1.0 - cos(2 * PI * fraction)) / 2.0).toFloat()
    val label = when {
        fraction < 0.03 || fraction > 0.97 -> "NEW MOON"
        fraction < 0.22 -> "WAXING CRESCENT"
        fraction < 0.28 -> "FIRST QUARTER"
        fraction < 0.47 -> "WAXING GIBBOUS"
        fraction < 0.53 -> "FULL MOON"
        fraction < 0.72 -> "WANING GIBBOUS"
        fraction < 0.78 -> "LAST QUARTER"
        else -> "WANING CRESCENT"
    }
    return MoonPhase(label, illumination, fraction < 0.5)
}

@Composable
private fun MoonGlyph(phase: MoonPhase, dimension: Dp) {
    Canvas(Modifier.size(dimension)) {
        val radius = size.minDimension / 2f * 0.86f
        val mid = center
        val disc = Rect(mid.x - radius, mid.y - radius, mid.x + radius, mid.y + radius)

        drawCircle(Color(0xFF0A1F4C), radius, mid)
        val lit = waxingLimbPath(mid, radius, phase.illumination)
        if (phase.waxing) {
            drawPath(lit, Palette.White)
        } else {
            scale(scaleX = -1f, scaleY = 1f, pivot = mid) {
                drawPath(lit, Palette.White)
            }
        }
        val shading = Path().apply { addOval(disc) }
        clipPath(shading) {
            drawCircle(Color(0xFF071A45).copy(alpha = 0.22f), radius * 0.16f, Offset(mid.x - radius * 0.3f, mid.y - radius * 0.24f))
            drawCircle(Color(0xFF071A45).copy(alpha = 0.18f), radius * 0.11f, Offset(mid.x + radius * 0.22f, mid.y + radius * 0.3f))
            drawCircle(Color(0xFF071A45).copy(alpha = 0.14f), radius * 0.09f, Offset(mid.x - radius * 0.02f, mid.y + radius * 0.5f))
        }
        drawCircle(Palette.Line.copy(alpha = 0.5f), radius, mid, style = Stroke(1.5f))
    }
}

/** Right-hand lit limb; mirrored with a scale transform for waning phases. */
private fun waxingLimbPath(mid: Offset, radius: Float, illumination: Float): Path {
    val terminatorWidth = radius * abs(1f - 2f * illumination).coerceAtLeast(0.001f)
    val outer = Rect(mid.x - radius, mid.y - radius, mid.x + radius, mid.y + radius)
    val inner = Rect(mid.x - terminatorWidth, mid.y - radius, mid.x + terminatorWidth, mid.y + radius)
    return Path().apply {
        moveTo(mid.x, mid.y - radius)
        arcTo(outer, -90f, 180f, false)
        arcTo(inner, 90f, if (illumination <= 0.5f) -180f else 180f, false)
        close()
    }
}

@Composable
fun SignalPanel(state: WeatherUiState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "signal")
    val blink by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    val failed = state is WeatherUiState.Failed

    PanelFrame(
        title = if (failed) "SIGNAL LOST" else "ACQUIRING SATELLITE FEED",
        caption = "WEATHERSTAR III • LOCAL DATA FEED",
        modifier = modifier
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (failed) Palette.Red else Palette.Amber, CircleShape)
                    .graphicsLayer { alpha = blink }
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (failed) {
                    (state as WeatherUiState.Failed).error.uppercase()
                } else {
                    "DOWNLOADING FORECAST PACKAGE"
                },
                style = Type.Body
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = if (failed) {
                "IT RETRIES AUTOMATICALLY EVERY 10 MINUTES. LAST GOOD DATA STAYS ON SCREEN."
            } else {
                "OPEN-METEO REGIONAL MODEL • REFRESHES EVERY 10 MINUTES"
            },
            style = Type.BodyDim
        )
        Spacer(Modifier.weight(1f))
        Text("NO STORM FATIGUE", style = Type.Small)
    }
}

/** Polls the Ski Apache trail-status page; keeps the last good report on failure. */
@Composable
fun rememberSkiApache(refreshToken: Int): SkiReport? {
    var report by remember { mutableStateOf<SkiReport?>(null) }
    LaunchedEffect(refreshToken) {
        while (true) {
            runCatching { SkiApache.fetch() }.onSuccess { report = it }
            delay(15 * 60 * 1000L)
        }
    }
    return report
}

/** How often a visible live cam still re-pulls while its slide is on screen. */
private const val LIVE_FRAME_REFRESH_MILLIS = 10_000L

/**
 * Fetches a cam still bypassing every cache layer, so each poll is a new live
 * frame. Kept out of the shared event-artwork LRU on purpose: timestamped live
 * URLs would otherwise evict real event flyers. Null when the cam is down;
 * callers keep the last good frame on screen.
 */
private suspend fun fetchLiveBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "image/*")
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", com.localonthe8s.tv.data.EventsFeed.BROWSER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/**
 * Ski Apache live conditions: the mountain-cam still beside the snow numbers
 * and the lift board. The still re-pulls every few seconds while the slide is
 * on screen and restarts fresh on every visit, so the picture tracks the live
 * cam instead of holding one frame. The resort's own report time stays off the
 * picture — the cam is live even when the resort last posted yesterday — and
 * is labeled as the resort report beside the lift board instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SkiApachePanel(report: SkiReport?, modifier: Modifier = Modifier) {
    PanelFrame(
        title = "SKI APACHE LIVE",
        caption = "SIERRA BLANCA • LIVE MOUNTAIN CONDITIONS",
        modifier = modifier
    ) {
        if (report == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("LINKING MOUNTAIN CAM…", style = Type.BodyDim)
            }
            return@PanelFrame
        }
        Row(Modifier.fillMaxSize()) {
            var frame by remember { mutableStateOf<ImageBitmap?>(null) }
            // Loop while composed: fresh frame on every visit plus a re-pull
            // mid-slide so the 15s slot reads as live, not a still. Leaving
            // the slide cancels the loop; coming back restarts it.
            LaunchedEffect(Unit) {
                while (true) {
                    try {
                        fetchLiveBitmap(SkiApache.SNAPSHOT_URL)?.let { frame = it }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the last good frame; retry on the next tick.
                    }
                    delay(LIVE_FRAME_REFRESH_MILLIS)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Navy.copy(alpha = 0.45f))
                    .border(1.dp, Palette.Line.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.BottomStart
            ) {
                frame?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = "● LIVE CAM",
                    style = Type.Small.copy(color = Palette.White),
                    modifier = Modifier
                        .padding(10.dp)
                        .background(Palette.Ink.copy(alpha = 0.72f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth()) {
                    SkiStat("24 HR", snowInches(report.past24), Modifier.weight(1f))
                    SkiStat("48 HR", snowInches(report.past48), Modifier.weight(1f))
                    SkiStat("BASE", snowInches(report.baseDepth), Modifier.weight(1f))
                    SkiStat("SEASON", snowInches(report.seasonTotal), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "LIFTS ${report.openLifts.ifBlank { "–" }} OPEN" +
                        " • TRAILS ${report.openTrails.ifBlank { "–" }} OPEN",
                    style = Type.Body.copy(fontWeight = FontWeight.Bold, color = Palette.Cyan)
                )
                if (report.updatedLabel.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "RESORT REPORT • ${report.updatedLabel.uppercase()}" +
                            (if (SkiApache.run { report.isStale() }) " • LATEST AVAILABLE" else ""),
                        style = Type.Small.copy(color = Palette.Muted)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f)) {
                    report.lifts.forEach { lift ->
                        LiftCell(lift, Modifier.fillMaxWidth())
                    }
                }
                if (report.activities.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = report.activities.joinToString(" • ") { "${it.name} ${it.status}" },
                        style = Type.Small,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "ROAD ${report.road.ifBlank { "–" }} • SKIAPACHE.COM",
                    style = Type.Small.copy(color = Palette.Muted)
                )
            }
        }
    }
}

/** Polls Open-Meteo for Ruidoso; keeps the last good snapshot on failure. */
@Composable
fun rememberRuidoso(refreshToken: Int): com.localonthe8s.tv.data.WeatherSnapshot? {
    var live by remember { mutableStateOf<com.localonthe8s.tv.data.WeatherSnapshot?>(null) }
    LaunchedEffect(refreshToken) {
        while (true) {
            runCatching { OpenMeteo.fetch(Ruidoso.PLACE) }.onSuccess { live = it }
            delay(15 * 60 * 1000L)
        }
    }
    return live
}

/**
 * Live conditions in Ruidoso: the Midtown webcam still beside the town's own
 * current readings. The still re-pulls every few seconds while the slide is
 * on screen and restarts fresh on every visit; the poster fills in only when
 * no live frame has arrived yet.
 */
@Composable
fun RuidosoPanel(live: com.localonthe8s.tv.data.WeatherSnapshot?, modifier: Modifier = Modifier) {
    PanelFrame(
        title = "RUIDOSO LIVE",
        caption = "MIDTOWN • LIVE CONDITIONS IN RUIDOSO",
        modifier = modifier
    ) {
        if (live == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("LINKING MIDTOWN CAM…", style = Type.BodyDim)
            }
            return@PanelFrame
        }
        val current = live.current
        val today = live.daily.firstOrNull()
        Row(Modifier.fillMaxSize()) {
            var frame by remember { mutableStateOf<ImageBitmap?>(null) }
            // Same live loop as the mountain cam: fresh frame on every visit
            // plus a re-pull mid-slide. The poster is a first-frame fallback
            // only — once a live frame lands it is never replaced by it.
            LaunchedEffect(Unit) {
                while (true) {
                    try {
                        val live = fetchLiveBitmap(Ruidoso.SNAPSHOT_URL)
                            ?: fetchLiveBitmap(Ruidoso.LIVE_THUMB)
                        if (live != null) {
                            frame = live
                        } else if (frame == null) {
                            fetchLiveBitmap(Ruidoso.POSTER)?.let { frame = it }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the last good frame; retry on the next tick.
                    }
                    delay(LIVE_FRAME_REFRESH_MILLIS)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Navy.copy(alpha = 0.45f))
                    .border(1.dp, Palette.Line.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.BottomStart
            ) {
                frame?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = "● MIDTOWN CAM • ${current.observedAtLabel}",
                    style = Type.Small.copy(color = Palette.White),
                    modifier = Modifier
                        .padding(10.dp)
                        .background(Palette.Ink.copy(alpha = 0.72f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(glyphFor(current.code), 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${current.tempF}", style = Type.Hero.copy(fontSize = 64.sp))
                            Text("°", style = Type.Hero.copy(fontSize = 64.sp, color = Palette.Amber))
                        }
                        Text(describe(current.code).uppercase(), style = Type.TempMid.copy(color = Palette.Cyan))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "FEELS LIKE ${current.feelsLikeF}° • WIND ${current.windDir} ${current.windMph} MPH",
                    style = Type.BodyDim
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    SkiStat("HIGH", today?.let { "${it.highF}°" } ?: "–", Modifier.weight(1f))
                    SkiStat("LOW", today?.let { "${it.lowF}°" } ?: "–", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    SkiStat("PRECIP", today?.let { "${it.precipChance}%" } ?: "–", Modifier.weight(1f))
                    SkiStat("HUMIDITY", "${current.humidity}%", Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "DOWNTOWN RUIDOSO NM • ELEV 6,920 FT",
                    style = Type.Small.copy(color = Palette.Muted)
                )
            }
        }
    }
}

private fun snowInches(value: String): String =
    if (value.isBlank()) "–" else "$value\""

@Composable
private fun SkiStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(end = 8.dp)
            .background(Palette.Navy.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = Type.Small)
        Spacer(Modifier.height(1.dp))
        Text(
            value.uppercase(),
            style = Type.Body.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun LiftCell(lift: com.localonthe8s.tv.data.LiftStatus, modifier: Modifier = Modifier) {
    // Eight lifts share the board with the snow tiles, so rows stay compact.
    val cellStyle = Type.Small.copy(fontSize = 12.sp)
    Row(
        modifier = modifier.padding(end = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(if (lift.open) Palette.Green else Palette.Red, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = lift.name,
            style = cellStyle,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = lift.status,
            style = cellStyle.copy(
                color = if (lift.open) Palette.Green else Palette.Muted,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
