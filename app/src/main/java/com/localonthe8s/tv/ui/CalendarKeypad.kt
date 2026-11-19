package com.localonthe8s.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * D-pad text entry for the Google Calendar .ics link. A remote has no keyboard,
 * so the URL is built one character at a time from a focusable grid. A USB
 * keyboard also works if one is attached. Back closes without saving.
 */
@Composable
fun CalendarKeypad(
    initial: String,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val initialFocus = remember { FocusRequester() }
    BackHandler(enabled = !busy) { onDismiss() }

    LaunchedEffect(Unit) { runCatching { initialFocus.requestFocus() } }

    val rows = listOf(
        listOf("a", "b", "c", "d", "e", "f", "g", "h"),
        listOf("i", "j", "k", "l", "m", "n", "o", "p"),
        listOf("q", "r", "s", "t", "u", "v", "w", "x"),
        listOf("y", "z", "0", "1", "2", "3", "4", "5"),
        listOf("6", "7", "8", "9", ":", "/", ".", "-"),
        listOf("_", "@", "=", "?", "&", "%", "+", " "),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Ink.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(860.dp)
                .background(Palette.Panel, RoundedCornerShape(6.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
                .padding(24.dp)
        ) {
            Text("GOOGLE CALENDAR LINK", style = Type.Kicker)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (text.isBlank()) "PASTE THE ICAL URL FROM CALENDAR SETTINGS" else text,
                style = Type.Small.copy(color = if (text.isBlank()) Palette.Muted else Palette.Cyan),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message ?: "${text.length} CHARS • OK TO ADD CHAR • BACK TO CANCEL",
                style = Type.Small.copy(color = if (message != null) Palette.Amber else Palette.Muted)
            )
            Spacer(Modifier.height(14.dp))

            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { glyph ->
                        UrlKey(
                            label = glyph,
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (glyph == "a") Modifier.focusRequester(initialFocus) else Modifier),
                            onPress = { if (text.length < 500) text += glyph }
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                UrlAction(label = "DEL", enabled = !busy && text.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    text = text.dropLast(1)
                }
                UrlAction(label = "CLEAR", enabled = !busy && text.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    text = ""
                }
                UrlAction(
                    label = "HTTPS://",
                    enabled = !busy && text.isEmpty(),
                    modifier = Modifier.weight(1f)
                ) { text = "https://" }
                UrlAction(
                    label = "SAVE",
                    enabled = !busy && text.isNotBlank(),
                    emphasised = true,
                    modifier = Modifier.weight(1f)
                ) { onSubmit(text.trim()) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "FIND IT IN GOOGLE CALENDAR → SETTINGS → INTEGRATE CALENDAR → PUBLIC ICAL ADDRESS",
                style = Type.Small
            )
        }
    }
}

@Composable
private fun UrlKey(
    label: String,
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (focused) Palette.Cyan.copy(alpha = 0.22f) else Palette.Navy.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (focused) Palette.Cyan else Palette.Line.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    if (enabled) onPress()
                    true
                } else false
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = Type.Small.copy(color = if (enabled) Palette.White else Palette.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        )
    }
}

@Composable
private fun UrlAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
    onPress: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val accent = if (emphasised) Palette.Amber else Palette.Cyan
    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                color = if (focused) accent.copy(alpha = 0.22f) else Palette.Navy.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(1.dp, if (focused) accent else Palette.Line.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0 &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    if (enabled) onPress()
                    true
                } else false
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = Type.Small.copy(
                color = if (enabled) Palette.White else Palette.Muted,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
