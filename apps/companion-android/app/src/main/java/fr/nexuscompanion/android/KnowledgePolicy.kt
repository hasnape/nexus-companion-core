package fr.nexuscompanion.android

/**
 * Pure rules shared by UI and library. External text is data, not commands.
 * The user must explicitly approve saving any material.
 */
internal object KnowledgePolicy {
    fun queryTerms(query: String): List<String> =
        query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }.distinct().take(8)

    fun isExpired(expiresAt: Long?, now: Long): Boolean =
        expiresAt != null && expiresAt <= now

    fun score(queryTerms: List<String>, title: String, excerpt: String): Int {
        if (queryTerms.isEmpty()) return 0
        val titleLower = title.lowercase()
        val excerptLower = excerpt.lowercase()
        return queryTerms.map { term ->
            when {
                titleLower.contains(term) -> 3
                excerptLower.contains(term) -> 1
                else -> 0
            }
        }.sum()
    }

    fun isValidUserNote(title: String, excerpt: String): Boolean =
        title.isNotBlank() && title.length <= 160 &&
            excerpt.isNotBlank() && excerpt.length <= 2000
}
