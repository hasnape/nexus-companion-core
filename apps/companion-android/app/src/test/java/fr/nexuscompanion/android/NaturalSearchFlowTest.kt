package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class NaturalSearchFlowTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun library(id:String)=LocalKnowledgeLibrary(context,id,LocalKnowledgeLibraryTest.JvmCipher(id))
    @Test fun webAndWikipediaHaveDistinctExplicitRoutesAndTheWebDoesNotPretendToReturnAnArticle() {
        val queries=mutableListOf<String>()
        NativeBrainService(context,library("search-scopes"),search={queries.add(it);WikipediaKnowledgeSearch.Article(1,"Volcan","Le volcan émet de la lave.")}).use { brain ->
            val web=brain.reply("cherche sur internet les réponses de salutations")
            assertEquals("les réponses de salutations",web.external?.target)
            assertTrue(queries.isEmpty());assertNull(web.source)
            assertNull(brain.reply("répète").external)
            val wiki=brain.reply("cherche sur Wikipédia volcan")
            assertEquals(listOf("volcan"),queries);assertNotNull(wiki.source);assertNull(wiki.external)
        }
    }
    @Test fun theObservedMisrecognitionRequiresConfirmationAndExpiresWithoutSearching() {
        var now=100L;var queries=0
        NativeBrainService(context,library("search-confusion"),elapsed={now},search={queries++;null}).use { brain ->
            val offer=brain.reply("cher sur interdit que répondre bonjour")
            assertTrue(offer.text.contains("mal compris"));assertNull(offer.external);assertEquals(0,queries)
            assertNull(brain.reply("répète").external)
            assertEquals("que répondre bonjour",brain.reply("oui").external?.target)
            assertNull(brain.reply("oui").external)
            brain.reply("cher sur interdit les volcans");now+=NativeCapacity.PENDING_CONFIRMATION_MS+1
            assertNull(brain.reply("oui").external)
            assertEquals(0,queries)
        }
    }
    @Test fun aBareSearchCanReuseOnlyTheLastPublicUserQuestionAndAcceptAMissingSubjectOnTheNextTurn() {
        val lib=library("search-context");lib.addUserNote("Prénom","Je m’appelle Amine.")
        NativeBrainService(context,lib).use { brain ->
            assertTrue(brain.reply("Que répondre à bonjour").text.contains("Tu peux répondre"))
            assertEquals("Que répondre à bonjour",brain.reply("cherche sur internet").external?.target)
            brain.reply("Quel est mon prénom ?")
            assertNull(brain.reply("cherche sur internet").external)
            assertEquals("les volcans",brain.reply("les volcans").external?.target)
            brain.clearConversation();assertNull(brain.reply("cherche sur internet").external)
            assertNull(brain.reply("mon adresse exacte").external)
        }
    }
    @Test fun offlineRefusalAndCancellationDoNotLeaveAnActionToExecuteLater() {
        NativeBrainService(context,library("search-cancel")).use { brain ->
            assertNull(brain.reply("cherche sur internet les volcans",false).external)
            brain.reply("cherche sur internet");brain.reply("non");assertNull(brain.reply("oui").external)
            brain.reply("cher sur interdit les volcans");assertNull(brain.reply("oui",false).external)
            assertNull(brain.reply("oui").external)
            brain.reply("cherche sur internet");brain.clearConversation();assertNull(brain.reply("les volcans").external)
        }
    }
}
