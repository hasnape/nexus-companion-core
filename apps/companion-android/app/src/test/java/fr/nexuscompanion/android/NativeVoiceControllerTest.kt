package fr.nexuscompanion.android

import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

/** Runs Android listener wiring through Robolectric's API-34 recognizer, not real audio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class NativeVoiceControllerTest {
    private fun bundle(vararg texts: String, scores: FloatArray? = null) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(*texts))
        if (scores != null) putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, scores)
    }

    @Test fun androidPartialsArePreviewedAndOnlyFinalAlternativesExecute() {
        val commands = mutableListOf<String>()
        val heard = mutableListOf<Pair<String, Boolean>>()
        val controller = NativeVoiceController(RuntimeEnvironment.getApplication(), {}, commands::add, {}, {},
            { text, final -> heard.add(text to final) })
        try {
            controller.start()
            shadowOf(Looper.getMainLooper()).idle()
            val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            assertTrue(recognizer.lastRecognizerIntent.getBooleanExtra("android.speech.extra.PARTIAL_RESULTS", false))
            recognizer.triggerOnReadyForSpeech(Bundle())
            recognizer.triggerOnPartialResults(bundle("Nexus bon"))
            assertEquals("Nexus bon" to false, heard.last())
            assertTrue(commands.isEmpty())
            recognizer.triggerOnEndOfSpeech()
            recognizer.triggerOnResults(bundle("nexusbonjour", "Nexus bonjour", scores = floatArrayOf(.85f, .8f)))
            assertEquals(listOf("bonjour"), commands)
            assertEquals("Nexus bonjour" to true, heard.last())
            controller.stop()
            recognizer.triggerOnPartialResults(bundle("phrase périmée"))
            assertEquals("" to false, heard.last())
        } finally { controller.stop() }
    }

    @Test fun explicitDictationReplacesTheOldRecognizerAndAcceptsAnUnprefixedFinal() {
        val commands = mutableListOf<String>()
        val controller = NativeVoiceController(RuntimeEnvironment.getApplication(), {}, commands::add, {})
        try {
            controller.start(); shadowOf(Looper.getMainLooper()).idle()
            val previous = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
            controller.listenForCommand(); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(shadowOf(previous).isDestroyed)
            val current = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            current.triggerOnReadyForSpeech(Bundle())
            current.triggerOnResults(bundle("bonjour"))
            assertEquals(listOf("bonjour"), commands)
        } finally { controller.stop() }
    }

    @Test fun silenceRestartsReleaseTheOldClientAndRejectItsLateCallbacks() {
        val commands = mutableListOf<String>()
        val controller = NativeVoiceController(RuntimeEnvironment.getApplication(), {}, commands::add, {})
        try {
            controller.start(); shadowOf(Looper.getMainLooper()).idle()
            repeat(10) {
                val engine = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
                val recognizer = shadowOf(engine)
                recognizer.triggerOnReadyForSpeech(Bundle())
                recognizer.triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                assertTrue(recognizer.isDestroyed)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
                assertNotSame(engine, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
                recognizer.triggerOnPartialResults(bundle("phrase périmée"))
                recognizer.triggerOnResults(bundle("Nexus ancienne commande"))
                recognizer.triggerOnError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                assertTrue(commands.isEmpty())
            }
            val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            recognizer.triggerOnReadyForSpeech(Bundle())
            recognizer.triggerOnResults(bundle("Nexus bonjour"))
            assertEquals(listOf("bonjour"), commands)
        } finally { controller.stop() }
    }
}
