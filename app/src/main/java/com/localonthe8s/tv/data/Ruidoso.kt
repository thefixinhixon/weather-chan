package com.localonthe8s.tv.data

/**
 * Keyless Midtown Ruidoso source: the public webcam's live thumbnail plus a
 * same-day Open-Meteo pull for the town, mirroring the Ski Apache pattern.
 */
object Ruidoso {

    const val VIDEO_ID = "XoANQufSSXY"

    /** Refreshes on YouTube's schedule while the stream is live; offline safe. */
    const val LIVE_THUMB = "https://i.ytimg.com/vi/XoANQufSSXY/hqdefault_live.jpg"

    /** Stream poster, used when the live thumbnail is unreachable. */
    const val POSTER = "https://i.ytimg.com/vi/XoANQufSSXY/maxresdefault.jpg"

    val PLACE = Place(
        name = "Ruidoso",
        region = "New Mexico, US",
        latitude = 33.3314,
        longitude = -105.2855,
        timezone = "America/Denver"
    )
}
