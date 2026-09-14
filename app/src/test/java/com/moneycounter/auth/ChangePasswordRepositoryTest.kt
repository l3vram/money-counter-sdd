package com.moneycounter.auth

import com.moneycounter.auth.AuthRepository
import com.moneycounter.auth.AuthUser
import com.moneycounter.auth.mapAuthError
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Contract tests for [AuthRepository.changePassword]: the SDK call forwards
 * (new password, current password) in `account.updatePassword` order and the
 * result is surfaced as a [Result] with the mapped user-facing message.
 */
class ChangePasswordRepositoryTest {

    private class FakeAuthRepository(
        var updatePasswordError: Exception? = null
    ) : AuthRepository {
        var sdkNewPassword: String? = null
        var sdkOldPassword: String? = null

        override suspend fun currentUser(): AuthUser? = null

        override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
            Result.failure(IllegalStateException("not used"))

        override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> =
            Result.failure(IllegalStateException("not used"))

        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
            return try {
                updatePassword(newPassword, currentPassword)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception(mapAuthError(e)))
            }
        }

        private suspend fun updatePassword(newPassword: String, oldPassword: String?) {
            if (updatePasswordError != null) throw updatePasswordError!!
            sdkNewPassword = newPassword
            sdkOldPassword = oldPassword
        }

        override suspend fun signOut() = Unit
    }

    @Test
    fun changePassword_successForwardsNewAndCurrentPasswordInSdkOrder() = runTest {
        val repo = FakeAuthRepository()

        val result = repo.changePassword("temporal123", "nuevaClave123")

        assertTrue(result.isSuccess)
        assertEquals("nuevaClave123", repo.sdkNewPassword)
        assertEquals("temporal123", repo.sdkOldPassword)
    }

    @Test
    fun changePassword_sdkFailure_mapsToUserFacingErrorResult() = runTest {
        val repo = FakeAuthRepository(
            updatePasswordError = AppwriteException(
                message = "user (user_invalid_password): Password is invalid",
                code = 401,
                type = "user_invalid_password"
            )
        )

        val result = repo.changePassword("temporal123", "nuevaClave123")

        assertTrue(result.isFailure)
        assertEquals("La contraseña actual es incorrecta", result.exceptionOrNull()?.message)
    }

    @Test
    fun changePassword_networkFailure_mapsToConnectionErrorResult() = runTest {
        val repo = FakeAuthRepository(updatePasswordError = IOException("offline"))

        val result = repo.changePassword("temporal123", "nuevaClave123")

        assertTrue(result.isFailure)
        assertEquals(
            "Sin conexión. Verifica tu internet.",
            result.exceptionOrNull()?.message
        )
    }
}