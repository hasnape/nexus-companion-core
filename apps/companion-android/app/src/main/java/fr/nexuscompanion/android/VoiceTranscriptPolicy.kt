package fr.nexuscompanion.android

import java.text.Normalizer
import kotlin.math.abs

internal data class SpeechHypothesis(val text: String, val confidence: Float? = null, val logLikelihood: Double? = null, val weakestWordConfidence: Float? = null)

/** Only an initial, whole wake phrase can activate a command. Partials never reach this policy. */
internal object VoiceTranscriptPolicy {
    private val wake = Regex(
        "^(?:(?:hey|hé|eh|ok)[\\s,;:!?.…-]+)?" +
            "(?:n[ée]xus|n[ée]xusse|n[ée]xuce|n[ée]x[\\s'’\\-]+us)(?=$|[\\s,;:!?.…-])" +
            "(?:[\\s,;:!?.…-]+(?:companion|compagnon)(?=$|[\\s,;:!?.…-]))?[\\s,;:!?.…-]*",
        RegexOption.IGNORE_CASE
    )
    data class Parsed(val hasWake: Boolean, val command: String)

    /** Only incomplete search requests may wait across a short acoustic endpoint. */
    fun isIncompleteSearch(text: String): Boolean {
        val words = commandWords(text).replaceFirst(Regex("^(?:peux tu|tu peux|pourrais tu) (?:me )?"), "")
        return words in setOf(
            "cherche", "chercher", "recherche", "rechercher", "cherche sur", "recherche sur",
            "cherche sur internet", "recherche sur internet", "cherche sur le web", "recherche sur le web",
            "cherche sur wikipedia", "recherche sur wikipedia", "cherche en ligne", "recherche en ligne"
        )
    }

    /** Exact approvals shared by the dialog worker and acoustic confidence check. */
    fun confirmation(text: String): Boolean? = when (commandWords(text)) {
        "oui", "oui merci", "oui je confirme", "je confirme", "d accord", "vas y",
        "oui cherche", "oui retiens", "oui retiens le", "retiens le", "enregistre le" -> true
        "non", "non merci", "annule", "laisse tomber", "n enregistre pas" -> false
        else -> null
    }

    fun isStopCommand(text: String): Boolean = commandWords(text) in setOf("stop", "arrete", "arrete toi", "arret", "stoppe", "stoppe toi", "arrete le corps", "arret du corps")

    fun uncertainConfirmation(hypothesis: SpeechHypothesis?): Boolean {
        val score = listOfNotNull(hypothesis?.confidence, hypothesis?.weakestWordConfidence).filter { it.isFinite() && it in 0f..1f }.minOrNull() ?: return false
        return score.isFinite() && score >= 0f && score < 0.45f && confirmation(hypothesis!!.text) != null
    }

    fun uncertainUtterance(hypothesis: SpeechHypothesis?): Boolean {
        if (hypothesis == null || isStopCommand(hypothesis.text)) return false
        val score = hypothesis.confidence ?: return false
        return score.isFinite() && score >= 0f && score < 0.35f
    }
    fun ambiguousAction(hypotheses: List<SpeechHypothesis>): Boolean {
        val best = hypotheses.firstOrNull() ?: return false
        val score = best.confidence?.takeIf { it.isFinite() && it in 0f..1f } ?: return false
        if (isStopCommand(best.text)) return false
        val words = commandWords(best.text)
        val action = Regex("^(?:avance|recule|tourne|ouvre|retiens|memorise|supprime|modifie|enregistre)\\b")
        if (confirmation(best.text) == null && !action.containsMatchIn(words)) return false
        return hypotheses.drop(1).take(2).any { other ->
            val competing = if (confirmation(best.text) != null) confirmation(other.text)?.let { it != confirmation(best.text) } == true
                else action.containsMatchIn(commandWords(other.text)) || isStopCommand(other.text)
            val otherScore = other.confidence
            other.text.isNotBlank() && otherScore != null && otherScore.isFinite() && otherScore in 0f..1f &&
                abs(score - otherScore) <= 0.05f && competing && commandWords(other.text) != words
        }
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
        val best = hypotheses.firstOrNull()?.takeIf { it.text.isNotBlank() && it.text.length <= NativeCapacity.MAX_MESSAGE_CHARS } ?: return null
        if ((awaitingCommand || parse(best.text).hasWake) && (uncertainConfirmation(best) || uncertainUtterance(best) || ambiguousAction(hypotheses))) return null
        if (awaitingCommand) return best.text.trim()
        if (parse(best.text).hasWake) return best.text.trim()
        // Recover only a lost word boundary; this never substitutes another command.
        val bestLikelihood = best.logLikelihood?.takeIf { it.isFinite() }
        if (bestLikelihood != null) {
            fun compact(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            val collapsed = compact(best.text)
            val recovered = hypotheses.drop(1).take(2).firstOrNull { candidate ->
                val likelihood = candidate.logLikelihood
                candidate.text.length <= NativeCapacity.MAX_MESSAGE_CHARS && parse(candidate.text).hasWake && likelihood != null && likelihood.isFinite() &&
                    abs(bestLikelihood - likelihood) <= 1.0 && compact(candidate.text) == collapsed && !uncertainConfirmation(candidate) && !uncertainUtterance(candidate)
            }
            if (recovered != null) return recovered.text.trim()
        }
        val bestScore = best.confidence?.takeIf { it.isFinite() && it in 0f..1f } ?: return best.text.trim()
        return hypotheses.drop(1).take(2).firstOrNull { candidate ->
            val score = candidate.confidence
            candidate.text.isNotBlank() && candidate.text.length <= NativeCapacity.MAX_MESSAGE_CHARS && parse(candidate.text).hasWake &&
                score != null && score.isFinite() && score in 0.65f..1f && abs(bestScore - score) <= 0.15f &&
                Normalizer.normalize(candidate.text, Normalizer.Form.NFD).lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "") == Normalizer.normalize(best.text, Normalizer.Form.NFD).lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "") &&
                !uncertainConfirmation(candidate) && !uncertainUtterance(candidate)
        }?.text?.trim() ?: best.text.trim()
    }
}
