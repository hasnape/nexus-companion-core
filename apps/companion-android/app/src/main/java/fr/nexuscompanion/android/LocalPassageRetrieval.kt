package fr.nexuscompanion.android

import java.text.Normalizer
import kotlin.math.ln

/** Recovered from the delivered 0.4.25: lexical rank plus BM25 over bounded passages. */
internal object LocalRankFusion {
    data class Hit(val id: String, val score: Double)
    fun fuse(channels: List<List<String>>): List<Hit> {
        val scores = linkedMapOf<String, Double>()
        channels.take(3).forEach { channel -> channel.take(128).filter { it.isNotBlank() }.distinct().forEachIndexed { i, id ->
            scores[id] = (scores[id] ?: 0.0) + 1.0 / (60.0 + i + 1)
        } }
        return scores.map { Hit(it.key, it.value) }.sortedByDescending { it.score }.take(64)
    }
}

internal class LocalPassageRetrieval(private val onParse: () -> Unit = {}) {
    private data class Passage(val normalized: String, val counts: Map<String, Int>, val length: Int)
    private data class Scored(val entry: LocalKnowledgeLibrary.Entry, val coverage: Int, val score: Double)
    private val cache = object : LinkedHashMap<LocalKnowledgeLibrary.Entry, List<Passage>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalKnowledgeLibrary.Entry, List<Passage>>?) = size > 64
    }
    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun passages(entry: LocalKnowledgeLibrary.Entry): List<Passage> {
        cache[entry]?.let { return it }
        onParse()
        val text = entry.excerpt
        val result = mutableListOf<Passage>()
        var start = 0
        while (start < text.length && result.size < 48) {
            var end = minOf(start + 900, text.length)
            if (end < text.length) {
                val boundary = (end downTo start + 450).firstOrNull { text[it - 1] in ".!?\n" && text[it].isWhitespace() }
                val space = text.lastIndexOf(' ', end)
                end = boundary ?: space.takeIf { it > start + 450 } ?: end
                if (end > start && text[end - 1].isHighSurrogate()) end--
            }
            val normalized = normalize(text.substring(start, end))
            val words = normalized.split(' ').filter { it.isNotEmpty() }
            if (words.isNotEmpty()) result.add(Passage(normalized, words.groupingBy { it }.eachCount(), words.size))
            if (end >= text.length) break
            start = maxOf(start + 1, end - 120)
            while (start < end && !text[start - 1].isWhitespace()) start++
            if (start < text.length && text[start].isLowSurrogate()) start++
        }
        return result.toList().also { cache[entry] = it }
    }
    private fun frequency(term: String, passage: Passage): Int {
        if (' ' !in term) return passage.counts[term] ?: 0
        val needle = " $term "; val haystack = " ${passage.normalized} "
        var start = 0; var count = 0
        while (start < haystack.length) {
            val found = haystack.indexOf(needle, start)
            if (found < 0) break
            count++; start = found + needle.length - 1
        }
        return count
    }
    fun rerank(terms: List<String>, lexical: List<LocalKnowledgeLibrary.Entry>): List<LocalKnowledgeLibrary.Entry> {
        if (lexical.size <= 1 || terms.isEmpty()) return lexical
        val query = terms.take(64).map(::normalize).filter { it.isNotEmpty() }.distinct()
        if (query.isEmpty()) return lexical
        val candidates = lexical.take(64)
        val byEntry = candidates.associateWith(::passages)
        val all = byEntry.values.flatten()
        if (all.isEmpty()) return lexical
        val average = all.sumOf { it.length }.toDouble() / all.size
        val df = query.associateWith { term -> all.count { frequency(term, it) > 0 } }
        val passageRank = candidates.map { entry ->
            val best = byEntry.getValue(entry).map { passage ->
                var score = 0.0; var coverage = 0
                for (term in query) {
                    val count = frequency(term, passage)
                    if (count == 0) continue
                    coverage++
                    val idf = ln(1.0 + (all.size - df.getValue(term) + 0.5) / (df.getValue(term) + 0.5))
                    score += idf * count * 2.2 / (count + 1.2 * (0.25 + 0.75 * passage.length / average))
                }
                coverage to score
            }.maxWithOrNull(compareBy<Pair<Int, Double>> { it.first }.thenBy { it.second }) ?: (0 to 0.0)
            Scored(entry, best.first, best.second)
        }.filter { it.coverage > 0 }.sortedWith(compareByDescending<Scored> { it.coverage }.thenByDescending { it.score })
        if (passageRank.isEmpty()) return lexical
        val indices = passageRank.mapIndexed { i, item -> item.entry.id to i }.toMap()
        val byId = candidates.associateBy { it.id }
        return LocalRankFusion.fuse(listOf(candidates.map { it.id }, passageRank.map { it.entry.id }))
            .sortedWith(compareByDescending<LocalRankFusion.Hit> { it.score }.thenBy { indices[it.id] ?: Int.MAX_VALUE })
            .mapNotNull { byId[it.id] } + lexical.drop(64)
    }
}
