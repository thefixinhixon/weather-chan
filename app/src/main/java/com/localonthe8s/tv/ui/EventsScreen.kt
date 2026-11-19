package com.localonthe8s.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localonthe8s.tv.data.CommunityEvent
import com.localonthe8s.tv.data.EventArtwork
import com.localonthe8s.tv.data.EventsFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale

/** How long one event card stays on screen before the slideshow advances. */
const val EVENT_SLIDE_SECONDS = 11

private const val CALENDAR_REFRESH_MILLIS = 15 * 60 * 1000L

data class CalendarState(
    val events: List<CommunityEvent> = emptyList(),
    val error: String? = null,
    val fetchedAtMillis: Long = 0L,
    val loading: Boolean = true
)

/**
 * Pulls DiscoverRuidoso + the optional Google Calendar .ics on launch, on manual
 * refresh and every fifteen minutes. A refresh that fails keeps the listings
 * already on screen, so the slideshow never goes blank between polls.
 */
@Composable
fun rememberCalendar(refreshToken: Int, calendarUrl: String = ""): CalendarState {
    var state by remember { mutableStateOf(CalendarState()) }
    val context = LocalContext.current

    LaunchedEffect(refreshToken, calendarUrl) {
        while (true) {
            try {
                // The shipped Greenery Room base calendar loads first so the screen
                // is never blank while the network feed is still linking.
                val bundled = runCatching {
                    com.localonthe8s.tv.data.GoogleCalendar.loadBundled(context)
                }.getOrDefault(emptyList())
                val discover = try {
                    EventsFeed.fetch()
                } catch (failure: Exception) {
                    val keep = state.events.filterNot { it.id.startsWith("gcal-") }
                    if (keep.isNotEmpty()) keep else if (bundled.isNotEmpty()) emptyList() else throw failure
                }
                val mine = if (calendarUrl.isNotBlank()) {
                    runCatching { com.localonthe8s.tv.data.GoogleCalendar.fetch(calendarUrl) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val merged = (bundled + mine + discover)
                    .distinctBy { it.id }
                    .sortedWith(compareBy({ it.startsOn }, { it.timeLabel }, { it.title }))
                    .take(com.localonthe8s.tv.data.EventsFeed.MAX_EVENTS)

                state = CalendarState(
                    events = merged,
                    error = null,
                    fetchedAtMillis = System.currentTimeMillis(),
                    loading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state = state.copy(error = failure.message ?: "NETWORK ERROR", loading = false)
            }
            delay(CALENDAR_REFRESH_MILLIS)
        }
    }

    return state
}

/**
 * The between-the-8s programming: one upcoming Ruidoso event per screen, artwork on
 * the left, dates and times on the right, jazz still running underneath.
 */
@Composable
fun EventsPanel(
    state: CalendarState,
    index: Int,
    next: CommunityEvent?,
    modifier: Modifier = Modifier
) {
    // The picture for the following card is fetched while this one is on screen, so
    // the slideshow does not flash its placeholder at every change.
    LaunchedEffect(next?.id) {
        val url = next?.artworkUrl ?: return@LaunchedEffect
        EventArtwork.load(url)
    }

    val total = state.events.size
    PanelFrame(
        title = "COMMUNITY CALENDAR",
        caption = if (total > 0) {
            "$total UPCOMING • THE GREENERY ROOM • ALL TIMES MOUNTAIN"
        } else {
            "THE GREENERY ROOM • RUIDOSO, NM"
        },
        modifier = modifier
    ) {
        val event = state.events.getOrNull(index)
        when {
            event != null -> Crossfade(
                targetState = event,
                animationSpec = tween(520),
                label = "event"
            ) { shown ->
                Row(Modifier.fillMaxSize()) {
                    EventArtworkPane(shown, Modifier.weight(0.92f).fillMaxHeight())
                    Spacer(Modifier.width(30.dp))
                    EventDetails(
                        event = shown,
                        position = index,
                        total = total,
                        updatedAt = state.fetchedAtMillis,
                        stale = state.error != null,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
            state.error != null -> CalendarDown(state.error)
            else -> Text(
                "PULLING THE GREENERY ROOM CALENDAR • EVENTS APPEAR HERE",
                style = Type.BodyDim
            )
        }
    }
}

@Composable
private fun EventArtworkPane(event: CommunityEvent, modifier: Modifier = Modifier) {
    var bitmap by remember(event.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(event.id) {
        val url = event.artworkUrl ?: return@LaunchedEffect
        bitmap = EventArtwork.cached(url)?.asImageBitmap() ?: EventArtwork.load(url)?.asImageBitmap()
    }        // Google Calendar ATTACH art and DiscoverRuidoso thumbs share this pane.
        // Event flyers are 16:9. Box them at that shape and centre them in the pane so
        // artwork keeps its whole picture instead of being cropped to a square.
        val isGreenery = event.id.startsWith("gcal-")
        Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Palette.Navy.copy(alpha = 0.6f))
                .border(1.dp, Palette.Line.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
        val art = bitmap
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isGreenery) {
            GreeneryFlyer(event, Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(Palette.NavyLight, Palette.Ink))
                    )
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        event.monthLabel.ifBlank { "RUID" },
                        style = Type.Kicker.copy(color = Palette.Amber)
                    )
                    Text(
                        event.dayLabel.ifBlank { "OSO" },
                        style = Type.Hero.copy(fontSize = 84.sp, color = Palette.White.copy(alpha = 0.85f))
                    )
                    Text("DISCOVERRUIDOSO", style = Type.Small)
                }
            }
        }
        }
    }
}

/**
 * Offline fallback flyer for Greenery Room .ics events whose ATTACH artwork
 * hasn't loaded (offline, slow link, or an event with no attachment yet).
 * Renders per-event artwork (bingo card / film strip / closed sign / pop-up
 * leaf) so ICS cards never fall back to the bare date placeholder.
 */
@Composable
private fun GreeneryFlyer(event: CommunityEvent, modifier: Modifier = Modifier) {
    val title = event.title.uppercase(java.util.Locale.US)
    val kind = when {
        title.contains("BINGO") -> FlyerKind.BINGO
        title.contains("MOVIE") -> FlyerKind.MOVIE
        title.contains("CLOSED") -> FlyerKind.CLOSED
        title.contains("POP") || title.contains("PURE") || title.contains("TAKEOVER") -> FlyerKind.POPUP
        else -> FlyerKind.GENERAL
    }
    val bg = when (kind) {
        FlyerKind.MOVIE -> listOf(Color(0xFF3B1D5E), Color(0xFF0E1A3D))
        FlyerKind.BINGO -> listOf(Color(0xFF0C4A3E), Color(0xFF082A24))
        FlyerKind.CLOSED -> listOf(Color(0xFF5E1D1D), Color(0xFF2A0E0E))
        FlyerKind.POPUP -> listOf(Color(0xFF1D4A1E), Color(0xFF0B2410))
        FlyerKind.GENERAL -> listOf(Color(0xFF14532D), Color(0xFF071A10))
    }
    Box(
        modifier
            .background(Brush.linearGradient(bg))
            .border(1.dp, Palette.Amber.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Dotted lounge-light texture behind the flyer copy.
            val dot = Color.White.copy(alpha = 0.07f)
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else 28f
                while (x < size.width) {
                    drawCircle(dot, 3.2f, Offset(x, y))
                    x += 56f
                }
                y += 52f
                row++
            }
            // Gold marquee rails top and bottom.
            drawRect(
                Palette.Amber.copy(alpha = 0.85f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, 6f)
            )
            drawRect(
                Palette.Amber.copy(alpha = 0.85f),
                topLeft = Offset(0f, size.height - 6f),
                size = androidx.compose.ui.geometry.Size(size.width, 6f)
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("THE GREENERY ROOM", style = Type.Kicker.copy(color = Palette.Amber))
            Spacer(Modifier.height(6.dp))
            Text(
                when (kind) {
                    FlyerKind.BINGO -> "B • I • N • G • O"
                    FlyerKind.MOVIE -> "▸ NOW SHOWING ◂"
                    FlyerKind.CLOSED -> "• HOLIDAY HOURS •"
                    FlyerKind.POPUP -> "◈ LOUNGE TAKEOVER ◈"
                    FlyerKind.GENERAL -> "✦ RUIDOSO • NM ✦"
                },
                style = Type.Small.copy(color = Palette.White.copy(alpha = 0.85f))
            )
            Spacer(Modifier.height(10.dp))
            // Bingo card grid doubles as the flyer visual for bingo nights.
            if (kind == FlyerKind.BINGO) {
                BingoGrid(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
            }
            if (kind == FlyerKind.MOVIE) {
                FilmStrip(Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            Text(
                event.title.uppercase(java.util.Locale.US),
                style = Type.Body.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.White
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                (event.timeLabel + " • " + event.dateLabel).uppercase(java.util.Locale.US),
                style = Type.Small.copy(color = Palette.Cyan),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text("MIDTOWN • ALTO • CARRIZOZO", style = Type.Small.copy(color = Palette.Amber))
        }
    }
}

private enum class FlyerKind { BINGO, MOVIE, CLOSED, POPUP, GENERAL }

@Composable
private fun BingoGrid(modifier: Modifier = Modifier) {
    val free = listOf("B", "I", "N", "G", "O")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        free.forEachIndexed { i, letter ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == 2) Palette.Amber else Palette.White.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter,
                    style = Type.Small.copy(
                        color = Palette.Ink,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun FilmStrip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("◼ ◼ ◼  FILM  ◼ ◼ ◼", style = Type.Small.copy(color = Palette.White.copy(alpha = 0.9f)))
    }
}

@Composable
private fun EventDetails(
    event: CommunityEvent,
    position: Int,
    total: Int,
    updatedAt: Long,
    stale: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .background(Palette.Amber, RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    event.monthLabel,
                    style = Type.Small.copy(color = Palette.Ink, fontWeight = FontWeight.Bold)
                )
                Text(
                    event.dayLabel,
                    style = Type.TempLarge.copy(color = Palette.Ink, fontSize = 34.sp)
                )
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    event.timeLabel.uppercase(Locale.US),
                    style = Type.TempMid.copy(color = Palette.Cyan, fontSize = 24.sp)
                )
                Spacer(Modifier.height(4.dp))
                Text(event.dateLabel, style = Type.Small)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = event.title.uppercase(Locale.US),
            style = Type.Body.copy(
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.White
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "• ${event.venue.uppercase(Locale.US)}",
            style = Type.Kicker,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (event.category != null) {
            Spacer(Modifier.height(12.dp))
            StatusChip(event.category.uppercase(Locale.US), Palette.Amber)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = event.description.uppercase(Locale.US),
            style = Type.BodyDim.copy(fontSize = 14.sp, letterSpacing = 1.sp),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EVENT ${pad(position + 1)} OF ${pad(total)}",
                style = Type.Small.copy(color = Palette.Amber)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    stale -> "CALENDAR FEED RETRYING"
                    else -> updatedLabel(updatedAt)
                },
                style = Type.Small.copy(color = if (stale) Palette.Red else Palette.Muted)
            )
        }
    }
}

@Composable
private fun CalendarDown(error: String) {
    Column {
        Text("CALENDAR LINK LOST", style = Type.PanelTitle.copy(color = Palette.Red))
        Spacer(Modifier.height(14.dp))
        Text(error.uppercase(Locale.US), style = Type.Body)
        Spacer(Modifier.height(12.dp))
        Text(                "THE CALENDAR FEED COULD NOT BE REACHED. IT RETRIES EVERY 15 MINUTES " +
                "AND THE LOCAL WEATHER STILL FOLLOWS THE EVENTS.",
            style = Type.BodyDim
        )
    }
}

private fun pad(value: Int): String = String.format(Locale.US, "%02d", value)

private fun updatedLabel(millis: Long): String {
    if (millis <= 0L) return "CALENDAR LINKING"
    val minutes = ((System.currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "CALENDAR UPDATED JUST NOW"
        minutes < 60 -> "CALENDAR UPDATED $minutes MIN AGO"
        else -> "CALENDAR UPDATED ${minutes / 60} H AGO"
    }
}
