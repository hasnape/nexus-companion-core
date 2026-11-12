package fr.nexuscompanion.android

import java.text.Normalizer
import kotlin.math.abs

internal data class SpeechHypothesis(val text: String, val confidence: Float? = null, val logLikelihood: Double? = null)

/** Only an initial, whole wake phrase can activate a command. Partials never reach this policy. */
internal object VoiceTranscriptPolicy {
    private val wake = Regex(
        "^(?:(?:hey|hé|eh|ok)[\\s,;:!?.…-]+)?" +
            "(?:n[ée]xus|n[ée]xusse|n[ée]xuce|n[ée]x[\\s'’\\-]+us)(?=$|[\\s,;:!?.…-])" +
            "(?:[\\s,;:!?.…-]+(?:companion|compagnon)(?=$|[\\s,;:!?.…-]))?[\\s,;:!?.…-]*",
        RegexOption.IGNORE_CASE
    )
    data class Parsed(val hasWake: Boolean, val command: String)

    fun isStopCommand(text: String): Boolean = commandWords(text) in setOf("stop", "arrete", "arrete toi", "arret", "stoppe", "stoppe toi", "arrete le corps", "arret du corps")

    fun uncertainConfirmation(hypothesis: SpeechHypothesis?): Boolean {
        val score = hypothesis?.confidence ?: return false
        return score.isFinite() && score >= 0f && score < 0.45f && commandWords(hypothesis.text) in
            setOf("oui", "non", "oui merci", "non merci", "d accord", "je confirme", "oui je confirme", "vas y")
    }

    /** Whole-command matching for conversational approvals and display controls. */
    fun commandWords(transcript: String): String = Normalizer.normalize(parse(transcript).command, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun parse(transcript: String): Parsed {
        val text = Normalizer.normalize(transcript.trim(), Normalizer.Form.NFC)
        val prefix = wake.find(text)
        return Parsed(prefix != null, if (prefix == null) text else text.substring(prefix.range.last + 1).trim())
    }

    fun select(hypotheses: List<SpeechHypothesis>, awaitingCommand: Boolean): String? {
        // Never promote another hypothesis when the best is blank or outside the command limit.
        val best = hypotheses.firstOrNull()?.takeIf { it.text.isNotBlank() && it.text.length <= 2000 } ?: return null
        if (awaitingCommand) return best.text.trim().takeUnless { uncertainConfirmation(best) }
        if (parse(best.text).hasWake) return best.text.trim()
        // Recover only a lost word boundary; this never substitutes another command.
        val bestLikelihood = best.logLikelihood?.takeIf { it.isFinite() }
        if (bestLikelihood != null) {
            fun compact(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            val collapsed = compact(best.text)
            val recovered = hypotheses.drop(1).take(2).firstOrNull { candidate ->
                val likelihood = candidate.logLikelihood
                candidate.text.length <= 2000 && parse(candidate.text).hasWake && likelihood != null && likelihood.isFinite() &&
                    abs(bestLikelihood - likelihood) <= 1.0 && compact(candidate.text) == collapsed
            }
            if (recovered != null) return recovered.text.trim()
        }
        val bestScore = best.confidence?.takeIf { it.isFinite() && it in 0f..1f } ?: return best.text.trim()
        return hypotheses.drop(1).take(2).firstOrNull { candidate ->
            val score = candidate.confidence
            candidate.text.isNotBlank() && candidate.text.length <= 2000 && parse(candidate.text).hasWake &&
                score != null && score.isFinite() && score in 0.65f..1f && abs(bestScore - score) <= 0.15f
        }?.text?.trim() ?: best.text.trim()
    }
}
