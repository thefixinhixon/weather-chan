package com.localonthe8s.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.localonthe8s.tv.data.OpenMeteo
import com.localonthe8s.tv.data.Place
import com.localonthe8s.tv.data.RadarFeed
import com.localonthe8s.tv.data.RadarLoop
import com.localonthe8s.tv.data.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Ready(val snapshot: WeatherSnapshot) : WeatherUiState
    data class Stale(val snapshot: WeatherSnapshot, val error: String) : WeatherUiState
    data class Failed(val error: String) : WeatherUiState
}

fun WeatherUiState.snapshot(): WeatherSnapshot? = when (this) {
    is WeatherUiState.Ready -> snapshot
    is WeatherUiState.Stale -> snapshot
    else -> null
}

fun WeatherUiState.isLive(): Boolean = this is WeatherUiState.Ready

private const val REFRESH_MILLIS = 10 * 60 * 1000L
private const val RETUNE_DEBOUNCE_MILLIS = 600L

/**
 * Polls Open-Meteo on launch, on manual refresh and every ten minutes. A failed
 * refresh keeps the previous snapshot on screen and flips the status chip.
 */
@Composable
fun rememberWeather(place: Place, refreshToken: Int): WeatherUiState {
    var state by remember(place) { mutableStateOf<WeatherUiState>(WeatherUiState.Loading) }

    LaunchedEffect(place, refreshToken) {
        while (true) {
            // Tun eing on the D-pad can change markets faster than a request completes;
            // wait out the burst so a few presses do not become a dozen feed calls.
            delay(RETUNE_DEBOUNCE_MILLIS)
            try {
                state = WeatherUiState.Ready(OpenMeteo.fetch(place))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val lastGood = state.snapshot()
                state = if (lastGood != null) {
                    WeatherUiState.Stale(lastGood, failure.message ?: "NETWORK ERROR")
                } else {
                    WeatherUiState.Failed(failure.message ?: "NETWORK ERROR")
                }
            }
            delay(REFRESH_MILLIS)
        }
    }

    return state
}

sealed interface RadarUiState {
    data object Loading : RadarUiState
    data class Ready(val loop: RadarLoop) : RadarUiState
    data class Failed(val error: String) : RadarUiState
}

/** Pulls one RainViewer frame per market and refreshes it with the forecast. */
@Composable
fun rememberRadar(place: Place, refreshToken: Int): RadarUiState {
    var state by remember(place) { mutableStateOf<RadarUiState>(RadarUiState.Loading) }

    LaunchedEffect(place, refreshToken) {
        while (true) {
            delay(RETUNE_DEBOUNCE_MILLIS)
            try {
                state = RadarUiState.Ready(RadarFeed.load(place))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // A stale frame beats a blank panel; only fail when nothing arrived.
                if (state !is RadarUiState.Ready) {
                    state = RadarUiState.Failed(failure.message ?: "RADAR FEED UNREACHABLE")
                }
            }
            delay(REFRESH_MILLIS)
        }
    }

    return state
}
