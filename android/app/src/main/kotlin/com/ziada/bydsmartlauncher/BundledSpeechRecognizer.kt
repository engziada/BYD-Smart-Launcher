package com.ziada.bydsmartlauncher

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.Executors

/** Main-thread session control. The worker owns the recorder and all native recognizer state. */
internal class BundledSpeechRecognizer(private val assets: AssetManager, private val main: Handler) {
    enum class Failure { MICROPHONE, ENGINE }

    private class Capture {
        @Volatile var cancelled = false
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var capture: Capture? = null
    private var vad: Vad? = null
    private var decoder: OfflineRecognizer? = null
    private var decoderLanguage: String? = null

    val active: Boolean get() = capture != null

    fun start(
        language: String,
        onListening: () -> Unit,
        onDecoding: () -> Unit,
        onResult: (String, Failure?) -> Unit,
    ) {
        if (active) return
        val current = Capture()
        capture = current
        fun postWhileActive(action: () -> Unit) = main.post {
            if (capture === current) action()
        }

        worker.execute {
            var text = ""
            var failure: Failure? = null
            try {
                if (current.cancelled) return@execute
                val detector = vad ?: Vad(assets, VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = "$MODEL_DIR/silero_vad.onnx",
                        minSilenceDuration = 0.45f,
                        minSpeechDuration = 0.15f,
                    ),
                )).also { vad = it }
                val samples = record(detector, current) { postWhileActive(onListening) }
                if (!current.cancelled && samples.isNotEmpty()) {
                    postWhileActive(onDecoding)
                    text = transcribe(samples, language)
                }
            } catch (error: MicrophoneException) {
                Log.w(TAG, "Could not capture microphone audio", error)
                failure = Failure.MICROPHONE
            } catch (error: Exception) {
                Log.w(TAG, "Could not run bundled speech recognition", error)
                failure = Failure.ENGINE
            } catch (error: LinkageError) {
                Log.w(TAG, "Bundled speech engine could not load", error)
                failure = Failure.ENGINE
            }
            postWhileActive {
                capture = null
                onResult(text, failure)
            }
        }
    }

    fun cancel() {
        capture?.cancelled = true
        capture = null
    }

    fun close() {
        cancel()
        worker.execute {
            decoder?.release()
            vad?.release()
        }
        worker.shutdown()
    }

    private fun record(detector: Vad, current: Capture, onListening: () -> Unit): FloatArray {
        detector.reset()
        if (current.cancelled) return FloatArray(0)
        val recorder = openRecorder()
        try {
            onListening()
            val pcm = ShortArray(512)
            val deadline = SystemClock.elapsedRealtime() + 8_000L
            while (!current.cancelled && SystemClock.elapsedRealtime() < deadline) {
                val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_NON_BLOCKING)
                if (count < 0) throw MicrophoneException("AudioRecord read failed ($count)")
                if (count == 0) {
                    SystemClock.sleep(10)
                    continue
                }
                detector.acceptWaveform(FloatArray(count) { pcm[it] / 32768f })
                if (!detector.empty()) return detector.front().samples
            }
            if (current.cancelled) return FloatArray(0)
            detector.flush()
            return if (detector.empty()) FloatArray(0) else detector.front().samples
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    @SuppressLint("MissingPermission") // OfflineVoiceAssistant checks RECORD_AUDIO first.
    private fun openRecorder(): AudioRecord {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, format)
        if (minimum <= 0) throw MicrophoneException("16 kHz mono capture is unavailable")
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val recorder = try {
                AudioRecord(source, SAMPLE_RATE, channel, format, maxOf(minimum, 4096))
            } catch (_: RuntimeException) {
                continue
            }
            try {
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) return recorder
                }
            } catch (_: RuntimeException) {
                // Some head units only expose the standard microphone source.
            }
            recorder.release()
        }
        throw MicrophoneException("No usable microphone source")
    }

    private fun transcribe(samples: FloatArray, language: String): String {
        if (decoderLanguage != language) {
            decoder?.release()
            decoder = null
            decoderLanguage = null
        }
        val recognizer = decoder ?: OfflineRecognizer(assets, OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "$MODEL_DIR/tiny-encoder.int8.onnx",
                    decoder = "$MODEL_DIR/tiny-decoder.int8.onnx",
                    language = language,
                ),
                tokens = "$MODEL_DIR/tiny-tokens.txt",
                numThreads = 2,
            ),
        )).also {
            decoder = it
            decoderLanguage = language
        }
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private class MicrophoneException(message: String) : Exception(message)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val MODEL_DIR = "offline-voice"
        const val TAG = "BYDOfflineVoice"
    }
}
