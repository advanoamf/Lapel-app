package com.lapel.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Single-owner login. The deployment stores only [PasswordHash.of] the password (from the GitHub
 * secret), never the password itself.
 * A successful login returns a signed token valid for [tokenLifetime]. The signing key is created
 * once at random and kept in the table, so it survives redeploys and never appears in code.
 */
class AuthService(
    private val passwordHash: String,
    store: RecordStore,
    private val clock: Clock,
    private val tokenLifetime: Duration = Duration.ofDays(30),
) {
    private val key: ByteArray by lazy {
        val fresh = ByteArray(32).also(SecureRandom()::nextBytes)
        Base64.getDecoder().decode(store.putMetaIfAbsent("token-key", Base64.getEncoder().encodeToString(fresh)))
    }

    fun login(attempt: String): LoginResponse? {
        if (passwordHash.length != 64) return null
        val ok = MessageDigest.isEqual(PasswordHash.of(attempt).toByteArray(), passwordHash.lowercase().toByteArray())
        if (!ok) return null
        val expires = clock.millis() + tokenLifetime.toMillis()
        return LoginResponse(sign(expires.toString()), expires)
    }

    fun isValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val parts = token.split('.')
        if (parts.size != 2) return false
        val payload = runCatching { String(B64D.decode(parts[0])) }.getOrNull() ?: return false
        val expected = sign(payload)
        if (!MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())) return false
        return (payload.toLongOrNull() ?: return false) > clock.millis()
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        return B64E.encodeToString(payload.toByteArray()) + "." + B64E.encodeToString(mac.doFinal(payload.toByteArray()))
    }

    companion object {
        private val B64E = Base64.getUrlEncoder().withoutPadding()
        private val B64D = Base64.getUrlDecoder()
    }
}
