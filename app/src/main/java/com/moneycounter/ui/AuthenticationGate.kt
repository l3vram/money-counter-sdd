package com.moneycounter.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.FirestoreAccessRepository
import com.moneycounter.access.FirestoreMembershipRepository
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.UserProfileData
import com.moneycounter.auth.AuthRepository
import com.moneycounter.auth.FirebaseAuthRepository
import com.moneycounter.domain.Member
import com.moneycounter.ui.screens.AccessRequiredScreen
import com.moneycounter.ui.screens.LoginScreen

private class AuthViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val authRepository: AuthRepository = FirebaseAuthRepository()
        val accessRepository: AccessRepository =
            FirestoreAccessRepository(FirebaseFirestore.getInstance())
        val membershipRepository: MembershipRepository =
            FirestoreMembershipRepository(FirebaseFirestore.getInstance())
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(authRepository, accessRepository, membershipRepository) as T
    }
}

@Composable
fun AuthenticationGate(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory()),
    content: @Composable (onLogout: () -> Unit, profile: UserProfileData?, onLoadProfile: () -> Unit, member: Member?) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val member by viewModel.member.collectAsState()

    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener {
            viewModel.checkAccess()
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    AuthenticationGateContent(
        state = state,
        isLoggingIn = isLoggingIn,
        loginError = loginError,
        onGoogleSignIn = { activity ->
            viewModel.signInWithGoogle(activity)
        },
        onRetry = {
            viewModel.checkAccess()
        },
        onLogout = {
            viewModel.signOut()
        },
        profile = profile,
        onLoadProfile = {
            viewModel.loadProfile()
        },
        member = member,
        content = content
    )
}

@Composable
fun AuthenticationGateContent(
    state: AppAccessState,
    isLoggingIn: Boolean,
    loginError: String?,
    onGoogleSignIn: (Activity) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    profile: UserProfileData?,
    onLoadProfile: () -> Unit,
    member: Member?,
    content: @Composable (onLogout: () -> Unit, profile: UserProfileData?, onLoadProfile: () -> Unit, member: Member?) -> Unit
) {
    when (state) {
        is AppAccessState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is AppAccessState.SignedOut -> {
            LoginScreen(
                isLoggingIn = isLoggingIn,
                errorMessage = loginError,
                onGoogleSignIn = onGoogleSignIn
            )
        }
        is AppAccessState.Pending -> {
            AccessRequiredScreen(
                accessStatus = AccessStatus.PENDING
            )
        }
        is AppAccessState.Blocked -> {
            AccessRequiredScreen(
                accessStatus = AccessStatus.BLOCKED
            )
        }
        is AppAccessState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error de conexión",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text("Reintentar")
                    }
                }
            }
        }
        is AppAccessState.Approved -> {
            LaunchedEffect(Unit) {
                onLoadProfile()
            }
            content(onLogout, profile, onLoadProfile, member)
        }
    }
}