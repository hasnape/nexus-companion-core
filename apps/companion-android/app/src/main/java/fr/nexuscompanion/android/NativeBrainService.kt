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
    private val search: (String) -> WikipediaKnowledgeSearch.Article? = { WikipediaKnowledgeSearch.search(context, it) }
) : AutoCloseable {
    data class Candidate(val content: String, val sensitive: Boolean)
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
        val protectedInformation: Boolean = false
    )
    private var runtime: SharedBrainRuntime? = null
    private sealed interface Pending {
        val expires: Long
        data class Lookup(val query: String, override val expires: Long, val target: String = "wikipedia") : Pending
        data class Topic(val target: String, override val expires: Long) : Pending
        data class Save(val title: String, val excerpt: String, val source: String, override val expires: Long) : Pending
        data class OpenApp(val request: ExternalAppPolicy.Request, val evidence: List<LocalKnowledgeLibrary.Entry>, override val expires: Long) : Pending
    }
    private var pending: Pending? = null
    private data class Conversation(val reply: Reply, val notes: List<LocalKnowledgeLibrary.Entry>, val expires: Long)
    private var conversation: Conversation? = null
    private var turnEvidence: List<LocalKnowledgeLibrary.Entry> = emptyList()
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
    fun clearConversation() { pending = null; conversation = null; turnEvidence = emptyList() }

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
        conversation = Conversation(reply.copy(body = null, editNote = null, external = null), turnEvidence.toList(), expires)
    }

    /** External prose is read as data; it never enters the command, body, or lookup parsers. */
    fun readBrowserResult(reading: BrowserReading): Reply {
        clearConversation()
        accessOnPreviousTurn = privateAccess()
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
            val stats = library.statistics()
            val history = if (stats.archivedNotes == 0) "" else " Je conserve aussi ${stats.archivedNotes} ${if (stats.archivedNotes == 1) "ancienne version" else "anciennes versions"}."
            return Reply("Ma mémoire contient ${stats.notes} notes. Elle occupe ${(stats.storedBytes + 1023) / 1024} kilo-octets. La compression économise ${stats.compressionPercent} pour cent avant chiffrement.$history", null, true, expectsAnswer = true)
        }
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
        val recall = KnowledgePolicy.recallFor(message)
        val terms = JSONObject(brain().retrievalJson(message)).getJSONArray("terms")
        val queryTerms = (0 until terms.length()).map { terms.getString(it) }
        val notes = try {
            val selected = requireNotNull(budget)
            val preferred = try { learning.preferredIds(queryTerms, library.list(), now) } catch (_: Exception) { emptyList() }
            focusedNote?.let(::listOf) ?: library.contextFor(message, now, recall, queryTerms, selected.contextNotes, preferred)
        } catch (_: Exception) { null }
        val array = JSONArray()
        val request = JSONObject().put("protocolVersion", 1).put("message", message)
            .put("notes", array).put("now", now).put("recallRequested", recall != null || focusedNote != null)
            .put("readRequested", recall is KnowledgePolicy.Recall.Read || focusedNote != null)
            .put("privateAccessGranted", privateAccess())
        if (focusedNote == null) {
            conversation?.reply?.publicContextTopic?.let { request.put("publicContextTopic", it) }
            if (previous is Pending.Topic) request.put("awaitingLookupTarget", previous.target)
        }
        var requestChars = request.toString().length
        // Budget each serialized record once; never repeatedly serialize an oversized context.
        for (note in notes.orEmpty()) {
            val item = noteJson(note)
            val added = item.toString().length + if (array.length() > 0) 1 else 0
            if (requestChars + added > minOf(SharedBrainRuntime.MAX_REQUEST_CHARS, budget?.requestChars ?: SharedBrainRuntime.MAX_REQUEST_CHARS)) break
            array.put(item); requestChars += added
        }
        val result = JSONObject(brain().replyJson(request.toString()))
        check(result.getInt("protocolVersion") == 1 && result.getString("engine") == "nexus-core-rules-v1")
        val text = result.getString("text")
        check(text.isNotBlank() && text.length <= NativeCapacity.MAX_REPLY_TEXT_CHARS)
        val candidate = result.optJSONObject("candidate")?.let {
            val content = it.getString("content")
            check(content.isNotBlank() && content.length <= NativeCapacity.MAX_NOTE_CHARS)
            Candidate(content, it.getBoolean("sensitive"))
        }
        val evidence = result.optJSONArray("evidenceIds")
        turnEvidence = notes.orEmpty().filter { note ->
            (0 until (evidence?.length() ?: 0)).any { evidence?.optString(it) == note.id }
        }.distinctBy { it.id }
        val recalledSource = notes.orEmpty().firstOrNull { note ->
            (0 until (evidence?.length() ?: 0)).any { evidence?.getString(it) == note.id }
                && note.source.matches(Regex("https://fr\\.wikipedia\\.org/\\?curid=[1-9][0-9]*"))
        }?.let { note -> note.source.substringAfter("curid=").toIntOrNull()?.let { id ->
            WikipediaKnowledgeSearch.Article(id, note.title, note.excerpt)
        } }
        val publicTopic = result.optString("publicContextTopic").takeIf { it.isNotBlank() }
        check(publicTopic == null || publicTopic.length <= 180)
        val local = Reply(text, candidate, notes != null, recalledSource, expectsAnswer = true, publicContextTopic = publicTopic,
            requiresUserCode = result.optBoolean("requiresUserCode"))
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
        val offer = result.optString("publicLookupOffer")
        if (offer.isNotEmpty()) {
            check(offer.length <= 180 && candidate == null)
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

    override fun close() { clearConversation(); runtime?.close(); runtime = null }
}
