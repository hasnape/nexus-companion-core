package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class SpeechTextTest {
    @Test fun partitionsWithoutLosingSpacesPunctuationOrEmoji() {
        val text = "Bonjour ! Voici une phrase complète. ".repeat(150) + "🛞".repeat(1000) + " FIN exacte."
        for (limit in listOf(2,17,1000,4000)) {
            val parts = SpeechText.split(text, limit)
            assertEquals(text, parts.joinToString(""))
            assertTrue(parts.all { it.length <= limit && it.isNotEmpty() })
            assertFalse(parts.dropLast(1).any { Character.isHighSurrogate(it.last()) })
            assertFalse(parts.drop(1).any { Character.isLowSurrogate(it.first()) })
        }
    }
    @Test fun prefersASentenceBoundaryAndRejectsAnInvalidEngineLimit() {
        assertEquals(listOf("Bonjour. ","Ça va ?"), SpeechText.split("Bonjour. Ça va ?", 10))
        assertTrue(SpeechText.split("",1000).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { SpeechText.split("test",1) }
    }
}
