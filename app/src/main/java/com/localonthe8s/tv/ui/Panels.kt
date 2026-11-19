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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localonthe8s.tv.data.DailyPoint
import com.localonthe8s.tv.data.HourlyPoint
import com.localonthe8s.tv.data.Observation
import com.localonthe8s.tv.data.StateBorders
import com.localonthe8s.tv.data.StateOutline
import com.localonthe8s.tv.data.WeatherSnapshot
import com.localonthe8s.tv.data.describe
import com.localonthe8s.tv.data.glyphFor
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
    CountyTown("CARRIZOZO", 33.6420, -105.8770)
)

@Composable
fun RadarPanel(snapshot: WeatherSnapshot, radar: RadarUiState, modifier: Modifier = Modifier) {
    val place = snapshot.place
    val loop = (radar as? RadarUiState.Ready)?.loop
    val context = LocalContext.current
    var borders by remember { mutableStateOf<List<StateOutline>>(emptyList()) }
    var frameIndex by remember(loop) { mutableStateOf(0) }

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

                    // Nearby county towns give the map its detail.
                    COUNTY_TOWNS.forEach { townMark ->
                        if (townMark.lon in radarFrame.west..radarFrame.east &&
                            townMark.lat in radarFrame.south..radarFrame.north
                        ) {
                            val dot = Offset(
                                radarFrame.xFraction(townMark.lon) * size.width,
                                radarFrame.yFraction(townMark.lat) * size.height
                            )
                            drawCircle(Palette.White.copy(alpha = 0.9f), 3f, dot)
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
            Text(
                "LINCOLN COUNTY",
                style = Type.Small.copy(color = Palette.Amber),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
            )
            if (shown != null) {
                Text(
                    text = "LOOP ${shown.ageLabel}",
                    style = Type.Small.copy(color = Palette.Amber),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp)
                )
            }
            // Town names are drawn in-canvas dots above; keep the readout near the crosshair.
            Text(
                place.name.uppercase(),
                style = Type.Small.copy(color = Palette.White),
                modifier = Modifier.align(Alignment.Center).offset(y = 18.dp)
            )
            Text(
                "${peakPrecip}% PEAK PRECIP • RINGS 25/50 MI",
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
