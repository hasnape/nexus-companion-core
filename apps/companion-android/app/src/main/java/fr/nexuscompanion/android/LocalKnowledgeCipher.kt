package fr.nexuscompanion.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authenticated encryption for a single companion's private knowledge library.
 * The AES key never leaves Android Keystore. This does NOT provide portable
 * backup: encrypted export requires a separate user-controlled key design.
 */
internal interface KnowledgeCipher {
    fun isEncrypted(bytes: ByteArray): Boolean
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

internal class LocalKnowledgeCipher(private val companionId: String) : KnowledgeCipher {
    private val alias = "nexus.knowledge.v1.$companionId"
    private val magic = byteArrayOf(0x4e, 0x58, 0x4b, 0x01) // NXK v1
    private val ivLength = 12

    override fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(alias.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        check(iv.size == ivLength)
        return magic + iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(isEncrypted(blob) && blob.size >= magic.size + ivLength + 16) {
            "Invalid encrypted knowledge envelope"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = blob.copyOfRange(magic.size, magic.size + ivLength)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(alias.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(blob.copyOfRange(magic.size + ivLength, blob.size))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
