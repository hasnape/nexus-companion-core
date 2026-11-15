package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class SelectiveMemoryAccessTest {
    private fun note(source: String, title: String = "Note") =
        LocalKnowledgeLibrary.Entry("id-$source", title, "Texte", source, 1000L, null, false)

    @Test fun legacyNotesArePrivateByDefault() {
        val notes = listOf(note("user:manual"), note("external:import"), note("https://example.org"))
        assertTrue(SelectiveMemoryAccess.accessible(notes, unlocked = false).isEmpty())
        assertTrue(SelectiveMemoryAccess.needsUnlock(notes, unlocked = false))
    }

    @Test fun explicitlySharedNotesCanBeUsedWithoutUnlock() {
        val public = note(SelectiveMemoryAccess.SHARED_SOURCE, "Préférence ordinaire")
        val secret = note("user:manual", "Code privé")
        assertEquals(listOf(public), SelectiveMemoryAccess.accessible(listOf(public, secret), false))
        assertTrue(SelectiveMemoryAccess.needsUnlock(listOf(public, secret), false))
    }

    @Test fun unlockedOwnerCanReadBothAndLockRevokesAccess() {
        val notes = listOf(note("user:manual"), note(SelectiveMemoryAccess.SHARED_SOURCE))
        assertEquals(notes, SelectiveMemoryAccess.accessible(notes, true))
        assertFalse(SelectiveMemoryAccess.needsUnlock(notes, true))
        assertEquals(1, SelectiveMemoryAccess.accessible(notes, false).size)
    }

    @Test fun sensitiveContentIsRejectedBeforeSharing() {
        assertFalse(SelectiveMemoryAccess.safeToShare("Wi-Fi", "Mot de passe : exemple"))
        assertFalse(SelectiveMemoryAccess.safeToShare("Code", "123456"))
        assertFalse(SelectiveMemoryAccess.safeToShare("Contact", "personne@example.com"))
        assertFalse(SelectiveMemoryAccess.safeToShare("Adresse", "12 rue des Lilas"))
        assertTrue(SelectiveMemoryAccess.safeToShare("Préférence", "J'aime les promenades."))
    }

    @Test fun spacedPhoneNumbersCannotBeDeclassifiedWithoutCode() {
        val numbers = listOf(
            "06 12 34 56 78",
            "06-12-34-56-78",
            "06.12.34.56.78",
            "+33 6 12 34 56 78",
            "0033 6 12 34 56 78",
            "06\u202f12\u202f34\u202f56\u202f78"
        )
        for (number in numbers) {
            assertFalse("Contact $number should remain protected",
                SelectiveMemoryAccess.safeToShare("Contact", "Appelle-moi au $number."))
        }
        assertTrue(SelectiveMemoryAccess.safeToShare("Cours", "J'ai terminé 12 modules et 3 exercices."))
    }

    @Test fun sourcePrefixCannotAccidentallyDeclassify() {
        assertFalse(SelectiveMemoryAccess.canRead(note("user:conversation-shared:fake"), false))
        assertFalse(SelectiveMemoryAccess.canRead(note("user:conversation"), false))
    }
}
