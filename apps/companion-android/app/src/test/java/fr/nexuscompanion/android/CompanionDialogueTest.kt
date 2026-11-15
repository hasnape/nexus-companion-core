package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class CompanionDialogueTest {
    @Test fun identityIsArtificialAndNameCanBeCustomized() {
        assertTrue(CompanionDialogue().answer("Qui es-tu ?")!!.contains("Alif"))
        assertTrue(CompanionDialogue({ "Noura" }).answer("Comment t'appelles-tu ?")!!.contains("Noura"))
        assertTrue(CompanionDialogue().answer("Tu es humain ?")!!.contains("pas un être humain"))
    }

    @Test fun responsesVaryAndAreNotPersonalMemories() {
        val dialogue = CompanionDialogue()
        val first = dialogue.answer("Bonjour")
        val second = dialogue.answer("Bonjour")
        assertNotEquals(first, second)
        assertNull(dialogue.answer("Que sais-tu de moi ?"))
        assertNull(dialogue.answer("Lis mes notes"))
        assertNull(dialogue.answer("Bonjour, lis mes notes"))
    }

    @Test fun sensitiveAndTechnicalQuestionsRemainOutsideTemplateRouting() {
        val dialogue = CompanionDialogue()
        assertNull(dialogue.answer("Combien font 30 moins 10 ?"))
        assertNull(dialogue.answer("Quel temps fait-il à Lyon ?"))
        assertNull(dialogue.answer("Comment modifier mon code secret ?"))
        assertNull(dialogue.answer("Oublie ça"))
        assertNull(dialogue.answer("Stop"))
        assertNull(dialogue.answer("J'ai un projet de stockage des mots de passe"))
    }

    @Test fun emotionalAndSafetyResponsesAreBounded() {
        val dialogue = CompanionDialogue()
        assertNotNull(dialogue.answer("Je suis triste"))
        assertNotNull(dialogue.answer("Je suis en danger"))
        assertNotNull(dialogue.answer("Je veux me faire du mal"))
        assertNull(dialogue.answer("Silence"))
    }
}
