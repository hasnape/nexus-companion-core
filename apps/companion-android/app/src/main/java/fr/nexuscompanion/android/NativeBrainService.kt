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
        val editNote: LocalKnowledgeLibrary.Entry? = null
    )
    private var runtime: SharedBrainRuntime? = null
    private sealed interface Pending {
        val expires: Long
        data class Lookup(val query: String, override val expires: Long) : Pending
        data class Save(val title: String, val excerpt: String, val source: String, override val expires: Long) : Pending
    }
    private var pending: Pending? = null
    private data class Conversation(val reply: Reply, val notes: List<LocalKnowledgeLibrary.Entry>, val expires: Long)
    private var conversation: Conversation? = null
    private var turnEvidence: List<LocalKnowledgeLibrary.Entry> = emptyList()

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
            if (repeat) return last?.reply?.copy(body = null, editNote = null)
                ?: Reply("Je n’ai plus de réponse récente à répéter. Pose-moi de nouveau ta question.", null, true, expectsAnswer = true)
            pending = null
            val note = last?.notes?.singleOrNull()?.let { library.get(it.id) }
                ?: return Reply("Précise le sujet de la note à utiliser. Tu peux aussi la choisir dans « Mes notes ».", null, true, expectsAnswer = true)
            turnEvidence = listOf(note)
            val result = if (edit) Reply("Tu peux relire et modifier cette note dans l’éditeur.", null, true, editNote = note)
                else answer(message, onlineEnabled, steeringAxle, note)
            rememberReply(result)
            return result
        }
        turnEvidence = emptyList()
        val result = answer(message, onlineEnabled, steeringAxle)
        rememberReply(result)
        return result
    }

    private fun rememberReply(reply: Reply) {
        val expires = minOf(elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS, pending?.expires ?: Long.MAX_VALUE)
        conversation = Conversation(reply.copy(body = null, editNote = null), turnEvidence.toList(), expires)
    }

    private fun answer(message: String, onlineEnabled: Boolean, steeringAxle: String, focusedNote: LocalKnowledgeLibrary.Entry? = null): Reply {
        val previous = pending?.takeIf { elapsed() < it.expires }
        pending = null
        val approval = VoiceTranscriptPolicy.confirmation(message)
        if (approval != null) {
            if (previous == null) return Reply("Je n’ai aucune recherche ou information en attente de confirmation. Que veux-tu savoir ?", null, true, expectsAnswer = true)
            if (!approval) return Reply("D’accord, j’annule cette proposition.", null, true, expectsAnswer = true)
            when (previous) {
                is Pending.Lookup -> return lookup(previous.query, onlineEnabled, true)
                is Pending.Save -> {
                    val saved = library.addApprovedSource(previous.title, previous.excerpt, previous.source)
                    turnEvidence = listOf(saved.entry)
                    return Reply(context.getString(if (saved.created) R.string.note_saved else R.string.note_already_saved), null, true, expectsAnswer = true)
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
        val now = System.currentTimeMillis()
        val recall = KnowledgePolicy.recallFor(message)
        val terms = JSONObject(brain().retrievalJson(message)).getJSONArray("terms")
        val queryTerms = (0 until terms.length()).map { terms.getString(it) }
        val notes = try { focusedNote?.let(::listOf) ?: library.contextFor(message, now, recall, queryTerms) } catch (_: Exception) { null }
        val array = JSONArray()
        val request = JSONObject().put("protocolVersion", 1).put("message", message)
            .put("notes", array).put("now", now).put("recallRequested", recall != null || focusedNote != null)
            .put("readRequested", recall is KnowledgePolicy.Recall.Read || focusedNote != null)
        var requestChars = request.toString().length
        // Budget each serialized record once; never repeatedly serialize an oversized context.
        for (note in notes.orEmpty()) {
            val item = JSONObject().apply {
                put("id", note.id); put("title", note.title); put("excerpt", note.excerpt)
                put("source", note.source); put("recordedAt", note.recordedAt)
                put("expiresAt", note.expiresAt ?: JSONObject.NULL); put("verified", false)
                put("manuallyEdited", note.manuallyEdited)
            }
            val added = item.toString().length + if (array.length() > 0) 1 else 0
            if (requestChars + added > SharedBrainRuntime.MAX_REQUEST_CHARS) break
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
        val local = Reply(text, candidate, notes != null, recalledSource, expectsAnswer = true)
        if (candidate != null && !candidate.sensitive && notes != null && candidate.content.length <= 600) {
            pending = Pending.Save(candidate.content.take(80), candidate.content, "user:manual", elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
            return local.copy(text = "Je peux retenir : « ${candidate.content} ». Tu confirmes son enregistrement local ?")
        }
        val offer = result.optString("publicLookupOffer")
        if (offer.isNotEmpty()) {
            check(offer.length <= 180 && candidate == null)
            if (!onlineEnabled) return local.copy(text = context.getString(R.string.online_disabled_reply))
            pending = Pending.Lookup(offer, elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
            return local
        }
        val query = result.optString("publicLookupQuery")
        if (query.isEmpty()) return local
        check(query.isNotBlank() && query.length <= 180 && candidate == null)
        return lookup(query, onlineEnabled, notes != null)
    }

    private fun lookup(query: String, onlineEnabled: Boolean, notesAvailable: Boolean): Reply {
        val local = Reply("", null, notesAvailable, expectsAnswer = true)
        if (!onlineEnabled) return local.copy(text = context.getString(R.string.online_disabled_reply))
        return try {
            val article = search(query) ?: return local.copy(text = context.getString(R.string.online_no_match))
            if (notesAvailable) pending = Pending.Save(article.title, article.extract, article.url, elapsed() + NativeCapacity.PENDING_CONFIRMATION_MS)
            local.copy(text = context.getString(R.string.online_answer, article.extract) +
                if (notesAvailable) " Veux-tu que je retienne cette information dans mes notes locales ?" else "", source = article)
        } catch (_: IOException) {
            local.copy(text = context.getString(R.string.online_unavailable))
        }
    }

    override fun close() { clearConversation(); runtime?.close(); runtime = null }
}
