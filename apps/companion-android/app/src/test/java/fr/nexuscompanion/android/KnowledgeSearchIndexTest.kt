package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KnowledgeSearchIndexTest {
    private fun entry(id: String, title: String, text: String, at: Long = 1) =
        LocalKnowledgeLibrary.Entry(id, title, text, "user:manual", at, null, false)

    @Test fun preservesFullTextRankingIncludingPhrasesAccentsAndStableDateTies() {
        val entries = listOf(
            entry("1", "Mémoire", "Une information concernant le créateur est disponible.", 3),
            entry("2", "Profil", "Le créateur est Amine.", 1),
            entry("3", "Repère", "Mémoire locale et énergie.", 5),
            entry("4", "Mémoire", "Une information plus récente.", 6)
        )
        val index = KnowledgeSearchIndex(entries)
        for (terms in listOf(listOf("memoire"),listOf("le createur est"),listOf("energie","memoire"),listOf("port"),emptyList())) {
            val expected = entries.map { it to KnowledgePolicy.score(terms, it.title, it.excerpt) }.filter { it.second > 0 }
                .sortedWith(compareByDescending<Pair<LocalKnowledgeLibrary.Entry,Int>> { it.second }.thenByDescending { it.first.recordedAt }).map { it.first }
            assertEquals(terms.toString(), expected, index.rank(terms))
        }
    }

    @Test fun hashCollisionsNeverProduceFalseMatches() {
        assertEquals("an".hashCode(), "c0".hashCode())
        val index = KnowledgeSearchIndex(listOf(entry("collision", "c0", "texte")))
        assertTrue(index.rank(listOf("an")).isEmpty())
        assertEquals("collision", index.rank(listOf("c0")).single().id)
    }

    @Test fun repeatedQueriesAvoidRescoringAndTheCacheRemainsBounded() {
        val entries = (0 until 10).map { entry("$it", "repere$it", "contenu") }
        var calls = 0
        val index = KnowledgeSearchIndex(entries) { terms, entry -> calls++; KnowledgePolicy.score(terms, entry.title, entry.excerpt) }
        repeat(20) { assertEquals("0", index.rank(listOf("repere0")).single().id) }
        assertEquals(1, calls)
        (1..9).forEach { index.rank(listOf("repere$it")) }
        val before = calls
        index.rank(listOf("repere0"))
        assertEquals(before + 1, calls)
    }

    @Test fun measuresColdAndWarmSearchesInTenThousandSyntheticNotesWithoutTimingAssumptions() {
        val entries = (0 until 10000).map { entry("$it", "dossier$it", "Observation locale sur le module repere$it.") }
        val queries = listOf(0,100,1200,5000,9999).map { listOf("repere$it") }
        val baselineStart = System.nanoTime()
        val expected = queries.map { terms -> entries.filter { KnowledgePolicy.score(terms,it.title,it.excerpt) > 0 } }
        val baselineNs = System.nanoTime() - baselineStart
        var calls = 0
        val coldStart = System.nanoTime()
        val index = KnowledgeSearchIndex(entries) { terms, entry -> calls++; KnowledgePolicy.score(terms,entry.title,entry.excerpt) }
        val actual = queries.map(index::rank)
        val coldNs = System.nanoTime() - coldStart
        assertEquals(expected, actual)
        assertEquals(5, calls)
        val warmStart = System.nanoTime()
        repeat(10) { assertEquals(expected, queries.map(index::rank)) }
        val warmNs = System.nanoTime() - warmStart
        assertEquals(5, calls)
        File("build/reports/memory-search-performance.txt").also { it.parentFile?.mkdirs() }.writeText(
            "entries=10000\nqueries=5\nbaselineScoreCalls=50000\nindexedScoreCalls=$calls\nwarmAdditionalScoreCalls=0\n" +
                "baselineNs=$baselineNs\ncoldIndexAndQueriesNs=$coldNs\ntenWarmQuerySetsNs=$warmNs\n")
    }
}
