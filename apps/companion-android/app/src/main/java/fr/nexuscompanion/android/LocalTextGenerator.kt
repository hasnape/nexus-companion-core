package fr.nexuscompanion.android

import android.content.Context
import android.os.Build
import android.os.PowerManager
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** The core authorizes the public question or a small set of current note excerpts. */
internal data class LocalDraftRequest(val question: String, val topic: String, val evidence: List<LocalDraftEvidence> = emptyList()) {
    init {
        require(question.isNotBlank() && question.length <= 600 && GroundedNotes.safeText(question) && topic.isNotBlank() && topic.length <= 180)
        require(evidence.size <= 2 && evidence.map { it.key }.distinct().size == evidence.size && evidence.sumOf { it.sentences.sumOf(String::length) } <= 840)
    }
}

internal interface LocalDraftGenerator : AutoCloseable {
    val usesNotes: Boolean get() = false
    fun generate(request: LocalDraftRequest): String?
    fun cancel()
}

internal object LocalModelSpec {
    const val NAME = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
    const val LABEL = "Liquid AI LFM2.5 · 1,2 milliard · Q4_K_M"
    const val SHA256 = "b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5"
    const val BYTES = 730895168L
    const val REVISION = "8ed288026e23958ad9dfa92d53ed773a8eee7125"
    const val URL = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/$REVISION/$NAME"
    const val MIN_AVAILABLE_RAM = 1536L * 1024 * 1024
}

/** A known model only. Interrupted or corrupt imports never replace an installed model. */
internal class LocalModelStore(context: Context, private val availableBytes: () -> Long = { android.os.StatFs(context.noBackupFilesDir.absolutePath).availableBytes }) {
    private val directory = File(context.noBackupFilesDir, "local-language-model").apply { mkdirs() }
    val file = File(directory, "model.gguf")
    private val stamp = File(directory, "verified.json")
    private val preferences = context.getSharedPreferences("nexus_local_llm", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
    var notesEnabled: Boolean
        get() = preferences.getBoolean("notes_enabled", false)
        set(value) { preferences.edit().putBoolean("notes_enabled", value).apply() }

    @Synchronized fun installed(): Boolean = try {
        val metadata = JSONObject(stamp.readText())
        file.isFile && file.length() == LocalModelSpec.BYTES &&
            metadata.getString("sha256") == LocalModelSpec.SHA256 &&
            metadata.getLong("bytes") == file.length() && metadata.getLong("modified") == file.lastModified()
    } catch (_: Exception) { false }

    @Synchronized fun import(input: InputStream, cancelled: () -> Boolean = { false }) = input.use { source ->
        check(!cancelled()) { "Importation annulée." }
        require(availableBytes() >= LocalModelSpec.BYTES + 64L * 1024 * 1024) { "Stockage insuffisant pour importer le modèle." }
        val temporary = File.createTempFile("import-", ".part", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    check(!cancelled() && !Thread.currentThread().isInterrupted) { "Importation annulée." }
                    val count = source.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= LocalModelSpec.BYTES) { "Modèle inattendu : taille incorrecte." }
                    digest.update(buffer, 0, count); output.write(buffer, 0, count)
                }
            }
            require(size == LocalModelSpec.BYTES && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == LocalModelSpec.SHA256) {
                "Modèle refusé : empreinte SHA-256 incorrecte."
            }
            check(!cancelled()) { "Importation annulée." }
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            stamp.writeText(JSONObject().put("sha256", LocalModelSpec.SHA256).put("bytes", size).put("modified", file.lastModified()).toString())
        } finally { temporary.delete() }
    }
    @Synchronized fun delete() { enabled = false; notesEnabled = false; stamp.delete(); file.delete() }
}

internal object NativeLocalLlm {
    val available: Boolean = try { System.loadLibrary("nexus_llm"); true } catch (_: LinkageError) { false }
    external fun create(): Long
    external fun generate(handle: Long, model: String, question: String, threads: Int, timeoutMs: Int): String
    external fun generateGrounded(handle: Long, model: String, question: String, evidence: String, threads: Int, timeoutMs: Int): String
    external fun cancel(handle: Long)
    external fun release(handle: Long)
}

internal object LocalDraftPolicy {
    /** Reasons are checked in the same order by the UI and the generator. A check is not a RAM reservation. */
    fun blockReason(snapshot: DeviceResources.Snapshot): String? = when {
        snapshot.abis.none { it == "arm64-v8a" || it == "x86_64" } ->
            "Architecture 64 bits non disponible."
        snapshot.lowMemory ->
            "Android signale une pression mémoire : génération suspendue."
        snapshot.availableRam < LocalModelSpec.MIN_AVAILABLE_RAM ->
            "RAM disponible insuffisante : ${snapshot.availableRam / (1024 * 1024)} Mio ; minimum ${LocalModelSpec.MIN_AVAILABLE_RAM / (1024 * 1024)} Mio pour lancer ce modèle."
        snapshot.powerSave ->
            "Mode économie d’énergie actif : génération suspendue."
        snapshot.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ->
            "Appareil trop chaud : génération suspendue."
        !snapshot.charging && (snapshot.batteryPercent == null || snapshot.batteryPercent <= 15) ->
            "Batterie trop faible ou niveau inconnu : brancher le téléphone."
        else -> null
    }

    fun permitted(snapshot: DeviceResources.Snapshot): Boolean = blockReason(snapshot) == null

    fun clean(text: String): String? {
        val cleaned = text.trim()
        if (cleaned.length !in 8..1800 || cleaned.contains(Regex("(?is)<(?:think|tool|\\|)|</(?:think|tool)|\\[/?INST\\]|\\{\\s*\"(?:tool|command)\""))) return null
        if (cleaned.any { it.code < 32 && it !in "\n\r\t" }) return null
        // Finish at a sentence boundary when a model hits its token budget.
        return if (cleaned.last() in ".!?…»\"'") cleaned else {
            val end = cleaned.lastIndexOfAny(charArrayOf('.', '!', '?'))
            if (end >= 20) cleaned.substring(0, end + 1) else null
        }
    }
}

internal class LocalTextGenerator(
    private val context: Context,
    private val store: LocalModelStore = LocalModelStore(context),
    private val resources: () -> DeviceResources.Snapshot = { DeviceResources.inspect(context) }
) : LocalDraftGenerator {
    override val usesNotes: Boolean get() = store.enabled && store.notesEnabled
    private val epoch = AtomicLong()
    private val active = AtomicLong()
    @Volatile private var closed = false
    override fun generate(request: LocalDraftRequest): String? {
        val token = epoch.get()
        if (closed || !android.os.Process.is64Bit() || !store.enabled || (request.evidence.isNotEmpty() && !store.notesEnabled) || !store.installed() || !LocalDraftPolicy.permitted(resources()) || !NativeLocalLlm.available) return null
        val handle = try { NativeLocalLlm.create() } catch (_: LinkageError) { return null }
        if (handle == 0L) return null
        active.set(handle)
        return try {
            if (closed || epoch.get() != token) return null
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val raw = if (request.evidence.isEmpty()) NativeLocalLlm.generate(handle, store.file.absolutePath, request.question, threads, 20_000)
                else NativeLocalLlm.generateGrounded(handle, store.file.absolutePath, request.question, GroundedNotes.prompt(request.evidence), threads, 20_000)
            if (closed || epoch.get() != token || !store.enabled || (request.evidence.isNotEmpty() && !store.notesEnabled)) null
                else if (request.evidence.isEmpty()) LocalDraftPolicy.clean(raw) else raw.takeIf { GroundedNotes.validate(it, request.evidence) != null }
        } catch (_: Exception) { null } catch (_: LinkageError) { null }
        finally { active.compareAndSet(handle, 0); NativeLocalLlm.release(handle) }
    }
    override fun cancel() {
        epoch.incrementAndGet()
        val handle = active.get()
        if (handle != 0L && NativeLocalLlm.available) NativeLocalLlm.cancel(handle)
    }
    override fun close() { closed = true; cancel() }
}
