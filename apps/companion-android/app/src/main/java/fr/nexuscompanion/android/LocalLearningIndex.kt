package fr.nexuscompanion.android

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Rebuildable derived index. It cannot change original notes, verification, or personality. */
internal class LocalLearningIndex(context: Context, companionId: String = "nexus-default",
    private val cipher: KnowledgeCipher = LocalKnowledgeCipher("learning-$companionId")) {
    companion object { private val lock = Any(); private const val SCHEMA = 1 }
    private val safeId = requireNotNull(companionId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) })
    private val file = AtomicFile(File(context.filesDir, "learning-$safeId.json"))
    private data class Learned(val fingerprint: String, val terms: List<String>, val exercises: Int, val passed: Int, val concepts: Int)
    data class Statistics(val consolidated: Int, val remaining: Int, val exercises: Int, val passed: Int, val concepts: Int)
    private var loaded: ByteArray? = null
    private var records = linkedMapOf<String, Learned>()
    private var dirty = false
    private val fingerprints = object : LinkedHashMap<LocalKnowledgeLibrary.Entry, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalKnowledgeLibrary.Entry, String>?) = size > NativeCapacity.MAX_LIBRARY_ENTRIES
    }

    private fun fingerprint(entry: LocalKnowledgeLibrary.Entry): String = fingerprints[entry] ?: MessageDigest.getInstance("SHA-256")
        .digest(JSONObject().put("id", entry.id).put("title", entry.title).put("text", entry.excerpt)
            .put("source", entry.source).put("time", entry.recordedAt).put("edited", entry.manuallyEdited)
            .put("expires", entry.expiresAt ?: JSONObject.NULL).toString().toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }.also { fingerprints[entry] = it }

    private fun read() {
        val bytes = try { file.openRead().use { KnowledgePayload.readBounded(it, 16 * 1024 * 1024) } }
            catch (_: java.io.FileNotFoundException) { loaded = null; records.clear(); dirty = false; return }
        if (loaded?.contentEquals(bytes) == true) return
        // This cache is disposable; a damaged cache must not make the primary library unusable.
        dirty = false
        records = try {
            val root = JSONObject(String(KnowledgePayload.decode(cipher.decrypt(bytes))))
            require(root.getInt("schema") == SCHEMA)
            val array = root.getJSONArray("records"); require(array.length() <= NativeCapacity.MAX_LIBRARY_ENTRIES)
            linkedMapOf<String, Learned>().apply {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i); val terms = item.getJSONArray("terms")
                    require(terms.length() <= 64)
                    val keys = (0 until terms.length()).map { terms.getString(it).also { key -> require(key.length in 1..180) } }
                    val exercises = item.getInt("exercises"); val passed = item.getInt("passed")
                    require(exercises in 0..6 && passed in 0..exercises)
                    put(item.getString("id"), Learned(item.getString("fingerprint"), keys, exercises, passed, item.getInt("concepts")))
                }
            }
        } catch (_: Exception) { dirty = true; linkedMapOf() }
        loaded = bytes
    }

    private fun live(entries: List<LocalKnowledgeLibrary.Entry>, now: Long) = entries.filter {
        it.supersededBy == null && !KnowledgePolicy.isExpired(it.expiresAt, now)
    }
    private fun reconcile(entries: List<LocalKnowledgeLibrary.Entry>) {
        val current = entries.associateBy { it.id }
        if (records.entries.removeAll { (id, learned) -> current[id]?.let { fingerprint(it) != learned.fingerprint } ?: true }) dirty = true
        fingerprints.keys.removeAll { current[it.id] != it }
    }

    fun statistics(entries: List<LocalKnowledgeLibrary.Entry>, now: Long = System.currentTimeMillis()): Statistics = synchronized(lock) {
        read(); val active = live(entries, now); reconcile(active)
        Statistics(records.size, active.size - records.size, records.values.sumOf { it.exercises },
            records.values.sumOf { it.passed }, records.values.sumOf { it.concepts })
    }

    /** Revalidate provenance on each lookup: deleted, edited, expired sources cannot be recalled. */
    fun preferredIds(terms: List<String>, entries: List<LocalKnowledgeLibrary.Entry>, now: Long): List<String> = synchronized(lock) {
        read(); reconcile(live(entries, now))
        val wanted = terms.map(KnowledgePolicy::noteKey).toSet()
        records.entries.map { it.key to it.value.terms.count { term -> KnowledgePolicy.noteKey(term) in wanted } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(NativeCapacity.MAX_CONTEXT_NOTES).map { it.first }
    }

    fun consolidate(entries: List<LocalKnowledgeLibrary.Entry>, budget: DeviceResources.Budget,
        learn: (LocalKnowledgeLibrary.Entry) -> JSONObject, now: Long = System.currentTimeMillis()): Statistics = synchronized(lock) {
        read(); val active = live(entries, now); reconcile(active)
        if (!budget.learningAllowed) return@synchronized statistics(active, now)
        val deadline = System.nanoTime() + 12_000_000_000L
        for (note in active.filter { it.id !in records }.take(budget.learningBatch)) {
            if (Thread.currentThread().isInterrupted || System.nanoTime() >= deadline) break
            val value = learn(note); val terms = value.getJSONArray("terms")
            require(terms.length() <= 64)
            val keys = (0 until terms.length()).map { terms.getString(it).also { key -> require(key.length in 1..180) } }
            val exercises = value.getInt("exercises"); val passed = value.getInt("successfulChecks")
            require(exercises in 0..6 && passed in 0..exercises)
            records[note.id] = Learned(fingerprint(note), keys, exercises, passed, value.getInt("concepts"))
            dirty = true
        }
        if (!dirty) return@synchronized statistics(active, now)
        val array = JSONArray()
        records.forEach { (id, item) -> array.put(JSONObject().put("id", id).put("fingerprint", item.fingerprint)
            .put("terms", JSONArray(item.terms)).put("exercises", item.exercises).put("passed", item.passed).put("concepts", item.concepts)) }
        val bytes = cipher.encrypt(KnowledgePayload.encode(JSONObject().put("schema", SCHEMA).put("records", array).toString().toByteArray()))
        require(bytes.size <= 16 * 1024 * 1024)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream); loaded = bytes; dirty = false }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
        statistics(active, now)
    }
}
