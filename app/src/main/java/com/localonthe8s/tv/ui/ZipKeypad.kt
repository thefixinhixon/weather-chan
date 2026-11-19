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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * D-pad numeric pad for ZIP entry — an onn remote has no number keys, so the digits
 * are a focusable grid. OK presses the focused key, Back closes the pad.
 */
@Composable
fun ZipKeypad(
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var digits by remember { mutableStateOf("") }
    val initialFocus = remember { FocusRequester() }
    BackHandler(enabled = !busy) { onDismiss() }

    LaunchedEffect(Unit) { runCatching { initialFocus.requestFocus() } }

    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Ink.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(560.dp)
                .background(Palette.Panel, RoundedCornerShape(6.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
                .padding(28.dp)
        ) {
            Text("ADD MARKET BY ZIP", style = Type.Kicker)
            Spacer(Modifier.height(16.dp))
            Text(
                text = digits.padEnd(5, '·').toCharArray().joinToString(" "),
                style = Type.Hero.copy(fontSize = 46.sp, letterSpacing = 6.sp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message ?: "FIVE DIGITS, THEN OK",
                style = Type.Small.copy(color = if (message != null) Palette.Amber else Palette.Muted)
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { key ->
                            KeypadKey(
                                label = key,
                                emphasised = key == "OK",
                                enabled = !busy,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (key == "1") Modifier.focusRequester(initialFocus) else Modifier),
                                onPress = {
                                    when (key) {
                                        "DEL" -> digits = digits.dropLast(1)
                                        "OK" -> if (digits.length == 5) onSubmit(digits)
                                        else -> if (digits.length < 5) digits += key
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("D-PAD TO MOVE • OK TO PRESS • BACK TO CANCEL", style = Type.Small)
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    emphasised: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val accent = when {
        emphasised -> Palette.Amber
        else -> Palette.Cyan
    }

    Box(
        modifier = modifier
            .height(62.dp)
            .background(
                color = when {
                    focused -> accent.copy(alpha = 0.22f)
                    else -> Palette.Navy.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = if (focused) accent else Palette.Line.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    if (enabled) onPress()
                    true
                } else {
                    false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = Type.Body.copy(
                color = if (enabled) Palette.White else Palette.Muted,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
