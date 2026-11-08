package fr.nexuscompanion.android

import java.text.Normalizer
import java.util.Locale

/**
 * Pure rules shared by UI and library. External text is data, not commands.
 * The user must explicitly approve saving any material.
 */
internal object KnowledgePolicy {
    private val accents = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "au", "aux", "ca", "ce", "ces", "de", "du", "en", "et", "il", "je", "la", "le", "ma", "me",
        "ne", "on", "ou", "sa", "se", "ta", "te", "tu", "un", "va", "que", "qui", "les", "des", "une",
        "pour", "dans", "avec", "sur", "mes", "mon", "moi", "est", "sais", "souviens", "rappelle"
    )
    private val notesByTopic = Regex(
        "^(?:(?:rappelle moi|montre moi|montre) )?mes notes (?:sur|concernant|au sujet de) (.+)$"
    )
    private fun normalize(value: String): String =
        accents.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    fun queryTerms(query: String): List<String> =
        normalize(query).split(separators)
            .filter { it.length >= 2 && it !in stopWords }.distinct().take(8)

    /** A targeted recall may only use notes matching the requested topic. */
    fun recallTopic(query: String): List<String>? {
        val command = VoiceTranscriptPolicy.parse(query).command
        val words = normalize(command).split(separators).filter { it.isNotEmpty() }.joinToString(" ")
        return notesByTopic.matchEntire(words)?.groupValues?.get(1)?.let(::queryTerms)
    }

    fun isExpired(expiresAt: Long?, now: Long): Boolean =
        expiresAt != null && expiresAt <= now

    fun score(queryTerms: List<String>, title: String, excerpt: String): Int {
        if (queryTerms.isEmpty()) return 0
        val titleWords = normalize(title).split(separators).toSet()
        val excerptWords = normalize(excerpt).split(separators).toSet()
        val coverage = queryTerms.count { it in titleWords || it in excerptWords }
        val titleMatches = queryTerms.count { it in titleWords }
        // Coverage ranks first. Title matches break ties at the same coverage.
        return coverage * (queryTerms.size + 1) + titleMatches
    }

    fun isValidUserNote(title: String, excerpt: String): Boolean =
        title.isNotBlank() && title.length <= 160 &&
            excerpt.isNotBlank() && excerpt.length <= 2000
}
