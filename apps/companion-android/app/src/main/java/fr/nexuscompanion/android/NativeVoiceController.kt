package fr.nexuscompanion.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Explicit foreground-only, offline-only voice session.
 * No network recognition fallback, no raw audio persistence, no hidden listening.
 * The Android on-device recognizer can still stop after silence.
 */
internal class NativeVoiceController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onCommand: (String) -> Unit,
    private val onSpeechState: (Boolean) -> Unit,
    private val onSessionState: (Boolean) -> Unit = {}
) : RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var enabled = false
    private var speaking = false
    private var listening = false
    private var awaitingCommand = false
    private var restartCount = 0
    private var sessionGeneration = 0
    private var utteranceId: String? = null
    private val commandTimeout = Runnable {
        awaitingCommand = false
        if (enabled) onStatus("Délai de commande écoulé. Dis « Nexus ».")
    }
    private val restartTask = Runnable { beginRecognition() }

    fun available(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start() {
        if (enabled) return
        if (!available()) {
            onStatus("Reconnaissance Android hors ligne indisponible sur cet appareil.")
            return
        }
        enabled = true
        sessionGeneration++
        onSessionState(true)
        restartCount = 0
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
            setRecognitionListener(this@NativeVoiceController)
        }
        val generation = sessionGeneration
        tts = TextToSpeech(context) { result ->
            if (!enabled || generation != sessionGeneration) return@TextToSpeech
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                val engine = tts ?: return@TextToSpeech
                val offlineFrench = engine.voices
                    ?.firstOrNull { voice ->
                        voice.locale.language == Locale.FRENCH.language &&
                            !voice.isNetworkConnectionRequired &&
                            !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                    }
                if (offlineFrench == null) {
                    ttsReady = false
                    handler.post { onStatus("Voix française hors ligne non installée : mode texte disponible.") }
                } else {
                    engine.voice = offlineFrench
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        handler.post {
                            if (enabled && speaking && this@NativeVoiceController.utteranceId == utteranceId) onSpeechState(true)
                        }
                    }
                    override fun onDone(utteranceId: String?) { handler.post { speechFinished(utteranceId) } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { handler.post { speechFinished(utteranceId) } }
                })
            }
        }
        onStatus("Écoute locale activée : dis « Nexus ».")
        beginRecognition()
    }

    private fun beginRecognition() {
        if (!enabled || speaking || listening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            listening = true
            recognizer?.startListening(intent)
        } catch (_: RuntimeException) {
            listening = false
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!enabled || speaking || listening) return
        restartCount++
        if (restartCount > 12) {
            stop()
            onStatus("Écoute interrompue plusieurs fois. Relance-la manuellement.")
            return
        }
        handler.removeCallbacks(restartTask)
        handler.postDelayed(restartTask, (500L * restartCount).coerceAtMost(3000L))
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim().orEmpty()
        if (transcript.isNotBlank() && enabled && !speaking) {
            val text = transcript.replace(Regex("[.,!?]+$"), "").trim()
            val wake = Regex("^(nexus|nexusse|nexuce)(\\s+|$)", RegexOption.IGNORE_CASE)
            if (awaitingCommand) {
                awaitingCommand = false
                handler.removeCallbacks(commandTimeout)
                restartCount = 0
                onCommand(text)
            } else if (wake.containsMatchIn(text)) {
                val rest = text.replaceFirst(wake, "").trim()
                restartCount = 0
                if (rest.isNotBlank()) onCommand(rest)
                else {
                    awaitingCommand = true
                    handler.removeCallbacks(commandTimeout)
                    handler.postDelayed(commandTimeout, 12_000L)
                    speak("Je t'écoute.")
                }
            }
        }
        if (!speaking) scheduleRestart()
    }

    fun speak(text: String) {
        if (!enabled) return
        if (!ttsReady || tts == null) {
            onStatus("Voix locale absente : installer une voix française hors ligne.")
            return
        }
        speaking = true
        handler.removeCallbacks(restartTask)
        if (listening) recognizer?.cancel()
        listening = false
        val id = "nexus-${System.nanoTime()}"
        utteranceId = id
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) speechFinished(id)
    }

    private fun speechFinished(callbackId: String?) {
        if (!speaking || callbackId != utteranceId) return
        speaking = false
        utteranceId = null
        onSpeechState(false)
        if (enabled) scheduleRestart()
    }

    override fun onReadyForSpeech(params: Bundle?) { if (enabled && !speaking) onStatus("Micro actif : reconnaissance locale") }
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onError(error: Int) {
        listening = false
        if (speaking || !enabled) return
        onStatus("Reconnaissance interrompue ; reprise locale.")
        scheduleRestart()
    }

    fun stop() {
        enabled = false
        sessionGeneration++
        onSessionState(false)
        handler.removeCallbacks(commandTimeout)
        awaitingCommand = false
        speaking = false
        utteranceId = null
        listening = false
        handler.removeCallbacks(restartTask)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        onSpeechState(false)
        onStatus("Micro désactivé")
    }
}
