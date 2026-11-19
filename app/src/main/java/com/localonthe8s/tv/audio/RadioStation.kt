package com.localonthe8s.tv.audio

/**
 * The internet stations the app can play. Both are SmoothJazz.com streams, listed
 * on their public "Ways to Listen" page; both are plain MP3 over HTTPS.
 */
enum class RadioStation(
    val label: String,
    val chip: String,
    val url: String
) {
    SMOOTH_JAZZ(
        label = "SMOOTH JAZZ GLOBAL",
        chip = "SMOOTH JAZZ",
        url = "https://smoothjazz.cdnstream1.com/2585_128.mp3"
    ),
    SMOOTH_LOUNGE(
        label = "SMOOTH LOUNGE GLOBAL",
        chip = "SMOOTH LOUNGE",
        url = "https://smoothjazz.cdnstream1.com/2586_128.mp3"
    );

    companion object {
        val DEFAULT: RadioStation = SMOOTH_JAZZ

        fun fromName(name: String?): RadioStation =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
