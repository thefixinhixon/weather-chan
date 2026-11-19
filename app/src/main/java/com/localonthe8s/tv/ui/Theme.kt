package com.localonthe8s.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** WeatherStar 4000-ish palette: deep blue field, cyan data, amber accents. */
object Palette {
    val Ink = Color(0xFF030A1F)
    val Navy = Color(0xFF071A45)
    val NavyLight = Color(0xFF0E2C6B)
    val Panel = Color(0xCC0A1F4C)
    val Line = Color(0x664C7CC4)
    val Cyan = Color(0xFF74E4FF)
    val Amber = Color(0xFFFFC857)
    val White = Color(0xFFF2F7FF)
    val Muted = Color(0xFF9DB4DA)
    val Green = Color(0xFF57E08A)
    val Red = Color(0xFFF2645A)
}

object Type {
    private val Mono = FontFamily.Monospace

    val Brand = TextStyle(
        fontFamily = Mono,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 5.sp,
        color = Palette.White
    )
    val Kicker = TextStyle(
        fontFamily = Mono,
        fontSize = 13.sp,
        letterSpacing = 3.sp,
        color = Palette.Cyan
    )
    val PanelTitle = TextStyle(
        fontFamily = Mono,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 4.sp,
        color = Palette.Amber
    )
    val Hero = TextStyle(
        fontFamily = Mono,
        fontSize = 128.sp,
        fontWeight = FontWeight.Bold,
        color = Palette.White
    )
    val TempLarge = TextStyle(
        fontFamily = Mono,
        fontSize = 42.sp,
        fontWeight = FontWeight.Bold,
        color = Palette.White
    )
    val TempMid = TextStyle(
        fontFamily = Mono,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = Palette.White
    )
    val Body = TextStyle(
        fontFamily = Mono,
        fontSize = 16.sp,
        letterSpacing = 1.sp,
        color = Palette.White
    )
    val BodyDim = TextStyle(
        fontFamily = Mono,
        fontSize = 15.sp,
        letterSpacing = 1.sp,
        color = Palette.Muted
    )
    val Clock = TextStyle(
        fontFamily = Mono,
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = Palette.White
    )
    val Small = TextStyle(
        fontFamily = Mono,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        color = Palette.Muted
    )
    val Ticker = TextStyle(
        fontFamily = Mono,
        fontSize = 15.sp,
        letterSpacing = 2.sp,
        color = Palette.White
    )
    val Search = TextStyle(
        fontFamily = Mono,
        fontSize = 18.sp,
        letterSpacing = 2.sp,
        color = Palette.White
    )
}
