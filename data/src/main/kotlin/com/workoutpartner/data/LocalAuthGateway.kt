package com.workoutpartner.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Dev/offline [AuthGateway] fallback (`docs/auth-roadmap.md` Phase 1): used
 * whenever constructing [FirebaseAuthGateway] fails (no `google-services.json`
 * provisioned yet), so Sign Up/Sign In work immediately without Firebase —
 * see [com.workoutpartner.app.di.AppContainer]'s `authGateway` wiring.
 *
 * Credentials live in `SharedPreferences` on-device only. This is a dev/
 * offline fallback holding no real user data, but still hashes passwords
 * (salted SHA-256) rather than storing them in the clear — the point isn't
 * to invite a false sense of security, there's just no reason to store
 * secrets plaintext either.
 */
class LocalAuthGateway(context: Context) : AuthGateway {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val currentUserId = MutableStateFlow(prefs.getString(KEY_CURRENT_USER_ID, null))

    override suspend fun signUpWithEmail(email: String, password: String): String {
        val normalizedEmail = normalize(email)
        require(EMAIL_REGEX.matches(normalizedEmail)) { "Invalid email format" }
        require(password.length >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters" }
        check(prefs.getString(saltKey(normalizedEmail), null) == null) { "An account already exists with this email" }

        val userId = "local_usr_${UUID.randomUUID()}"
        val salt = newSalt()
        prefs.edit()
            .putString(saltKey(normalizedEmail), salt)
            .putString(hashKey(normalizedEmail), hash(password, salt))
            .putString(userIdKey(normalizedEmail), userId)
            .putString(KEY_CURRENT_USER_ID, userId)
            .apply()
        currentUserId.value = userId
        return userId
    }

    override suspend fun signInWithEmail(email: String, password: String): String {
        val normalizedEmail = normalize(email)
        val salt = prefs.getString(saltKey(normalizedEmail), null)
        val storedHash = prefs.getString(hashKey(normalizedEmail), null)
        val userId = prefs.getString(userIdKey(normalizedEmail), null)
        check(salt != null && storedHash != null && userId != null) { "No such account" }
        check(hash(password, salt) == storedHash) { "Wrong password" }

        prefs.edit().putString(KEY_CURRENT_USER_ID, userId).apply()
        currentUserId.value = userId
        return userId
    }

    override suspend fun signOut() {
        prefs.edit().remove(KEY_CURRENT_USER_ID).apply()
        currentUserId.value = null
    }

    private fun normalize(email: String) = email.trim().lowercase()

    private fun saltKey(email: String) = "salt:$email"
    private fun hashKey(email: String) = "hash:$email"
    private fun userIdKey(email: String) = "uid:$email"

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(salt, Base64.NO_WRAP))
        return Base64.encodeToString(digest.digest(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private companion object {
        const val PREFS_NAME = "local_auth"
        const val KEY_CURRENT_USER_ID = "current_user_id"
        const val MIN_PASSWORD_LENGTH = 6
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
