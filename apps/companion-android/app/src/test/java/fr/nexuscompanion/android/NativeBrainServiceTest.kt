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
    @Test fun longPlaybackRenewsAReadOnlyResponseButCannotReviveConsent() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"played",LocalKnowledgeLibraryTest.JvmCipher("played"))
        var elapsed=0L;var calls=0
        authorizedBrain(context,library,{elapsed}) { calls++;null }.use { brain ->
            val greeting=brain.reply("bonjour")
            elapsed=NativeCapacity.PENDING_CONFIRMATION_MS+100L
            brain.replyPlayed()
            assertEquals(greeting.text,brain.reply("répète").text)
            brain.reply("Qu’est-ce qu’un volcan ?")
            elapsed+=NativeCapacity.PENDING_CONFIRMATION_MS+100L
            brain.replyPlayed();brain.reply("oui")
            assertEquals(0,calls)
            assertTrue(library.list().isEmpty())
        }
    }
    @Test fun anExternallyChangedNoteWithTheSameIdentityIsNotReplayed() {
        val context=RuntimeEnvironment.getApplication()
        val cipher=LocalKnowledgeLibraryTest.JvmCipher("external-focus")
        val library=LocalKnowledgeLibrary(context,"external-focus",cipher)
        library.addUserNote("Métier","Je travaille comme technicien.")
        val file=File(context.filesDir,"knowledge-external-focus.json")
        authorizedBrain(context,library).use { brain ->
            assertTrue(brain.reply("Quel est mon métier ?").text.contains("technicien"))
            val root=JSONObject(String(KnowledgePayload.decode(cipher.decrypt(file.readBytes()))))
            root.getJSONArray("entries").getJSONObject(0).put("excerpt","Je travaille comme électricien.")
            file.writeBytes(cipher.encrypt(KnowledgePayload.encode(root.toString().toByteArray())))
            assertFalse(brain.reply("répète").text.contains("technicien"))
            assertTrue(brain.reply("Quel est mon métier ?").text.contains("électricien"))
        }
    }
    @Test fun repeatKeepsAMemoryApprovalButNeverRenewsItsExpiry() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"repeat-memory",LocalKnowledgeLibraryTest.JvmCipher("repeat-memory"))
        var elapsed=0L
        authorizedBrain(context,library,{elapsed}) { null }.use { brain ->
            val proposal=brain.reply("retiens que je préfère le thé vert")
            assertEquals(proposal.text,brain.reply("répète").text)
            assertTrue(library.list().isEmpty())
            elapsed=NativeCapacity.PENDING_CONFIRMATION_MS-1L
            assertEquals(proposal.text,brain.reply("répète").text)
            elapsed+=2
            brain.reply("oui")
            assertTrue(library.list().isEmpty())
            assertTrue(brain.reply("répète").text.contains("aucune recherche"))
        }
    }
    @Test fun repeatingBodySpeechDoesNotCreateAnotherMovementPlan() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"repeat-body",LocalKnowledgeLibraryTest.JvmCipher("repeat-body"))
        authorizedBrain(context,library).use { brain ->
            val moved=brain.reply("avance de vingt centimètres")
            assertNotNull(moved.body)
            val repeated=brain.reply("répète ta réponse")
            assertEquals(moved.text,repeated.text)
            assertNull(repeated.body)
            assertTrue(library.list().isEmpty())
        }
    }
    @Test fun focusedReadAndEditUseOnlyTheActualSingleNoteFromTheLastAnswer() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"focus",LocalKnowledgeLibraryTest.JvmCipher("focus"))
        val note=library.addUserNote("Métier","Je travaille comme technicien informatique. Une information complémentaire locale.").entry
        val before=File(context.filesDir,"knowledge-focus.json").readBytes()
        authorizedBrain(context,library).use { brain ->
            assertTrue(brain.reply("Quel est mon métier ?").text.contains("technicien"))
            assertTrue(brain.reply("lis la note complète").text.endsWith(note.excerpt))
            assertEquals(note,brain.reply("modifie cette note").editNote)
            assertArrayEquals(before,File(context.filesDir,"knowledge-focus.json").readBytes())
            brain.clearConversation()
            assertNull(brain.reply("modifie cette note").editNote)
        }
    }
    @Test fun editedOrDeletedEvidenceCannotBeReplayedFromAnOldConversation() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"stale-focus",LocalKnowledgeLibraryTest.JvmCipher("stale-focus"))
        val original=library.addUserNote("Métier","Je travaille comme technicien.").entry
        authorizedBrain(context,library).use { brain ->
            assertTrue(brain.reply("Quel est mon métier ?").text.contains("technicien"))
            val revised=library.replace(original,"Métier","Je travaille comme ingénieur.").entry
            assertFalse(brain.reply("répète").text.contains("technicien"))
            assertTrue(brain.reply("Et mon métier").text.contains("ingénieur"))
            assertTrue(library.remove(revised.id))
            assertNull(brain.reply("modifie cette note").editNote)
            assertFalse(brain.reply("répète").text.contains("ingénieur"))
        }
    }
    @Test fun ambiguousEvidenceDoesNotChooseANoteToEditAndBackgroundClearsTheFocus() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"ambiguous-focus",LocalKnowledgeLibraryTest.JvmCipher("ambiguous-focus"))
        library.addUserNote("Métier","Je travaille comme technicien.")
        library.addUserNote("Métier","Je travaille comme électricien.")
        authorizedBrain(context,library).use { brain ->
            assertTrue(brain.reply("Quel est mon métier ?").text.contains("Laquelle"))
            assertNull(brain.reply("modifie cette note").editNote)
            assertFalse(brain.reply("lis la note complète").text.contains("technicien"))
            brain.clearConversation()
            assertTrue(brain.reply("répète").text.contains("plus de réponse"))
            assertEquals(2,library.list().size)
        }
    }
    @Test fun repeatingAnOnlineExtractDoesNotSearchAgainOrSaveItBeforeApproval() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"repeat-source",LocalKnowledgeLibraryTest.JvmCipher("repeat-source"))
        var calls=0
        authorizedBrain(context,library) { calls++;WikipediaKnowledgeSearch.Article(42,"Volcan","Un volcan émet de la lave.") }.use { brain ->
            val first=brain.reply("cherche sur Wikipédia volcan")
            assertEquals(first.text,brain.reply("répète",false).text)
            assertEquals(1,calls)
            assertTrue(library.list().isEmpty())
            brain.reply("oui")
            assertEquals(1,library.list().size)
            assertEquals(1,calls)
        }
    }
    @Test fun repeatingAProposalDoesNotDiscardOrExecuteItsPendingApproval() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context,"repeat-proposal",LocalKnowledgeLibraryTest.JvmCipher("repeat-proposal"))
        var calls = 0
        authorizedBrain(context,library) { calls++;null }.use { brain ->
            val first = brain.reply("Qu’est-ce qu’un volcan ?")
            assertEquals(first.text,brain.reply("Nexus, répète ta réponse").text)
            assertEquals(0,calls)
            brain.reply("oui")
            assertEquals(1,calls)
            assertTrue(library.list().isEmpty())
        }
    }
    @Test fun escapedLargeContextsAreBudgetedWithoutTruncatingTheStoredNotes() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"escaped-expanded",LocalKnowledgeLibraryTest.JvmCipher("escaped-expanded"))
        val content="\u0001".repeat(15900)
        repeat(32) { library.addUserNote("Repère $it",content+"note$it") }
        authorizedBrain(context,library).use { brain -> assertTrue(brain.reply("bonjour").text.contains("Bonjour")) }
        assertEquals(32,library.list().size)
        assertTrue(library.list().all { it.excerpt.startsWith(content) })
    }
    @Test fun explicitlyReadingALongNoteReturnsItsCompleteTextWithoutNetworkAccess() {
        val context=RuntimeEnvironment.getApplication()
        val library=LocalKnowledgeLibrary(context,"full-read",LocalKnowledgeLibraryTest.JvmCipher("full-read"))
        val content="Observation du robot. ".repeat(700)+"FIN EXACTE."
        val note=library.addUserNote("Robot",content).entry
        var networkCalls=0
        authorizedBrain(context,library) { networkCalls++;null }.use { brain ->
            val read=brain.reply("Nexus, lis ma note sur le robot",false)
            assertTrue(read.text.endsWith(content))
            assertTrue(read.text.contains("Robot"))
            assertNull(read.candidate)
            assertFalse(brain.reply("Lis ma note sur le vélo",false).text.contains(content))
            assertFalse(brain.reply("Lis ma note",false).text.contains(content))
            assertEquals(content,library.list().single().excerpt)
            assertEquals(note.id,library.list().single().id)
            assertEquals(0,networkCalls)
        }
    }
    @Test fun longExplicitMemoryProposalsSurviveIntactAndStillNeedManualApproval() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "long-proposal", LocalKnowledgeLibraryTest.JvmCipher("long-proposal"))
        val content = "Une observation locale. ".repeat(290) + "Dernière information exacte."
        authorizedBrain(context, library).use { brain ->
            val reply = brain.reply("retiens que $content")
            assertEquals(content, reply.candidate?.content)
            assertTrue(reply.text.contains("confirmer"))
            brain.reply("oui")
            assertTrue(library.list().isEmpty())
        }
    }
    @Test fun spokenQuestionVariantsDoNotBecomeNotesAndTheMissingSlotIsExplained() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "questions", LocalKnowledgeLibraryTest.JvmCipher("questions"))
        library.addUserNote("Profil", "Le créateur est Amine.")
        library.addUserNote("Métier", "Je travaille comme technicien informatique.")
        authorizedBrain(context, library) { null }.use { brain ->
            assertTrue(brain.reply("Peux-tu me dire mon prénom").text.contains("Amine"))
            assertTrue(brain.reply("Où est-ce que je travaille").text.contains("lieu"))
            assertFalse(brain.reply("Quelle est mon adresse").text.contains("que je retienne"))
            brain.reply("oui")
            assertEquals(2, library.list().size)
        }
    }

    @Test fun rollingBodyUsesSharedKinematicsWithBothSteeringPositionsAndNoNoteWrites() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "rolling", LocalKnowledgeLibraryTest.JvmCipher("rolling"))
        authorizedBrain(context, library).use { brain ->
            val front = brain.reply("tourne à gauche de trente degrés", steeringAxle = "front")
            val rear = brain.reply("tourne à gauche de trente degrés", steeringAxle = "rear")
            assertTrue(front.text.contains("Simulation"))
            assertTrue(front.body!!.steeringRad > 0); assertTrue(rear.body!!.steeringRad < 0)
            assertEquals(front.body.samples, rear.body.samples)
            assertTrue(brain.reply("avance de 20 centimètres").body!!.samples.last().xM > 0)
            assertNull(brain.reply("avance de 200 centimètres").body)
            assertTrue(brain.reply("État de ta mémoire").text.contains("0 notes"))
            assertTrue(library.list().isEmpty())
        }
    }
    @Test fun spokenSearchLearningAndOfflineReuseKeepTheEvidenceAndNeverSaveBeforeApproval() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "learn", LocalKnowledgeLibraryTest.JvmCipher("learn"))
        val queries = mutableListOf<String>()
        val article = WikipediaKnowledgeSearch.Article(42, "Volcan", "Un volcan émet de la lave.")
        authorizedBrain(context, library) { topic -> queries.add(topic); article }.use { brain ->
            val offer = brain.reply("Qu’est-ce qu’un volcan ?")
            assertTrue(offer.text.contains("Tu veux"))
            assertTrue(offer.expectsAnswer)
            assertTrue(queries.isEmpty())
            assertTrue(library.list().isEmpty())
            val answer = brain.reply("oui")
            assertEquals(article, answer.source)
            assertTrue(answer.text.contains("Veux-tu que je retienne"))
            assertEquals(listOf("un volcan"), queries)
            assertTrue(library.list().isEmpty())
            assertTrue(brain.reply("oui je confirme").text.contains("enregistrée"))
            val stored = library.list().single()
            assertEquals(article.url, stored.source)
            assertFalse(stored.verified)
            val offline = brain.reply("Qu’est-ce qu’un volcan ?", onlineEnabled = false)
            assertTrue(offline.text.contains("émet de la lave"))
            assertEquals(article, offline.source)
            assertEquals(1, queries.size)
        }
    }

    @Test fun ordinaryQuestionsRetrieveOlderPersonalFactsUsingTheSharedVocabulary() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "profile", LocalKnowledgeLibraryTest.JvmCipher("profile"))
        library.addUserNote("Identité", "Le créateur est Amine.")
        library.addUserNote("Métier", "Je travaille comme technicien informatique.")
        repeat(15) { library.addUserNote("Repère-$it", "Le créateur préfère les objets numéro $it.") }
        authorizedBrain(context, library).use { brain ->
            assertTrue(brain.reply("Quel est mon prénom ?", false).text.contains("Ton prénom est Amine"))
            assertTrue(brain.reply("C’est quoi mon prénom", false).text.contains("Ton prénom est Amine"))
            assertTrue(brain.reply("Quel est mon métier ?", false).text.contains("Tu travailles comme technicien"))
            assertFalse(brain.reply("Quelle est ma couleur préférée ?", false).text.contains("objets numéro"))
        }
    }

    @Test fun voiceCanApproveAMemoryProposalAndExactDuplicatesAreNotAdded() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "approve", LocalKnowledgeLibraryTest.JvmCipher("approve"))
        authorizedBrain(context, library).use { brain ->
            assertTrue(brain.reply("retiens que je préfère le thé vert").text.contains("Tu confirmes"))
            assertTrue(library.list().isEmpty())
            assertTrue(brain.reply("oui").text.contains("enregistrée"))
            assertTrue(brain.reply("retiens que je préfère le thé vert").text.contains("déjà"))
            brain.reply("oui")
            assertEquals(1, library.list().size)
            assertTrue(brain.reply("Quelle boisson je préfère ?", false).text.contains("Tu préfères le thé vert"))
        }
    }

    @Test fun noExpiryTopicChangeAndBackgroundClearPreventStaleApprovals() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "cancel", LocalKnowledgeLibraryTest.JvmCipher("cancel"))
        var elapsed = 0L
        var calls = 0
        authorizedBrain(context, library, { elapsed }) { calls++; WikipediaKnowledgeSearch.Article(42, "Volcan", "La lave.") }.use { brain ->
            brain.reply("Qu’est-ce qu’un volcan ?"); brain.reply("non"); brain.reply("oui")
            brain.reply("Qu’est-ce qu’un volcan ?"); elapsed += NativeCapacity.PENDING_CONFIRMATION_MS; brain.reply("oui")
            brain.reply("Qu’est-ce qu’un volcan ?"); brain.reply("bonjour"); brain.reply("oui")
            brain.reply("Qu’est-ce qu’un volcan ?"); brain.clearConversation(); brain.reply("oui")
            assertEquals(0, calls)
            brain.reply("Cherche sur Wikipédia volcan"); assertEquals(1, calls)
            brain.reply("non merci"); brain.reply("oui")
            assertTrue(library.list().isEmpty())
        }
    }

    @Test fun ambiguousApprovalAndDisablingInternetNeverSendAQuery() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "privacy", LocalKnowledgeLibraryTest.JvmCipher("privacy"))
        var calls = 0
        authorizedBrain(context, library) { calls++; null }.use { brain ->
            brain.reply("Qu’est-ce qu’un volcan ?")
            brain.reply("je dis oui parfois mais pas cette fois")
            brain.reply("oui")
            brain.reply("Qu’est-ce qu’un volcan ?")
            assertEquals(context.getString(R.string.online_disabled_reply), brain.reply("oui", false).text)
            brain.reply("Quelle est mon adresse ?"); brain.reply("oui")
            assertEquals(0, calls)
        }
    }

    @Test fun sensitiveProposalsKeepTheExplicitReviewScreen() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "sensitive", LocalKnowledgeLibraryTest.JvmCipher("sensitive"))
        authorizedBrain(context, library).use { brain ->
            assertTrue(brain.reply("retiens que mon adresse exacte est 12 rue du Test").candidate?.sensitive == true)
            brain.reply("oui")
            assertTrue(library.list().isEmpty())
        }
    }

    @Test fun onlyAnExplicitPublicTopicReachesSearchAndAnOutageLeavesLocalAnswersAvailable() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "online", LocalKnowledgeLibraryTest.JvmCipher("online"))
        library.addUserNote("Préférence", "Je préfère le thé vert")
        val queries = mutableListOf<String>()
        val article = WikipediaKnowledgeSearch.Article(1359783, "Tour Eiffel", "Une tour à Paris.")
        authorizedBrain(context, library) { topic ->
            queries.add(topic)
            when (topic) {
                "un sujet sans article" -> null
                "volcan" -> throw IOException("Offline")
                else -> article
            }
        }.use { brain ->
            val command = "Nexus, cherche sur Wikipédia la tour Eiffel"
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
            assertEquals(context.getString(R.string.online_no_match), brain.reply("Cherche sur Wikipédia un sujet sans article").text)
            assertEquals(context.getString(R.string.online_unavailable), brain.reply("Cherche sur Wikipédia volcan").text)
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
        assertEquals("Un résultat secondaire",WikipediaKnowledgeSearch.readArticle(response)?.title)
    }

    @Test fun sharedBrainUsesEncryptedNotesButNeverPersistsAProposalByItself() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "bridge", LocalKnowledgeLibraryTest.JvmCipher("bridge"))
        library.addUserNote("Préférence", "Je préfère le thé vert")
        val file = File(context.filesDir, "knowledge-bridge.json")
        val before = file.readBytes()
        authorizedBrain(context, library).use { brain ->
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
        authorizedBrain(context, library).use { brain ->
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
        authorizedBrain(context, library).use { brain ->
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
