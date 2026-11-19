package com.localonthe8s.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ClockTick(
    val timeLabel: String,
    val dateLabel: String,
    val minute: Int,
    val second: Int
)

/** Ticks twice a second so the on-screen seconds always look live. */
@Composable
fun rememberClock(timezone: String): ClockTick {
    val zone = remember(timezone) { TimeZone.getTimeZone(timezone) }
    val timeFormat = remember(zone) {
        SimpleDateFormat("h:mm:ss a", Locale.US).apply { timeZone = zone }
    }
    val dateFormat = remember(zone) {
        SimpleDateFormat("EEE MMM d", Locale.US).apply { timeZone = zone }
    }
    var tick by remember(zone) {
        mutableStateOf(ClockTick(timeLabel = "--:--:--", dateLabel = "---", minute = 0, second = 0))
    }

    LaunchedEffect(zone) {
        while (true) {
            val now = Date()
            val calendar = Calendar.getInstance(zone).apply { time = now }
            tick = ClockTick(
                timeLabel = timeFormat.format(now).uppercase(Locale.US),
                dateLabel = dateFormat.format(now).uppercase(Locale.US),
                minute = calendar.get(Calendar.MINUTE),
                second = calendar.get(Calendar.SECOND)
            )
            delay(500)
        }
    }
    return tick
}
