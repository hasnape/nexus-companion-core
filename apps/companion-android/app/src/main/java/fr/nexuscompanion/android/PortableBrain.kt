package fr.nexuscompanion.android

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable content encryption. Android Keystore keys and the user PIN are never exported. */
internal object PortableBrain {
    const val ITERATIONS = 600_000
    private const val HEADER_BYTES = 52
    const val MAX_ARCHIVE_BYTES = KnowledgePayload.MAX_JSON_BYTES + HEADER_BYTES + 16
    private val magic = byteArrayOf(78, 88, 66, 1) // NXB v1
    private fun derive(secret: CharArray, salt: ByteArray): ByteArray {
        require(secret.size in 12..128 && secret.any { !it.isWhitespace() }) { "Use a secret phrase of 12 to 128 characters" }
        val spec = PBEKeySpec(secret, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
    }
    fun encrypt(json: ByteArray, secret: CharArray): ByteArray {
        require(json.size <= KnowledgePayload.MAX_JSON_BYTES)
        val random = SecureRandom()
        val salt = ByteArray(32).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(magic).putInt(ITERATIONS).put(salt).put(iv).array()
        val key = derive(secret, salt)
        val packed = KnowledgePayload.encode(json).copyOf()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            return header + cipher.doFinal(packed)
        } finally { key.fill(0); packed.fill(0) }
    }
    fun decrypt(archive: ByteArray, secret: CharArray): ByteArray {
        require(archive.size in HEADER_BYTES + 16..MAX_ARCHIVE_BYTES) { "Invalid backup size" }
        val header = archive.copyOfRange(0, HEADER_BYTES)
        val input = ByteBuffer.wrap(header)
        require(ByteArray(4).also(input::get).contentEquals(magic) && input.int == ITERATIONS) { "Unsupported backup format" }
        val salt = ByteArray(32).also(input::get)
        val iv = ByteArray(12).also(input::get)
        val key = derive(secret, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            val packed = cipher.doFinal(archive, HEADER_BYTES, archive.size - HEADER_BYTES)
            return try { KnowledgePayload.decode(packed).copyOf() } finally { packed.fill(0) }
        } finally { key.fill(0) }
    }
}
