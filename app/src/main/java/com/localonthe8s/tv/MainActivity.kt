package com.localonthe8s.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import com.localonthe8s.tv.audio.JazzEngine
import com.localonthe8s.tv.audio.MusicState
import com.localonthe8s.tv.audio.RadioStation
import com.localonthe8s.tv.data.LocationStore
import com.localonthe8s.tv.data.Place
import com.localonthe8s.tv.ui.AppBackdrop
import com.localonthe8s.tv.ui.BroadcastScreen
import com.localonthe8s.tv.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private lateinit var store: LocationStore
    private lateinit var jazz: JazzEngine

    private val placeState = mutableStateOf<Place?>(null)
    private val playlistState = mutableStateOf<List<Place>>(emptyList())
    private val stationState = mutableStateOf(RadioStation.DEFAULT)
    private val settingsState = mutableStateOf(false)
    private val musicState = mutableStateOf(true)
    private val streamState = mutableStateOf(MusicState.MUTED)
    private val refreshState = mutableIntStateOf(0)
    private val focusState = mutableIntStateOf(0)
    private val calendarState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        store = LocationStore(this)
        jazz = JazzEngine { state -> streamState.value = state }
        // One-time migration to the Ruidoso-area lineup: viewers who never edited
        // the old 46-city presets (or are still tuned to one of them) move to
        // Ruidoso with the new 8-town seed. Edited lineups are left alone.
        if (store.isDefaultPlaylist() && LocationStore.indexOf(LocationStore.PRESETS, store.place) < 0) {
            val ruidoso = LocationStore.PRESETS.first()
            if (store.place == null || LocationStore.indexOf(store.playlist, store.place) < 0) {
                store.place = ruidoso
            }
        }
        playlistState.value = store.playlist
        placeState.value = store.place
        stationState.value = store.station
        musicState.value = store.musicEnabled
        calendarState.value = store.calendarUrl
        settingsState.value = placeState.value == null

        // An onn remote has no letter keys: BACK is how you reach the market lineup,
        // and BACK again goes back on air. Home still leaves the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (placeState.value == null) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    return
                }
                settingsState.value = !settingsState.value
                if (!settingsState.value) reclaimBroadcastFocus()
            }
        })

        setContent {
            val place = placeState.value
            val playlist = playlistState.value
            val showingSetup = settingsState.value || place == null

            DisposableEffect(Unit) {
                onDispose { jazz.stop() }
            }

            AppBackdrop {
                if (showingSetup) {
                    SetupScreen(
                        playlist = playlist,
                        current = place,
                        station = stationState.value,
                        musicLabel = musicLabel(),
                        calendarUrl = calendarState.value,
                        onPick = { picked ->
                            store.place = picked
                            placeState.value = picked
                            settingsState.value = false
                            refreshState.intValue++
                            reclaimBroadcastFocus()
                        },
                        onRemove = ::removeMarket,
                        onAdd = ::addMarket,
                        onStation = ::chooseStation,
                        onCalendarSave = ::saveCalendar,
                        onCalendarClear = ::clearCalendar,
                        onResume = if (place != null) {
                            {
                                settingsState.value = false
                                reclaimBroadcastFocus()
                            }
                        } else {
                            null
                        }
                    )
                } else {
                    BroadcastScreen(
                        place = place!!,
                        refreshToken = refreshState.intValue,
                        focusToken = focusState.intValue,
                        musicLabel = musicLabel(),
                        musicOn = musicState.value,
                        marketIndex = LocationStore.indexOf(playlist, place),
                        marketCount = playlist.size,
                        calendarUrl = calendarState.value,
                        onMarket = ::cycleMarket
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (musicState.value) jazz.start(stationState.value)
    }

    override fun onStop() {
        jazz.stop()
        super.onStop()
    }

    override fun onDestroy() {
        jazz.stop()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!settingsState.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    toggleMusic()
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    settingsState.value = true
                    return true
                }
                KeyEvent.KEYCODE_R -> {
                    refreshState.intValue++
                    return true
                }
                // Belt and braces: if the broadcast ever loses key focus (the lineup
                // subtree being torn down can do that), tuning still works.
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cycleMarket(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cycleMarket(-1)
                    return true
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_S && placeState.value != null) {
            // Honours the on-screen hint for anyone with a keyboard attached.
            settingsState.value = false
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** D-pad up/down tunes to the next market in the lineup. */
    private fun cycleMarket(delta: Int) {
        val current = placeState.value
        val next = LocationStore.cycle(playlistState.value, current, delta) ?: return
        if (next == current) return
        store.place = next
        placeState.value = next
        refreshState.intValue++
    }

    private fun addMarket(place: Place) {
        val lineup = playlistState.value
        if (LocationStore.indexOf(lineup, place) >= 0) return
        val updated = lineup + place
        store.playlist = updated
        playlistState.value = updated

        // On first run there is no market yet, so the added one goes straight on air.
        if (placeState.value == null) {
            store.place = place
            placeState.value = place
            settingsState.value = false
            refreshState.intValue++
        }
    }

    private fun removeMarket(place: Place) {
        val lineup = playlistState.value
        if (lineup.size <= 1) return
        val updated = lineup.filterNot { LocationStore.sameMarket(it, place) }
        store.playlist = updated
        playlistState.value = updated

        if (LocationStore.sameMarket(placeState.value, place)) {
            val replacement = updated.first()
            store.place = replacement
            placeState.value = replacement
            refreshState.intValue++
        }
    }

    private fun saveCalendar(url: String) {
        store.calendarUrl = url
        calendarState.value = store.calendarUrl
        refreshState.intValue++
    }

    private fun clearCalendar() {
        store.calendarUrl = ""
        calendarState.value = ""
        refreshState.intValue++
    }

    private fun chooseStation(station: RadioStation) {
        if (stationState.value == station) return
        stationState.value = station
        store.station = station
        jazz.setStation(station)
    }

    /** Asks the broadcast to take key focus back after the lineup closes. */
    private fun reclaimBroadcastFocus() {
        focusState.intValue++
    }

    private fun musicLabel(): String {
        if (!musicState.value) return "JAZZ MUTED"
        val name = stationState.value.chip
        return when (streamState.value) {
            MusicState.STREAMING -> "$name • LIVE"
            MusicState.LINKING -> "$name • LINKING"
            MusicState.SYNTH -> "$name • SYNTH BED"
            MusicState.MUTED -> "$name • IDLE"
        }
    }

    private fun toggleMusic() {
        val enabled = !musicState.value
        musicState.value = enabled
        store.musicEnabled = enabled
        if (enabled) jazz.start(stationState.value) else jazz.stop()
    }
}
