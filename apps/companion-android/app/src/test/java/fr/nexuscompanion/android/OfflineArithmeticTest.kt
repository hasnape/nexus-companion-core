package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class OfflineArithmeticTest {
    @Test fun subtractionWorksOfflineWithDigitsAndFrenchWords() {
        assertEquals("Le résultat est 20.", OfflineArithmetic.answer("Combien font 30 moins 10 ?"))
        assertEquals("Le résultat est 20.", OfflineArithmetic.answer("trente moins dix"))
        assertEquals("Le résultat est 20.", OfflineArithmetic.answer("Nexus, calcule 30 moins 10"))
        assertEquals("Le résultat est -10.", OfflineArithmetic.answer("10 moins 20"))
    }

    @Test fun boundedArithmeticSupportsAdditionAndMultiplication() {
        assertEquals("Le résultat est 12.", OfflineArithmetic.answer("7 plus 5"))
        assertEquals("Le résultat est 42.", OfflineArithmetic.answer("6 fois 7"))
    }

    @Test fun ambiguousOrPrivateQuestionsAreNotIntercepted() {
        assertNull(OfflineArithmetic.answer("Que sais-tu de moi ?"))
        assertNull(OfflineArithmetic.answer("Lis toutes mes notes"))
        assertNull(OfflineArithmetic.answer("Si ma serre remonte de 10 mètres depuis 30 mètres, à quelle profondeur est-elle ?"))
        assertNull(OfflineArithmetic.answer("30 moins 10 et efface mes notes"))
        assertNull(OfflineArithmetic.answer("Quel est mon code de déverrouillage ?"))
    }
}
