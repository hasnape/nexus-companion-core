package fr.nexuscompanion.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/** Called only by the dedicated worker. Android owns persistence; the shared brain sees data only. */
internal class NativeBrainService(
    private val context: Context,
    private val library: LocalKnowledgeLibrary,
    private val search: (String) -> WikipediaKnowledgeSearch.Article? = { WikipediaKnowledgeSearch.search(context, it) }
) : AutoCloseable {
    data class Candidate(val content: String, val sensitive: Boolean)
    data class Reply(
        val text: String,
        val candidate: Candidate?,
        val notesAvailable: Boolean,
        val source: WikipediaKnowledgeSearch.Article? = null
    )
    private var runtime: SharedBrainRuntime? = null

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

    fun reply(message: String, onlineEnabled: Boolean = true): Reply {
        require(message.isNotBlank() && message.length <= 2000)
        val now = System.currentTimeMillis()
        val recall = KnowledgePolicy.recallFor(message)
        val notes = try { library.contextFor(message, now, recall) } catch (_: Exception) { null }
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
        val local = Reply(text, candidate, notes != null)
        val query = result.optString("publicLookupQuery")
        if (query.isEmpty()) return local
        check(query.isNotBlank() && query.length <= 180 && candidate == null)
        if (!onlineEnabled) return local.copy(text = context.getString(R.string.online_disabled_reply))
        return try {
            val article = search(query) ?: return local.copy(text = context.getString(R.string.online_no_match))
            local.copy(text = context.getString(R.string.online_answer, article.extract), source = article)
        } catch (_: IOException) {
            local.copy(text = context.getString(R.string.online_unavailable))
        }
    }

    override fun close() { runtime?.close(); runtime = null }
}
