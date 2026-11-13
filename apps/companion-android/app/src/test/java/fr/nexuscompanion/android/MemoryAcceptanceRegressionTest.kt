package fr.nexuscompanion.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The 35 preceding audit scenarios are now regression checks in an authorized session.
 * Locked access and first-name/city minimum are tested independently in PrivateLearningTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MemoryAcceptanceRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val rows = JSONArray()
    private var serial = 0
    private val salutation = "Une salutation est une manière de manifester de la politesse envers une personne rencontrée. Bonjour, bonsoir et salut sont des formules de salutation."
    private val politesse = "La politesse est un ensemble de règles de respect dans les relations sociales. Dire bonjour et remercier sont des marques de politesse."

    private fun library(seed: Boolean = true): LocalKnowledgeLibrary {
        val id = "memory-audit-${serial++}"
        return LocalKnowledgeLibrary(context, id, LocalKnowledgeLibraryTest.JvmCipher(id)).also { lib ->
            if (seed) {
                lib.addApprovedSource("Salutation", salutation, "external:fixture-salutation")
                lib.addApprovedSource("Politesse", politesse, "external:fixture-politesse")
                lib.addUserNote("Le créateur est amine.", "Le créateur est amine.")
                lib.addUserNote("Je vis a lyon.", "Je vis à Lyon.")
                lib.addUserNote("Je m’appelle Amine", "Je m’appelle Amine.")
                lib.addUserNote("Le créateur préfère les réponses courtes.", "Le créateur préfère les réponses courtes.")
            }
        }
    }

    private fun record(id: String, query: String, expected: String, reply: NativeBrainService.Reply,
        pass: Boolean, extra: JSONObject = JSONObject()) {
        rows.put(JSONObject().put("id", id).put("query", query).put("expected", expected)
            .put("passed", pass).put("actual", reply.text).put("notesAvailable", reply.notesAvailable)
            .put("candidate", reply.candidate?.content ?: JSONObject.NULL)
            .put("external", reply.external?.app?.name ?: JSONObject.NULL)
            .put("externalQuery", reply.external?.target ?: JSONObject.NULL)
            .put("hasBodyPlan", reply.body != null).put("details", extra))
    }

    private fun query(id: String, text: String, expected: String,
        seeded: Boolean = true, prepare: (LocalKnowledgeLibrary) -> Unit = {},
        accept: (NativeBrainService.Reply) -> Boolean) {
        val lib = library(seeded)
        prepare(lib)
        val before = lib.list().size
        var networkCalls = 0
        authorizedBrain(context, lib, search = { networkCalls++; null }).use { brain ->
            val reply = brain.reply(text, false)
            record(id, text, expected, reply, accept(reply) && lib.list().size == before && networkCalls == 0,
                JSONObject().put("notesBefore", before).put("notesAfter", lib.list().size).put("networkCalls", networkCalls))
        }
    }

    private fun grounded(reply: NativeBrainService.Reply, vararg words: String): Boolean =
        words.all { reply.text.contains(it, ignoreCase = true) } && reply.candidate == null && reply.external == null &&
            !reply.text.contains("Je ne sais pas encore") && !reply.text.contains("Je ne retrouve pas")

    @Test fun auditUnderstandingAndMemory() {
        query("name_direct", "Quel est mon prénom ?", "Retrouver Amine") { grounded(it, "Amine") }
        query("name_natural", "Comment je m’appelle ?", "Retrouver Amine") { grounded(it, "Amine") }
        query("city_direct", "Où est-ce que je vis ?", "Retrouver Lyon") { grounded(it, "Lyon") }
        query("city_response_person", "Où est-ce que je vis ?", "Répondre au propriétaire à la deuxième personne") {
            grounded(it, "Lyon") && it.text.contains(Regex("[Tt]u (?:vis|habites)")) }
        query("city_paraphrase", "Où est-ce que j’habite ?", "Retrouver Lyon") { grounded(it, "Lyon") }
        query("city_elliptic", "Et ma ville ?", "Retrouver Lyon") { grounded(it, "Lyon") }
        query("creator", "Qui est le créateur ?", "Retrouver Amine") { grounded(it, "Amine") }
        query("reply_preference", "Quel type de réponses je préfère ?", "Retrouver la préférence de réponses courtes") { grounded(it, "courtes") }
        query("description_imperative", "Décris la salutation", "Utiliser la note Salutation") { grounded(it, "salutation", "politesse") || grounded(it, "formules de salutation") }
        query("description_infinitive", "décrire la salutation", "Utiliser la note Salutation, cas de la capture") { grounded(it, "salutation", "politesse") || grounded(it, "formules de salutation") }
        query("definition_verb", "Définis la salutation", "Utiliser la note Salutation") { grounded(it, "salutation", "politesse") || grounded(it, "formules de salutation") }
        query("explain", "Explique la salutation", "Utiliser la note Salutation") { grounded(it, "salutation", "politesse") || grounded(it, "formules de salutation") }
        query("polite_explain", "Peux-tu m’expliquer la salutation", "Utiliser la note Salutation") { grounded(it, "salutation", "politesse") || grounded(it, "formules de salutation") }
        query("politeness", "C’est quoi la politesse ?", "Utiliser la note Politesse") { grounded(it, "règles de respect") }
        query("full_read", "Lis la note Salutation", "Lire exactement le contenu de Salutation") { it.text.contains(salutation) }
        query("recall_topic", "Qu’ai-je noté sur la salutation ?", "Rappeler les notes pertinentes") { grounded(it, "salutation") }
        query("read_politeness", "Lis la note Politesse", "Lire exactement la note Politesse") { it.text.contains(politesse) }
        query("bare_note_title", "La note Salutation", "Comprendre une demande de note ou demander quelle action effectuer") {
            grounded(it, "salutation", "politesse") || it.text.contains(Regex("(?:[Pp]récise|[Vv]eux-tu|[Qq]uelle).*(?:note|lire)")) }
        query("misheard_note_title", "la note solution", "Demander une précision sur la note, sans deviner Salutation") {
            it.external == null && it.text.contains(Regex("(?:[Pp]récise|[Vv]eux-tu|[Qq]uelle).*(?:note|lire)")) }
        query("missing_note", "Lis la note Astronomie", "Indiquer qu’aucune note pertinente n’est disponible") {
            it.text.contains("Je ne retrouve pas") && !it.text.contains("Amine") && !it.text.contains("Lyon") }
        query("practical_with_notes", "Que répondre à bonjour ?", "Donner une formule de réponse pratique, même avec des notes") {
            it.text.contains("Tu peux répondre") && it.text.contains("Bonjour") }
        query("practical_without_notes", "Que répondre à bonjour ?", "Donner une formule pratique sans note", seeded = false) {
            it.text.contains("Tu peux répondre") && it.text.contains("Bonjour") }
        query("no_invented_preference", "Quelle est ma couleur préférée ?", "Admettre l’absence de cette information") {
            it.text.contains("Je ne retrouve pas") && !it.text.contains("Tu préfères le") }
        query("no_relative_confusion", "Quel est le prénom de ma fille ?", "Ne pas utiliser Amine comme prénom de la fille") {
            !it.text.contains("Amine") && it.external == null }
        query("no_invented_reason", "Pourquoi est-ce que je vis à Lyon ?", "Admettre que la raison n’est pas précisée") {
            it.text.contains("raison") && !it.text.contains("parce que") }
        query("compound_facts", "Quel est mon prénom et mon métier ?", "Combiner Amine et technicien informatique",
            prepare = { it.addUserNote("Métier", "Je travaille comme technicien informatique.") }) {
            grounded(it, "Amine", "technicien informatique") }
        query("conflicting_names", "Quel est mon prénom ?", "Demander clarification entre deux prénoms contradictoires",
            prepare = { it.addUserNote("Prénom", "Je m’appelle Karim.") }) {
            it.text.contains("Amine") && it.text.contains("Karim") && it.text.contains("Laquelle") }
        query("internal_memory_definition", "Qu’est-ce que ta mémoire interne ?", "Expliquer localement la mémoire de Nexus") {
            it.text.contains("mémoire") && (it.text.contains("notes") || it.text.contains("local")) &&
                !it.text.contains("Je ne sais pas") && !it.text.contains("cherche") }
        query("memory_statistics", "État de ta mémoire", "Compter les six notes et décrire l’occupation locale") {
            it.text.contains("6 notes") && it.text.contains("compression") }

        val learned = library(false)
        authorizedBrain(context, learned, search = { null }).use { brain ->
            val proposed = brain.reply("retiens que je préfère le thé vert", false)
            record("save_requires_approval", "Retiens que je préfère le thé vert", "Proposer, sans enregistrer immédiatement", proposed,
                proposed.text.contains("Tu confirmes") && learned.list().isEmpty())
            val saved = brain.reply("oui", false)
            record("save_after_yes", "oui", "Enregistrer exactement une note après accord", saved, learned.list().size == 1)
        }
        authorizedBrain(context, learned, search = { null }).use { brain ->
            val reply = brain.reply("Quelle boisson je préfère ?", false)
            record("recall_after_restart", "Quelle boisson je préfère ?", "Retrouver le thé vert après recréation du moteur", reply, grounded(reply, "thé vert"))
        }

        val expired = library(false)
        var clock = 0L
        authorizedBrain(context, expired, elapsed = { clock }, search = { null }).use { brain ->
            brain.reply("retiens que je préfère le thé vert", false)
            clock += NativeCapacity.PENDING_CONFIRMATION_MS + 1L
            val reply = brain.reply("oui", false)
            record("expired_consent", "oui après expiration", "Ne pas enregistrer un ancien accord", reply, expired.list().isEmpty())
        }

        val unrelated = library()
        var calls = 0
        authorizedBrain(context, unrelated, search = { calls++; null }).use { brain ->
            val reply = brain.reply("cherche sur Internet les réponses de salutations", true)
            record("web_scope", "cherche sur Internet les réponses de salutations", "Préparer Chrome sans appeler Wikipédia", reply,
                reply.external?.app == ExternalAppPolicy.App.CHROME && calls == 0 && reply.source == null)
            brain.clearConversation()
            val uncertain = brain.reply("cher sur interdit que répondre bonjour", true)
            record("corrupted_search", "cher sur interdit que répondre bonjour", "Demander confirmation sans ouvrir de recherche", uncertain,
                uncertain.text.contains("mal compris") && uncertain.external == null && calls == 0)
        }

        val passed = (0 until rows.length()).count { rows.getJSONObject(it).getBoolean("passed") }
        val report = JSONObject().put("version", BuildConfig.VERSION_NAME).put("engine", "nexus-core-rules-v1")
            .put("fixture", "Six notes reconstituées ; contenus Salutation et Politesse synthétiques, pas une copie des données du téléphone")
            .put("total", rows.length()).put("passed", passed).put("failed", rows.length() - passed).put("rows", rows)
        val output = File(requireNotNull(System.getProperty("nexus.acceptance.reportDir")), "understanding-${BuildConfig.BUILD_TYPE}.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(report.toString(2))
        assertEquals(35, rows.length())
        assertEquals("All 35 acceptance scenarios must pass: $report", rows.length(), passed)
    }
}
