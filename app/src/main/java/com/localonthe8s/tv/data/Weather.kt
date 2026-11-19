package com.localonthe8s.tv.data

/** A market the broadcast can be localized to. */
data class Place(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String
) {
    val displayName: String
        get() = if (region.isBlank()) name else "$name, $region"
}

data class CurrentConditions(
    val tempF: Int,
    val feelsLikeF: Int,
    val humidity: Int,
    val windMph: Int,
    val windDir: String,
    val pressureInHg: String,
    val cloudCover: Int,
    val code: Int,
    val isDay: Boolean,
    val observedAtLabel: String
)

data class HourlyPoint(
    val label: String,
    val tempF: Int,
    val precipChance: Int,
    val code: Int
)

data class DailyPoint(
    val label: String,
    val highF: Int,
    val lowF: Int,
    val precipChance: Int,
    val code: Int
)

/** A reading from an automated point roughly 35 miles out in one compass direction. */
data class Observation(
    val label: String,
    val tempF: Int,
    val code: Int
)

data class WeatherSnapshot(
    val place: Place,
    val current: CurrentConditions,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val observations: List<Observation>,
    val sunrise: String,
    val sunset: String,
    val daylight: String,
    val timezoneAbbrev: String,
    val fetchedAtMillis: Long
)

enum class Glyph { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SHOWERS, SNOW, STORM }

fun glyphFor(code: Int): Glyph = when (code) {
    0 -> Glyph.CLEAR
    1, 2 -> Glyph.PARTLY
    3 -> Glyph.CLOUDY
    45, 48 -> Glyph.FOG
    51, 53, 55, 56, 57 -> Glyph.RAIN
    61, 63, 65, 66, 67 -> Glyph.RAIN
    71, 73, 75, 77, 85, 86 -> Glyph.SNOW
    80, 81, 82 -> Glyph.SHOWERS
    95, 96, 99 -> Glyph.STORM
    else -> Glyph.PARTLY
}

fun describe(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mainly Clear"
    2 -> "Partly Cloudy"
    3 -> "Overcast"
    45 -> "Fog"
    48 -> "Freezing Fog"
    51 -> "Light Drizzle"
    53 -> "Drizzle"
    55 -> "Heavy Drizzle"
    56, 57 -> "Freezing Drizzle"
    61 -> "Light Rain"
    63 -> "Rain"
    65 -> "Heavy Rain"
    66, 67 -> "Freezing Rain"
    71 -> "Light Snow"
    73 -> "Snow"
    75 -> "Heavy Snow"
    77 -> "Snow Grains"
    80 -> "Light Showers"
    81 -> "Showers"
    82 -> "Heavy Showers"
    85, 86 -> "Snow Showers"
    95 -> "Thunderstorms"
    96, 99 -> "Severe Storms"
    else -> "Fair"
}
