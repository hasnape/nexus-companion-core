package fr.nexuscompanion.android

import java.text.Normalizer
import java.util.Locale

/**
 * Narrow, deterministic, offline arithmetic. Deliberately refuses ambiguous
 * natural-language questions rather than guessing or touching private memory.
 */
internal object OfflineArithmetic {
    private fun normalize(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.FRANCE)
        .replace('’', '\'').replace(Regex("[?!.,]"), " ").replace(Regex("\\s+"), " ").trim()

    private val numbers = mapOf(
        "zero" to 0L, "un" to 1L, "une" to 1L, "deux" to 2L,
        "trois" to 3L, "quatre" to 4L, "cinq" to 5L,
        "six" to 6L, "sept" to 7L, "huit" to 8L,
        "neuf" to 9L, "dix" to 10L, "vingt" to 20L,
        "trente" to 30L, "quarante" to 40L, "cinquante" to 50L,
        "soixante" to 60L, "cent" to 100L
    )
    private val number = "(?:[0-9]{1,9}|zero|un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|vingt|trente|quarante|cinquante|soixante|cent)"
    private val prefix = "(?:(?:combien (?:font|fait)|calcule|quel est le resultat de|nexus calcule) )?"
    private val subtraction = Regex("^$prefix($number) (?:moins|-) ($number)$")
    private val addition = Regex("^$prefix($number) (?:plus|\\+) ($number)$")
    private val multiplication = Regex("^$prefix($number) (?:fois|multiplie par|\\*) ($number)$")

    fun answer(input: String): String? {
        if (input.length > 120) return null
        val query = normalize(VoiceTranscriptPolicy.parse(input).command)
        val match = subtraction.matchEntire(query) ?: addition.matchEntire(query) ?: multiplication.matchEntire(query) ?: return null
        val left = match.groupValues[1].toLongOrNull() ?: numbers[match.groupValues[1]] ?: return null
        val right = match.groupValues[2].toLongOrNull() ?: numbers[match.groupValues[2]] ?: return null
        val value = when {
            subtraction.matches(query) -> left - right
            addition.matches(query) -> left + right
            else -> left * right
        }
        return "Le résultat est $value."
    }
}
