package com.moneycounter.ui

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.UserProfileData
import com.moneycounter.appwrite.Appwrite
import com.moneycounter.appwrite.AppwriteAccessRepository
import com.moneycounter.appwrite.AppwriteAuthRepository
import com.moneycounter.appwrite.AppwriteHealth
import com.moneycounter.appwrite.AppwriteMembershipRepository
import com.moneycounter.auth.AuthRepository
import com.moneycounter.domain.Member
import com.moneycounter.ui.screens.AccessRequiredScreen
import com.moneycounter.ui.screens.LoginScreen

private class AuthViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        Appwrite.init(context)
        val authRepository: AuthRepository = AppwriteAuthRepository()
        val accessRepository: AccessRepository = AppwriteAccessRepository()
        val membershipRepository: MembershipRepository = AppwriteMembershipRepository()
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(authRepository, accessRepository, membershipRepository) as T
    }
}

@Composable
fun AuthenticationGate(
    content: @Composable (onLogout: () -> Unit, profile: UserProfileData?, onLoadProfile: () -> Unit, member: Member?) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val viewModel: AuthViewModel = viewModel(factory = remember(context) { AuthViewModelFactory(context) })
    val state by viewModel.uiState.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val member by viewModel.member.collectAsState()

    AuthenticationGateContent(
        state = state,
        isLoggingIn = isLoggingIn,
        loginError = loginError,
        onLogin = { email, password ->
            viewModel.signInWithEmail(email, password)
        },
        onVerifyConnection = {
            AppwriteHealth.ping()
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
    onLogin: (email: String, password: String) -> Unit,
    onVerifyConnection: suspend () -> Result<Long>,
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
                onLogin = onLogin,
                onVerifyConnection = onVerifyConnection
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