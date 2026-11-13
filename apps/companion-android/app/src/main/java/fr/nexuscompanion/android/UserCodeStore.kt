package fr.nexuscompanion.android

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** This session is process-local and is revoked on leaving the app. Never stored in an Intent. */
internal object UserAccessSession {
    const val LIFETIME_MS = 300_000L
    @Volatile private var expires = 0L
    fun allowed(now: Long = SystemClock.elapsedRealtime()): Boolean = expires > now
    fun unlock(now: Long = SystemClock.elapsedRealtime()) { expires = now + LIFETIME_MS }
    fun lock() { expires = 0 }
}

/** Separate from the creator identity. No default PIN; only a salted hash is persisted, encrypted. */
internal class UserCodeStore(context: Context,
    private val cipher: KnowledgeCipher = LocalKnowledgeCipher("user-code"),
    private val now: () -> Long = System::currentTimeMillis) {
    companion object { private val lock = Any(); const val ITERATIONS = 600_000 }
    enum class Result { ACCEPTED, WRONG, WAIT, DEVICE_RECOVERY_REQUIRED }
    private val file = AtomicFile(File(context.filesDir, "user-code.v1"))
    fun configured(): Boolean = synchronized(lock) { read() != null }

    private fun read(): JSONObject? {
        val bytes = try { file.openRead().use { KnowledgePayload.readBounded(it, 8192) } }
            catch (_: java.io.FileNotFoundException) { return null }
        val value = JSONObject(String(cipher.decrypt(bytes)))
        require(value.getInt("schema") == 1 && value.getInt("iterations") == ITERATIONS)
        require(Base64.decode(value.getString("salt"), Base64.NO_WRAP).size == 32)
        require(Base64.decode(value.getString("hash"), Base64.NO_WRAP).size == 32)
        require(value.getInt("failures") in 0..10 && value.getLong("blockedUntil") >= 0)
        return value
    }
    private fun hash(code: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
    }
    private fun write(value: JSONObject) {
        val bytes = cipher.encrypt(value.toString().toByteArray())
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    /** Setup/change is only called by the non-exported secure Activity after its authorization flow. */
    fun set(code: CharArray) = synchronized(lock) {
        require(code.size in 6..12 && code.all { it in '0'..'9' })
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        write(JSONObject().put("schema", 1).put("iterations", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("hash", Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
            .put("failures", 0).put("blockedUntil", 0L))
    }
    fun verify(code: CharArray): Result = synchronized(lock) {
        val value = read() ?: return@synchronized Result.WRONG
        if (value.getInt("failures") >= 10) return@synchronized Result.DEVICE_RECOVERY_REQUIRED
        if (now() < value.getLong("blockedUntil")) return@synchronized Result.WAIT
        if (code.size !in 6..12 || code.any { it !in '0'..'9' }) return@synchronized Result.WRONG
        val expected = Base64.decode(value.getString("hash"), Base64.NO_WRAP)
        val actual = hash(code, Base64.decode(value.getString("salt"), Base64.NO_WRAP))
        val accepted = MessageDigest.isEqual(expected, actual)
        actual.fill(0); expected.fill(0)
        if (accepted) { value.put("failures", 0).put("blockedUntil", 0L); write(value); Result.ACCEPTED }
        else {
            val failures = value.getInt("failures") + 1
            value.put("failures", failures).put("blockedUntil", if (failures >= 5) now() + 60_000L else 0L)
            write(value)
            if (failures >= 10) Result.DEVICE_RECOVERY_REQUIRED else if (failures >= 5) Result.WAIT else Result.WRONG
        }
    }
}
