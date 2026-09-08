package com.moneycounter.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository : AuthRepository {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun currentUser(): AuthUser? {
        val firebaseUser = firebaseAuth.currentUser ?: return null
        return firebaseUser.toAuthUser()
    }

    override suspend fun signInWithGoogle(activity: Activity): Result<AuthUser> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(
                    "305096312538-0ocn8eqlqsen2e9hfhmrv3p74qg7tubk.apps.googleusercontent.com"
                )
                .setFilterByAuthorizedAccounts(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activity as Context)
            val response = credentialManager.getCredential(activity as Context, request)

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user?.toAuthUser()
                ?: return Result.failure(Exception("Firebase returned null user"))
            Result.success(user)
        } catch (e: Exception) {
            val message = mapAuthError(e)
            Result.failure(Exception(message))
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser(): AuthUser =
        AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl?.toString()
        )
}
