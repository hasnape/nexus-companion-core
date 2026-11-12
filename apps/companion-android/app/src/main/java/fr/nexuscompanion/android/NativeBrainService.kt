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
        val body: RollingBodyState.Plan? = null
    )
    private var runtime: SharedBrainRuntime? = null
    private sealed interface Pending {
        val expires: Long
        data class Lookup(val query: String, override val expires: Long) : Pending
        data class Save(val title: String, val excerpt: String, val source: String, override val expires: Long) : Pending
    }
    private var pending: Pending? = null

    /** Session context is transient and confined to the worker, never written as history. */
    fun clearConversation() { pending = null }

    private fun confirmation(message: String): Boolean? {
        val words = VoiceTranscriptPolicy.commandWords(message)
        return when (words) {
            "oui", "oui merci", "oui je confirme", "je confirme", "d accord", "vas y",
            "oui cherche", "oui retiens", "oui retiens le", "retiens le", "enregistre le" -> true
            "non", "non merci", "annule", "laisse tomber", "n enregistre pas" -> false
            else -> null
        }
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
        require(message.isNotBlank() && message.length <= 2000)
        val previous = pending?.takeIf { elapsed() < it.expires }
        pending = null
        val approval = confirmation(message)
        if (approval != null) {
            if (previous == null) return Reply("Je n’ai aucune recherche ou information en attente de confirmation. Que veux-tu savoir ?", null, true, expectsAnswer = true)
            if (!approval) return Reply("D’accord, j’annule cette proposition.", null, true, expectsAnswer = true)
            when (previous) {
                is Pending.Lookup -> return lookup(previous.query, onlineEnabled, true)
                is Pending.Save -> {
                    val saved = library.addApprovedSource(previous.title, previous.excerpt, previous.source)
                    return Reply(context.getString(if (saved.created) R.string.note_saved else R.string.note_already_saved), null, true, expectsAnswer = true)
                }
            }
        }
        if (VoiceTranscriptPolicy.commandWords(message) in setOf("etat de ta memoire", "etat de la memoire", "bilan memoire", "combien de notes as tu")) {
            val stats = library.statistics()
            return Reply("Ma mémoire contient ${stats.notes} notes. Elle occupe ${(stats.storedBytes + 1023) / 1024} kilo-octets. La compression économise ${stats.compressionPercent} pour cent avant chiffrement.", null, true, expectsAnswer = true)
        }
        val body = JSONObject(brain().bodyJson(JSONObject().put("message", message).put("steeringAxle", steeringAxle).toString()))
        if (body.getBoolean("handled")) return Reply(body.getString("text"), null, true, expectsAnswer = true,
            body = body.optJSONObject("plan")?.let(RollingBodyState.Plan::read))
        val now = System.currentTimeMillis()
        val recall = KnowledgePolicy.recallFor(message)
        val terms = JSONObject(brain().retrievalJson(message)).getJSONArray("terms")
        val queryTerms = (0 until terms.length()).map { terms.getString(it) }
        val notes = try { library.contextFor(message, now, recall, queryTerms) } catch (_: Exception) { null }
        val array = JSONArray()
        notes.orEmpty().forEach { note ->
            array.put(JSONObject().apply {
                put("id", note.id); put("title", note.title); put("excerpt", note.excerpt)
                put("source", note.source); put("recordedAt", note.recordedAt)
                put("expiresAt", note.expiresAt ?: JSONObject.NULL); put("verified", false)
            })
        }
        val request = JSONObject().put("protocolVersion", 1).put("message", message)
            .put("notes", array).put("now", now).put("recallRequested", recall != null)
        // JSON escaping can expand valid note text. Bound context without truncating stored notes.
        while (request.toString().length > SharedBrainRuntime.MAX_REQUEST_CHARS && array.length() > 0) {
            array.remove(array.length() - 1)
        }
        val result = JSONObject(brain().replyJson(request.toString()))
        check(result.getInt("protocolVersion") == 1 && result.getString("engine") == "nexus-core-rules-v1")
        val text = result.getString("text")
        check(text.isNotBlank() && text.length <= 12_000)
        val candidate = result.optJSONObject("candidate")?.let {
            val content = it.getString("content")
            check(content.isNotBlank() && content.length <= 2000)
            Candidate(content, it.getBoolean("sensitive"))
        }
        val evidence = result.optJSONArray("evidenceIds")
        val recalledSource = notes.orEmpty().firstOrNull { note ->
            (0 until (evidence?.length() ?: 0)).any { evidence?.getString(it) == note.id }
                && note.source.matches(Regex("https://fr\\.wikipedia\\.org/\\?curid=[1-9][0-9]*"))
        }?.let { note -> note.source.substringAfter("curid=").toIntOrNull()?.let { id ->
            WikipediaKnowledgeSearch.Article(id, note.title, note.excerpt)
        } }
        val local = Reply(text, candidate, notes != null, recalledSource, expectsAnswer = true)
        if (candidate != null && !candidate.sensitive && notes != null && candidate.content.length <= 600) {
            pending = Pending.Save(candidate.content.take(80), candidate.content, "user:manual", elapsed() + 90_000L)
            return local.copy(text = "Je peux retenir : « ${candidate.content} ». Tu confirmes son enregistrement local ?")
        }
        val offer = result.optString("publicLookupOffer")
        if (offer.isNotEmpty()) {
            check(offer.length <= 180 && candidate == null)
            if (!onlineEnabled) return local.copy(text = context.getString(R.string.online_disabled_reply))
            pending = Pending.Lookup(offer, elapsed() + 90_000L)
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
            if (notesAvailable) pending = Pending.Save(article.title, article.extract, article.url, elapsed() + 90_000L)
            local.copy(text = context.getString(R.string.online_answer, article.extract) +
                if (notesAvailable) " Veux-tu que je retienne cette information dans mes notes locales ?" else "", source = article)
        } catch (_: IOException) {
            local.copy(text = context.getString(R.string.online_unavailable))
        }
    }

    override fun close() { clearConversation(); runtime?.close(); runtime = null }
}
