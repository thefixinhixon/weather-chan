package com.localonthe8s.tv.ui

enum class Segment(val title: String, val durationSec: Int) {
    OPENING("LOCAL FORECAST", 7),
    CURRENT("CURRENT CONDITIONS", 13),
    OBSERVATIONS("REGIONAL OBSERVATIONS", 11),
    SEVEN_DAY("7-DAY OUTLOOK", 15),
    HOURLY("HOURLY FORECAST", 12),
    RADAR("REGIONAL DOPPLER", 14),
    ALMANAC("ALMANAC", 10);

    fun next(): Segment = values()[(ordinal + 1) % values().size]

    fun previous(): Segment = values()[(ordinal + values().size - 1) % values().size]
}

/**
 * Legacy channel clock from the "on the 8s" era. The broadcast no longer gates on
 * wall-clock minutes — it runs events first, then one weather rotation per town.
 * Kept so old references still compile; new code should not use it.
 */
object AirSchedule {

    const val AIRING_MINUTES = 5

    @Deprecated("Clock gating removed: the loop is events-then-weather.")
    fun onAir(minute: Int): Boolean = false

    @Deprecated("Clock gating removed: the loop is events-then-weather.")
    fun minutesUntilAir(minute: Int): Int = 0
}
