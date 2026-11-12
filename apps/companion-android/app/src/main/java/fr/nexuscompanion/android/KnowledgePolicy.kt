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
    private val overviewRequests = setOf(
        "mes notes", "montre mes notes", "montre moi mes notes", "rappelle moi mes notes",
        "que sais tu de moi", "tu te souviens de moi", "te souviens tu de moi", "de quoi te souviens tu",
        "qu ai je note", "qu est ce que j ai note", "qu as tu retenu", "qu as tu memorise",
        "qu est ce que tu as retenu", "qu est ce que tu as memorise"
    )
    private val recallRequest = Regex(
        "^(?:(?:(?:rappelle moi|montre(?: moi)?) )?mes notes|" +
            "(?:(?:rappelle moi )?ce que|qu est ce que) (?:j ai note|tu as (?:retenu|memorise))|" +
            "qu (?:ai je note|as tu (?:retenu|memorise))|de quoi te souviens tu|" +
            "rappelle moi|tu te souviens|te souviens tu|que sais tu|mes preferences)(?: (.*))?$"
    )
    private val subjectPrefix = Regex("^(?:sur|concernant|au sujet de)(?: |$)")

    sealed interface Recall {
        object Overview : Recall
        data class Topic(val terms: List<String>) : Recall
        data class Read(val terms: List<String>) : Recall
    }

    private fun normalize(value: String): String =
        accents.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    /** Compact prefilter; collisions must be verified by score(), never used as evidence. */
    fun searchHashes(value: String): IntArray = normalize(value).split(separators)
        .asSequence().filter { it.isNotEmpty() }.map { it.hashCode() }.distinct().sorted().toList().toIntArray()

    fun queryTerms(query: String): List<String> =
        normalize(query).split(separators)
            .filter { it.length >= 2 && it !in stopWords }.distinct().take(8)

    /** Recognize an overview separately so a missing topic never falls back to recent notes. */
    fun recallFor(query: String): Recall? {
        val command = VoiceTranscriptPolicy.parse(query).command
        val words = normalize(command).split(separators).filter { it.isNotEmpty() }.joinToString(" ")
        Regex("^(?:lis|lire|lis moi) (?:ma|la|une) note(?: (.*))?$").matchEntire(words)?.let {
            return Recall.Read(queryTerms(it.groupValues[1].replaceFirst(subjectPrefix, "")))
        }
        if (words in overviewRequests) return Recall.Overview
        if (words == "mes preferences") return Recall.Topic(listOf("preference", "preferences", "prefere"))
        val subject = recallRequest.matchEntire(words)?.groupValues?.get(1) ?: return null
        return Recall.Topic(queryTerms(subject.replaceFirst(subjectPrefix, "")))
    }

    fun recallTopic(query: String): List<String>? = (recallFor(query) as? Recall.Topic)?.terms

    fun isExpired(expiresAt: Long?, now: Long): Boolean =
        expiresAt != null && expiresAt <= now

    fun score(queryTerms: List<String>, title: String, excerpt: String): Int {
        if (queryTerms.isEmpty()) return 0
        val titleWords = normalize(title).split(separators).toSet()
        val excerptWords = normalize(excerpt).split(separators).toSet()
        val normalizedText = " " + normalize("$title $excerpt").split(separators).joinToString(" ") + " "
        val coverage = queryTerms.count { it in titleWords || it in excerptWords || (it.contains(' ') && normalizedText.contains(" $it ")) }
        val titleMatches = queryTerms.count { it in titleWords }
        // Coverage ranks first. Title matches break ties at the same coverage.
        return coverage * (queryTerms.size + 1) + titleMatches
    }

    fun isValidUserNote(title: String, excerpt: String): Boolean =
        title.isNotBlank() && title.length <= 160 &&
            excerpt.isNotBlank() && excerpt.length <= NativeCapacity.MAX_NOTE_CHARS

    fun noteKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace('’', '\'').trim().replace(Regex("\\s+"), " ")
        .replace(Regex("[.!?]+$"), "").lowercase(Locale.FRANCE)
}
