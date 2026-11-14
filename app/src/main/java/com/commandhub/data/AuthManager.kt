package com.commandhub.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Suppress("DEPRECATION")
class AuthManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = try {
        EncryptedSharedPreferences.create(
            context,
            "secure_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("fallback_auth_prefs", Context.MODE_PRIVATE)
    }

    private val activeSessions = ConcurrentHashMap<String, Long>() // sessionId -> expirationTimestamp
    private val loginAttempts = ConcurrentHashMap<String, Pair<Int, Long>>() // ip -> (attempts, lockoutUntil)

    companion object {
        private const val TOKEN_KEY = "auth_bearer_token"
        private const val SESSION_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 60 * 1000L // 1 minute
    }

    init {
        if (!prefs.contains(TOKEN_KEY)) {
            rotateToken()
        }
    }

    @Synchronized
    fun getToken(): String {
        var token = prefs.getString(TOKEN_KEY, null)
        if (token.isNullOrEmpty()) {
            token = generateSecureToken()
            prefs.edit { putString(TOKEN_KEY, token) }
        }
        return token
    }

    @Synchronized
    fun rotateToken(): String {
        val newToken = generateSecureToken()
        prefs.edit { putString(TOKEN_KEY, newToken) }
        activeSessions.clear()
        return newToken
    }

    fun validateBearerToken(providedToken: String?): Boolean {
        if (providedToken.isNullOrEmpty()) return false
        val storedToken = getToken()
        return constantTimeEquals(storedToken, providedToken)
    }

    fun createSession(): String {
        val sessionBytes = ByteArray(32)
        SecureRandom().nextBytes(sessionBytes)
        val sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(sessionBytes)
        activeSessions[sessionId] = System.currentTimeMillis() + SESSION_DURATION_MS
        return sessionId
    }

    fun validateSession(sessionId: String?): Boolean {
        if (sessionId.isNullOrEmpty()) return false
        val expiration = activeSessions[sessionId] ?: return false
        if (System.currentTimeMillis() > expiration) {
            activeSessions.remove(sessionId)
            return false
        }
        return true
    }

    fun revokeSession(sessionId: String?) {
        if (!sessionId.isNullOrEmpty()) {
            activeSessions.remove(sessionId)
        }
    }

    fun checkRateLimit(clientIp: String): Boolean {
        val now = System.currentTimeMillis()
        val record = loginAttempts[clientIp]
        if (record != null) {
            val (attempts, lockoutUntil) = record
            if (now < lockoutUntil) {
                return false // Locked out
            }
            if (attempts >= MAX_ATTEMPTS) {
                loginAttempts[clientIp] = Pair(1, now + LOCKOUT_DURATION_MS)
                return false
            }
        }
        return true
    }

    fun recordFailedAttempt(clientIp: String) {
        val now = System.currentTimeMillis()
        val record = loginAttempts[clientIp]
        if (record != null) {
            val (attempts, lockoutUntil) = record
            if (now >= lockoutUntil) {
                loginAttempts[clientIp] = Pair(1, 0L)
            } else {
                val newAttempts = attempts + 1
                val newLockout = if (newAttempts >= MAX_ATTEMPTS) now + LOCKOUT_DURATION_MS else 0L
                loginAttempts[clientIp] = Pair(newAttempts, newLockout)
            }
        } else {
            loginAttempts[clientIp] = Pair(1, 0L)
        }
    }

    fun clearFailedAttempts(clientIp: String) {
        loginAttempts.remove(clientIp)
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }
}
