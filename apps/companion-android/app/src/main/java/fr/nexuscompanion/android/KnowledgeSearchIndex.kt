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
