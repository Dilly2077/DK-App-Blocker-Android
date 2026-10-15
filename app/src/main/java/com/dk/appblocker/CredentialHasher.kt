package com.dk.appblocker

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Versioned, salted credentials. Legacy SHA-256 is accepted only for migration. */
object CredentialHasher {
    private const val ITERATIONS = 600_000
    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        return "pbkdf2-sha256\$$ITERATIONS\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(derive(password, salt, ITERATIONS))}"
    }

    fun verify(password: String, stored: String): Boolean = runCatching {
        if (stored.matches(Regex("[0-9a-fA-F]{64}"))) {
            val expected = stored.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)))
        } else {
            val parts = stored.split('$')
            if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
            val rounds = parts[1].toInt()
            if (rounds !in 100_000..ITERATIONS) return false
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            if (salt.size != 16 || expected.size != 32) return false
            MessageDigest.isEqual(expected, derive(password, salt, rounds))
        }
    }.getOrDefault(false)

    fun needsMigration(stored: String) = !stored.startsWith("pbkdf2-sha256\$")

    private fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
