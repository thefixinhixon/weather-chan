# AGENTS.md — The Greenery Room (Ruidoso Local, Android TV)

A native Google TV / Android TV app (`com.localonthe8s.tv`): the in-lounge
display for The Greenery Room cannabis lounge in Ruidoso — a cool local
events channel that also shows weather. Events slideshow first,
then one full weather rotation per town, then back to events. Smooth jazz
throughout. Kotlin + Jetpack Compose, single `MainActivity`, no backend, no
API keys.

Product vibe (do not regress): instrumental smooth jazz under everything;
straight meteorological data, no storm-fatigue sensationalism; pure, simple
WeatherStar presentation (deep blue, monospace data, amber ticker, scanlines).
The old "on the 8s" clock (:08/:18 airings) is gone — do not reintroduce
wall-clock gating.

## Build

Toolchain is self-contained and git-ignored in `.tools/` (Temurin JDK 17 +
Android SDK; `local.properties` already points at it):

```bash
export JAVA_HOME="$PWD/.tools/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug --console=plain
```

- `compileSdk`/`targetSdk` 34, `minSdk` 23, Java/Kotlin 17.
- Dependencies: Compose BOM 2024.10.00 (`ui`, `foundation`, `material`,
  `animation`), `activity-compose`, `core-ktx`, `kotlinx-coroutines-android`.
  Do NOT add new libraries without asking (the calendar scraper, ICS parser
  and feeds are deliberately dependency-free).
- Install/launch on the onn device at `192.168.0.73:5555`:
  `adb install -r app/debug/app-debug.apk`
  then `adb shell am force-stop com.localonthe8s.tv` + `adb shell am start -n
  com.localonthe8s.tv/.MainActivity`. The manifest is `singleTop` — always
  force-stop before relaunching or you will stack duplicate activities in
  different tasks and get stale-state bugs.

## Architecture

```
app/src/main/java/com/localonthe8s/tv/
  MainActivity.kt       state, remote keys, music lifecycle, setup/broadcast switch, ICS URL state
  data/LocationStore.kt persisted playlist / market / station / calendarUrl (SharedPreferences)
  data/OpenMeteo.kt     keyless forecast + 35-mi regional readings + timezones
  data/RadarFeed.kt     keyless NEXRAD loop via Iowa State mesonet WMS mirror
  data/EventsFeed.kt    DiscoverRuidoso Squarespace scraper + artwork cache
  data/GoogleCalendar.kt keyless public .ics reader (RFC 5545 unfold + VEVENT parse)
  data/ZipLookup.kt     ZIP -> place via zippopotam.us + Open-Meteo timezone
  data/Weather.kt       models, WMO wording/glyph mapping
  data/StateBorders.kt  bundled state outlines (assets/us-states.json)
  audio/                two SmoothJazz.com streams + MediaPlayer engine w/ synth fallback
  ui/Segments.kt        Segment enum (durations); AirSchedule is legacy, do not use
  ui/BroadcastScreen.kt events-then-weather rotation, playlist auto-advance, key handling
  ui/EventsScreen.kt    events slideshow state (11s/event, 15-min refresh, keeps last good)
  ui/Panels.kt          six forecast panels + signal-loss panel, radar animation
  ui/SetupScreen.kt     D-pad setup: tune/remove/add-by-ZIP, link/unlink calendar, station
  ui/CalendarKeypad.kt  D-pad text entry for the Google Calendar .ics link
  ui/{Chrome,Theme,WeatherGlyph,Clock,WeatherState,ZipKeypad}.kt
```

## The loop (events → weather → events)

- `BroadcastScreen` owns a `Phase` state machine, not a clock. EVENTS shows each
  calendar card 11s (`EVENT_SLIDE_SECONDS`); after the last event it switches to
  WEATHER at `Segment.OPENING`. WEATHER rotates the 7 segments per market
  (~82s); wrapping past the last market returns to EVENTS.
- Entering WEATHER always restarts at `Segment.OPENING`. Pausing with OK holds
  the current card/panel. D-pad left/right steps events or panels, up/down jumps
  markets (up from EVENTS jumps into WEATHER).
- Segment durations live on the `Segment` enum. Empty calendar skips to WEATHER.
- `AirSchedule` in `ui/Segments.kt` is legacy from the :08s era — do not gate
  anything on it.

## Conventions for changes

- Remote-only input: every action must work on the onn D-pad (no keyboard,
  no touch). Test focus paths — tearing down the setup subtree can drop
  broadcast focus; `BroadcastScreen` re-requests it via `focusToken`.
- Key handling: guard `FocusButton` clicks with
  `event.nativeKeyEvent.repeatCount == 0` — a held OK otherwise auto-fires,
  and on REMOVE that silently eats the whole lineup.
-Calendar: `rememberCalendar(refreshToken, calendarUrl)` merges the bundled
Greenery Room ICS + DiscoverRuidoso + Google Calendar (`GoogleCalendar.fetch`),
with Google `ATTACH` image URLs and inline image links shown as event artwork,
sorted by date, capped at
  `MAX_EVENTS=60`, refreshed every 15 min. On failure keep the last good list,
  show `CALENDAR FEED RETRYING`, never blank the screen.
- ICS link UX: Setup → LINK GOOGLE CALENDAR opens `CalendarKeypad` (D-pad grid
  + DEL/CLEAR/HTTPS/SAVE). Validate `startsWith("http")`; empty means
  DiscoverRuidoso-only. Get the URL from Google Calendar → Settings →
  Integrate calendar → Public iCal address. Never add a key-gated service.
- Data is live-only: on failure keep the last good snapshot, show
  `SIGNAL LOST` / `CALENDAR FEED RETRYING`, retry on the next poll.
- Lineup: the 8-town seed is Ruidoso, Alto, Capitan, Carrizozo, Alamogordo,
  Roswell, El Paso, Albuquerque (all `America/Denver`). `MainActivity` migrates
  unedited old installs to Ruidoso once; never wipe an edited playlist.
- Persistence keys (`local_on_the_8s` prefs): `place_name/region/tz`,
  `place_lat/lon` (+ `_bits` exact-double keys), `market_playlist` (JSON),
  `calendar_ics_url`. The playlist + calendar link are user data — never clear
  them, never write a 1-market list over them.
- Jazz must keep playing across screens and phases. `JazzEngine` retries 3×
  then falls back to generated synth — keep that contract.
- Style: WeatherStar palette in `ui/Theme.kt` (`Palette`/`Type`), monospace
  uppercase copy, `PanelFrame` for panels, amber `TickerBar` badge per phase
  (`COMMUNITY EVENTS` / `LOCAL WEATHER`).

## Verify

- `./gradlew assembleDebug` must be green; check `adb logcat -b crash` for
  `FATAL EXCEPTION` after installing (expect 0).
- On-device checks: screenshots via `adb exec-out screencap -p`, prefs via
  `run-as com.localonthe8s.tv cat shared_prefs/local_on_the_8s.xml`.
  Confirm: events play first, weather follows for each of the 8 towns, then
  events again; jazz chip stays live; linking/unlinking the calendar changes
  the mix after refresh.
- Do not change the device clock, push to remote repos, or publish/deploy
  without being asked. Keep unrelated files and system settings untouched.
