package fr.nexuscompanion.android

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/** Called only by the dedicated worker. Android owns persistence; the shared brain sees data only. */
internal class NativeBrainService(
    private val context: Context,
    private val library: LocalKnowledgeLibrary,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val privateAccess: () -> Boolean = { false },
    private val resources: () -> DeviceResources.Snapshot = { DeviceResources.inspect(context) },
    private val learning: LocalLearningIndex = LocalLearningIndex(context),
    private val search: (String) -> WikipediaKnowledgeSearch.Article? = { WikipediaKnowledgeSearch.search(context, it) },
    private val generator: LocalDraftGenerator = LocalTextGenerator(context)
) : AutoCloseable {
    data class Candidate(val content: String, val sensitive: Boolean)
    data class EvidenceSource(val id: String, val title: String, val source: String)
    data class Reply(
        val text: String,
        val candidate: Candidate?,
        val notesAvailable: Boolean,
        val source: WikipediaKnowledgeSearch.Article? = null,
        val expectsAnswer: Boolean = false,
        val body: RollingBodyState.Plan? = null,
        val editNote: LocalKnowledgeLibrary.Entry? = null,
        val external: ExternalAppPolicy.Request? = null,
        val publicContextTopic: String? = null,
        val browserReading: BrowserReading? = null,
        val requiresUserCode: Boolean = false,
        val protectedInformation: Boolean = false,
        val evidenceSources: List<EvidenceSource> = emptyList(),
        val generatedLocally: Boolean = false
    )
    private val dialogue = CompanionDialogue()
    private var runtime: SharedBrainRuntime? = null
    private sealed interface Pending {
        val expires: Long
        data class Lookup(val query: String, override val expires: Long, val target: String = "wikipedia") : Pending
        data class Topic(val target: String, override val expires: Long) : Pending
        data class Save(val title: String, val excerpt: String, val source: String, override val expires: Long) : Pending
        data class OpenApp(val request: ExternalAppPolicy.Request, val evidence: List<LocalKnowledgeLibrary.Entry>, override val expires: Long) : Pending
    }
    private var pending: Pending? = null
    private data class Conversation(val reply: Reply, val notes: List<LocalKnowledgeLibrary.Entry>, val expires: Long, val question: String? = null)
    private var conversation: Conversation? = null
    private var turnEvidence: List<LocalKnowledgeLibrary.Entry> = emptyList()
    private var turnQuestion: String? = null
    fun cancelGeneration() { generator.cancel() }
    private var accessOnPreviousTurn = false
    private var budget: DeviceResources.Budget? = null
    private fun codeRequired() = Reply("Le code utilisateur est nécessaire pour consulter ces informations personnelles. Saisis-le dans l’écran sécurisé.", null, true, requiresUserCode = true)

    fun learningStatistics() = learning.statistics(library.list())
    fun consolidate(foreground: Boolean = false): LocalLearningIndex.Statistics {
        val raw = DeviceResources.budget(resources())
        val selected = if (foreground) raw.copy(learningBatch = minOf(3, raw.learningBatch)) else raw
        brain().configure(selected)
        return learning.consolidate(library.list(), selected, { note ->
            JSONObject(brain().learningJson(JSONObject().put("protocolVersion", 1).put("message", "consolidation")
                .put("notes", JSONArray().put(noteJson(note))).put("now", System.currentTimeMillis()).toString()))
        })
    }
    private fun noteJson(note: LocalKnowledgeLibrary.Entry) = JSONObject().apply {
        put("id", note.id); put("title", note.title); put("excerpt", note.excerpt)
        put("source", note.source); put("recordedAt", note.recordedAt)
        put("expiresAt", note.expiresAt ?: JSONObject.NULL); put("verified", false)
        put("manuallyEdited", note.manuallyEdited)
    }

    /** Session context is transient and confined to the worker, never written as history. */
    fun clearConversation() { pending = null; conversation = null; turnEvidence = emptyList(); turnQuestion = null }

    /** A long reading starts its follow-up lifetime after confirmed playback, not before it. */
    fun replyPlayed() {
        // Approval deadlines are never revived or extended by speech completion.
        if (pending == null) conversation = conversation?.copy(expires = elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
    }

    private fun brain(): SharedBrainRuntime {
        runtime?.let { return it }
        val source = context.assets.open("brain/nexus-brain.js").use { it.readBytes() }
        val manifest = JSONObject(context.assets.open("brain/manifest.json").bufferedReader().use { it.readText() })
        val digest = MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) }
        check(manifest.getInt("protocolVersion") == 1 && digest == manifest.getString("sha256")) {
            "Packaged brain integrity check failed"
        }
        return SharedBrainRuntime(source.toString(Charsets.UTF_8)).also { runtime = it }
    }

    fun reply(message: String, onlineEnabled: Boolean = true, steeringAxle: String = "front"): Reply {
        require(message.isNotBlank() && message.length <= NativeCapacity.MAX_MESSAGE_CHARS)
        val access = privateAccess()
        if (accessOnPreviousTurn && !access) clearConversation()
        accessOnPreviousTurn = access
        budget = DeviceResources.budget(resources())
        brain().configure(requireNotNull(budget))
        val instant = elapsed()
        if (conversation?.expires?.let { instant >= it } == true) conversation = null
        if (pending?.expires?.let { instant >= it } == true) pending = null
        turnEvidence = emptyList()
        turnQuestion = message.takeIf { it.length <= 600 }
        val words = VoiceTranscriptPolicy.commandWords(message)
        val last = conversation
        val repeat = words in setOf("repete", "repete ta reponse", "peux tu repeter", "redis moi ta reponse", "repete s il te plait")
        val read = words in setOf("lis la note complete", "lis cette note", "lis moi cette note", "lis toute la note")
        val edit = words in setOf("modifie cette note", "corrige cette note", "modifier cette note")
        if (repeat || read || edit) {
            if (!access && (edit || last?.reply?.protectedInformation == true)) { clearConversation(); return codeRequired() }
            val available = try {
                if (last == null || last.notes.isEmpty()) true else {
                    val current = library.list().associateBy { it.id }
                    last.notes.all { note -> current[note.id] == note && !KnowledgePolicy.isExpired(note.expiresAt, System.currentTimeMillis()) }
                }
            } catch (_: Exception) {
                clearConversation()
                return Reply(context.getString(R.string.library_read_failed), null, false)
            }
            if (!available) {
                clearConversation()
                return Reply("La note a été modifiée, supprimée ou a expiré. Pose de nouveau ta question pour utiliser son état actuel.", null, true, expectsAnswer = true)
            }
            if (repeat) return last?.reply?.copy(body = null, editNote = null, external = null)
                ?: Reply("Je n’ai plus de réponse récente à répéter. Pose-moi de nouveau ta question.", null, true, expectsAnswer = true)
            pending = null
            val note = last?.notes?.singleOrNull()?.let { library.get(it.id) }
                ?: return Reply("Précise le sujet de la note à utiliser. Tu peux aussi la choisir dans « Mes notes ».", null, true, expectsAnswer = true)
            turnEvidence = listOf(note)
            val result = (if (edit) Reply("Tu peux relire et modifier cette note dans l’éditeur.", null, true, editNote = note)
                else answer(message, onlineEnabled, steeringAxle, note)
                ).let { if (access) it.copy(protectedInformation = true) else it }
            rememberReply(result)
            return result
        }
        turnEvidence = emptyList()
        val result = answer(message, onlineEnabled, steeringAxle).let { if (access) it.copy(protectedInformation = true) else it }
        rememberReply(result)
        return result
    }

    private fun rememberReply(reply: Reply) {
        val expires = minOf(elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS, pending?.expires ?: Long.MAX_VALUE)
        conversation = Conversation(reply.copy(body = null, editNote = null, external = null), turnEvidence.toList(), expires, turnQuestion)
    }

    /** External prose is read as data; it never enters the command, body, or lookup parsers. */
    fun readBrowserResult(reading: BrowserReading): Reply {
        clearConversation()
        accessOnPreviousTurn = privateAccess()
        val terms = JSONObject(brain().retrievalJson(reading.text)).getJSONArray("terms")
        turnQuestion = "Explique " + (0 until minOf(8, terms.length())).joinToString(" ") { terms.getString(it) }
        val metadata = JSONObject(brain().learningJson(JSONObject().put("protocolVersion", 1).put("message", "classification")
            .put("now", System.currentTimeMillis()).put("notes", JSONArray().put(JSONObject()
                .put("id", "browser-classification").put("title", reading.title).put("excerpt", reading.text)
                .put("source", reading.source).put("recordedAt", System.currentTimeMillis())
                .put("expiresAt", JSONObject.NULL).put("verified", false))).toString()))
        if (metadata.getBoolean("credential")) return Reply("Ce texte contient des identifiants ou des codes. Je ne le lis pas à voix haute.", null, true)
        if (metadata.getBoolean("private") && !privateAccess()) return codeRequired()
        val notesAvailable = try { library.list(); true } catch (_: Exception) { false }
        if (notesAvailable) pending = Pending.Save(reading.title, reading.text, reading.source, elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
        val prefix = if (reading.source == "external:clipboard") "Je lis le texte que tu as copié, non vérifié."
            else "Je lis la partie accessible de la réponse IA dans Chrome, non vérifiée."
        val suffix = if (notesAvailable) "Veux-tu que je retienne ce texte dans mes notes locales ?" else context.getString(R.string.library_read_failed)
        val reply = Reply("$prefix\n${reading.text}\n$suffix",
            null, notesAvailable, expectsAnswer = true, browserReading = reading, protectedInformation = metadata.getBoolean("private"))
        rememberReply(reply)
        return reply
    }

    private fun answer(message: String, onlineEnabled: Boolean, steeringAxle: String, focusedNote: LocalKnowledgeLibrary.Entry? = null): Reply {
        val previous = pending?.takeIf { elapsed() < it.expires }
        pending = null
        val approval = VoiceTranscriptPolicy.confirmation(message)
        if (approval != null) {
            if (previous == null) return Reply("Je n’ai aucune recherche ou information en attente de confirmation. Que veux-tu savoir ?", null, true, expectsAnswer = true)
            if (!approval) return Reply("D’accord, j’annule cette proposition.", null, true, expectsAnswer = true)
            when (previous) {
                is Pending.Lookup -> return lookup(previous.query, onlineEnabled, true, previous.target)
                is Pending.Topic -> {
                    if (!onlineEnabled) return Reply(context.getString(R.string.online_disabled_reply), null, true)
                    pending = previous
                    return Reply("Précise le sujet public à rechercher ${if (previous.target == "web") "sur le Web" else "dans Wikipédia"}.", null, true, expectsAnswer = true)
                }
                is Pending.Save -> {
                    val saved = library.addApprovedSource(previous.title, previous.excerpt, previous.source)
                    turnEvidence = listOf(saved.entry)
                    return Reply(context.getString(if (saved.created) R.string.note_saved else R.string.note_already_saved), null, true, expectsAnswer = true)
                }
                is Pending.OpenApp -> {
                    if (!privateAccess()) return codeRequired()
                    if (!onlineEnabled) return Reply(context.getString(R.string.external_online_disabled), null, true)
                    val current = try { library.list().associateBy { it.id } } catch (_: Exception) {
                        return Reply(context.getString(R.string.library_read_failed), null, false)
                    }
                    if (previous.evidence.any { current[it.id] != it || KnowledgePolicy.isExpired(it.expiresAt, System.currentTimeMillis()) })
                        return Reply("L’adresse a été modifiée, supprimée ou a expiré. Redemande le lieu avant de l’ouvrir.", null, true, expectsAnswer = true)
                    turnEvidence = previous.evidence
                    return Reply(previous.request.openingText(), null, true, external = previous.request)
                }
            }
        }
        if (VoiceTranscriptPolicy.commandWords(message) in setOf("etat de ta memoire", "etat de la memoire", "bilan memoire", "combien de notes as tu")) {
            // A locked overview must not reveal counts, byte sizes or version history of private notes.
            if (!privateAccess()) {
                val now = System.currentTimeMillis()
                val shared = library.list().count { it.source == SelectiveMemoryAccess.SHARED_SOURCE &&
                    !KnowledgePolicy.isExpired(it.expiresAt, now) }
                return Reply("Ma mémoire accessible sans code contient $shared ${if (shared == 1) "note partagée" else "notes partagées"}. Pour voir le bilan complet, déverrouille l’accès personnel avec ton code.",
                    null, true, expectsAnswer = true)
            }
            val stats = library.statistics()
            val history = if (stats.archivedNotes == 0) "" else " Je conserve aussi ${stats.archivedNotes} ${if (stats.archivedNotes == 1) "ancienne version" else "anciennes versions"}."
            return Reply("Ma mémoire contient ${stats.notes} notes. Elle occupe ${(stats.storedBytes + 1023) / 1024} kilo-octets. La compression économise ${stats.compressionPercent} pour cent avant chiffrement.$history", null, true, expectsAnswer = true)
        }
        // Static dialogue examples are not memories. Match only unambiguous intents,
        // after explicit approvals and before the optional language model.
        dialogue.answer(message)?.let { return Reply(it, null, true, expectsAnswer = true) }
        // Pure public arithmetic is answered offline, even when the optional GGUF model
        // cannot be loaded. This path never reads or exposes personal notes.
        OfflineArithmetic.answer(message)?.let { return Reply(it, null, true, expectsAnswer = true) }
        val body = JSONObject(brain().bodyJson(JSONObject().put("message", message).put("steeringAxle", steeringAxle).toString()))
        if (body.getBoolean("handled")) return Reply(body.getString("text"), null, true, expectsAnswer = true,
            body = body.optJSONObject("plan")?.let(RollingBodyState.Plan::read))
        ExternalAppPolicy.parse(message)?.let { command ->
            if (!onlineEnabled) return Reply(context.getString(R.string.external_online_disabled), null, true)
            when (command) {
                is ExternalAppPolicy.Command.Open -> return Reply(command.request.openingText(), null, true, external = command.request)
                is ExternalAppPolicy.Command.Clarify -> return Reply(command.text, null, true, expectsAnswer = true)
                is ExternalAppPolicy.Command.SavedPlace -> {
                    if (!privateAccess()) return codeRequired()
                    val entries = try { library.list() } catch (_: Exception) {
                        return Reply(context.getString(R.string.library_read_failed), null, false)
                    }
                    val places = SavedPlaceResolver.resolve(command.place, entries, System.currentTimeMillis())
                    if (places.size != 1) return Reply(if (places.isEmpty())
                        "Je ne retrouve pas d’adresse explicite pour ce lieu dans tes notes personnelles. Donne-moi la destination, ou ajoute une note « Adresse domicile » ou « Adresse travail »."
                        else "Plusieurs adresses différentes sont enregistrées pour ce lieu. Précise celle à utiliser ou corrige tes notes.", null, true, expectsAnswer = true)
                    val place = places.single()
                    val request = try { ExternalAppPolicy.Request(ExternalAppPolicy.App.MAPS, command.operation, place.target) }
                        catch (_: IllegalArgumentException) { return Reply("Cette adresse ne peut pas être ouverte. Vérifie la note ou précise le lieu.", null, true, expectsAnswer = true) }
                    pending = Pending.OpenApp(request, place.evidence, elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
                    turnEvidence = place.evidence
                    return Reply("J’ai retrouvé « ${place.target} » dans tes notes. Tu confirmes l’envoi de cette adresse à Google Maps pour ${if (command.operation == ExternalAppPolicy.Operation.DIRECTIONS) "préparer l’itinéraire" else "afficher le lieu"} ?", null, true, expectsAnswer = true)
                }
            }
        }
        val now = System.currentTimeMillis()
        val last = conversation.takeIf { focusedNote == null }
        val externalNote = if (!last?.notes.isNullOrEmpty()) null else last?.reply?.browserReading?.let {
            LocalKnowledgeLibrary.Entry("external-context", it.title, it.text, it.source, now, null, false)
        } ?: last?.reply?.source?.let { LocalKnowledgeLibrary.Entry("external-context", it.title, it.extract, it.url, now, null, false) }
        val previousNotes = last?.notes?.takeIf { it.isNotEmpty() } ?: externalNote?.let(::listOf).orEmpty()
        val question = if (externalNote != null && last?.reply?.source != null) "Explique ${last.reply.source.title}" else last?.question
        val evidenceContext = question?.takeIf { previousNotes.isNotEmpty() }?.let {
            JSONObject().put("question", it).put("evidenceIds", JSONArray(previousNotes.map { note -> note.id }))
        }
        val understandingRequest = JSONObject().put("message", message).apply { if (evidenceContext != null) put("evidenceContext", evidenceContext) }
        val understanding = JSONObject(brain().understandingJson(understandingRequest.toString()))
        // "Et le projet Vega ?" names a different saved note even if the
        // language classifier initially considers the turn a continuation.
        val proposedContextual = understanding.getBoolean("contextual") && !understanding.getBoolean("needsContext")
        val explicitlyDifferentSource = proposedContextual && previousNotes.isNotEmpty() && try {
            MemoryRecallScope.refersToDifferentTitle(message, previousNotes,
                SelectiveMemoryAccess.accessible(library.list(), privateAccess()))
        } catch (_: Exception) { false }
        val contextual = proposedContextual && !explicitlyDifferentSource
        if (contextual && last?.reply?.protectedInformation == true && !privateAccess()) { clearConversation(); return codeRequired() }
        if (contextual && !last?.notes.isNullOrEmpty()) {
            val current = try { library.list().associateBy { it.id } } catch (_: Exception) {
                clearConversation(); return Reply(context.getString(R.string.library_read_failed), null, false)
            }
            if (last!!.notes.any { current[it.id] != it || KnowledgePolicy.isExpired(it.expiresAt, now) }) {
                clearConversation(); return Reply("La source a été modifiée, supprimée ou a expiré. Pose de nouveau ta question pour utiliser son état actuel.", null, true, expectsAnswer = true)
            }
        }
        val resolvedQuery = understanding.getString("query")
        turnQuestion = resolvedQuery.takeIf { it.length <= 600 }
        val recall = if (contextual) null else KnowledgePolicy.recallFor(message)
        val terms = JSONObject(brain().retrievalJson(resolvedQuery)).getJSONArray("terms")
        val queryTerms = (0 until terms.length()).map { terms.getString(it) }
        var preferredIds: List<String> = emptyList()
        val notes = try {
            val selected = requireNotNull(budget)
            preferredIds = try { learning.preferredIds(queryTerms, library.list(), now) } catch (_: Exception) { emptyList() }
            focusedNote?.let(::listOf) ?: if (contextual) previousNotes else library.contextFor(resolvedQuery, now, recall, queryTerms, selected.contextNotes, preferredIds)
        } catch (_: Exception) { null }
        // Filter BEFORE serializing notes for the shared brain or local generator.
        // Legacy notes are private until their owner explicitly shares them.
        val unlocked = privateAccess()
        val accessibleNotes = notes?.let { SelectiveMemoryAccess.accessible(it, unlocked) }
        // Continuations with multiple source notes have no unique referent.
        // Ask which one, rather than mixing two people or projects into a fact.
        if (contextual && accessibleNotes.orEmpty().size > 1 && focusedNote == null) {
            clearConversation()
            return Reply("J’ai plusieurs souvenirs sur ce sujet. Laquelle de ces notes veux-tu consulter ? Précise le nom ou le titre.", null, true, expectsAnswer = true)
        }
        val visibleNotes = if (!contextual && recall == null && focusedNote == null && accessibleNotes != null) {
            when (val decision = MemoryRecallScope.forFact(queryTerms, accessibleNotes, preferredIds)) {
                is MemoryRecallScope.Decision.Single -> listOf(decision.note)
                MemoryRecallScope.Decision.NoMatch -> emptyList()
                MemoryRecallScope.Decision.Ambiguous -> {
                    clearConversation()
                    return Reply("J’ai plusieurs souvenirs qui correspondent à ta question. Laquelle veux-tu consulter ? Précise la personne ou le titre pour éviter de mélanger les informations.", null, true, expectsAnswer = true)
                }
            }
        } else accessibleNotes
        if (!unlocked && recall != null && !notes.isNullOrEmpty() && visibleNotes.isNullOrEmpty()) {
            return codeRequired()
        }
        // For an explicit overview or topic request, present the distinct note
        // titles instead of letting the engine splice facts from several people
        // or projects into an unattributed answer.
        if (recall != null && visibleNotes.orEmpty().size > 1) {
            val choices = visibleNotes.orEmpty().take(6)
            val listing = choices.joinToString("\n") { note ->
                val title = note.title.replace(Regex("[\\r\\n\\t]+"), " ").take(80)
                // Display a complete short excerpt under its own title. Large
                // or control-character-laden notes require an explicit read.
                val excerpt = note.excerpt.takeIf { it.length <= 180 && GroundedNotes.safeText(it) }
                    ?.replace(Regex("[\\r\\n\\t]+"), " ")
                if (excerpt == null) "• « $title » (contenu disponible sur demande)"
                else "• « $title » : $excerpt"
            }
            val suffix = if (visibleNotes.orEmpty().size > choices.size)
                "\n… et d’autres notes. Précise le titre recherché pour lire les notes complètes." else
                "\nPrécise le titre recherché pour lire les notes complètes."
            return Reply("J’ai plusieurs notes distinctes. Je préfère ne pas mélanger leurs informations :\n" +
                listing + suffix, null, true, expectsAnswer = true,
                protectedInformation = choices.any { it.source != SelectiveMemoryAccess.SHARED_SOURCE },
                evidenceSources = choices.map { EvidenceSource(it.id, it.title, it.source) })
        }
        val sharedContext = !unlocked && !visibleNotes.isNullOrEmpty() &&
            visibleNotes.all { it.source == SelectiveMemoryAccess.SHARED_SOURCE }
        val array = JSONArray()
        val request = JSONObject().put("protocolVersion", 1).put("message", message)
            .put("notes", array).put("now", now).put("recallRequested", recall != null || focusedNote != null)
            .put("readRequested", recall is KnowledgePolicy.Recall.Read || focusedNote != null)
            .put("privateAccessGranted", unlocked || sharedContext)
        // Do not carry the previous source into an unrelated new question.
        if (contextual && evidenceContext != null) request.put("evidenceContext", evidenceContext)
        if (focusedNote == null) {
            if (contextual) conversation?.reply?.publicContextTopic?.let { request.put("publicContextTopic", it) }
            if (previous is Pending.Topic) request.put("awaitingLookupTarget", previous.target)
        }
        var requestChars = request.toString().length
        val submittedNotes = mutableListOf<LocalKnowledgeLibrary.Entry>()
        // Budget each serialized record once; never repeatedly serialize an oversized context.
        for (note in visibleNotes.orEmpty()) {
            val item = noteJson(note)
            val added = item.toString().length + if (array.length() > 0) 1 else 0
            if (requestChars + added > minOf(SharedBrainRuntime.MAX_REQUEST_CHARS, budget?.requestChars ?: SharedBrainRuntime.MAX_REQUEST_CHARS)) break
            array.put(item); submittedNotes.add(note); requestChars += added
        }
        val result = JSONObject(brain().replyJson(request.toString()))
        // Privacy or content may have changed while the brain was processing.
        // Never return a computed answer using revoked, deleted, expired or
        // newly locked evidence. A future model must obey this same boundary.
        if (submittedNotes.any { it.id != "external-context" }) {
            val current = try { library.list().associateBy { it.id } } catch (_: Exception) {
                clearConversation()
                return Reply(context.getString(R.string.library_read_failed), null, false)
            }
            if (submittedNotes.any { it.id != "external-context" &&
                    (current[it.id] != it || KnowledgePolicy.isExpired(it.expiresAt, System.currentTimeMillis())) }) {
                clearConversation()
                return Reply("La source a changé pendant la réponse. Pose de nouveau ta question pour consulter la version actuelle.", null, true, expectsAnswer = true)
            }
            if (submittedNotes.any { it.id != "external-context" && !SelectiveMemoryAccess.canRead(it, privateAccess()) }) {
                clearConversation()
                return codeRequired()
            }
        }
        check(result.getInt("protocolVersion") == 1 && result.getString("engine") == "nexus-core-rules-v1")
        val text = result.getString("text")
        check(text.isNotBlank() && text.length <= NativeCapacity.MAX_REPLY_TEXT_CHARS)
        val candidate = result.optJSONObject("candidate")?.let {
            val content = it.getString("content")
            check(content.isNotBlank() && content.length <= NativeCapacity.MAX_NOTE_CHARS)
            Candidate(content, it.getBoolean("sensitive"))
        }
        val evidence = result.optJSONArray("evidenceIds")
        turnEvidence = submittedNotes.filter { note ->
            note.id != "external-context" && (0 until (evidence?.length() ?: 0)).any { evidence?.optString(it) == note.id }
        }.distinctBy { it.id }
        val sources = result.optJSONArray("evidenceSources")
        check((sources?.length() ?: 0) <= 4)
        val evidenceSources = (0 until (sources?.length() ?: 0)).mapNotNull { i ->
            val item = requireNotNull(sources).getJSONObject(i)
            val source = EvidenceSource(item.getString("id"), item.getString("title"), item.getString("source"))
            check(source.id.length in 1..64 && source.title.length in 1..160 && source.source.length in 1..512)
            // Evidence metadata must not disclose a locked note's title or source.
            if (submittedNotes.none { it.id == source.id } ||
                (0 until (evidence?.length() ?: 0)).none { evidence?.optString(it) == source.id }) null else source
        }.distinctBy { it.id }
        val recalledSource = submittedNotes.firstOrNull { note ->
            (0 until (evidence?.length() ?: 0)).any { evidence?.getString(it) == note.id }
                && note.source.matches(Regex("https://fr\\.wikipedia\\.org/\\?curid=[1-9][0-9]*"))
        }?.let { note -> note.source.substringAfter("curid=").toIntOrNull()?.let { id ->
            WikipediaKnowledgeSearch.Article(id, note.title, note.excerpt)
        } }
        val publicTopic = result.optString("publicContextTopic").takeIf { it.isNotBlank() }
        check(publicTopic == null || publicTopic.length <= 180)
        val local = Reply(text, candidate, notes != null, recalledSource ?: last?.reply?.source.takeIf { contextual }, expectsAnswer = true, publicContextTopic = publicTopic,
            browserReading = last?.reply?.browserReading.takeIf { contextual },
            requiresUserCode = result.optBoolean("requiresUserCode"), evidenceSources = evidenceSources)
        if (local.requiresUserCode) return local
        val needsTopic = result.optString("lookupNeedsTopic")
        if (needsTopic.isNotEmpty()) {
            check(needsTopic in setOf("web", "wikipedia") && candidate == null)
            if (!onlineEnabled) return local.copy(text = context.getString(R.string.online_disabled_reply))
            pending = Pending.Topic(needsTopic, elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
            return local
        }
        if (candidate != null && !candidate.sensitive && notes != null && candidate.content.length <= 600) {
            pending = Pending.Save(candidate.content.take(80), candidate.content, "user:manual", elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
            return local.copy(text = "Je peux retenir : « ${candidate.content} ». Tu confirmes son enregistrement local ?")
        }
        // A read, edit, confirmation, lookup or clarification never becomes a model operation.
        // Explicitly shared notes may ground the local model without a code.
        // Unshared evidence always requires a live, authenticated private session.
        if (generator.usesNotes && candidate == null && recall == null && focusedNote == null &&
            !text.trimEnd().endsWith("?") && result.optString("publicLookupOffer").isEmpty() &&
            result.optString("publicLookupQuery").isEmpty() && result.optString("webLookupQuery").isEmpty() &&
            turnEvidence.isNotEmpty() && turnEvidence.all { SelectiveMemoryAccess.canRead(it, privateAccess()) }) {
            groundedReply(message, queryTerms, local)?.let { return it }
        }
        val offer = result.optString("publicLookupOffer")
        if (offer.isNotEmpty()) {
            check(offer.length <= 180 && candidate == null)
            // Only this core-validated public fallback may enter the language model.
            // Do not replace a locally matching saved source with an ungrounded public model answer.
            // Unrelated personal notes must not block ordinary public questions.
            val savedEvidenceMatches = notes.orEmpty().any { note ->
                KnowledgePolicy.score(queryTerms, note.title, note.excerpt) > 0
            }
            // Preserve usable personal knowledge offline even when native model
            // admission fails (RAM, battery, installation or thermal limits).
            if (savedEvidenceMatches && evidenceContext == null && recall == null && focusedNote == null) {
                offlineEvidenceReply(queryTerms, visibleNotes.orEmpty())?.let { return it }
            }
            if (message.length <= 600 && GroundedNotes.safeText(message) && evidenceContext == null &&
                turnEvidence.isEmpty() && !savedEvidenceMatches) {
                generator.generate(LocalDraftRequest(message, offer))?.let { draft ->
                    return local.copy(text = draft, generatedLocally = true, publicContextTopic = null)
                }
            }
            if (!onlineEnabled) return local.copy(text = context.getString(R.string.online_disabled_reply))
            val target = result.optString("lookupTarget", "wikipedia")
            check(target in setOf("web", "wikipedia"))
            pending = Pending.Lookup(offer, elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS, target)
            return local
        }
        val webQuery = result.optString("webLookupQuery")
        if (webQuery.isNotEmpty()) {
            check(webQuery.length <= 180 && candidate == null)
            return lookup(webQuery, onlineEnabled, notes != null, "web")
        }
        val query = result.optString("publicLookupQuery")
        if (query.isEmpty()) return local
        check(query.isNotBlank() && query.length <= 180 && candidate == null)
        return lookup(query, onlineEnabled, notes != null)
    }

    /**
     * Answer from a current, permitted local sentence when the optional GGUF
     * model cannot be used. This is quotation-based recall, not text generation.
     * A generic recent-note fallback must never be presented as a relevant fact.
     */
    private fun offlineEvidenceReply(queryTerms: List<String>, notes: List<LocalKnowledgeLibrary.Entry>): Reply? {
        if (queryTerms.isEmpty()) return null
        val selected = OfflineMemoryEvidence.select(queryTerms, notes) ?: return null
        val likely = selected.note
        val sentence = selected.sentence

        // Revalidate after selection: a note may be edited, removed, expired,
        // made private or locked while the shared brain is processing a turn.
        val unlocked = privateAccess()
        if (!SelectiveMemoryAccess.canRead(likely, unlocked)) {
            clearConversation()
            return codeRequired()
        }
        val current = try { library.get(likely.id) } catch (_: Exception) { null }
        if (current != likely || KnowledgePolicy.isExpired(likely.expiresAt, System.currentTimeMillis())) {
            clearConversation()
            return Reply("La source a été modifiée, supprimée ou a expiré. Pose de nouveau ta question pour utiliser son état actuel.",
                null, true, expectsAnswer = true)
        }
        turnEvidence = listOf(likely)
        return Reply(
            "D’après la note « ${likely.title} » (information enregistrée, non vérifiée) : ${sentence}",
            null, true, expectsAnswer = true,
            protectedInformation = likely.source != SelectiveMemoryAccess.SHARED_SOURCE,
            evidenceSources = listOf(EvidenceSource(likely.id, likely.title, likely.source))
        )
    }

    private fun groundedReply(message: String, terms: List<String>, fallback: Reply): Reply? {
        if (message.length > 600 || !GroundedNotes.safeText(message)) return null
        return try {
            val selected = turnEvidence.take(2).filter { note ->
                val metadata = JSONObject(brain().learningJson(JSONObject().put("protocolVersion", 1).put("message", "classification")
                    .put("now", System.currentTimeMillis()).put("notes", JSONArray().put(noteJson(note))).toString()))
                !metadata.getBoolean("credential")
            }
            val evidence = GroundedNotes.prepare(selected, terms)
            if (evidence.isEmpty() || selected.any { !SelectiveMemoryAccess.canRead(it, privateAccess()) }) return null
            val request = LocalDraftRequest(message, "notes", evidence)
            val quotes = GroundedNotes.validate(generator.generate(request), evidence) ?: return null
            // A model result cannot extend the unlock window or keep superseded evidence alive.
            if (selected.any { !SelectiveMemoryAccess.canRead(it, privateAccess()) }) {
                clearConversation(); return codeRequired()
            }
            val current = library.list().associateBy { it.id }
            if (selected.any { current[it.id] != it || KnowledgePolicy.isExpired(it.expiresAt, System.currentTimeMillis()) }) {
                clearConversation()
                return Reply("La source a été modifiée, supprimée ou a expiré. Pose de nouveau ta question pour utiliser son état actuel.", null, true, expectsAnswer = true)
            }
            val ids = quotes.map { quote -> evidence.single { it.key == quote.source }.noteId }.toSet()
            turnEvidence = selected.filter { it.id in ids }
            fallback.copy(text = "D’après tes notes enregistrées, non vérifiées :\n" + quotes.joinToString("\n") { it.text },
                generatedLocally = true,
                protectedInformation = selected.any { it.source != SelectiveMemoryAccess.SHARED_SOURCE },
                evidenceSources = fallback.evidenceSources.filter { it.id in ids })
        } catch (_: Exception) { null }
    }

    private fun lookup(query: String, onlineEnabled: Boolean, notesAvailable: Boolean, target: String = "wikipedia"): Reply {
        val local = Reply("", null, notesAvailable, expectsAnswer = true, publicContextTopic = query)
        if (!onlineEnabled) return local.copy(text = context.getString(R.string.online_disabled_reply))
        if (target == "web") {
            val request = try { ExternalAppPolicy.Request(ExternalAppPolicy.App.CHROME, ExternalAppPolicy.Operation.SEARCH, query) }
                catch (_: IllegalArgumentException) { return local.copy(text = "Précise un sujet public plus court pour la recherche Web.") }
            return local.copy(text = "Je lance une recherche Web dans le navigateur sur « $query ». Les résultats s’affichent dans le navigateur.", external = request)
        }
        return try {
            val article = search(query) ?: return local.copy(text = context.getString(R.string.online_no_match))
            if (notesAvailable) pending = Pending.Save(article.title, article.extract, article.url, elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
            local.copy(text = context.getString(R.string.online_answer, WikipediaAnswerPolicy.excerpt(query, article.extract)) +
                if (notesAvailable) " Veux-tu que je retienne cette information dans mes notes locales ?" else "", source = article)
        } catch (_: IOException) {
            local.copy(text = context.getString(R.string.online_unavailable))
        }
    }

    override fun close() { generator.close(); clearConversation(); runtime?.close(); runtime = null }
}
