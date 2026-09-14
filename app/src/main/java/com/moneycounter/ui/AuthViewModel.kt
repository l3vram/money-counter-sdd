package com.moneycounter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.CachedSession
import com.moneycounter.access.MemberCacheRepository
import com.moneycounter.access.SessionCacheRepository
import com.moneycounter.access.SessionOutcome
import com.moneycounter.access.sessionOutcomeFor
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.MembershipUpdate
import com.moneycounter.access.UserProfileData
import com.moneycounter.access.toAppAccessState
import com.moneycounter.appwrite.CloudOrgRepository
import com.moneycounter.auth.AuthRepository
import io.appwrite.exceptions.AppwriteException
import com.moneycounter.auth.mapAuthError
import com.moneycounter.domain.Branch
import com.moneycounter.domain.Member
import com.moneycounter.domain.Organization
import com.moneycounter.domain.Role
import com.moneycounter.repository.TenantRepository
import com.moneycounter.signup.PasswordGenerator
import com.moneycounter.signup.SignupRepository
import com.moneycounter.signup.SignupRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRepository,
    private val membershipRepository: MembershipRepository,
    private val signupRepository: SignupRepository? = null,
    private val tenantRepository: TenantRepository? = null,
    private val cloudOrgRepository: CloudOrgRepository? = null,
    private val memberCacheRepository: MemberCacheRepository? = null,
    private val sessionCacheRepository: SessionCacheRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppAccessState>(AppAccessState.Loading)
    val uiState: StateFlow<AppAccessState> = _uiState.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isSigningUp = MutableStateFlow(false)
    val isSigningUp: StateFlow<Boolean> = _isSigningUp.asStateFlow()

    private val _signUpError = MutableStateFlow<String?>(null)
    val signUpError: StateFlow<String?> = _signUpError.asStateFlow()

    private val _superuserWhatsapp = MutableStateFlow<String?>(null)
    val superuserWhatsapp: StateFlow<String?> = _superuserWhatsapp.asStateFlow()

    private val _isChangingPassword = MutableStateFlow(false)
    val isChangingPassword: StateFlow<Boolean> = _isChangingPassword.asStateFlow()

    private val _changePasswordError = MutableStateFlow<String?>(null)
    val changePasswordError: StateFlow<String?> = _changePasswordError.asStateFlow()

    private val _profile = MutableStateFlow<UserProfileData?>(null)
    val profile: StateFlow<UserProfileData?> = _profile.asStateFlow()

    private val _member = MutableStateFlow<Member?>(null)
    val member: StateFlow<Member?> = _member.asStateFlow()

    /**
     * Plan 033: whether the membership poll has answered at all, which is NOT the same as
     * [member] being null. Without this, a first launch would flash the locked screen before
     * the membership arrives; with it, an unresolved membership shows the loading state, which
     * grants nothing either.
     */
    /**
     * The password used to sign in, held **in memory only** for this session and never
     * persisted. It exists so the forced change does not have to ask for the temporary
     * password the user just typed: nobody remembers a generated password minutes later, and
     * asking again is the step where people got stuck. Cleared on sign-out and as soon as the
     * change succeeds.
     *
     * Appwrite requires the old password to change a password on an account that has one, so
     * this is what lets the screen ask only for the new one. It is not a credential store:
     * if the app is reopened from a restored session without typing anything, this is null and
     * the screen asks for it.
     */
    private var sessionPassword: String? = null

    /** Whether the forced change can proceed without asking for the current password. */
    val knowsCurrentPassword: StateFlow<Boolean>
        get() = _knowsCurrentPassword.asStateFlow()
    private val _knowsCurrentPassword = MutableStateFlow(false)

    /**
     * Whether this session is running on cached data because the server could not be reached
     * (plan 034). Drives the "sin conexión" banner; cleared as soon as a call succeeds.
     */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _membershipResolved = MutableStateFlow(false)
    val membershipResolved: StateFlow<Boolean> = _membershipResolved.asStateFlow()

    private var profileJob: Job? = null
    private var memberJob: Job? = null
    private var seedInProgress = false

    init {
        checkAccess()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val user = try {
                authRepository.currentUser()
            } catch (e: Exception) {
                null
            }
            if (user == null) {
                profileJob?.cancel()
                profileJob = null
                _profile.value = null
                return@launch
            }
            if (profileJob?.isActive == true) return@launch
            profileJob = launch {
                accessRepository.observeUserProfile(user.uid).collect { profile ->
                    _profile.value = profile
                }
            }
        }
    }

    /**
     * Plan 034: the startup path no longer dies without a network.
     *
     * `currentUser()` and `ensureUserDocument()` both hit the server, and any failure used to
     * become [AppAccessState.Error] — so a seller opening the app with no signal could not even
     * look at what they already had. Now the failure is classified by [sessionOutcomeFor]: a
     * transport failure falls back to the cached session, a 401 signs out, and only a failure
     * with nothing cached shows the connection screen.
     */
    fun checkAccess() {
        viewModelScope.launch {
            val user = try {
                authRepository.currentUser()
            } catch (e: Exception) {
                resolveFailedVerification(e)
                return@launch
            }
            if (user == null) {
                _uiState.value = AppAccessState.SignedOut
                memberJob?.cancel()
                memberJob = null
                _member.value = null
                _membershipResolved.value = false
                return@launch
            }
            _uiState.value = AppAccessState.Loading
            try {
                val accessStatus = accessRepository.ensureUserDocument(user)
                observeMember(user.uid)
                val mustChangePassword = signupRepository?.readMustChangePassword(user.uid) ?: false
                // The server answered: this session is verified, so remember it for the next
                // start and drop the offline banner.
                _isOffline.value = false
                sessionCacheRepository?.save(
                    CachedSession(
                        uid = user.uid,
                        email = user.email,
                        displayName = user.displayName,
                        photoUrl = user.photoUrl,
                        access = accessStatus,
                        savedAtMs = System.currentTimeMillis()
                    )
                )
                _uiState.value = if (accessStatus == AccessStatus.APPROVED && mustChangePassword) {
                    AppAccessState.PasswordChangeRequired(user)
                } else {
                    toAppAccessState(user, accessStatus)
                }
            } catch (e: Exception) {
                resolveFailedVerification(e)
            }
        }
    }

    /**
     * Decides what a failed verification means. The HTTP status is the whole signal: null means
     * no response at all (offline), 401 means the session is gone, anything else means the
     * server is reachable but unhelpful — see [sessionOutcomeFor].
     */
    private fun resolveFailedVerification(e: Exception) {
        val cached = sessionCacheRepository?.load()
        val code = (e as? AppwriteException)?.code
        when (sessionOutcomeFor(code, hasCachedSession = cached != null)) {
            SessionOutcome.SIGN_OUT -> {
                // Revoked, deleted, or access withdrawn. This is what makes an offline session
                // with no expiry acceptable: it ends the moment the device hears the server.
                signOut()
            }

            SessionOutcome.USE_CACHE -> {
                val session = cached ?: return run {
                    _uiState.value = AppAccessState.Error(mapAuthError(e))
                }

                // An approved session still needs a membership to operate (plan 033), and
                // offline the only source is the local cache. With no cached membership there
                // is nothing to decide with: show the connection screen, which is the honest
                // answer. Opening would reintroduce plan 033's hole, and `AwaitingAssignment`
                // would blame the administrator for a network problem — while plan 033 alone
                // would leave the session spinning in Loading forever.
                val hasCachedMembership =
                    memberCacheRepository?.load()?.takeIf { it.uid == session.uid } != null
                if (session.access == AccessStatus.APPROVED && !hasCachedMembership) {
                    _isOffline.value = false
                    _uiState.value = AppAccessState.Error(
                        "Sin conexión y sin datos guardados de tu cuenta. " +
                            "Conéctate una vez para poder entrar sin internet más adelante."
                    )
                    return
                }

                _isOffline.value = true
                // Keep polling: the membership — and connectivity — come back on their own.
                observeMember(session.uid)
                _uiState.value = toAppAccessState(session.toAuthUser(), session.access)
            }

            SessionOutcome.FAIL -> {
                _isOffline.value = false
                _uiState.value = AppAccessState.Error(mapAuthError(e))
            }
        }
    }

    /**
     * Plan 030: seed the last known membership from the local cache *before* the
     * first network poll, so an offline start keeps its real role instead of
     * falling back to "no member" (which grants every permission). The cached
     * member is only reused when its uid matches the signed-in user — one
     * account's role must never carry into another's session.
     */
    private fun observeMember(uid: String) {
        if (memberJob?.isActive == true) return
        if (_member.value == null) {
            memberCacheRepository?.load()?.takeIf { it.uid == uid }?.let { _member.value = it }
        }
        memberJob = viewModelScope.launch {
            membershipRepository.observeMember(uid).collect { update ->
                when (update) {
                    is MembershipUpdate.Assigned -> {
                        _membershipResolved.value = true
                        // The poll answered, so connectivity is back — this is the cheapest
                        // signal there is, and it is why the offline banner needs no timer.
                        _isOffline.value = false
                        _member.value = update.member
                        memberCacheRepository?.save(update.member)
                        seedCloudTenantIfNeeded(uid, update.member)
                    }

                    MembershipUpdate.Missing -> {
                        _membershipResolved.value = true
                        _isOffline.value = false
                        if (memberCacheRepository?.load() == null) {
                            // Genuinely absent and nothing was ever cached: since plan 033
                            // that means no access, not the old single-user free-for-all.
                            _member.value = null
                        }
                        seedCloudTenantIfNeeded(uid, null)
                    }

                    MembershipUpdate.Revoked -> {
                        // Plan 034: a 401 here means the session died while the app was open —
                        // revoked, deleted, or access withdrawn. Before, this was swallowed and
                        // the app kept running on a dead session until the next restart.
                        signOut()
                    }
                }
            }
        }
    }

    /**
     * Plan 028: once a signed-in user's membership row shows up (web admin
     * approval) with a cloud orgId the local JSON tenant config doesn't have,
     * seed the org/branches from Appwrite. Failures are never fatal and get
     * retried on the next poll; the local config is never overwritten for a
     * different org.
     */
    private fun seedCloudTenantIfNeeded(uid: String, member: Member?) {
        val tenantRepository = tenantRepository ?: return
        val cloudOrgRepository = cloudOrgRepository ?: return
        val orgId = member?.orgId?.takeIf { it.isNotBlank() } ?: return
        if (tenantRepository.loadOrganization()?.id == orgId) return
        if (seedInProgress) return
        seedInProgress = true
        viewModelScope.launch {
            try {
                val orgInfo = cloudOrgRepository.getOrg(orgId) ?: return@launch
                val branches = cloudOrgRepository.getBranches(orgId)
                tenantRepository.seedFromCloud(
                    Organization(
                        id = orgInfo.id,
                        name = orgInfo.name,
                        ownerUid = uid,
                        whatsappNumber = orgInfo.whatsappNumber,
                        createdAt = orgInfo.createdAt,
                        active = true
                    ),
                    branches.map {
                        Branch(
                            id = it.id,
                            orgId = it.orgId,
                            name = it.name,
                            createdAt = it.createdAt,
                            active = true
                        )
                    }
                )
            } catch (e: Exception) {
                // Offline-tolerant: stay unseeded and retry on the next poll.
                // Logging is intentionally omitted (android.util.Log is not mockable
                // in the JVM unit tests; matches the appwrite repo catch style).
            } finally {
                seedInProgress = false
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        _isLoggingIn.value = true
        _loginError.value = null
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(email, password)
            _isLoggingIn.value = false
            result.fold(
                onSuccess = {
                    sessionPassword = password
                    _knowsCurrentPassword.value = true
                    checkAccess()
                },
                onFailure = { error ->
                    _loginError.value = error.localizedMessage ?: "Error al iniciar sesión"
                }
            )
        }
    }

    fun signOut() {
        profileJob?.cancel()
        profileJob = null
        memberJob?.cancel()
        memberJob = null
        _profile.value = null
        _member.value = null
        _membershipResolved.value = false
        sessionPassword = null
        _knowsCurrentPassword.value = false
        _isOffline.value = false
        // Shared device: never let the next user inherit this session's role or identity.
        memberCacheRepository?.clear()
        sessionCacheRepository?.clear()
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AppAccessState.SignedOut
        }
    }

    /**
     * @param current only used when [knowsCurrentPassword] is false — the app was reopened from
     *   a restored session, so the password was never typed on this run. Normally the screen
     *   leaves it blank and the remembered one is used.
     */
    fun changePassword(current: String, new: String, confirm: String) {
        val state = _uiState.value
        val user = (state as? AppAccessState.PasswordChangeRequired)?.user ?: return
        val effectiveCurrent = sessionPassword ?: current.takeIf { it.isNotBlank() }
        if (effectiveCurrent == null) {
            _changePasswordError.value = "Escribe tu contraseña actual para poder cambiarla."
            return
        }
        if (new.length < MIN_PASSWORD_LENGTH) {
            _changePasswordError.value = "La nueva contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            return
        }
        if (new != confirm) {
            _changePasswordError.value = "Las contraseñas no coinciden."
            return
        }
        if (new == effectiveCurrent) {
            _changePasswordError.value = "La nueva contraseña debe ser diferente de la actual."
            return
        }
        _isChangingPassword.value = true
        _changePasswordError.value = null
        viewModelScope.launch {
            val result = authRepository.changePassword(effectiveCurrent, new)
            _isChangingPassword.value = false
            result.fold(
                onSuccess = {
                    // The new one is now this session's password: the user may be asked to
                    // change it again later, and we must not keep the old one around.
                    sessionPassword = new
                    runCatching { signupRepository?.setMustChangePassword(user.uid, false) }
                    _uiState.value = AppAccessState.Approved(user)
                },
                onFailure = { error ->
                    // No re-mapear: `authRepository.changePassword` ya devuelve el mensaje
                    // traducido por mapAuthError. Mapearlo otra vez lo degrada a "Error
                    // inesperado", porque el texto en español no coincide con ningún patrón.
                    _changePasswordError.value =
                        error.localizedMessage ?: "Error al cambiar la contraseña"
                }
            )
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun signUp(email: String, role: Role, businessName: String?, branches: List<String>) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty()) {
            _signUpError.value = "Introduce un correo electrónico válido."
            return
        }
        val repository = signupRepository
        if (repository == null) {
            _signUpError.value = "El registro no está disponible en este momento."
            return
        }
        _isSigningUp.value = true
        _signUpError.value = null
        viewModelScope.launch {
            val tempPassword = PasswordGenerator.generate()
            // Registration creates the account explicitly. It used to call signInWithEmail and
            // rely on its auto-registration side effect, so removing that side effect broke
            // signing up entirely: the sign-in failed with "invalid credentials" for an account
            // that did not exist yet.
            val result = authRepository.signUpWithEmail(normalizedEmail, tempPassword)
            sessionPassword = tempPassword
            _knowsCurrentPassword.value = true
            _isSigningUp.value = false
            result.fold(
                onSuccess = { user ->
                    val request = SignupRequest(
                        uid = user.uid,
                        email = normalizedEmail,
                        role = role,
                        businessName = businessName,
                        branches = branches,
                        mustChangePassword = true,
                        status = "PENDING",
                        createdAtMs = System.currentTimeMillis()
                    )
                    try {
                        repository.submit(request)
                        _superuserWhatsapp.value = runCatching {
                            repository.settingsSuperuserWhatsapp()
                        }.getOrNull()
                        _uiState.value = AppAccessState.SignUpPending(tempPassword, request)
                    } catch (e: Exception) {
                        _signUpError.value = mapAuthError(e)
                    }
                },
                onFailure = { error ->
                    _signUpError.value = error.localizedMessage ?: "Error al crear la cuenta"
                }
            )
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
