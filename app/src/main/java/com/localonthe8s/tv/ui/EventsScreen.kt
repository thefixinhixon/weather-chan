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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
                val discover = try {
                    EventsFeed.fetch()
                } catch (failure: Exception) {
                    if (state.events.isEmpty()) throw failure
                    state.events.filterNot { it.id.startsWith("gcal-") }
                }
                // The shipped local-events.ics base plus the optional linked Google feed.
                val bundled = runCatching {
                    com.localonthe8s.tv.data.GoogleCalendar.loadBundled(context)
                }.getOrDefault(emptyList())
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
            "$total UPCOMING • RUIDOSO, NM • ALL TIMES MOUNTAIN"
        } else {
            "RUIDOSO, NM • ${EventsFeed.CALENDAR_URL.removePrefix("https://").removePrefix("www.")}"
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
                "PULLING THE EVENTS CALENDAR • DISCOVERRUIDOSO.COM",
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
    }

    // Event flyers are 16:9. Box them at that shape and centre them in the pane so
    // artwork keeps its whole picture instead of being cropped to a square.
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
        Text(                "DISCOVERRUIDOSO.COM COULD NOT BE REACHED. IT RETRIES EVERY 15 MINUTES " +
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
