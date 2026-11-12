package fr.nexuscompanion.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

internal interface VoiceInput : AutoCloseable {
    fun available(): Boolean
    fun recognizer(events: RecognitionEvents): VoiceRecognition
    override fun close() = Unit
}

internal fun defaultVoiceInput(context: Context): VoiceInput =
    if (EmbeddedFrenchInput.isPackaged(context)) EmbeddedFrenchInput(context) else AndroidVoiceInput(context)

/** Android adapters only. No network recognizer fallback or raw audio persistence. */
internal class NativeVoiceController(
    context: Context,
    onStatus: (String) -> Unit,
    onCommand: (String) -> Unit,
    onSpeechState: (Boolean) -> Unit,
    onSessionState: (Boolean) -> Unit = {},
    onTranscript: (String, Boolean) -> Unit = { _, _ -> },
    onInputLevel: (Int) -> Unit = {},
    onSpeechOutput: (SpeechOutputState) -> Unit = {},
    private val input: VoiceInput = defaultVoiceInput(context),
    onMicrophoneReady: (Boolean) -> Unit = {}
) {
    private val session = VoiceSession(AndroidVoicePlatform(context, input), onStatus, onCommand, onSpeechState,
        onSessionState, onTranscript, onInputLevel, onSpeechOutput, onMicrophoneReady)
    fun available() = session.available()
    fun start() = session.start()
    fun listenForCommand() = session.listenForCommand()
    fun playReply(text: String) = session.playReply(text)
    fun beginResponse() = session.beginResponse()
    fun finishResponse(text: String?, expectAnswer: Boolean = false) = session.finishResponse(text, expectAnswer)
    fun setBodyMoving(active: Boolean) = session.setBodyMoving(active)
    fun setSpokenRepliesEnabled(enabled: Boolean) = session.setSpokenRepliesEnabled(enabled)
    fun stopListening() = session.stopListening()
    fun stop() = session.stop()
    fun close() { session.stop(); input.close() }
}

internal fun SpeechOutputState.messageResource() = when (this) {
    SpeechOutputState.IDLE -> R.string.voice_output_idle
    SpeechOutputState.PREPARING -> R.string.voice_output_preparing
    SpeechOutputState.PLAYING -> R.string.voice_output_playing
    SpeechOutputState.COMPLETED -> R.string.voice_output_completed
    SpeechOutputState.UNAVAILABLE -> R.string.voice_output_unavailable
    SpeechOutputState.FAILED -> R.string.voice_output_failed
    SpeechOutputState.INTERRUPTED -> R.string.voice_output_interrupted
}

private class AndroidVoicePlatform(private val context: Context, private val input: VoiceInput) : VoicePlatform {
    private val handler = Handler(Looper.getMainLooper())
    override fun available() = input.available()
    override fun recognizer(events: RecognitionEvents) = input.recognizer(events)
    override fun after(delayMs: Long, action: () -> Unit): VoiceTask {
        val task = Runnable(action)
        handler.postDelayed(task, delayMs)
        return VoiceTask { handler.removeCallbacks(task) }
    }

    override fun speech(events: SpeechEvents): VoiceSpeech = AndroidSpeech(context, handler, events)
}

internal class AndroidVoiceInput(private val context: Context) : VoiceInput {
    override fun available() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun recognizer(events: RecognitionEvents): VoiceRecognition {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = events.ready()
            override fun onResults(results: Bundle?) = events.alternatives(NativeRecognitionSettings.hypotheses(results))
            override fun onError(error: Int) = events.error(when (error) {
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognitionFailure.SILENCE
                SpeechRecognizer.ERROR_NO_MATCH -> RecognitionFailure.NO_MATCH
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognitionFailure.PERMISSION
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> RecognitionFailure.LANGUAGE_NOT_SUPPORTED
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> RecognitionFailure.LANGUAGE_NOT_INSTALLED
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> RecognitionFailure.BUSY
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> RecognitionFailure.RATE_LIMIT
                else -> RecognitionFailure.TEMPORARY
            })
            override fun onBeginningOfSpeech() = events.speechStarted()
            override fun onRmsChanged(rmsdB: Float) = events.level(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = events.speechEnded()
            override fun onPartialResults(partialResults: Bundle?) = events.partial(
                NativeRecognitionSettings.hypotheses(partialResults).firstOrNull()?.text.orEmpty()
            )
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        return object : VoiceRecognition {
            override fun start() {
                recognizer.startListening(NativeRecognitionSettings.intent())
            }
            override fun close() {
                try { recognizer.cancel() } finally { recognizer.destroy() }
            }
        }
    }

}

private class AndroidSpeech(context: Context, private val handler: Handler, private val events: SpeechEvents) : VoiceSpeech {
    override val maxInputChars: Int get() = TextToSpeech.getMaxSpeechInputLength()
    private val audio = context.getSystemService(AudioManager::class.java)
    private var closed = false
    private var ready = false
    private var engine: TextToSpeech? = null
    private var activeUtterance: String? = null
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (!closed && change < 0) {
                stop()
                events.interrupted()
            }
        }, handler).build()

    init {
        engine = TextToSpeech(context) { result ->
            // Serialize initialization with constructor completion and lifecycle events.
            handler.post {
                if (!closed) {
                    try {
                        val tts = engine
                        val voices = if (result == TextToSpeech.SUCCESS) tts?.voices.orEmpty().filter {
                            it.locale.language == Locale.FRENCH.language && !it.isNetworkConnectionRequired &&
                                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                        }.sortedByDescending { it.locale.country == Locale.FRANCE.country } else emptyList()
                        ready = tts != null && voices.any { tts.setVoice(it) == TextToSpeech.SUCCESS }
                        tts?.setAudioAttributes(attributes)
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                handler.post { if (!closed && utteranceId != null) events.started(utteranceId) }
                            }
                            override fun onDone(utteranceId: String?) = complete(utteranceId)
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) = complete(utteranceId, failed = true)
                            override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId, failed = true)
                            override fun onStop(utteranceId: String?, interrupted: Boolean) = complete(utteranceId, failed = true)
                        })
                    } catch (_: RuntimeException) {
                        ready = false
                    }
                    events.ready(ready)
                }
            }
        }
    }

    private fun complete(id: String?, failed: Boolean = false) {
        handler.post {
            if (!closed && id != null && activeUtterance == id) {
                activeUtterance = null
                if (failed) events.failed(id) else events.finished(id)
                // The session may immediately start the next part; keep focus between them.
                if (activeUtterance == null) audio.abandonAudioFocusRequest(focus)
            }
        }
    }

    override fun speak(text: String, id: String): Boolean {
        if (closed || !ready || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        activeUtterance = id
        val success = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        if (!success) {
            activeUtterance = null
            audio.abandonAudioFocusRequest(focus)
        }
        return success
    }

    override fun stop() {
        activeUtterance = null
        try { engine?.stop() } finally { audio.abandonAudioFocusRequest(focus) }
    }

    override fun close() {
        closed = true
        ready = false
        try { stop() } finally { engine?.shutdown(); engine = null }
    }
}
