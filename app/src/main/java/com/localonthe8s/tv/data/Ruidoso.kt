package com.localonthe8s.tv.data

/**
 * Keyless Midtown Ruidoso source: the ruidoso.net Midtown webcam (BrownRice
 * live embed, same platform as the Ski Apache cam) plus a same-day Open-Meteo
 * pull for the town. The BrownRice snapshot is the live picture; the old
 * YouTube thumbnail proved stale, so it is only a last-resort fallback.
 * Embed: https://www.ruidoso.net/visitor-information/ruidoso-midtown-webcam
 * (iframe live1.brownrice.com/embed/ruidosowebcorp2).
 */
object Ruidoso {

    const val VIDEO_ID = "XoANQufSSXY"

    /** Live Midtown still served by the BrownRice cam (image/jpeg, no key). */
    const val SNAPSHOT_URL = "https://player.brownrice.com/snapshot/ruidosowebcorp2"

    /** Stale YouTube thumbnail; kept only as a last-resort fallback. */
    const val LIVE_THUMB = "https://i.ytimg.com/vi/XoANQufSSXY/hqdefault_live.jpg"

    /** Stream poster, used when no live frame has arrived yet. */
    const val POSTER = "https://i.ytimg.com/vi/XoANQufSSXY/maxresdefault.jpg"

    val PLACE = Place(
        name = "Ruidoso",
        region = "New Mexico, US",
        latitude = 33.3314,
        longitude = -105.2855,
        timezone = "America/Denver"
    )
}
