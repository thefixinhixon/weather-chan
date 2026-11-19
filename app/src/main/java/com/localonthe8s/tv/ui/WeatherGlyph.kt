package com.localonthe8s.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.localonthe8s.tv.data.Glyph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFFFC857)
private val CloudColor = Color(0xFFDCE9FF)

@Composable
fun WeatherGlyph(glyph: Glyph, dimension: Dp, tint: Color = Palette.Cyan) {
    Canvas(Modifier.size(dimension)) { drawGlyph(glyph, tint) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, tint: Color) {
    val unit = size.minDimension / 100f

    when (glyph) {
        Glyph.CLEAR -> drawSun(Offset(50f * unit, 50f * unit), 21f * unit, true)

        Glyph.PARTLY -> {
            drawSun(Offset(66f * unit, 34f * unit), 15f * unit, true)
            drawCloud(Offset(45f * unit, 58f * unit), 64f * unit, CloudColor)
        }

        Glyph.CLOUDY -> {
            drawCloud(Offset(38f * unit, 45f * unit), 56f * unit, CloudColor.copy(alpha = 0.7f))
            drawCloud(Offset(56f * unit, 60f * unit), 66f * unit, CloudColor)
        }

        Glyph.FOG -> {
            drawCloud(Offset(50f * unit, 38f * unit), 58f * unit, CloudColor.copy(alpha = 0.6f))
            for (index in 0 until 3) {
                val y = (64f + index * 11f) * unit
                drawLine(
                    color = tint,
                    start = Offset(20f * unit, y),
                    end = Offset(80f * unit, y),
                    strokeWidth = 4f * unit,
                    cap = StrokeCap.Round
                )
            }
        }

        Glyph.RAIN, Glyph.SHOWERS -> {
            drawCloud(Offset(50f * unit, 40f * unit), 68f * unit, CloudColor)
            val drops = if (glyph == Glyph.RAIN) 4 else 3
            for (index in 0 until drops) {
                val x = (26f + index * (48f / (drops - 1))) * unit
                drawLine(
                    color = tint,
                    start = Offset(x, 66f * unit),
                    end = Offset(x - 4f * unit, 84f * unit),
                    strokeWidth = 4f * unit,
                    cap = StrokeCap.Round
                )
            }
        }

        Glyph.SNOW -> {
            drawCloud(Offset(50f * unit, 40f * unit), 68f * unit, CloudColor)
            for (index in 0 until 3) {
                val x = (32f + index * 18f) * unit
                drawCircle(tint, 4f * unit, Offset(x, 76f * unit))
                drawCircle(tint.copy(alpha = 0.7f), 3f * unit, Offset(x + 5f * unit, 87f * unit))
            }
        }

        Glyph.STORM -> {
            drawCloud(Offset(50f * unit, 38f * unit), 70f * unit, CloudColor)
            val bolt = Path().apply {
                moveTo(53f * unit, 60f * unit)
                lineTo(42f * unit, 78f * unit)
                lineTo(51f * unit, 78f * unit)
                lineTo(45f * unit, 94f * unit)
                lineTo(64f * unit, 71f * unit)
                lineTo(54f * unit, 71f * unit)
                lineTo(61f * unit, 60f * unit)
                close()
            }
            drawPath(bolt, Color(0xFFFFD166))
            drawLine(tint, Offset(26f * unit, 68f * unit), Offset(22f * unit, 82f * unit), 3f * unit, StrokeCap.Round)
            drawLine(tint, Offset(78f * unit, 68f * unit), Offset(74f * unit, 82f * unit), 3f * unit, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, rays: Boolean) {
    if (rays) {
        for (index in 0 until 8) {
            val angle = index * PI / 4.0
            val inner = Offset(
                center.x + cos(angle).toFloat() * radius * 1.45f,
                center.y + sin(angle).toFloat() * radius * 1.45f
            )
            val outer = Offset(
                center.x + cos(angle).toFloat() * radius * 1.95f,
                center.y + sin(angle).toFloat() * radius * 1.95f
            )
            drawLine(SunColor, inner, outer, radius * 0.16f, StrokeCap.Round)
        }
    }
    drawCircle(SunColor, radius, center)
}

private fun DrawScope.drawCloud(center: Offset, width: Float, color: Color) {
    val radius = width * 0.20f
    drawCircle(color, radius, Offset(center.x - width * 0.26f, center.y + radius * 0.2f))
    drawCircle(color, radius * 1.35f, Offset(center.x, center.y - radius * 0.45f))
    drawCircle(color, radius * 1.05f, Offset(center.x + width * 0.26f, center.y + radius * 0.15f))
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - width * 0.36f, center.y),
        size = Size(width * 0.72f, radius * 1.3f),
        cornerRadius = CornerRadius(radius * 0.4f)
    )
}
