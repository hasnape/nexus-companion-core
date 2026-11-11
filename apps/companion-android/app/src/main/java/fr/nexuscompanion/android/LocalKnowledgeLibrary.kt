package fr.nexuscompanion.android

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

internal class InvalidKnowledgeNote : IllegalArgumentException("Invalid knowledge note")
internal class KnowledgeCapacityReached : IllegalStateException("Knowledge capacity reached")

/**
 * Local library, not the brain. Sources are evidence, never instructions.
 * Each companion has its own namespace; no cloud account or network access.
 * External information may be imported only by an explicit user action.
 */
internal class LocalKnowledgeLibrary(
    context: Context,
    companionId: String = "nexus-default",
    private val cipher: KnowledgeCipher = LocalKnowledgeCipher(companionId)
) {
    private companion object {
        // AtomicFile requires callers to serialize readers and writers across instances.
        val storeLock = Any()
        const val MAX_ENCRYPTED_BYTES = KnowledgePayload.MAX_JSON_BYTES + 32
    }

    private val safeId = requireNotNull(companionId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) })
    private val store = AtomicFile(File(context.filesDir, "knowledge-$safeId.json"))
    private val maxEntries = 500

    data class Entry(
        val id: String,
        val title: String,
        val excerpt: String,
        val source: String,
        val recordedAt: Long,
        val expiresAt: Long?,
        val verified: Boolean
    )

    data class NoteSave(val entry: Entry, val created: Boolean)

    fun list(): List<Entry> = synchronized(storeLock) { readEntries() }

    private fun readEntries(): List<Entry> {
        val bytes = try { store.openRead().use { KnowledgePayload.readBounded(it, MAX_ENCRYPTED_BYTES) } }
            catch (_: java.io.FileNotFoundException) { return emptyList() }
        // Legacy alpha files were plaintext. Convert only recognized schema-v1 JSON,
        // atomically, before returning records. Do not overwrite on decrypt failure.
        val legacyPlaintext = !cipher.isEncrypted(bytes)
        val plaintext = if (!legacyPlaintext) {
            KnowledgePayload.decode(cipher.decrypt(bytes))
        } else {
            require(bytes.size <= 2_000_000 && bytes.firstOrNull() == '{'.code.toByte()) {
                "Unknown library format"
            }
            bytes
        }
        val root = JSONObject(String(plaintext, StandardCharsets.UTF_8))
        require(root.optInt("schema") == 1) { "Unknown library schema" }
        val entries = root.getJSONArray("entries")
        require(entries.length() <= maxEntries) { "Invalid library capacity" }
        val parsed = (0 until entries.length()).map { i ->
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
        // Validate every record before replacing a legacy plaintext file.
        require(parsed.all { KnowledgePolicy.isValidUserNote(it.title, it.excerpt) &&
            it.source.isNotBlank() && it.source.length <= 512 }) { "Invalid library entry" }
        if (legacyPlaintext) writeEncrypted(bytes)
        return parsed
    }

    fun addUserNote(title: String, excerpt: String): NoteSave =
        saveNote(title, excerpt, "user:manual", verified = false, expiresAt = null)

    /** The conversation must obtain explicit approval before calling this method. */
    fun addApprovedSource(title: String, excerpt: String, source: String): NoteSave =
        saveNote(title, excerpt, source, verified = false, expiresAt = null)

    /**
     * Intended for future explicit import flow; do not silently fetch URLs.
     * verified=true requires a separate verification process, not user input.
     */
    fun add(
        title: String, excerpt: String, source: String,
        verified: Boolean = false, expiresAt: Long? = null
    ): Entry = saveNote(title, excerpt, source, verified, expiresAt).entry

    private fun saveNote(
        title: String, excerpt: String, source: String, verified: Boolean, expiresAt: Long?
    ): NoteSave = synchronized(storeLock) {
        if (!KnowledgePolicy.isValidUserNote(title, excerpt)) throw InvalidKnowledgeNote()
        require(source.isNotBlank() && source.length <= 512)
        require(!verified) { "Verification must be performed by a separate trusted verifier" }
        val existing = readEntries()
        val cleanTitle = title.trim()
        val cleanExcerpt = excerpt.trim()
        val cleanSource = source.trim()
        val duplicate = existing.firstOrNull {
            it.title == cleanTitle && it.excerpt == cleanExcerpt && it.source == cleanSource &&
                it.expiresAt == expiresAt && !it.verified
        }
        if (duplicate != null) return@synchronized NoteSave(duplicate, created = false)
        if (existing.size >= maxEntries) throw KnowledgeCapacityReached()
        val entry = Entry(UUID.randomUUID().toString(), cleanTitle, cleanExcerpt,
            cleanSource, System.currentTimeMillis(), expiresAt, false)
        save(existing + entry)
        NoteSave(entry, created = true)
    }

    fun remove(id: String): Boolean = synchronized(storeLock) {
        val existing = readEntries()
        val updated = existing.filterNot { it.id == id }
        if (updated.size == existing.size) return@synchronized false
        save(updated)
        true
    }

    fun search(query: String, now: Long = System.currentTimeMillis()): List<Entry> {
        val terms = KnowledgePolicy.recallTopic(query) ?: KnowledgePolicy.queryTerms(query)
        return matchingEntries(list(), terms, now).take(5)
    }

    /** One atomic read per conversation turn; expired notes never enter the brain context. */
    fun contextFor(
        query: String,
        now: Long = System.currentTimeMillis(),
        recall: KnowledgePolicy.Recall? = KnowledgePolicy.recallFor(query),
        queryTerms: List<String> = KnowledgePolicy.queryTerms(query)
    ): List<Entry> {
        val entries = list().filterNot { KnowledgePolicy.isExpired(it.expiresAt, now) }
        val terms = when (recall) {
            is KnowledgePolicy.Recall.Topic -> recall.terms
            KnowledgePolicy.Recall.Overview -> emptyList()
            null -> queryTerms
        }
        val matches = matchingEntries(entries, terms, now)
        val ranked = if (matches.isEmpty() && recall !is KnowledgePolicy.Recall.Topic) {
            entries.sortedByDescending { it.recordedAt }
        } else matches
        return ranked.distinctBy { it.title to it.excerpt }.take(8)
    }

    private fun matchingEntries(entries: List<Entry>, tokens: List<String>, now: Long): List<Entry> {
        if (tokens.isEmpty()) return emptyList()
        return entries.filterNot { KnowledgePolicy.isExpired(it.expiresAt, now) }
            .map { entry ->
                entry to KnowledgePolicy.score(tokens, entry.title, entry.excerpt)
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }
                .thenByDescending { it.first.recordedAt })
            .map { it.first }
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
        val encrypted = cipher.encrypt(KnowledgePayload.encode(plaintext))
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
