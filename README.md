# The Greenery Room — Ruidoso Local

A native Google TV / Android TV app: the in-lounge display for The Greenery Room
cannabis lounge in Ruidoso — a cool local events channel that also shows
weather. The community calendar plays first, then the local
weather runs once through every town, then it is back to events — with a
smooth-jazz radio stream that never stops.

## What it does

- **Community calendar first** – upcoming Ruidoso events from
  [DiscoverRuidoso.com](https://www.discoverruidoso.com/calendar-of-events): the
  event's artwork, month/day tag, time, venue, category and description, one card
  every eleven seconds. The calendar refreshes every fifteen minutes and keeps the
  last good listings if the site cannot be reached. Smooth jazz plays throughout.
- **Your own events too** – link any public Google Calendar (Settings → Integrate
  calendar → Public iCal address) from the setup screen and those events merge
  into the same slideshow, tagged `MY CALENDAR` (bundled Greenery Room events are
  tagged `GREENERY ROOM`). Image attachments (`ATTACH`) and pasted image links in
  the event description show as the event flyer art, including Google Drive share
  links. Unlink any time to go back to DiscoverRuidoso only.
- **Local weather after the events** – once the slideshow has played through all
  events, the channel runs one full weather rotation per town: current conditions,
  regional observations, 7-day outlook, hourly forecast, radar and almanac
  (about 80 seconds each), then back to events:
  **Ruidoso, Alto, Capitan, Carrizozo, Alamogordo, Roswell, El Paso,
  Albuquerque**.
- **Editable lineup**: remove towns you do not want and add any US market by
  ZIP code through an on-screen numeric pad, since the remote has no number keys.
  The lineup is stored in `SharedPreferences` and survives a power cycle.
- **Live data** from [Open-Meteo](https://open-meteo.com/) – no API key, no
  account. Forecast and four regional points 35 miles out are fetched on launch,
  every ten minutes, and on demand.
- **Real radar**, animated: NOAA NEXRAD base reflectivity for the market, from the
  Iowa State mesonet mirror (keyless, public domain). Eight frames at five-minute
  steps loop oldest-to-newest and then hold on the latest, drawn with state
  borders, a whole-degree graticule and 25/50 mile range rings.
- **Smooth jazz over the internet**: two SmoothJazz.com streams (Global and
  Smooth Lounge, 128 kbps MP3) play through the platform `MediaPlayer`, and the
  setup screen switches between them. If a stream cannot be reached after three
  tries, a procedurally generated bed takes over so the broadcast is never silent.
- **WeatherStar styling**: deep blue panels, monospace data, amber lower-third
  ticker, scanlines and vignette.

## Data behavior

The app is live-only. If a refresh fails, the last good snapshot stays on screen,
the header flips to `SIGNAL LOST`, and the next poll retries automatically.
There is no key, so there is nothing to configure.

## Build

Requirements: JDK 17 and the Android SDK (platform 34, build-tools 34.x). Opening
the project in Android Studio is the easy path; the Gradle wrapper is included.

This checkout has a self-contained toolchain in `.tools/` (git-ignored):
Temurin JDK 17 at `.tools/jdk17` and the Android SDK at `.tools/android-sdk`
(`local.properties` already points at it). Build with:

```bash
export JAVA_HOME="$PWD/.tools/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"

# debug APK
./gradlew assembleDebug

# install on a Google TV device or the Android TV emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# launch
adb shell am start -n com.localonthe8s.tv/.MainActivity
```

The app targets Android TV (`android.software.leanback`) and uses the leanback
launcher category, so it shows up on the Google TV home row with its 320x180
banner.

## Controls

Everything works with the onn remote; no keyboard is needed at any point.

| Remote | Action |
| --- | --- |
| D-pad up / down | Jump to the next / previous town (in weather); up jumps from events into weather |
| D-pad left / right | Previous / next panel in weather, previous / next event |
| D-pad center or OK | Pause / resume the rotation |
| Back | Open setup (Back again returns to the broadcast) |
| Home | Leave the app |

In the setup screen: D-pad browses, OK tunes in, `REMOVE` deletes the focused
market, `ADD BY ZIP` opens the numeric pad (OK presses a digit, `DEL` rubs out,
`OK` looks the ZIP up), `LINK GOOGLE CALENDAR` opens the text pad (pick letters
with the D-pad, `DEL` rubs out, `SAVE` links it, `UNLINK` removes it), and the
`MUSIC` chips choose the station.

With a keyboard or remote app attached, `S` opens setup, `R` refreshes now
and `M` toggles the music.

The chosen market, lineup, calendar link and music preference are stored in
`SharedPreferences`, so the next launch goes straight back to the broadcast.

## Layout

```
app/src/main/java/com/localonthe8s/tv/
  MainActivity.kt          remote keys, music lifecycle, settings/broadcast switch
  data/OpenMeteo.kt        keyless forecast, regional observations, timezones
  data/RadarFeed.kt        keyless NOAA NEXRAD image for the market
  data/EventsFeed.kt       DiscoverRuidoso calendar scraper, artwork loader
  data/GoogleCalendar.kt   public Google Calendar .ics reader (keyless)
  data/StateBorders.kt     bundled state outlines for the radar map
  data/ZipLookup.kt        ZIP -> place, state and timezone (keyless)
  data/Weather.kt          models, WMO code wording and glyph mapping
  data/LocationStore.kt    persisted playlist, market, station, calendar link
  audio/RadioStation.kt    the two SmoothJazz.com streams
  audio/JazzEngine.kt      station stream, sort-of-jazz synth fallback
  ui/BroadcastScreen.kt    events-then-weather rotation, key handling, ticker
  ui/EventsScreen.kt       community calendar slideshow, feed polling + merge
  ui/Panels.kt             the six forecast panels and the signal-loss panel
  ui/Chrome.kt             header, chips, progress line, ticker, scanlines
  ui/SetupScreen.kt        D-pad setup: tune in, remove, add, calendar, station
  ui/CalendarKeypad.kt     D-pad text pad for the calendar link
  ui/ZipKeypad.kt          D-pad numeric pad for ZIP entry
  assets/us-states.json    state boundaries (US Census, via PublicaMundi, MIT)
  ui/WeatherGlyph.kt       Canvas-drawn condition icons
```

## Notes

- The community calendar is read straight off the DiscoverRuidoso.com event list,
  which is a Squarespace collection: each listing is an `eventlist-event` article
  with a date tag, title link, venue, excerpt and thumbnail. No key or account is
  involved, and the slideshow keeps its last good copy when a poll fails.
- Your Google Calendar is read as a public `.ics` file (RFC 5545 `VEVENT`s) and
  merged with DiscoverRuidoso, sorted by date. Only the public iCal address is
  stored on the device; nothing is uploaded anywhere.
- `minSdk` is 23; tested target is `compileSdk`/`targetSdk` 34.
- The radar panel is real reflectivity and plays a 35-minute loop; the amber
  `LOOP -20 MIN` chip names the frame on screen, and `NO ECHOES IN RANGE` means
  the mosaic really is clear there. State lines come from the bundled GeoJSON, so
  the map keeps its geography offline.
- Regional observations are real Open-Meteo readings at four coordinates 35 miles
  from the market, not invented city names.
