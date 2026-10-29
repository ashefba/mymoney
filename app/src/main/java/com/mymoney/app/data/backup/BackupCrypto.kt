package com.mymoney.app.data.backup

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("The password is incorrect or the file is damaged.")
class InvalidBackupException(message: String) : Exception(message)

/**
 * Password-based encryption for backup files.
 *
 * Format: MAGIC | iterations (int32) | salt (16 bytes) | iv (12 bytes) | AES-256-GCM ciphertext of gzip(payload).
 * The key is derived with PBKDF2-HMAC-SHA256.
 */
object BackupCrypto {
    private val MAGIC = "MYMONEY-ENC1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 150_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val ct = cipher.doFinal(gzip(plain))
        return ByteBuffer.allocate(MAGIC.size + 4 + SALT_LEN + IV_LEN + ct.size)
            .put(MAGIC).putInt(ITERATIONS).put(salt).put(iv).put(ct)
            .array()
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(data)) throw InvalidBackupException("This is not an encrypted MyMoney backup.")
        val buf = ByteBuffer.wrap(data)
        buf.position(MAGIC.size)
        val iterations = buf.int
        if (iterations !in 10_000..5_000_000) throw InvalidBackupException("The backup file is damaged.")
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val compressed = try {
            cipher.doFinal(ct)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return gunzip(compressed)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}
