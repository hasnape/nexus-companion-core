package fr.nexuscompanion.android

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Local library, not the brain. Sources are evidence, never instructions.
 * Each companion has its own namespace; no cloud account or network access.
 * External information may be imported only by an explicit user action.
 */
internal class LocalKnowledgeLibrary(context: Context, companionId: String = "nexus-default") {
    private val safeId = requireNotNull(companionId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) })
    private val store = AtomicFile(File(context.filesDir, "knowledge-$safeId.json"))
    private val cipher = LocalKnowledgeCipher(safeId)
    private val maxEntries = 500
    private val maxText = 2000

    data class Entry(
        val id: String,
        val title: String,
        val excerpt: String,
        val source: String,
        val recordedAt: Long,
        val expiresAt: Long?,
        val verified: Boolean
    )

    @Synchronized
    fun list(): List<Entry> {
        val bytes = try { store.openRead().use { it.readBytes() } }
            catch (_: java.io.FileNotFoundException) { return emptyList() }
        // Legacy alpha files were plaintext. Convert only recognized schema-v1 JSON,
        // atomically, before returning records. Do not overwrite on decrypt failure.
        val plaintext = if (cipher.isEncrypted(bytes)) {
            cipher.decrypt(bytes)
        } else {
            require(bytes.size <= 2_000_000 && bytes.firstOrNull() == '{'.code.toByte()) {
                "Unknown library format"
            }
            val legacy = JSONObject(String(bytes, StandardCharsets.UTF_8))
            require(legacy.optInt("schema") == 1 && legacy.has("entries")) {
                "Unknown legacy library schema"
            }
            writeEncrypted(bytes)
            bytes
        }
        val root = JSONObject(String(plaintext, StandardCharsets.UTF_8))
        require(root.optInt("schema") == 1) { "Unknown library schema" }
        val entries = root.getJSONArray("entries")
        return (0 until entries.length()).map { i ->
            val item = entries.getJSONObject(i)
            Entry(
                id = item.getString("id"),
                title = item.getString("title"),
                excerpt = item.getString("excerpt"),
                source = item.getString("source"),
                recordedAt = item.getLong("recordedAt"),
                expiresAt = if (item.isNull("expiresAt")) null else item.getLong("expiresAt"),
                verified = item.getBoolean("verified")
            )
        }
    }

    @Synchronized
    fun addUserNote(title: String, excerpt: String): Entry =
        add(title, excerpt, "user:manual", verified = false, expiresAt = null)

    /**
     * Intended for future explicit import flow; do not silently fetch URLs.
     * verified=true requires a separate verification process, not user input.
     */
    @Synchronized
    fun add(
        title: String, excerpt: String, source: String,
        verified: Boolean = false, expiresAt: Long? = null
    ): Entry {
        require(KnowledgePolicy.isValidUserNote(title, excerpt))
        require(source.isNotBlank() && source.length <= 512)
        require(!verified) { "Verification must be performed by a separate trusted verifier" }
        val existing = list()
        require(existing.size < maxEntries) { "Library capacity reached" }
        val entry = Entry(UUID.randomUUID().toString(), title.trim(), excerpt.trim(),
            source.trim(), System.currentTimeMillis(), expiresAt, false)
        save(existing + entry)
        return entry
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val existing = list()
        val updated = existing.filterNot { it.id == id }
        if (updated.size == existing.size) return false
        save(updated)
        return true
    }

    fun search(query: String, now: Long = System.currentTimeMillis()): List<Entry> {
        val tokens = KnowledgePolicy.queryTerms(query)
        if (tokens.isEmpty()) return emptyList()
        return list().filterNot { KnowledgePolicy.isExpired(it.expiresAt, now) }
            .map { entry ->
                entry to KnowledgePolicy.score(tokens, entry.title, entry.excerpt)
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }
                .thenByDescending { it.first.recordedAt })
            .take(5).map { it.first }
    }

    private fun save(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("title", entry.title)
                put("excerpt", entry.excerpt)
                put("source", entry.source)
                put("recordedAt", entry.recordedAt)
                put("expiresAt", entry.expiresAt ?: JSONObject.NULL)
                put("verified", entry.verified)
            })
        }
        val bytes = JSONObject().put("schema", 1).put("entries", array)
            .toString().toByteArray(StandardCharsets.UTF_8)
        writeEncrypted(bytes)
    }

    private fun writeEncrypted(plaintext: ByteArray) {
        val encrypted = cipher.encrypt(plaintext)
        val stream = store.startWrite()
        try {
            stream.write(encrypted)
            store.finishWrite(stream)
        } catch (failure: Exception) {
            store.failWrite(stream)
            throw failure
        }
    }
}
