package com.localonthe8s.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localonthe8s.tv.data.Place

@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Palette.NavyLight, Palette.Navy, Palette.Ink)
                )
            ),
        content = content
    )
}

/** CRT scanlines drawn over the whole broadcast. */
@Composable
fun Scanlines(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.16f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += step
        }
    }
}

@Composable
fun Vignette(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = center,
                radius = size.maxDimension * 0.7f
            )
        )
    }
}

@Composable
fun BroadcastHeader(
    place: Place,
    clock: ClockTick,
    live: Boolean,
    musicOn: Boolean,
    musicLabel: String,
    kicker: String = place.displayName.uppercase(),
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Palette.Green, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("G", style = Type.Brand.copy(color = Palette.Ink, letterSpacing = 0.sp))
                }
                Spacer(Modifier.width(12.dp))
                Text("THE GREENERY ROOM", style = Type.Brand)
            }
            Spacer(Modifier.height(4.dp))
            Text(kicker, style = Type.Kicker)
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.End) {
            Text(clock.timeLabel, style = Type.Clock)
            Text(clock.dateLabel, style = Type.Kicker.copy(color = Palette.Muted))
        }

        Spacer(Modifier.width(24.dp))

        Column(horizontalAlignment = Alignment.End) {
            StatusChip(if (live) "LIVE" else "SIGNAL LOST", if (live) Palette.Green else Palette.Red)
            Spacer(Modifier.height(6.dp))
            StatusChip(musicLabel, if (musicOn) Palette.Cyan else Palette.Muted)
        }
    }
}

@Composable
fun StatusChip(label: String, color: Color) {
    Text(
        text = label,
        style = Type.Small.copy(color = color),
        modifier = Modifier
            .background(Palette.Panel, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun SegmentProgress(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawRect(Palette.Line.copy(alpha = 0.35f))
        drawRect(
            color = Palette.Amber,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TickerBar(
    text: String,
    badge: String = "LOCAL FORECAST",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .background(Palette.Amber),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = " $badge ",
            style = Type.Small.copy(color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            Text(
                text = text,
                style = Type.Ticker.copy(color = Palette.Ink, fontSize = 14.sp),
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )
        }
    }
}

@Composable
fun PausedBadge(modifier: Modifier = Modifier) {
    Text(
        text = "PAUSED",
        style = Type.Small.copy(color = Palette.Amber, fontWeight = FontWeight.Bold),
        modifier = modifier
            .background(Palette.Panel, RoundedCornerShape(3.dp))
            .border(1.dp, Palette.Amber, RoundedCornerShape(3.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun PanelFrame(
    title: String,
    caption: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Panel, RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Type.PanelTitle)
            if (!caption.isNullOrBlank()) {
                Spacer(Modifier.width(16.dp))
                Text(caption, style = Type.Small)
            }
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}
