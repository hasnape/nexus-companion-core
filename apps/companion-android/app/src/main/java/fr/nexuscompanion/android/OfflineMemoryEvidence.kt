package fr.nexuscompanion.android

/**
 * Deterministic, quote-only memory fallback when optional native generation is
 * unavailable. Ranking never uses recency alone: a remembered fact must match
 * the question, and its exact complete sentence must be safe for quotation.
 *
 * This component does NOT grant access to notes; NativeBrainService must first
 * filter by SelectiveMemoryAccess, and recheck current state before display.
 */
internal object OfflineMemoryEvidence {
    data class Selection(val note: LocalKnowledgeLibrary.Entry, val sentence: String)

    fun select(queryTerms: List<String>, notes: List<LocalKnowledgeLibrary.Entry>): Selection? {
        if (queryTerms.isEmpty()) return null
        return notes.asSequence().mapNotNull { note ->
            val score = KnowledgePolicy.score(queryTerms, note.title, note.excerpt)
            val coverage = score / (queryTerms.size + 1)
            val distinctiveTitleMatch = queryTerms.any { term ->
                term.length >= 5 && KnowledgePolicy.score(listOf(term), note.title, "") > 0
            }
            // One generic token is not sufficient; a distinctive exact title
            // match is the narrow exception for short spoken questions.
            if (coverage < 2 && !(coverage >= 1 && distinctiveTitleMatch && queryTerms.size <= 3))
                return@mapNotNull null
            val sentence = GroundedNotes.prepare(listOf(note), queryTerms)
                .firstOrNull()?.sentences?.firstOrNull() ?: return@mapNotNull null
            score to Selection(note, sentence)
        }.maxByOrNull { it.first }?.second
    }
}
