package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SharedBrainRuntimeTest {
    @Test fun theExtendedProfileCanUseTheHundredAndTwentyEighthSource() {
        val notes = (0 until NativeCapacity.MAX_CONTEXT_NOTES).joinToString(",", "[", "]") { i ->
            val excerpt = if (i == NativeCapacity.MAX_CONTEXT_NOTES - 1) "Je préfère le thé vert." else "Une observation documentaire générale."
            """{"id":"note-$i","title":"Dossier $i","excerpt":"$excerpt","source":"user:manual","recordedAt":$i,"expiresAt":null,"verified":false}"""
        }
        runtime().use { brain ->
            val reply = brain.replyJson(request("Quelle boisson je préfère ?", notes))
            assertTrue(reply, reply.contains("thé vert")); assertTrue(reply, reply.contains("note-127"))
        }
    }
    @Test fun findsTheThirtySecondNoteInTheExpandedRealInterpreterContext() {
        val notes = (0 until 32).joinToString(",", "[", "]") { i ->
            val excerpt = "Observation documentaire. ".repeat(400) + if (i == 31) "Je préfère le thé vert." else "Une information générale."
            """{"id":"note-$i","title":"Dossier $i","excerpt":"$excerpt","source":"user:manual","recordedAt":$i,"expiresAt":null,"verified":false}"""
        }
        val input = request("Quelle boisson je préfère ?", notes)
        assertTrue(input.length > 160_000)
        runtime().use { brain ->
            val reply = brain.replyJson(input)
            assertTrue(reply,reply.contains("thé vert"))
            assertTrue(reply,reply.contains("note-31"))
        }
    }
    @Test fun expandedContextExecutesInTheRealInterpreterAndFindsTheSixteenthNote() {
        val notes = (0 until 16).joinToString(",", "[", "]") { i ->
            val excerpt = "Observation documentaire. ".repeat(280) + if (i == 15) "Je préfère le thé vert." else "Une information générale."
            """{"id":"note-$i","title":"Dossier $i","excerpt":"$excerpt","source":"user:manual","recordedAt":$i,"expiresAt":null,"verified":false}"""
        }
        val input = request("Quelle boisson je préfère ?", notes)
        assertTrue(input.length > 40_000)
        runtime().use { brain ->
            val reply = brain.replyJson(input)
            assertTrue(reply, reply.contains("thé vert"))
            assertTrue(reply, reply.contains("note-15"))
        }
    }
    private fun runtime() = SharedBrainRuntime(File(requireNotNull(System.getProperty("nexus.brain.asset"))).readText())
    private fun request(message: String, notes: String = "[]"): String {
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"protocolVersion":1,"message":"$escaped","notes":$notes,"now":1000000,"privateAccessGranted":true}"""
    }

    @Test fun executesTheActualPackagedSharedBrain() {
        runtime().use { brain ->
            for (text in listOf("bonjour", "Nexus, bonjour !", "comment vas tu", "comment ça va ?")) {
                val output = brain.replyJson(request(text))
                assertTrue(output.contains("Bonjour") || output.contains("Ça va"))
                assertTrue(output.contains("nexus-core-rules-v1"))
                assertTrue(output.contains("\"candidate\":null"))
            }
        }
    }

    @Test fun returnsMemoryProposalWithNoStorageSideEffects() {
        runtime().use { brain ->
            val output = brain.replyJson(request("retiens que je préfère le thé"))
            assertTrue(output.contains("confirmer"))
            assertTrue(output.contains("thé"))
            assertFalse(output.contains("\"candidate\":null"))
            assertTrue(brain.replyJson(request("mes notes")).contains("ne retrouve pas"))
        }
    }

    @Test fun recallsOnlyUnexpiredNotesWithoutCredentialsWhenAuthorized() {
        val notes = """[
          {"id":"safe","title":"Thé","excerpt":"Je préfère le thé vert","source":"user:manual","recordedAt":10,"expiresAt":null,"verified":false},
          {"id":"private","title":"Code","excerpt":"mon mot de passe est privé","source":"user:manual","recordedAt":10,"expiresAt":null,"verified":false},
          {"id":"old","title":"Ancienne note","excerpt":"ancienne préférence café","source":"user:manual","recordedAt":10,"expiresAt":999999,"verified":false}
        ]"""
        runtime().use { brain ->
            val output = brain.replyJson(request("de quoi te souviens-tu ?", notes))
            assertTrue(output.contains("thé vert"))
            assertFalse(output.contains("mot de passe est privé"))
            assertFalse(output.contains("préférence café"))
        }
    }

    @Test fun enforcesTheSharedSafetyDecisionInTheActualInterpreter() {
        runtime().use { brain ->
            assertTrue(brain.replyJson(request("observe-moi sans me le dire")).contains("Je ne peux pas"))
            assertTrue(brain.replyJson(request("formate tout le disque")).contains("Je ne peux pas"))
        }
    }

    @Test fun userInputIsNotEvaluatedAsJavaScript() {
        runtime().use { brain ->
            assertTrue(brain.replyJson(request("\"; while(true){}; //")).contains("protocolVersion"))
            assertTrue(brain.replyJson(request("bonjour")).contains("Bonjour"))
        }
    }

    @Test fun sandboxDoesNotExposeJavaNetworkingFilesOrHostReflection() {
        SharedBrainRuntime("""var NexusNativeBrain={handleNativeTurnJson:function(input){return JSON.stringify([
          typeof java,typeof Packages,typeof JavaAdapter,typeof getClass,typeof fetch,
          typeof XMLHttpRequest,typeof load,typeof readFile,typeof input.getClass
        ]);}};""").use { brain ->
            assertEquals("[" + List(9) { "\"undefined\"" }.joinToString(",") + "]", brain.replyJson("data"))
        }
    }

    @Test fun infiniteLoopIsInterruptedByTheInstructionBudget() {
        SharedBrainRuntime("var NexusNativeBrain={handleNativeTurnJson:function(){while(true){};}};", instructionLimit = 20_000).use { brain ->
            assertThrows(IllegalStateException::class.java) { brain.replyJson("data") }
        }
    }

    @Test fun failedEvaluationCannotLeaveDirtyStateForTheNextTurn() {
        SharedBrainRuntime("""var count=0;var NexusNativeBrain={handleNativeTurnJson:function(input){
          count++;if(input==='bad')throw Error('failure');return String(count);}};""").use { brain ->
            assertEquals("1", brain.replyJson("good"))
            assertThrows(RuntimeException::class.java) { brain.replyJson("bad") }
            assertEquals("1", brain.replyJson("good"))
        }
    }

    @Test fun rejectsOversizedInputBeforeEvaluationAndClosedRuntimes() {
        val brain = runtime()
        assertThrows(IllegalArgumentException::class.java) { brain.replyJson("x".repeat(NativeCapacity.MAX_REQUEST_CHARS + 1)) }
        brain.close()
        assertThrows(IllegalStateException::class.java) { brain.replyJson(request("bonjour")) }
    }
}
