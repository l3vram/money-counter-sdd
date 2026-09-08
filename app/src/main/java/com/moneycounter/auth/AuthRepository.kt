package com.moneycounter.auth

import android.app.Activity

/**
 * Repository that abstracts the authentication actions.
 * Implementations should be pure Kotlin without DI frameworks.
 */
interface AuthRepository {

    /**
     * Returns the currently signed‑in user, or **null** if no user is authenticated.
     */
    fun currentUser(): AuthUser?

    /**
     * Signs the user in with Google using the **Credential Manager**.
     *
     * @param activity the calling `Activity` – required by the Credential Manager UI.
     * @return a [Result] that contains an [AuthUser] on success or an error on failure.
     */
    suspend fun signInWithGoogle(activity: Activity): Result<AuthUser>

    /**
     * Signs the current user out from Firebase.
     */
    fun signOut()
}