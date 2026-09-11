package com.moneycounter.auth

/**
 * Repository that abstracts the authentication actions.
 * Implementations should be pure Kotlin without DI frameworks.
 */
interface AuthRepository {

    /**
     * Resolves the currently signed‑in user, or **null** when no session is
     * active (or the stored session is no longer valid).
     */
    suspend fun currentUser(): AuthUser?

    /**
     * Signs the user in with email + password.
     *
     * When the account does not exist yet it is created on the fly
     * (auto-registration); the access flow then puts it under
     * [com.moneycounter.access.AccessStatus.PENDING] until an admin approves it.
     *
     * @return a [Result] that contains an [AuthUser] on success or an error on failure.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>

    /**
     * Signs the current user out.
     */
    suspend fun signOut()
}