package fr.nexuscompanion.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import java.util.Locale

internal enum class FrenchModelState { CHECKING, READY, DOWNLOADABLE, PENDING, REQUESTED, UNAVAILABLE, UNKNOWN, SERVICE_ABSENT, PERMISSION }

internal object FrenchVoicePolicy {
    private fun matches(tags: List<String>) = tags.any {
        val locale = Locale.forLanguageTag(it.trim().replace('_', '-'))
        locale.language == "fr" && (locale.country.isBlank() || locale.country == "FR")
    }
    fun state(installed: List<String>, pending: List<String>, supported: List<String>) = when {
        matches(installed) -> FrenchModelState.READY
        matches(pending) -> FrenchModelState.PENDING
        matches(supported) -> FrenchModelState.DOWNLOADABLE
        else -> FrenchModelState.UNAVAILABLE
    }
}

/** Checks language support without opening audio. Downloads only follow an explicit button tap. */
internal class FrenchVoiceSupport(private val context: Context, private val onState: (FrenchModelState) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var recognizer: SpeechRecognizer? = null
    private var timeout: Runnable? = null
    private var state = FrenchModelState.UNKNOWN

    fun check() = query(false)
    fun requestDownload() { if (state == FrenchModelState.DOWNLOADABLE) query(true) }

    private fun publish(next: FrenchModelState) { state = next; onState(next) }

    private fun query(download: Boolean) {
        close()
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { publish(FrenchModelState.SERVICE_ABSENT); return }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            publish(FrenchModelState.PERMISSION); return
        }
        if (Build.VERSION.SDK_INT < 33) { publish(FrenchModelState.UNKNOWN); return }
        querySupported(download, generation)
    }

    @androidx.annotation.RequiresApi(33)
    private fun querySupported(download: Boolean, turn: Long) {
        publish(FrenchModelState.CHECKING)
        fun finish(result: FrenchModelState) {
            if (turn != generation) return
            close()
            publish(result)
        }
        timeout = Runnable { finish(if (download) FrenchModelState.REQUESTED else FrenchModelState.UNKNOWN) }.also {
            handler.postDelayed(it, 7_000L)
        }
        try {
            val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { recognizer = it }
            if (download) engine.triggerModelDownload(NativeRecognitionSettings.intent())
            engine.checkRecognitionSupport(NativeRecognitionSettings.intent(), context.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val result = FrenchVoicePolicy.state(support.installedOnDeviceLanguages,
                        support.pendingOnDeviceLanguages, support.supportedOnDeviceLanguages)
                    finish(if (download && result == FrenchModelState.DOWNLOADABLE) FrenchModelState.REQUESTED else result)
                }
                override fun onError(error: Int) {
                    finish(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> FrenchModelState.PERMISSION
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> FrenchModelState.UNAVAILABLE
                        else -> if (download) FrenchModelState.REQUESTED else FrenchModelState.UNKNOWN
                    })
                }
            })
        } catch (_: SecurityException) { finish(FrenchModelState.PERMISSION) }
        catch (_: RuntimeException) { finish(FrenchModelState.UNKNOWN) }
    }

    fun close() {
        ++generation
        timeout?.let { handler.removeCallbacks(it) }; timeout = null
        val previous = recognizer; recognizer = null
        try { previous?.destroy() } catch (_: RuntimeException) { /* callbacks invalidated */ }
    }
}
