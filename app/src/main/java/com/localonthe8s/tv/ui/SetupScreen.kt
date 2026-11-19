package com.localonthe8s.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localonthe8s.tv.audio.RadioStation
import com.localonthe8s.tv.data.LocationStore
import com.localonthe8s.tv.data.Place
import com.localonthe8s.tv.data.ZipLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The editable channel setup. Everything is reachable with a remote's D-pad: browse
 * and tune in, remove the focused market, add a market by ZIP through the numeric
 * pad, link a Google Calendar .ics feed, and pick which station the music bed plays.
 */
@Composable
fun SetupScreen(
    playlist: List<Place>,
    current: Place?,
    station: RadioStation,
    musicLabel: String,
    calendarUrl: String,
    onPick: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onAdd: (Place) -> Unit,
    onStation: (RadioStation) -> Unit,
    onCalendarSave: (String) -> Unit,
    onCalendarClear: () -> Unit,
    onResume: (() -> Unit)?
) {
    var focusedPlace by remember { mutableStateOf(current ?: playlist.firstOrNull()) }
    var showKeypad by remember { mutableStateOf(false) }
    var zipBusy by remember { mutableStateOf(false) }
    var zipMessage by remember { mutableStateOf<String?>(null) }
    var showCalendarPad by remember { mutableStateOf(false) }
    var calendarMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val currentIndex = LocationStore.indexOf(playlist, current)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(currentIndex) {
        runCatching {
            if (currentIndex > 0) listState.scrollToItem(currentIndex)
            focusRequester.requestFocus()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("THE GREENERY ROOM", style = Type.Brand)
                Spacer(Modifier.width(18.dp))
                Text("CHANNEL SETUP • RUIDOSO LOCAL", style = Type.Kicker)
                Spacer(Modifier.weight(1f))
                Text(musicLabel, style = Type.Small)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${playlist.size} MARKETS IN THIS LINEUP • D-PAD TO BROWSE • OK TO TUNE IN • BACK GOES ON AIR",
                style = Type.BodyDim
            )
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp)
                ) {
                    itemsIndexed(playlist) { index, market ->
                        MarketRow(
                            place = market,
                            channel = index + 1,
                            onAir = LocationStore.sameMarket(market, current),
                            onClick = { onPick(market) },
                            onFocus = { focusedPlace = market },
                            modifier = if (index == currentIndex) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                }

                // One D-pad press right of any row lands on these actions, instead of
                // making the viewer scroll a 46-row list to reach the footer.
                Column(Modifier.width(430.dp).fillMaxHeight()) {
                    Text("SELECTED", style = Type.Small)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = focusedPlace?.displayName?.uppercase(Locale.US) ?: "NONE",
                        style = Type.Body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    FocusButton(
                        label = "REMOVE MARKET",
                        enabled = playlist.size > 1 && focusedPlace != null,
                        onClick = { focusedPlace?.let(onRemove) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    FocusButton(
                        label = "ADD BY ZIP",
                        onClick = {
                            zipMessage = null
                            showKeypad = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(22.dp))
                    Text("MY CALENDAR", style = Type.Small)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (calendarUrl.isBlank()) "GREENERY ROOM FILE + DISCOVERRUIDOSO" else "GOOGLE LINKED • ${calendarUrl.take(38)}…",
                        style = Type.Small,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    FocusButton(
                        label = if (calendarUrl.isBlank()) "LINK GOOGLE CALENDAR" else "CHANGE CALENDAR LINK",
                        onClick = {
                            calendarMessage = null
                            showCalendarPad = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (calendarUrl.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        FocusButton(
                            label = "UNLINK CALENDAR",
                            onClick = onCalendarClear,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    Text("STATION", style = Type.Small)
                    Spacer(Modifier.height(8.dp))
                    RadioStation.entries.forEach { option ->
                        FocusButton(
                            label = option.label,
                            selected = option == station,
                            onClick = { onStation(option) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.weight(1f))
                    if (onResume != null) {
                        FocusButton(
                            label = "BACK TO BROADCAST",
                            onClick = onResume,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (showCalendarPad) {
            CalendarKeypad(
                initial = calendarUrl,
                busy = false,
                message = calendarMessage,
                onDismiss = {
                    calendarMessage = null
                    showCalendarPad = false
                },
                onSubmit = { url ->
                    if (!url.startsWith("http")) {
                        calendarMessage = "LINK MUST START WITH HTTP"
                    } else {
                        onCalendarSave(url)
                        calendarMessage = null
                        showCalendarPad = false
                    }
                }
            )
        }

        if (showKeypad) {
            ZipKeypad(
                busy = zipBusy,
                message = zipMessage,
                onDismiss = {
                    zipMessage = null
                    showKeypad = false
                },
                onSubmit = { zip ->
                    zipBusy = true
                    zipMessage = "LOOKING UP $zip"
                    scope.launch {
                        try {
                            val found = ZipLookup.resolve(zip)
                            onAdd(found)
                            focusedPlace = found
                            zipBusy = false
                            zipMessage = null
                            showKeypad = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            zipBusy = false
                            zipMessage = (failure.message ?: "LOOKUP FAILED").uppercase(Locale.US)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MarketRow(
    place: Place,
    channel: Int,
    onAir: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                color = if (focused) Palette.Cyan.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (focused) Palette.Cyan else Palette.Line.copy(alpha = 0.4f),
                shape = RoundedCornerShape(4.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format(Locale.US, "%02d", channel),
            style = Type.Small.copy(color = Palette.Amber),
            modifier = Modifier.width(42.dp)
        )
        Text(
            text = place.displayName.uppercase(Locale.US),
            style = Type.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onAir) {
            Spacer(Modifier.width(16.dp))
            Text("ON AIR", style = Type.Small.copy(color = Palette.Green))
        }
        Spacer(Modifier.width(18.dp))
        Text(
            text = "${coordinate(place.latitude)} / ${coordinate(place.longitude)}",
            style = Type.Small,
            maxLines = 1
        )
    }
}

@Composable
private fun FocusButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val accent = if (enabled) Palette.Amber else Palette.Muted

    Box(
        modifier = modifier
            .background(
                color = when {
                    selected -> Palette.Amber.copy(alpha = 0.9f)
                    focused -> Palette.Amber.copy(alpha = 0.22f)
                    else -> Palette.Panel
                },
                shape = RoundedCornerShape(4.dp)
            )
            .border(1.dp, accent.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                // repeatCount guards against a held OK: each auto-repeat would
                // otherwise fire again, and on REMOVE that eats the whole lineup.
                if (event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0 &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    if (enabled) onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            style = Type.Small.copy(
                color = when {
                    selected -> Palette.Ink
                    enabled -> Palette.White
                    else -> Palette.Muted
                },
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun coordinate(value: Double): String = String.format(Locale.US, "%.2f", value)
