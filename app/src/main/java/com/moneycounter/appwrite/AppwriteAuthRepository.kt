package com.moneycounter.appwrite

import android.util.Log
import com.moneycounter.auth.AuthRepository
import com.moneycounter.auth.AuthUser
import com.moneycounter.auth.mapAuthError
import io.appwrite.ID
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.User
import io.appwrite.services.Account
import java.io.IOException

/**
 * Appwrite-backed authentication using email + password sessions.
 *
 * Auto-registration: when the email has no account yet the first sign-in
 * creates it (createEmailPasswordSession -> 401 "user_not_found" -> create
 * account -> retry the session).
 */
class AppwriteAuthRepository : AuthRepository {

    private val account: Account = Account(Appwrite.client)

    override suspend fun currentUser(): AuthUser? {
        return try {
            account.get().toAuthUser()
        } catch (e: AppwriteException) {
            if (e.code == 401) {
                // Stored session expired or was deleted: treat as signed out.
                null
            } else {
                throw e
            }
        } catch (e: IOException) {
            throw e
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        return try {
            ensureSession(email, password)
            val user = account.get()
            Result.success(user.toAuthUser())
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail failed", e)
            Result.failure(Exception(mapAuthError(e)))
        }
    }

    override suspend fun signOut() {
        runCatching { account.deleteSession("current") }
    }

    private suspend fun ensureSession(email: String, password: String) {
        try {
            account.createEmailPasswordSession(email, password)
        } catch (e: AppwriteException) {
            if (isUserNotFound(e)) {
                // First sign-in ever: register the account, then open the session.
                account.create(
                    userId = ID.unique(),
                    email = email,
                    password = password,
                    name = email.substringBefore('@')
                )
                account.createEmailPasswordSession(email, password)
            } else {
                throw e
            }
        }
    }

    private fun isUserNotFound(e: AppwriteException): Boolean =
        e.type?.contains("user_not_found") == true || e.code == 401

    private fun User<*>.toAuthUser(): AuthUser = AuthUser(
        uid = id,
        email = email,
        displayName = name,
        photoUrl = null
    )

    private companion object {
        const val TAG = "MoneyCounterAuth"
    }
}