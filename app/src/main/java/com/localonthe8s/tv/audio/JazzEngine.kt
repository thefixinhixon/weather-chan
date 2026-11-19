package com.localonthe8s.tv.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** What the music bed is doing right now, so the header chip can tell the truth. */
enum class MusicState { LINKING, STREAMING, SYNTH, MUTED }

/**
 * Plays the SmoothJazz.com Global stream (128 kbps MP3, no key, no account) through
 * the platform MediaPlayer. If the stream cannot be reached after a few attempts the
 * procedural bed from earlier builds takes over, so the broadcast is never silent.
 */
class JazzEngine(private val onState: (MusicState) -> Unit) {

    private val main = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var synth: AudioTrack? = null
    private var synthThread: Thread? = null
    private var running = false
    private var attempt = 0
    private var generation = 0
    private var station: RadioStation = RadioStation.DEFAULT

    fun start(station: RadioStation) {
        this.station = station
        if (running) {
            reconnect()
            return
        }
        running = true
        attempt = 0
        report(MusicState.LINKING)
        connectStream()
    }

    /** Swaps the station under a running engine; harmless when music is muted. */
    fun setStation(next: RadioStation) {
        if (station == next) return
        station = next
        if (running) {
            report(MusicState.LINKING)
            reconnect()
        }
    }

    private fun reconnect() {
        generation++
        main.removeCallbacksAndMessages(null)
        releaseStream()
        stopSynth()
        attempt = 0
        connectStream()
    }

    fun stop() {
        running = false
        generation++
        main.removeCallbacksAndMessages(null)
        releaseStream()
        stopSynth()
        report(MusicState.MUTED)
    }

    private fun connectStream() {
        releaseStream()
        val token = generation
        attempt++

        val media = MediaPlayer()
        player = media
        media.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        media.setOnPreparedListener { prepared ->
            if (!running || token != generation) return@setOnPreparedListener
            prepared.setVolume(0.7f, 0.7f)
            prepared.start()
            attempt = 0
            report(MusicState.STREAMING)
        }
        media.setOnCompletionListener {
            // An internet radio stream should never finish; if it does, reconnect.
            if (running && token == generation) {
                main.post { if (running && token == generation) connectStream() }
            }
        }
        media.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "jazz stream error what=$what extra=$extra")
            if (running && token == generation) retryOrFallBack()
            true
        }

        runCatching {
            media.setDataSource(station.url)
            media.prepareAsync()
        }.onFailure {
            Log.w(TAG, "jazz stream could not start", it)
            if (running && token == generation) retryOrFallBack()
        }
    }

    private fun retryOrFallBack() {
        releaseStream()
        if (attempt >= MAX_ATTEMPTS) {
            startSynth()
            return
        }
        report(MusicState.LINKING)
        val token = generation
        main.postDelayed({ if (running && token == generation) connectStream() }, RETRY_DELAY_MILLIS)
    }

    private fun startSynth() {
        if (synth != null || synthThread != null) return
        val token = generation
        synthThread = Thread {
            val pcm = JazzSynth.renderLoop()
            synchronized(this) {
                if (!running || token != generation) return@synchronized
                synthThread = null
                buildTrack(pcm)?.let { track ->
                    synth = track
                    track.play()
                    report(MusicState.SYNTH)
                }
            }
        }.apply {
            isDaemon = true
            name = "jazz-synth"
            start()
        }
    }

    private fun releaseStream() {
        val media = player ?: return
        player = null
        runCatching { media.stop() }
        runCatching { media.reset() }
        runCatching { media.release() }
    }

    private fun stopSynth() {
        synthThread = null
        synth?.let { track ->
            runCatching { track.pause() }
            runCatching { track.release() }
        }
        synth = null
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack? = runCatching {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(JazzSynth.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { built ->
                built.write(pcm, 0, pcm.size)
                built.setLoopPoints(0, pcm.size / 2, -1)
                built.setVolume(0.5f)
            }
    }.getOrNull()

    /** Safe from any thread, including the synthesis worker. */
    private fun report(state: MusicState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onState(state)
        } else {
            main.post { onState(state) }
        }
    }

    companion object {
        private const val TAG = "JazzEngine"

        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MILLIS = 5_000L
    }
}

/** Fallback bed: four bars of ii-V-ish changes at 92 BPM, generated on device. */
private object JazzSynth {

    const val SAMPLE_RATE = 44_100
    private const val BPM = 92.0

    private val CHORDS = arrayOf(
        intArrayOf(65, 69, 72, 76), // Fmaj7
        intArrayOf(62, 65, 69, 72), // Dm7
        intArrayOf(67, 70, 74, 77), // Gm7
        intArrayOf(60, 64, 67, 70)  // C7
    )
    private val BASS = intArrayOf(41, 38, 43, 36)

    fun renderLoop(): ShortArray {
        val beat = 60.0 / BPM * SAMPLE_RATE
        val barFrames = (beat * 4).toInt()
        val totalFrames = barFrames * CHORDS.size
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        for (bar in CHORDS.indices) {
            val barStart = bar * barFrames

            addChord(left, right, barStart, (beat * 1.5).toInt(), CHORDS[bar], 0.11f)
            addChord(left, right, barStart + (beat * 2.5).toInt(), (beat * 1.2).toInt(), CHORDS[bar], 0.08f)

            addTone(left, right, barStart, (beat * 1.8).toInt(), midiFreq(BASS[bar]), 0.20f, 0.5f, 1.2f, 0.0)
            addTone(left, right, barStart + (beat * 2.0).toInt(), (beat * 1.4).toInt(),
                midiFreq(BASS[bar] + 7), 0.12f, 0.5f, 1.4f, 0.0)

            for (eighth in 0 until 8) {
                val swing = if (eighth % 2 == 1) beat * (2.0 / 3.0) - beat * 0.5 else 0.0
                val start = barStart + (eighth * 0.5 * beat + swing).toInt()
                val accent = if (eighth % 2 == 0) 1f else 0.66f
                addNoise(left, right, start, (SAMPLE_RATE * 0.09).toInt(), 0.035f * accent, 0.62f, 30f)
            }
            for (backbeat in intArrayOf(1, 3)) {
                addNoise(left, right, barStart + (backbeat * beat).toInt(),
                    (SAMPLE_RATE * 0.16).toInt(), 0.045f, 0.5f, 15f)
            }
        }

        val fade = (SAMPLE_RATE * 0.03).toInt()
        for (i in 0 until fade) {
            val gain = i / fade.toFloat()
            left[i] *= gain
            right[i] *= gain
            left[totalFrames - 1 - i] *= gain
            right[totalFrames - 1 - i] *= gain
        }

        val out = ShortArray(totalFrames * 2)
        for (i in 0 until totalFrames) {
            out[i * 2] = (left[i] * 0.85f).coerceIn(-1f, 1f).times(32767f).toInt().toShort()
            out[i * 2 + 1] = (right[i] * 0.85f).coerceIn(-1f, 1f).times(32767f).toInt().toShort()
        }
        return out
    }

    private fun addChord(
        left: FloatArray,
        right: FloatArray,
        start: Int,
        length: Int,
        notes: IntArray,
        gain: Float
    ) {
        notes.forEachIndexed { index, note ->
            val pan = if (index % 2 == 0) 0.42f else 0.58f
            addRhodes(left, right, start, length, midiFreq(note), gain, pan)
        }
    }

    private fun addRhodes(
        left: FloatArray,
        right: FloatArray,
        start: Int,
        length: Int,
        freq: Double,
        gain: Float,
        pan: Float
    ) {
        val frames = minOf(length, left.size - start)
        if (frames <= 0) return
        val gainLeft = gain * (1f - pan) * 2f
        val gainRight = gain * pan * 2f
        for (i in 0 until frames) {
            val t = i / SAMPLE_RATE.toDouble()
            val envelope = exp(-t * 1.5)
            val tremolo = 1.0 + 0.12 * sin(2 * PI * 3.4 * t)
            val body = sin(2 * PI * freq * t) +
                0.28 * sin(2 * PI * freq * 2 * t) * exp(-t * 3.5) +
                0.10 * sin(2 * PI * freq * 3 * t) * exp(-t * 6.0)
            val sample = (body * envelope * tremolo).toFloat()
            left[start + i] += sample * gainLeft
            right[start + i] += sample * gainRight
        }
    }

    private fun addTone(
        left: FloatArray,
        right: FloatArray,
        start: Int,
        length: Int,
        freq: Double,
        gain: Float,
        pan: Float,
        decay: Float,
        vibrato: Double
    ) {
        val frames = minOf(length, left.size - start)
        if (frames <= 0) return
        val gainLeft = gain * (1f - pan) * 2f
        val gainRight = gain * pan * 2f
        for (i in 0 until frames) {
            val t = i / SAMPLE_RATE.toDouble()
            val wobble = 1.0 + 0.004 * sin(2 * PI * vibrato * t)
            val sample = (sin(2 * PI * freq * wobble * t) +
                0.12 * sin(2 * PI * freq * 2 * wobble * t)).toFloat() * exp(-t * decay).toFloat()
            left[start + i] += sample * gainLeft
            right[start + i] += sample * gainRight
        }
    }

    private fun addNoise(
        left: FloatArray,
        right: FloatArray,
        start: Int,
        length: Int,
        gain: Float,
        pan: Float,
        decay: Float
    ) {
        val frames = minOf(length, left.size - start)
        if (frames <= 0) return
        val gainLeft = gain * (1f - pan) * 2f
        val gainRight = gain * pan * 2f
        var seed = (start * 1_103_515_245 + 12_345).toLong()
        for (i in 0 until frames) {
            seed = seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            val white = ((seed ushr 40) and 0xFFFFL).toFloat() / 32_768f - 1f
            val envelope = exp(-(i / SAMPLE_RATE.toFloat()) * decay)
            val sample = white * envelope
            left[start + i] += sample * gainLeft
            right[start + i] += sample * gainRight
        }
    }

    private fun midiFreq(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)
}
