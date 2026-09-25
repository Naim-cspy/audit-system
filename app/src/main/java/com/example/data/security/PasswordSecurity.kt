package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic password hashing and verification using PBKDF2 with HMAC-SHA256.
 * Enforces per-user cryptographic salts, multi-iteration key derivation, and
 * constant-time digest comparison to prevent timing side-channel attacks.
 * Replaces legacy unsalted/weak hashing and eliminates any plaintext comparisons.
 */
object PasswordSecurity {
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH = 256 // in bits
    private const val SALT_LENGTH = 16 // in bytes

    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt.toHex()
    }

    fun hashPassword(password: String, saltHex: String): String {
        val saltBytes = saltHex.hexToByteArray()
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        return hashBytes.toHex()
    }

    fun verifyPassword(password: String, storedHashHex: String, saltHex: String): Boolean {
        if (saltHex.isBlank() || storedHashHex.isBlank() || password.isBlank()) return false
        val computedHash = hashPassword(password, saltHex)
        return MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            storedHashHex.toByteArray(Charsets.UTF_8)
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) +
                    Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
