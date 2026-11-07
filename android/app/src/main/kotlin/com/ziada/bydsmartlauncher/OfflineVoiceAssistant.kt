package com.ziada.bydsmartlauncher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/** Push-to-talk. Audio is routed only to Android's on-device speech provider. */
internal class OfflineVoiceAssistant(
    private val activity: Activity,
    private val answer: (VoiceCommand, Boolean, (String) -> Unit) -> Unit
) {
    private val prefs = activity.getSharedPreferences("voice_assistant", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        stopListening()
        show(localized("Listening timed out.", "انتهى وقت الاستماع."))
    }
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var awaitingPermission = false
    private var foreground = false
    private var demo = false
    private var request = 0L
    private var arabic = prefs.getBoolean("arabic", Locale.getDefault().language == "ar")

    private val locale get() = if (arabic) Locale.forLanguageTag("ar-EG") else Locale.US
    val canStart: Boolean get() = foreground && !demo
    private fun localized(english: String, arabicText: String) = if (arabic) arabicText else english
    private fun show(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) stop()
    }

    fun setDemo(enabled: Boolean) {
        demo = enabled
        if (enabled) stop()
    }

    fun setLanguage(useArabic: Boolean) {
        stop()
        arabic = useArabic
        prefs.edit().putBoolean("arabic", arabic).apply()
        show(localized("Voice language: English", "لغة الأوامر: العربية"))
    }

    fun start() {
        if (!canStart || recognizer != null || awaitingPermission) return
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
            show(localized("Offline speech recognition is unavailable on this head unit.",
                "التعرّف على الصوت بدون إنترنت غير متاح على هذه الشاشة."))
            return
        }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            awaitingPermission = true
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
            return
        }
        pendingSpeech = null
        tts?.stop()
        request++
        try {
            val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (recognizer === current) show(localized("Listening…", "سامعك…"))
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    if (recognizer !== current) return
                    stopListening()
                    show(when (error) {
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            localized("The selected offline speech language is unavailable.",
                                "اللغة المختارة غير متاحة للتعرّف على الصوت بدون إنترنت.")
                        else -> localized("Could not recognize that command.", "ماقدرتش أفهم الأمر.")
                    })
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== current) return
                    stopListening()
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    answerCommand(phrase.orEmpty())
                }
            })
            current.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            handler.postDelayed(timeout, 12_000L)
        } catch (_: RuntimeException) {
            stopListening()
            show(localized("Could not start offline speech recognition.", "تعذّر تشغيل التعرّف على الصوت."))
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val requested = awaitingPermission
        awaitingPermission = false
        if (requested && foreground && !demo) {
            if (granted) start() else show(localized("Microphone permission denied.", "تم رفض إذن الميكروفون."))
        }
    }

    fun submitText(text: String) {
        if (!canStart) return
        stop()
        answerCommand(text)
    }

    private fun answerCommand(text: String) {
        val token = ++request
        answer(VoiceCommand.parse(text), arabic) { response ->
            if (token == request) reply(response)
        }
    }

    private fun reply(text: String) {
        if (!foreground || demo) return
        show(text)
        pendingSpeech = text
        if (tts == null) {
            tts = TextToSpeech(activity.applicationContext) { status ->
                handler.post {
                    if (tts != null) {
                        ttsReady = status == TextToSpeech.SUCCESS
                        if (ttsReady) speakPending() else pendingSpeech = null
                    }
                }
            }
        } else if (ttsReady) speakPending()
    }

    private fun speakPending() {
        val text = pendingSpeech ?: return
        pendingSpeech = null
        val engine = tts ?: return
        val voice = engine.voices.orEmpty().filter {
            !it.isNetworkConnectionRequired && it.locale.language == locale.language &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }.maxByOrNull { if (it.locale == locale) 1 else 0 } ?: return
        if (engine.setVoice(voice) == TextToSpeech.SUCCESS) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vehicle-voice-reply")
        }
    }

    private fun stopListening() {
        handler.removeCallbacks(timeout)
        val current = recognizer
        recognizer = null
        current?.destroy()
    }

    private fun stop() {
        request++
        stopListening()
        pendingSpeech = null
        tts?.stop()
    }

    fun close() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val MIC_REQUEST = 9031
    }
}
