package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class KnowledgePolicyTest {
    @Test fun extractsFrenchQueryTermsWithoutCommonPunctuation() {
        assertEquals(listOf("nexus", "memoire"), KnowledgePolicy.queryTerms("Nexus, mémoire?").map {
            if (it == "mémoire") "memoire" else it
        })
    }

    @Test fun neverMatchesEmptyOrTinyQueries() {
        assertEquals(0, KnowledgePolicy.score(KnowledgePolicy.queryTerms("a ?"), "Titre", "texte"))
    }

    @Test fun ranksTitlesAboveBodyMatches() {
        val terms = KnowledgePolicy.queryTerms("batterie")
        assertTrue(KnowledgePolicy.score(terms, "Batterie", "conseils") >
            KnowledgePolicy.score(terms, "Énergie", "batterie faible"))
    }

    @Test fun acceptsShortTopicsAndRanksCoverageBeforeTitleMatches() {
        val topic = KnowledgePolicy.recallTopic("Hé Nexus, montre-moi mes notes sur l’IA et la sécurité.")
        assertEquals(listOf("ia", "securite"), topic)
        val terms = requireNotNull(topic)
        assertTrue(KnowledgePolicy.score(terms, "Projet", "Sécurité de l’IA") >
            KnowledgePolicy.score(terms, "IA", "Réunion"))
        assertNull(KnowledgePolicy.recallTopic("mes notes"))
    }

    @Test fun distinguishesNaturalTopicRecallFromAnOverviewOrLearningRequest() {
        val expected = KnowledgePolicy.Recall.Topic(listOf("batterie"))
        assertEquals(expected, KnowledgePolicy.recallFor("Nexus Companion, qu’est-ce que j’ai noté sur la batterie ?"))
        assertEquals(expected, KnowledgePolicy.recallFor("Rappelle-moi ce que tu as mémorisé au sujet de ma batterie."))
        assertEquals(expected, KnowledgePolicy.recallFor("Tu te souviens de ma batterie ?"))
        assertEquals(KnowledgePolicy.Recall.Topic(emptyList()), KnowledgePolicy.recallFor("Mes notes sur"))
        assertEquals(KnowledgePolicy.Recall.Overview, KnowledgePolicy.recallFor("Qu’ai-je noté ?"))
        assertNull(KnowledgePolicy.recallFor("Souviens-toi que la batterie se recharge en USB."))
    }

    @Test fun filtersExpiredKnowledge() {
        assertTrue(KnowledgePolicy.isExpired(100L, 100L))
        assertFalse(KnowledgePolicy.isExpired(null, 100L))
        assertFalse(KnowledgePolicy.isExpired(101L, 100L))
    }

    @Test fun rejectsEmptyAndOverlongNotes() {
        assertFalse(KnowledgePolicy.isValidUserNote("", "texte"))
        assertFalse(KnowledgePolicy.isValidUserNote("titre", "x".repeat(2001)))
        assertTrue(KnowledgePolicy.isValidUserNote("titre", "texte"))
    }

    @Test fun matchesFrenchAccentsWithoutDependingOnTheKeyboard() {
        assertTrue(KnowledgePolicy.score(KnowledgePolicy.queryTerms("memoire"), "Mémoire", "") > 0)
        assertTrue(KnowledgePolicy.score(KnowledgePolicy.queryTerms("THÉ"), "Thé vert", "") > 0)
    }

    @Test fun ignoresCommonQuestionWordsAndDoesNotMatchWordFragments() {
        assertEquals(listOf("projet"), KnowledgePolicy.queryTerms("Que sais-tu de mon projet ?"))
        assertEquals(0, KnowledgePolicy.score(KnowledgePolicy.queryTerms("port"), "Transport", "portefeuille"))
    }
}
