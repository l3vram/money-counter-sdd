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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.moneycounter.access.effectiveAccessState
import com.moneycounter.access.JsonMemberCacheRepository
import com.moneycounter.access.JsonSessionCacheRepository
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.UserProfileData
import com.moneycounter.appwrite.Appwrite
import com.moneycounter.appwrite.AppwriteAccessRepository
import com.moneycounter.appwrite.AppwriteAuthRepository
import com.moneycounter.appwrite.AppwriteCloudOrgRepository
import com.moneycounter.appwrite.AppwriteHealth
import com.moneycounter.appwrite.AppwriteMembershipRepository
import com.moneycounter.appwrite.AppwriteSignupRepository
import com.moneycounter.auth.AuthRepository
import com.moneycounter.domain.Member
import com.moneycounter.domain.Role
import com.moneycounter.repository.JsonTenantRepository
import com.moneycounter.ui.screens.AccessRequiredScreen
import com.moneycounter.ui.screens.AssignmentPendingScreen
import com.moneycounter.ui.screens.ChangePasswordScreen
import com.moneycounter.ui.screens.LoginScreen
import com.moneycounter.ui.screens.OfflineAwareContent
import com.moneycounter.ui.screens.SignUpScreen
import com.moneycounter.ui.screens.SignUpSuccessScreen

private class AuthViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        Appwrite.init(context)
        val authRepository: AuthRepository = AppwriteAuthRepository()
        val accessRepository: AccessRepository = AppwriteAccessRepository()
        val membershipRepository: MembershipRepository = AppwriteMembershipRepository()
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(
            authRepository,
            accessRepository,
            membershipRepository,
            AppwriteSignupRepository(),
            tenantRepository = JsonTenantRepository(context),
            cloudOrgRepository = AppwriteCloudOrgRepository(),
            memberCacheRepository = JsonMemberCacheRepository(context),
            sessionCacheRepository = JsonSessionCacheRepository(context)
        ) as T
    }
}

@Composable
fun AuthenticationGate(
    content: @Composable (onLogout: () -> Unit, profile: UserProfileData?, onLoadProfile: () -> Unit, member: Member?) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val viewModel: AuthViewModel = viewModel(factory = remember(context) { AuthViewModelFactory(context) })
    val rawState by viewModel.uiState.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val isSigningUp by viewModel.isSigningUp.collectAsState()
    val signUpError by viewModel.signUpError.collectAsState()
    val superuserWhatsapp by viewModel.superuserWhatsapp.collectAsState()
    val isChangingPassword by viewModel.isChangingPassword.collectAsState()
    val changePasswordError by viewModel.changePasswordError.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val member by viewModel.member.collectAsState()
    val membershipResolved by viewModel.membershipResolved.collectAsState()
    val knowsCurrentPassword by viewModel.knowsCurrentPassword.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    // Plan 033: being approved is not enough — the membership decides whether the app opens.
    val state = effectiveAccessState(rawState, member, membershipResolved)

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
        onSignUp = { email, role, businessName, branches ->
            viewModel.signUp(email, role, businessName, branches)
        },
        isSigningUp = isSigningUp,
        signUpError = signUpError,
        superuserWhatsapp = superuserWhatsapp,
        onBackToLogin = {
            viewModel.signOut()
        },
        onChangePassword = { current, new, confirm ->
            viewModel.changePassword(current, new, confirm)
        },
        isChangingPassword = isChangingPassword,
        changePasswordError = changePasswordError,
        knowsCurrentPassword = knowsCurrentPassword,
        isOffline = isOffline,
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
    onSignUp: (email: String, role: Role, businessName: String?, branches: List<String>) -> Unit,
    isSigningUp: Boolean = false,
    signUpError: String? = null,
    superuserWhatsapp: String? = null,
    onBackToLogin: () -> Unit,
    onChangePassword: (current: String, new: String, confirm: String) -> Unit,
    isChangingPassword: Boolean = false,
    changePasswordError: String? = null,
    knowsCurrentPassword: Boolean = true,
    isOffline: Boolean = false,
    profile: UserProfileData?,
    onLoadProfile: () -> Unit,
    member: Member?,
    content: @Composable (onLogout: () -> Unit, profile: UserProfileData?, onLoadProfile: () -> Unit, member: Member?) -> Unit
) {
    var showSignUp by remember { mutableStateOf(false) }

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
            if (showSignUp) {
                SignUpScreen(
                    isSigningUp = isSigningUp,
                    errorMessage = signUpError,
                    onSignUp = onSignUp,
                    onBack = { showSignUp = false }
                )
            } else {
                LoginScreen(
                    isLoggingIn = isLoggingIn,
                    errorMessage = loginError,
                    onLogin = onLogin,
                    onVerifyConnection = onVerifyConnection,
                    onNavigateToSignUp = { showSignUp = true }
                )
            }
        }
        is AppAccessState.SignUpPending -> {
            SignUpSuccessScreen(
                tempPassword = state.tempPassword,
                request = state.request,
                superuserWhatsapp = superuserWhatsapp,
                onBackToLogin = onBackToLogin
            )
        }
        is AppAccessState.Pending -> {
            AccessRequiredScreen(
                accessStatus = AccessStatus.PENDING
            )
        }
        is AppAccessState.PasswordChangeRequired -> {
            ChangePasswordScreen(
                isChangingPassword = isChangingPassword,
                errorMessage = changePasswordError,
                knowsCurrentPassword = knowsCurrentPassword,
                onChangePassword = onChangePassword
            )
        }
        is AppAccessState.AwaitingAssignment -> {
            AssignmentPendingScreen(
                panelOnly = false,
                onRetry = onRetry,
                onLogout = onLogout
            )
        }
        is AppAccessState.PanelOnlyAccount -> {
            AssignmentPendingScreen(
                panelOnly = true,
                onLogout = onLogout
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
            // El cartel de "sin conexión" envuelve TODO el contenido, así que se ve en
            // cualquier pantalla a la que el usuario navegue (plan 034).
            OfflineAwareContent(isOffline = isOffline) {
                content(onLogout, profile, onLoadProfile, member)
            }
        }
    }
}