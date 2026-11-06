package fr.nexuscompanion.android

import java.text.Normalizer
import kotlin.math.abs

internal data class SpeechHypothesis(val text: String, val confidence: Float? = null)

/** Only an initial, whole wake phrase can activate a command. Partials never reach this policy. */
internal object VoiceTranscriptPolicy {
    private val wake = Regex(
        "^(?:(?:hey|hé|eh|ok)[\\s,;:!?.…-]+)?" +
            "(?:n[ée]xus|n[ée]xusse|n[ée]xuce|n[ée]x[\\s'’\\-]+us)(?=$|[\\s,;:!?.…-])" +
            "(?:[\\s,;:!?.…-]+(?:companion|compagnon)(?=$|[\\s,;:!?.…-]))?[\\s,;:!?.…-]*",
        RegexOption.IGNORE_CASE
    )
    data class Parsed(val hasWake: Boolean, val command: String)

    fun parse(transcript: String): Parsed {
        val text = Normalizer.normalize(transcript.trim(), Normalizer.Form.NFC)
        val prefix = wake.find(text)
        return Parsed(prefix != null, if (prefix == null) text else text.substring(prefix.range.last + 1).trim())
    }

    fun select(hypotheses: List<SpeechHypothesis>, awaitingCommand: Boolean): String? {
        // Never promote another hypothesis when the best is blank or outside the command limit.
        val best = hypotheses.firstOrNull()?.takeIf { it.text.isNotBlank() && it.text.length <= 2000 } ?: return null
        if (awaitingCommand || parse(best.text).hasWake) return best.text.trim()
        val bestScore = best.confidence?.takeIf { it.isFinite() && it in 0f..1f } ?: return best.text.trim()
        return hypotheses.drop(1).take(2).firstOrNull { candidate ->
            val score = candidate.confidence
            candidate.text.isNotBlank() && candidate.text.length <= 2000 && parse(candidate.text).hasWake &&
                score != null && score.isFinite() && score in 0.65f..1f && abs(bestScore - score) <= 0.15f
        }?.text?.trim() ?: best.text.trim()
    }
}
