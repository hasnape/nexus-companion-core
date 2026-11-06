package fr.nexuscompanion.android

import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35])
class NativeRecognitionSettingsTest {
    @Test fun recognitionUsesFrenchOfflinePartialsAndBoundedAlternatives() {
        val intent = NativeRecognitionSettings.intent()
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("fr-FR", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals(5, intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0))
    }

    @Test fun hintsContainOnlyPublicNamesOnSupportedVersions() {
        val intent = NativeRecognitionSettings.intent()
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(listOf("Nexus", "Nexus Companion"), intent.getStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS))
            assertTrue(intent.hasExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT))
            assertFalse(intent.getBooleanExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, true))
        } else {
            assertFalse(intent.hasExtra("android.speech.extra.BIASING_STRINGS"))
            assertFalse(intent.hasExtra("android.speech.extra.ENABLE_BIASING_DEVICE_CONTEXT"))
        }
    }

    @Test fun incompleteAndMissingResultBundlesDoNotInventConfidenceScores() {
        assertTrue(NativeRecognitionSettings.hypotheses(null).isEmpty())
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("un", "deux"))
            putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(.9f))
        }
        assertEquals(listOf(SpeechHypothesis("un", .9f), SpeechHypothesis("deux")), NativeRecognitionSettings.hypotheses(bundle))
    }

    @Test fun resultExtractionLimitsTheNumberOfHypotheses() {
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, ArrayList((1..20).map { "phrase $it" }))
        }
        assertEquals(5, NativeRecognitionSettings.hypotheses(bundle).size)
    }
}
