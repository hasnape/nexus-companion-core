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
internal class KnowledgeChanged : IllegalStateException("Knowledge note changed")

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
    private val rescue = AtomicFile(File(context.noBackupFilesDir, "knowledge-rescue-$safeId.bin"))
    private val maxEntries = NativeCapacity.MAX_LIBRARY_ENTRIES
    private data class Snapshot(val encrypted: ByteArray, val entries: List<Entry>, val jsonBytes: Int, val payloadBytes: Int,
        var searchIndex: KnowledgeSearchIndex? = null)
    private var snapshot: Snapshot? = null

    data class Entry(
        val id: String,
        val title: String,
        val excerpt: String,
        val source: String,
        val recordedAt: Long,
        val expiresAt: Long?,
        val verified: Boolean,
        val supersededBy: String? = null,
        val manuallyEdited: Boolean = false
    )

    data class NoteSave(val entry: Entry, val created: Boolean)
    data class Statistics(val notes: Int, val storedBytes: Int, val jsonBytes: Int, val payloadBytes: Int, val archivedNotes: Int = 0) {
        val compressionPercent: Int get() = if (jsonBytes == 0) 0 else ((jsonBytes - payloadBytes).toLong() * 100 / jsonBytes).toInt().coerceIn(0, 100)
    }

    fun list(): List<Entry> = synchronized(storeLock) { readEntries().filter { it.supersededBy == null } }

    fun get(id: String): Entry? = synchronized(storeLock) { readEntries().firstOrNull { it.id == id && it.supersededBy == null } }

    /** Newest predecessor first; the current version itself is not part of the history. */
    fun history(id: String): List<Entry> = synchronized(storeLock) {
        val entries = readEntries()
        if (entries.none { it.id == id && it.supersededBy == null }) throw KnowledgeChanged()
        val preceding = entries.filter { it.supersededBy != null }.associateBy { it.supersededBy }
        val result = mutableListOf<Entry>()
        var cursor = preceding[id]
        while (cursor != null) { result.add(cursor); cursor = preceding[cursor.id] }
        result
    }

    fun statistics(): Statistics = synchronized(storeLock) {
        val entries = readEntries()
        val current = snapshot
        val archived = entries.count { it.supersededBy != null }
        Statistics(entries.size - archived, current?.encrypted?.size ?: 0, current?.jsonBytes ?: 0, current?.payloadBytes ?: 0, archived)
    }

    private fun readEntries(): List<Entry> {
        val bytes = try { store.openRead().use { KnowledgePayload.readBounded(it, MAX_ENCRYPTED_BYTES) } }
            catch (_: java.io.FileNotFoundException) { snapshot = null; return emptyList() }
        // Compare authenticated file bytes, not timestamps: other instances and tampering remain visible.
        snapshot?.takeIf { it.encrypted.contentEquals(bytes) }?.let { return it.entries.toList() }
        // Legacy alpha files were plaintext. Convert only recognized JSON schemas,
        // atomically, before returning records. Do not overwrite on decrypt failure.
        val legacyPlaintext = !cipher.isEncrypted(bytes)
        var payloadBytes = bytes.size
        val plaintext = if (!legacyPlaintext) {
            val packed = cipher.decrypt(bytes)
            payloadBytes = packed.size
            KnowledgePayload.decode(packed)
        } else {
            require(bytes.size <= 2_000_000 && bytes.firstOrNull() == '{'.code.toByte()) {
                "Unknown library format"
            }
            bytes
        }
        val parsed = parseEntries(plaintext)
        if (legacyPlaintext) writeEncrypted(bytes, parsed)
        else snapshot = Snapshot(bytes, parsed.toList(), plaintext.size, payloadBytes)
        return parsed
    }

    private fun parseEntries(plaintext: ByteArray, portable: Boolean = false): List<Entry> {
        require(plaintext.size <= KnowledgePayload.MAX_JSON_BYTES)
        val decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(plaintext)).toString()
        val parser = org.json.JSONTokener(decoded)
        val root = parser.nextValue() as? JSONObject ?: error("Invalid library object")
        require(parser.nextClean() == '\u0000')
        val schema = root.optInt("schema")
        require(schema in 1..2) { "Unknown library schema" }
        if (portable) require(root.keys().asSequence().toSet() == setOf("schema", "entries") && root.get("schema") is Int)
        val entries = root.getJSONArray("entries")
        require(entries.length() <= NativeCapacity.MAX_LIBRARY_RECORDS) { "Invalid library capacity" }
        val parsed = (0 until entries.length()).map { i ->
            val item = entries.getJSONObject(i)
            if (portable) {
                val allowed = setOf("id", "title", "excerpt", "source", "recordedAt", "expiresAt", "verified", "supersededBy", "manuallyEdited")
                require(item.keys().asSequence().all { it in allowed })
                require(listOf("id", "title", "excerpt", "source").all { item.get(it) is String })
                require(item.get("recordedAt") is Int || item.get("recordedAt") is Long)
                require(item.isNull("expiresAt") || item.get("expiresAt") is Int || item.get("expiresAt") is Long)
                require(item.get("verified") is Boolean)
            }
            Entry(
                id = item.getString("id"),
                title = item.getString("title"),
                excerpt = item.getString("excerpt"),
                source = item.getString("source"),
                recordedAt = item.getLong("recordedAt"),
                expiresAt = if (item.isNull("expiresAt")) null else item.getLong("expiresAt"),
                verified = item.getBoolean("verified"),
                supersededBy = if (!item.has("supersededBy") || item.isNull("supersededBy")) null else {
                    val value = item.get("supersededBy"); require(value is String); value
                },
                manuallyEdited = if (!item.has("manuallyEdited")) false else {
                    val value = item.get("manuallyEdited"); require(value is Boolean); value
                }
            )
        }
        // Validate every record before replacing a legacy plaintext file.
        require(parsed.all { KnowledgePolicy.isValidUserNote(it.title, it.excerpt) &&
            it.source.isNotBlank() && it.source.length <= 512 }) { "Invalid library entry" }
        require(schema == 2 || parsed.none { it.supersededBy != null || it.manuallyEdited }) { "Version history requires schema 2" }
        validateVersions(parsed)
        if (portable) {
            val limit = 9_007_199_254_740_991L
            require(parsed.all { !it.verified && it.recordedAt in 0..limit && (it.expiresAt == null || it.expiresAt in 0..limit) }) { "Unsupported portable metadata" }
        }
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
        val active = existing.filter { it.supersededBy == null }
        val duplicate = active.firstOrNull {
            KnowledgePolicy.noteKey(it.title) == KnowledgePolicy.noteKey(cleanTitle) &&
                KnowledgePolicy.noteKey(it.excerpt) == KnowledgePolicy.noteKey(cleanExcerpt) && it.source == cleanSource &&
                it.expiresAt == expiresAt && !it.verified
        }
        if (duplicate != null) return@synchronized NoteSave(duplicate, created = false)
        if (active.size >= maxEntries || existing.size >= NativeCapacity.MAX_LIBRARY_RECORDS) throw KnowledgeCapacityReached()
        val entry = Entry(UUID.randomUUID().toString(), cleanTitle, cleanExcerpt,
            cleanSource, System.currentTimeMillis(), expiresAt, false)
        save(existing + entry)
        NoteSave(entry, created = true)
    }

    /** Compare-and-swap revision: preserve every old byte of text and its source in history. */
    fun replace(expected: Entry, title: String, excerpt: String): NoteSave = synchronized(storeLock) {
        if (!KnowledgePolicy.isValidUserNote(title, excerpt)) throw InvalidKnowledgeNote()
        val entries = readEntries()
        val current = entries.firstOrNull { it.id == expected.id && it.supersededBy == null } ?: throw KnowledgeChanged()
        if (current != expected) throw KnowledgeChanged()
        if (current.title == title.trim() && current.excerpt == excerpt.trim()) return@synchronized NoteSave(current, false)
        if (entries.size >= NativeCapacity.MAX_LIBRARY_RECORDS) throw KnowledgeCapacityReached()
        val updated = current.copy(id = UUID.randomUUID().toString(), title = title.trim(), excerpt = excerpt.trim(),
            recordedAt = System.currentTimeMillis(), verified = false, manuallyEdited = true)
        save(entries.map { if (it.id == current.id) it.copy(supersededBy = updated.id) else it } + updated)
        NoteSave(updated, true)
    }

    /** An explicit permanent deletion removes the entire version chain. */
    fun remove(id: String, expected: Entry? = null): Boolean = synchronized(storeLock) {
        val existing = readEntries()
        val byId = existing.associateBy { it.id }
        var selected = byId[id] ?: return@synchronized false
        if (expected != null && (selected != expected || selected.supersededBy != null)) throw KnowledgeChanged()
        while (selected.supersededBy != null) selected = byId.getValue(selected.supersededBy!!)
        val preceding = existing.filter { it.supersededBy != null }.associateBy { it.supersededBy }
        val removed = mutableSetOf(selected.id)
        var older = preceding[selected.id]
        while (older != null) { removed.add(older.id); older = preceding[older.id] }
        val updated = existing.filterNot { it.id in removed }
        save(updated)
        true
    }

    fun search(query: String, now: Long = System.currentTimeMillis()): List<Entry> = synchronized(storeLock) {
        readEntries()
        val terms = KnowledgePolicy.recallTopic(query) ?: KnowledgePolicy.queryTerms(query)
        matchingEntries(terms, now).take(NativeCapacity.MAX_SEARCH_RESULTS)
    }

    /** One atomic read per conversation turn; expired notes never enter the brain context. */
    fun contextFor(
        query: String,
        now: Long = System.currentTimeMillis(),
        recall: KnowledgePolicy.Recall? = KnowledgePolicy.recallFor(query),
        queryTerms: List<String> = KnowledgePolicy.queryTerms(query),
        maxNotes: Int = NativeCapacity.MAX_CONTEXT_NOTES,
        preferredIds: List<String> = emptyList()
    ): List<Entry> = synchronized(storeLock) {
        val entries = readEntries().filter { it.supersededBy == null && !KnowledgePolicy.isExpired(it.expiresAt, now) }
        val terms = when (recall) {
            is KnowledgePolicy.Recall.Topic -> recall.terms
            is KnowledgePolicy.Recall.Read -> recall.terms
            KnowledgePolicy.Recall.Overview -> emptyList()
            null -> queryTerms
        }
        val matches = matchingEntries(terms, now, recall == null)
        val ranked = if (matches.isEmpty() && recall !is KnowledgePolicy.Recall.Topic && recall !is KnowledgePolicy.Recall.Read) {
            entries.sortedByDescending { it.recordedAt }
        } else matches
        // Learned associations never override an explicit full-note selection or an expired source.
        val byId = if (preferredIds.isNotEmpty()) entries.associateBy { it.id } else emptyMap()
        val learned = if (recall == null) preferredIds.take(NativeCapacity.MAX_CONTEXT_NOTES).mapNotNull(byId::get) else emptyList()
        // Preserve exact search matches. Learned associations fill the remaining context;
        // they may replace the recent-note fallback, never crowd out a direct match.
        (matches + learned + ranked).distinctBy { it.title to it.excerpt }.take(maxNotes.coerceIn(1, NativeCapacity.MAX_CONTEXT_NOTES))
    }

    private fun matchingEntries(tokens: List<String>, now: Long, conversational: Boolean = false): List<Entry> {
        if (tokens.isEmpty()) return emptyList()
        val current = snapshot ?: return emptyList()
        val index = current.searchIndex ?: KnowledgeSearchIndex(current.entries.filter { it.supersededBy == null }).also { current.searchIndex = it }
        // Expiry is evaluated on every call, including cached rankings.
        return (if (conversational) index.rankForConversation(tokens, now) else index.rank(tokens)).filterNot { KnowledgePolicy.isExpired(it.expiresAt, now) }
    }

    private fun save(entries: List<Entry>, authorize: () -> Unit = {}) {
        if (entries.size > NativeCapacity.MAX_LIBRARY_RECORDS || entries.count { it.supersededBy == null } > maxEntries) throw KnowledgeCapacityReached()
        writeEncrypted(serializeEntries(entries), entries, authorize)
    }

    private fun serializeEntries(entries: List<Entry>): ByteArray {
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
                entry.supersededBy?.let { put("supersededBy", it) }
                if (entry.manuallyEdited) put("manuallyEdited", true)
            })
        }
        val schema = if (entries.any { it.supersededBy != null || it.manuallyEdited }) 2 else 1
        val bytes = JSONObject().put("schema", schema).put("entries", array)
            .toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > KnowledgePayload.MAX_JSON_BYTES) throw KnowledgeCapacityReached()
        return bytes
    }

    data class PortableSummary(val activeNotes: Int, val archivedNotes: Int)
    fun portableSnapshot(): ByteArray = synchronized(storeLock) { serializeEntries(readEntries()) }
    fun portableFingerprint(): String = synchronized(storeLock) { fingerprint(serializeEntries(readEntries())) }
    fun inspectPortable(data: ByteArray): PortableSummary = synchronized(storeLock) {
        val entries = parseEntries(data, true)
        PortableSummary(entries.count { it.supersededBy == null }, entries.count { it.supersededBy != null })
    }
    private fun fingerprint(data: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun rescueBytes(): ByteArray? = try { rescue.openRead().use { KnowledgePayload.readBounded(it, MAX_ENCRYPTED_BYTES + 160) } }
        catch (_: java.io.FileNotFoundException) { null }
    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    /** Preserve a device-encrypted undo point before the atomic library commit. */
    fun restorePortable(data: ByteArray, expectedFingerprint: String, authorize: () -> Unit = {}) = synchronized(storeLock) {
        val parsed = parseEntries(data, true)
        val previous = readEntries()
        if (fingerprint(serializeEntries(previous)) != expectedFingerprint) throw KnowledgeChanged()
        val oldEncrypted = snapshot?.encrypted ?: cipher.encrypt(KnowledgePayload.encode(serializeEntries(previous)))
        val after = fingerprint(serializeEntries(parsed)).toByteArray(Charsets.US_ASCII)
        val journal = cipher.encrypt(byteArrayOf(78, 88, 82, 1) + after + oldEncrypted)
        val previousRescue = rescueBytes()
        authorize()
        writeAtomic(rescue, journal)
        try { save(parsed, authorize) }
        catch (failure: Exception) {
            if (previousRescue == null) rescue.delete() else writeAtomic(rescue, previousRescue)
            throw failure
        }
    }
    private fun recovery(): Pair<String, List<Entry>>? {
        val bytes = rescueBytes() ?: return null
        val decoded = cipher.decrypt(bytes)
        require(decoded.size >= 68 && decoded.copyOfRange(0, 4).contentEquals(byteArrayOf(78, 88, 82, 1)))
        val expected = String(decoded.copyOfRange(4, 68), Charsets.US_ASCII)
        require(expected.matches(Regex("[a-f0-9]{64}")))
        val old = KnowledgePayload.decode(cipher.decrypt(decoded.copyOfRange(68, decoded.size)))
        return expected to parseEntries(old)
    }
    fun canUndoRestore(): Boolean = synchronized(storeLock) {
        try { recovery()?.first == fingerprint(serializeEntries(readEntries())) } catch (_: Exception) { false }
    }
    fun undoRestore(authorize: () -> Unit = {}) = synchronized(storeLock) {
        val (expected, previous) = recovery() ?: throw KnowledgeChanged()
        if (expected != fingerprint(serializeEntries(readEntries()))) throw KnowledgeChanged()
        save(previous, authorize)
        rescue.delete()
    }

    /** History is a set of independent, finite, non-branching version chains. */
    private fun validateVersions(entries: List<Entry>) {
        require(entries.count { it.supersededBy == null } <= maxEntries) { "Invalid active note capacity" }
        require(entries.all { it.id.isNotBlank() && it.id.length <= 64 }) { "Invalid note identity" }
        val byId = entries.associateBy { it.id }
        require(byId.size == entries.size) { "Duplicate note identity" }
        val successors = entries.mapNotNull { it.supersededBy }
        require(successors.distinct().size == successors.size && successors.all { it in byId }) { "Invalid note history" }
        val resolved = mutableSetOf<String>()
        for (entry in entries) {
            val path = mutableSetOf<String>()
            var cursor: Entry? = entry
            while (cursor != null && cursor.id !in resolved) {
                require(path.add(cursor.id)) { "Cyclic note history" }
                cursor = cursor.supersededBy?.let(byId::getValue)
            }
            resolved.addAll(path)
        }
    }

    private fun writeEncrypted(plaintext: ByteArray, entries: List<Entry>, authorize: () -> Unit = {}) {
        val packed = KnowledgePayload.encode(plaintext)
        val encrypted = cipher.encrypt(packed)
        authorize()
        val stream = store.startWrite()
        try {
            stream.write(encrypted)
            store.finishWrite(stream)
            snapshot = Snapshot(encrypted, entries.toList(), plaintext.size, packed.size)
        } catch (failure: Exception) {
            store.failWrite(stream)
            throw failure
        }
    }
}
