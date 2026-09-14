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
     * Signs the user in with email + password. **Never creates an account**: an unknown email
     * or a wrong password both fail, and registration goes through [signUpWithEmail].
     *
     * It used to auto-register, which caused two bugs: a wrong password (also a 401) was read
     * as "user does not exist" and reported as "la cuenta ya existe", and an unknown email
     * silently produced an account with no `signups` row — no role, no business, impossible to
     * approve from the panel.
     *
     * @return a [Result] that contains an [AuthUser] on success or an error on failure.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>

    /**
     * Registers a new account and opens its session. This is the only path that creates
     * accounts; the access flow then leaves it under
     * [com.moneycounter.access.AccessStatus.PENDING] until the SUPERUSER approves it.
     *
     * @return a [Result] that contains the new [AuthUser] on success or an error on failure.
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser>

    /**
     * Updates the signed-in user's password. [currentPassword] is validated
     * against the account before [newPassword] is applied.
     *
     * @return a [Result] that succeeds when the password was updated or an error on failure.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /**
     * Signs the current user out.
     */
    suspend fun signOut()
}