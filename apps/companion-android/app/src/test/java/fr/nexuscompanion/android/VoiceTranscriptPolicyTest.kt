package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class VoiceTranscriptPolicyTest {
    @Test fun frenchWakeVariantsPreserveTheFullCommand() {
        for (wake in listOf("Nexus", "Néxus", "nexusse", "nexuce", "Nex us", "Nex-us", "Nex’us", "Hey Nexus", "Hé Nexus", "OK Nexus", "Nexus Companion", "Nexus compagnon")) {
            val parsed = VoiceTranscriptPolicy.parse("$wake, Retiens que je préfère le français !")
            assertTrue(wake, parsed.hasWake)
            assertEquals(wake, "Retiens que je préfère le français !", parsed.command)
        }
    }

    @Test fun embeddedWordsAndAmbientMentionsDoNotActivate() {
        for (text in listOf("nexusbonjour", "Je parle de Nexus", "Nexustech bonjour", "nex usb", "nexuses", "hey quelqu'un nexus")) {
            assertFalse(text, VoiceTranscriptPolicy.parse(text).hasWake)
        }
    }

    @Test fun wakeAloneAndDecomposedAccentsAreHandled() {
        assertEquals(VoiceTranscriptPolicy.Parsed(true, ""), VoiceTranscriptPolicy.parse("  NEXUS ! "))
        assertEquals(VoiceTranscriptPolicy.Parsed(true, "bonjour"), VoiceTranscriptPolicy.parse("Ne\u0301xus : bonjour"))
    }

    @Test fun closeHighConfidenceAlternativeCanRecoverTheWake() {
        assertEquals("Nexus bonjour", VoiceTranscriptPolicy.select(listOf(
            SpeechHypothesis("nexusbonjour", .84f), SpeechHypothesis("Nexus bonjour", .78f)
        ), false))
    }

    @Test fun lowDistantUnknownAndInvalidScoresDoNotPromoteAlternatives() {
        for ((best, other) in listOf(.9f to .5f, .95f to .7f, .4f to .95f, null to .9f, .9f to null, Float.NaN to .9f, .9f to Float.NaN, -1f to .9f, .9f to 1.1f)) {
            assertEquals("conversation", VoiceTranscriptPolicy.select(listOf(
                SpeechHypothesis("conversation", best), SpeechHypothesis("Nexus bonjour", other)
            ), false))
        }
    }

    @Test fun onlyTheFirstThreeHypothesesCanSupplyAWake() {
        val candidates = listOf("conversation", "bonjour", "autre", "Nexus bonjour").map { SpeechHypothesis(it, .9f) }
        assertEquals("conversation", VoiceTranscriptPolicy.select(candidates, false))
    }

    @Test fun commandModeAndValidTopWakeNeverReorderTheTranscript() {
        val candidates = listOf(SpeechHypothesis("Retiens ma préférence", .8f), SpeechHypothesis("Nexus autre chose", .79f))
        assertEquals("Retiens ma préférence", VoiceTranscriptPolicy.select(candidates, true))
        assertEquals("Nexus première phrase", VoiceTranscriptPolicy.select(listOf(
            SpeechHypothesis("Nexus première phrase", .5f), SpeechHypothesis("Nexus autre phrase", .9f)
        ), false))
    }

    @Test fun malformedOrOversizedTopTranscriptIsRejectedRatherThanTruncated() {
        for (text in listOf("", "  ", "x".repeat(2001))) {
            assertNull(VoiceTranscriptPolicy.select(listOf(SpeechHypothesis(text, .9f), SpeechHypothesis("Nexus bonjour", .9f)), false))
        }
        assertNull(VoiceTranscriptPolicy.select(emptyList(), true))
        assertEquals(2000, VoiceTranscriptPolicy.select(listOf(SpeechHypothesis("x".repeat(2000))), true)?.length)
    }
}
