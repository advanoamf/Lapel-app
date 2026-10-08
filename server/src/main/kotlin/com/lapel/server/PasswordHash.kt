package com.lapel.server

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-SHA256 of the login password, as lowercase hex. The deploy workflow computes the same
 * value with Python's hashlib.pbkdf2_hmac, so only the hash ever reaches AWS.
 */
object PasswordHash {
    const val SALT = "lapel-login-v1"
    const val ITERATIONS = 200_000

    fun of(password: String): String {
        val spec = PBEKeySpec(password.toCharArray(), SALT.toByteArray(), ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
