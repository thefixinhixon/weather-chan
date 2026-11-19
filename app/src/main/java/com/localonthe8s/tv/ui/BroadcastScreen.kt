package com.localonthe8s.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localonthe8s.tv.data.CommunityEvent
import com.localonthe8s.tv.data.Place
import com.localonthe8s.tv.data.WeatherSnapshot
import com.localonthe8s.tv.data.describe
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Phase { EVENTS, WEATHER }

/**
 * The broadcast: a community-calendar slideshow first, then one full weather
 * rotation per town in the lineup, then back to events. D-pad left/right steps
 * events or panels, up/down jumps markets, OK pauses.
 */
@Composable
fun BroadcastScreen(
    place: Place,
    refreshToken: Int,
    focusToken: Int,
    musicLabel: String,
    musicOn: Boolean,
    marketIndex: Int,
    marketCount: Int,
    calendarUrl: String,
    onMarket: (Int) -> Unit
) {
    val state = rememberWeather(place, refreshToken)
    val radar = rememberRadar(place, refreshToken)
    val snapshot = state.snapshot()
    val clock = rememberClock(place.timezone)

    var phase by remember { mutableStateOf(Phase.EVENTS) }
    var segment by remember(place) { mutableStateOf(Segment.OPENING) }
    var paused by remember { mutableStateOf(false) }
    var marketBanner by remember(place) { mutableStateOf(true) }
    var eventIndex by remember { mutableIntStateOf(0) }
    var slideToken by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    val calendar = rememberCalendar(refreshToken, calendarUrl)
    val events = calendar.events

    // The lineup is a second focus tree; tearing it down can leave the broadcast with
    // no focused node, and then the D-pad silently stops tuning markets. Ask again
    // once the first frame is placed, not just on the very first composition.
    LaunchedEffect(focusToken) {
        runCatching { focusRequester.requestFocus() }
        delay(200)
        runCatching { focusRequester.requestFocus() }
    }

    // Weather phase: rotate segments; a full rotation advances to the next market,
    // and wrapping past the last market returns to the events slideshow.
    LaunchedEffect(segment, paused, phase, place) {
        if (paused || phase != Phase.WEATHER) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(segment.durationSec * 1000, easing = LinearEasing)
        )
        val next = segment.next()
        segment = next
        if (next == Segment.OPENING) {
            if (marketIndex + 1 >= marketCount) {
                phase = Phase.EVENTS
                eventIndex = 0
            } else {
                onMarket(1)
            }
        }
    }

    // Events phase: one upcoming event at a time, then the weather block runs once.
    LaunchedEffect(phase, paused, events.size, slideToken, calendar.loading) {
        if (paused || phase != Phase.EVENTS) return@LaunchedEffect
        if (events.isEmpty()) {
            // Still linking the feed — hold the "pulling events" card, don't skip.
            if (calendar.loading) return@LaunchedEffect
            // Truly nothing to show — skip straight to weather instead of idling.
            phase = Phase.WEATHER
            segment = Segment.OPENING
            return@LaunchedEffect
        }
        if (eventIndex >= events.size) eventIndex = 0
        progress.snapTo(0f)
        while (true) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(EVENT_SLIDE_SECONDS * 1000, easing = LinearEasing)
            )
            if (eventIndex + 1 >= events.size) {
                phase = Phase.WEATHER
                segment = Segment.OPENING
                eventIndex = 0
                progress.snapTo(0f)
                break
            }
            eventIndex += 1
            progress.snapTo(0f)
        }
    }

    // Entering weather always opens on the bumper, like the old cable feed.
    LaunchedEffect(phase) {
        if (phase == Phase.WEATHER) {
            segment = Segment.OPENING
            paused = false
        }
    }

    // A cable box announces the channel you landed on, then gets out of the way.
    LaunchedEffect(place) {
        marketBanner = true
        delay(2600)
        marketBanner = false
    }

    fun stepSlide(delta: Int) {
        val count = events.size
        if (count == 0) return
        eventIndex = ((eventIndex + delta) % count + count) % count
        slideToken++
    }

    val showingWeather = phase == Phase.WEATHER

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B2E6E), Color(0xFF071A45), Color(0xFF030A1F))
                )
            )
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (showingWeather) segment = segment.previous() else stepSlide(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        if (showingWeather) segment = segment.next() else stepSlide(1)
                        true
                    }
                    Key.DirectionUp -> {
                        if (showingWeather) onMarket(1) else {
                            phase = Phase.WEATHER
                            segment = Segment.OPENING
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (showingWeather) onMarket(-1) else stepSlide(-1)
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        paused = !paused
                        true
                    }
                    else -> false
                }
            }
    ) {
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 14.dp)) {
            BroadcastHeader(
                place = place,
                clock = clock,
                live = state.isLive(),
                musicOn = musicOn,
                musicLabel = musicLabel,
                kicker = if (showingWeather) {
                    place.displayName.uppercase(Locale.US)
                } else {
                    "RUIDOSO LOCAL • COMMUNITY CALENDAR"
                }
            )
            Spacer(Modifier.height(10.dp))
            SegmentProgress(progress = progress.value)
            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Crossfade(
                    targetState = showingWeather,
                    animationSpec = tween(420),
                    label = "phase"
                ) { weather ->
                    if (weather) {
                        Crossfade(
                            targetState = segment,
                            animationSpec = tween(420),
                            label = "segment"
                        ) { current ->
                            ForecastContent(current, place, state, snapshot, radar)
                        }
                    } else {
                        val count = events.size
                        EventsPanel(
                            state = calendar,
                            index = if (count == 0) 0 else eventIndex % count,
                            next = if (count == 0) null else events[(eventIndex + 1) % count],
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TickerBar(
                text = if (showingWeather) tickerText(snapshot, place) else calendarTicker(events),
                badge = if (showingWeather) "LOCAL WEATHER" else "COMMUNITY EVENTS",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Scanlines(Modifier.fillMaxSize())
        Vignette(Modifier.fillMaxSize())

        if (paused) {
            PausedBadge(Modifier.align(Alignment.TopEnd).padding(top = 118.dp, end = 56.dp))
        }
        if (state is WeatherUiState.Stale) {
            Text(
                text = "LAST GOOD DATA • RETRYING",
                style = Type.Small.copy(color = Palette.Red),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 60.dp)
            )
        }
        if (marketBanner && showingWeather) {
            MarketBanner(
                place = place,
                channel = (marketIndex + 1).coerceAtLeast(1),
                total = marketCount,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)
            )
        }
    }
}

@Composable
private fun ForecastContent(
    segment: Segment,
    place: Place,
    state: WeatherUiState,
    snapshot: WeatherSnapshot?,
    radar: RadarUiState
) {
    val modifier = Modifier.fillMaxSize()
    when (segment) {
        Segment.OPENING -> OpeningPanel(place, snapshot, modifier)
        Segment.CURRENT -> if (snapshot != null) {
            CurrentPanel(snapshot, modifier)
        } else {
            SignalPanel(state, modifier)
        }
        Segment.OBSERVATIONS -> if (snapshot != null) {
            ObservationsPanel(snapshot, modifier)
        } else {
            SignalPanel(state, modifier)
        }
        Segment.SEVEN_DAY -> if (snapshot != null) {
            SevenDayPanel(snapshot, modifier)
        } else {
            SignalPanel(state, modifier)
        }
        Segment.HOURLY -> if (snapshot != null) {
            HourlyPanel(snapshot, modifier)
        } else {
            SignalPanel(state, modifier)
        }
        Segment.RADAR -> if (snapshot != null) {
            RadarPanel(snapshot, radar, modifier)
        } else {
            SignalPanel(state, modifier)
        }
        Segment.ALMANAC -> if (snapshot != null) {
            AlmanacPanel(snapshot, modifier)
        } else {
            SignalPanel(state, modifier)
        }
    }
}

@Composable
private fun MarketBanner(place: Place, channel: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Palette.Ink.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Amber.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CH " + String.format(Locale.US, "%02d", channel),
            style = Type.Body.copy(color = Palette.Amber, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.width(10.dp))
        Text("OF ${String.format(Locale.US, "%02d", total)}", style = Type.Small)
        Spacer(Modifier.width(18.dp))
        Text(
            text = place.displayName.uppercase(Locale.US),
            style = Type.Body.copy(color = Palette.White)
        )
    }
}

private fun calendarTicker(events: List<CommunityEvent>): String {
    if (events.isEmpty()) {
        return "PULLING THE GREENERY ROOM CALENDAR • JAZZ KEEPS PLAYING • LOCAL WEATHER FOLLOWS THE EVENTS"
    }
    return buildString {
        append("THE GREENERY ROOM • RUIDOSO EVENTS • ")
        events.take(3).forEachIndexed { index, event ->
            if (index > 0) append("  •  ")
            append(event.dateLabel.uppercase(Locale.US))
            append(' ').append(event.timeLabel.uppercase(Locale.US))
            append(" — ").append(event.title.uppercase(Locale.US))
            append(" AT ").append(event.venue.uppercase(Locale.US))
        }
        append("  •  ALL TIMES MOUNTAIN  •  WEATHER FOLLOWS THE CALENDAR")
    }
}

private fun tickerText(snapshot: WeatherSnapshot?, place: Place): String {
    val hint = "EVENTS FIRST • THEN WEATHER • UP/DOWN JUMPS TOWNS • BACK FOR SETUP"
    if (snapshot == null) {
        return "ACQUIRING REGIONAL DATA FOR ${place.displayName.uppercase(Locale.US)} • $hint"
    }
    val current = snapshot.current
    val today = snapshot.daily.firstOrNull()
    return buildString {
        append(place.name.uppercase(Locale.US)).append(' ')
        append(current.tempF).append("° ").append(describe(current.code).uppercase(Locale.US))
        append(" • FEELS LIKE ").append(current.feelsLikeF).append("°")
        append(" • WIND ").append(current.windDir).append(' ').append(current.windMph).append(" MPH")
        append(" • HUMIDITY ").append(current.humidity).append('%')
        if (today != null) {
            append(" • TODAY HIGH ").append(today.highF)
            append("° LOW ").append(today.lowF).append("°")
        }
        append(" • NO STORM FATIGUE • ").append(hint)
    }
}
