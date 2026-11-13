package fr.nexuscompanion.android

/** In-memory acceleration only. A hash hit is always checked against the original text. */
internal class KnowledgeSearchIndex(
    private val entries: List<LocalKnowledgeLibrary.Entry>,
    private val score: (List<String>, LocalKnowledgeLibrary.Entry) -> Int = { terms, entry ->
        KnowledgePolicy.score(terms, entry.title, entry.excerpt)
    }
) {
    private val hashes = entries.map { KnowledgePolicy.searchHashes(it.title + " " + it.excerpt) }
    private val results = object : LinkedHashMap<List<String>, List<LocalKnowledgeLibrary.Entry>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<String>, List<LocalKnowledgeLibrary.Entry>>?) = size > 8
    }

    private val passages = LocalPassageRetrieval()
    private data class ConversationRanking(val active: List<LocalKnowledgeLibrary.Entry>, val ranked: List<LocalKnowledgeLibrary.Entry>)
    private val conversationResults = object : LinkedHashMap<List<String>, ConversationRanking>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<String>, ConversationRanking>?) = size > 8
    }
    fun rankForConversation(terms: List<String>, now: Long): List<LocalKnowledgeLibrary.Entry> {
        val active = rank(terms).filterNot { KnowledgePolicy.isExpired(it.expiresAt, now) }
        conversationResults[terms]?.takeIf { it.active == active }?.let { return it.ranked }
        return passages.rerank(terms, active).also { conversationResults[terms.toList()] = ConversationRanking(active, it) }
    }

    fun rank(terms: List<String>): List<LocalKnowledgeLibrary.Entry> {
        if (terms.isEmpty()) return emptyList()
        results[terms]?.let { return it }
        val termHashes = terms.map(KnowledgePolicy::searchHashes)
        val ranked = entries.mapIndexedNotNull { i, entry ->
            val potential = termHashes.any { words -> words.isNotEmpty() && words.all { hashes[i].binarySearch(it) >= 0 } }
            if (!potential) null else score(terms, entry).takeIf { it > 0 }?.let { entry to it }
        }.sortedWith(compareByDescending<Pair<LocalKnowledgeLibrary.Entry, Int>> { it.second }
            .thenByDescending { it.first.recordedAt }).map { it.first }
        results[terms.toList()] = ranked
        return ranked
    }
}
