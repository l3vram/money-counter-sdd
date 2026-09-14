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

    /**
     * Signing in NEVER creates an account. It used to: any 401 was read as "user not found"
     * and the account was registered on the spot. That produced two bugs — a wrong password
     * became "la cuenta ya existe" (the create failed instead of the session), and an unknown
     * email became an account with no `signups` row, so with no role and no business, which
     * the panel cannot approve. Registration has its own screen, which collects those.
     */
    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        return try {
            account.createEmailPasswordSession(email, password)
            val user = account.get()
            Result.success(user.toAuthUser())
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail failed", e)
            Result.failure(Exception(mapAuthError(e)))
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            account.updatePassword(password = newPassword, oldPassword = currentPassword)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "changePassword failed", e)
            Result.failure(Exception(mapAuthError(e)))
        }
    }

    override suspend fun signOut() {
        runCatching { account.deleteSession("current") }
    }

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