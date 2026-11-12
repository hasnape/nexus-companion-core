package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalSpeechResultTest {
    @Test fun keepsVoskLikelihoodSeparateFromWordConfidence() {
        val result = LocalSpeechResult.hypotheses("""{"alternatives":[{"text":"nexusbonjour","confidence":120.5},{"text":"nexus bonjour","confidence":120.1}]}""")
        assertNull(result.first().confidence)
        assertEquals(120.5, result.first().logLikelihood!!, 0.001)
        assertEquals("nexus bonjour", VoiceTranscriptPolicy.select(result, false))
    }
    @Test fun doesNotReplaceAnUncertainCommandWithAnotherMeaning() {
        val result = LocalSpeechResult.hypotheses("""{"alternatives":[{"text":"bonjour","confidence":120.5},{"text":"nexus avance","confidence":120.1}]}""")
        assertEquals("bonjour", VoiceTranscriptPolicy.select(result, false))
    }
    @Test fun readsLegacyWordConfidenceAndRejectsAMalformedTopAlternative() {
        val result = LocalSpeechResult.hypotheses("""{"text":"bonjour","result":[{"conf":0.8},{"conf":1.0}]}""").single()
        assertEquals(0.9f, result.confidence!!, 0.001f)
        for (payload in listOf("""{"alternatives":[{"text":""},{"text":"nexus avance"}]}""", """{"alternatives":[null,{"text":"nexus avance"}]}""")) {
            assertTrue(LocalSpeechResult.hypotheses(payload).isEmpty())
        }
    }
}
