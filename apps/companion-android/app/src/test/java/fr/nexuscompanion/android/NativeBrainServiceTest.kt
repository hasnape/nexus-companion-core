package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeBrainServiceTest {
    @Test fun onlyAnExplicitPublicTopicReachesSearchAndAnOutageLeavesLocalAnswersAvailable() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "online", LocalKnowledgeLibraryTest.JvmCipher("online"))
        library.addUserNote("Préférence", "Je préfère le thé vert")
        val queries = mutableListOf<String>()
        val article = WikipediaKnowledgeSearch.Article(1359783, "Tour Eiffel", "Une tour à Paris.")
        NativeBrainService(context, library) { topic ->
            queries.add(topic)
            when (topic) {
                "un sujet sans article" -> null
                "volcan" -> throw IOException("Offline")
                else -> article
            }
        }.use { brain ->
            val command = "Nexus, cherche sur Internet la tour Eiffel"
            assertEquals(context.getString(R.string.online_disabled_reply), brain.reply(command, onlineEnabled = false).text)
            assertTrue(brain.reply("mes notes").text.contains("thé vert"))
            assertTrue(brain.reply("Mon PC est lent").text.contains("Gestionnaire des tâches"))
            assertNull(brain.reply("Cherche sur Internet mon adresse").source)
            assertTrue(queries.isEmpty())

            val online = brain.reply(command)
            assertEquals(article, online.source)
            assertTrue(online.text.contains("Une tour à Paris."))
            assertNull(online.candidate)
            assertEquals(listOf("la tour Eiffel"), queries)
            assertEquals(context.getString(R.string.online_no_match), brain.reply("Cherche en ligne un sujet sans article").text)
            assertEquals(context.getString(R.string.online_unavailable), brain.reply("Cherche en ligne volcan").text)
            assertTrue(brain.reply("bonjour").text.contains("Bonjour"))
        }
    }

    @Test fun wikipediaUsesSearchRankAndAvoidsDisambiguationPages() {
        val response = JSONObject("""
            {"query":{"pages":[
                {"pageid":55,"title":"Un résultat secondaire","index":2,"extract":"Texte secondaire"},
                {"pageid":12,"title":"Le bon article","index":1,"extract":"Extrait public"}
            ]}}
        """.trimIndent())
        val article = WikipediaKnowledgeSearch.readArticle(response)
        assertEquals("Le bon article", article?.title)
        assertEquals("https://fr.wikipedia.org/?curid=12", article?.url)
        response.getJSONObject("query").getJSONArray("pages").getJSONObject(1)
            .put("pageprops", JSONObject().put("disambiguation", ""))
        assertNull(WikipediaKnowledgeSearch.readArticle(response))
    }

    @Test fun sharedBrainUsesEncryptedNotesButNeverPersistsAProposalByItself() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "bridge", LocalKnowledgeLibraryTest.JvmCipher("bridge"))
        library.addUserNote("Préférence", "Je préfère le thé vert")
        val file = File(context.filesDir, "knowledge-bridge.json")
        val before = file.readBytes()
        NativeBrainService(context, library).use { brain ->
            val recall = brain.reply("de quoi te souviens-tu ?")
            assertTrue(recall.text.contains("thé vert"))
            assertTrue(recall.notesAvailable)
            val proposal = brain.reply("retiens que je préfère les réponses courtes")
            assertNotNull(proposal.candidate)
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun escapedLargeContextIsBoundedWithoutMutatingAnyStoredNote() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "large", LocalKnowledgeLibraryTest.JvmCipher("large"))
        repeat(8) { library.addUserNote("note-$it", "\u0001".repeat(1990) + "note-$it") }
        val file = File(context.filesDir, "knowledge-large.json")
        val before = file.readBytes()
        NativeBrainService(context, library).use { brain ->
            val recall = brain.reply("mes notes")
            assertTrue(recall.notesAvailable)
            assertTrue(recall.text.length < 1200)
            assertTrue(recall.text.contains("notes complètes"))
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun topicRecallUsesMatchingNotesAndAvoidsAnUnrelatedFallback() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "topic", LocalKnowledgeLibraryTest.JvmCipher("topic"))
        library.addUserNote("Batterie", "La batterie de secours se recharge en USB.")
        library.addUserNote("Préférence", "Je préfère le thé vert.")
        NativeBrainService(context, library).use { brain ->
            for (question in listOf("Nexus, mes notes sur la batterie", "Qu’est-ce que j’ai noté sur la batterie ?")) {
                val match = brain.reply(question)
                assertTrue(question, match.text.contains("batterie de secours"))
                assertFalse(question, match.text.contains("thé vert"))
            }

            for (question in listOf("Mes notes sur le permis", "Tu te souviens de mon permis ?", "Mes notes sur")) {
                val missing = brain.reply(question)
                assertTrue(question, missing.text.contains("Je ne retrouve pas"))
                assertFalse(question, missing.text.contains("batterie de secours") || missing.text.contains("thé vert"))
            }

            val overview = brain.reply("Qu’ai-je noté ?")
            assertTrue(overview.text.contains("batterie de secours") && overview.text.contains("thé vert"))
        }
    }
}
