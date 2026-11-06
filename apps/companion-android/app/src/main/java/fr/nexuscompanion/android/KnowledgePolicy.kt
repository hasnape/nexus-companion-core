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
    private val stopWords = setOf("que", "qui", "les", "des", "une", "pour", "dans", "avec", "sur", "mes", "mon", "moi", "est", "sais", "souviens", "rappelle")
    private fun normalize(value: String): String =
        accents.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    fun queryTerms(query: String): List<String> =
        normalize(query).split(separators)
            .filter { it.length >= 3 && it !in stopWords }.distinct().take(8)

    fun isExpired(expiresAt: Long?, now: Long): Boolean =
        expiresAt != null && expiresAt <= now

    fun score(queryTerms: List<String>, title: String, excerpt: String): Int {
        if (queryTerms.isEmpty()) return 0
        val titleWords = normalize(title).split(separators).toSet()
        val excerptWords = normalize(excerpt).split(separators).toSet()
        return queryTerms.fold(0) { score, term ->
            score + when {
                term in titleWords -> 3
                term in excerptWords -> 1
                else -> 0
            }
        }
    }

    fun isValidUserNote(title: String, excerpt: String): Boolean =
        title.isNotBlank() && title.length <= 160 &&
            excerpt.isNotBlank() && excerpt.length <= 2000
}
