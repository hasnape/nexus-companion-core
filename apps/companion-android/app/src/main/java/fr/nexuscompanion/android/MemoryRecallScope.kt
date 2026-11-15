package fr.nexuscompanion.android

/**
 * Chooses a single evidence source for an ordinary factual question.
 *
 * This is deliberately separate from explicit overview/topic/read requests:
 * those may display multiple individually attributed notes. No single-answer
 * turn may silently merge several independent people, dates or projects.
 *
 * This class only receives notes already authorized by SelectiveMemoryAccess.
 * A learned association may help with a synonym, but cannot override a direct
 * lexical match or make two equally likely sources appear unambiguous.
 */
internal object MemoryRecallScope {
    sealed interface Decision {
        data class Single(val note: LocalKnowledgeLibrary.Entry) : Decision
        data object NoMatch : Decision
        data object Ambiguous : Decision
    }

    fun forFact(
        terms: List<String>,
        accessibleNotes: List<LocalKnowledgeLibrary.Entry>,
        preferredIds: List<String> = emptyList()
    ): Decision {
        if (terms.isEmpty() || accessibleNotes.isEmpty()) return Decision.NoMatch
        val current = accessibleNotes.distinctBy { it.id }
            .filter { !KnowledgePolicy.isExpired(it.expiresAt, System.currentTimeMillis()) }

        // Do not use arbitrary recent memories or weak learned links to
        // displace exact lexical evidence from a specific person's note.
        val ranked = current.mapNotNull { note ->
            KnowledgePolicy.score(terms, note.title, note.excerpt)
                .takeIf { it > 0 }?.let { note to it }
        }.sortedWith(compareByDescending<Pair<LocalKnowledgeLibrary.Entry, Int>> { it.second }
            .thenBy { it.first.id })

        if (ranked.isNotEmpty()) {
            if (ranked.size > 1 && ranked[0].second == ranked[1].second)
                return Decision.Ambiguous
            return Decision.Single(ranked.first().first)
        }

        // A single exact, current learned reference is safe to consider when
        // the user's wording uses a synonym absent from the original note.
        val byId = current.associateBy { it.id }
        val learned = preferredIds.distinct().mapNotNull(byId::get)
        return when (learned.size) {
            0 -> Decision.NoMatch
            1 -> Decision.Single(learned.single())
            else -> Decision.Ambiguous
        }
    }
}
