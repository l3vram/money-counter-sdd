package com.moneycounter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.MemberCacheRepository
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.UserProfileData
import com.moneycounter.access.toAppAccessState
import com.moneycounter.appwrite.CloudOrgRepository
import com.moneycounter.auth.AuthRepository
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
    private val memberCacheRepository: MemberCacheRepository? = null
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

    fun checkAccess() {
        viewModelScope.launch {
            val user = try {
                authRepository.currentUser()
            } catch (e: Exception) {
                _uiState.value = AppAccessState.Error(e.localizedMessage ?: "Error de conexión")
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
                _uiState.value = if (accessStatus == AccessStatus.APPROVED && mustChangePassword) {
                    AppAccessState.PasswordChangeRequired(user)
                } else {
                    toAppAccessState(user, accessStatus)
                }
            } catch (e: Exception) {
                _uiState.value = AppAccessState.Error(e.localizedMessage ?: "Error de conexión")
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
            membershipRepository.observeMember(uid).collect { member ->
                // Either kind of answer resolves the question. The repository only emits null
                // on a genuine 404 (plan 030), so this is not a transport failure.
                _membershipResolved.value = true
                if (member != null) {
                    _member.value = member
                    memberCacheRepository?.save(member)
                } else if (memberCacheRepository?.load() == null) {
                    // The row is genuinely missing and nothing was ever cached: since plan
                    // 033 that means no access, not the old single-user free-for-all.
                    _member.value = null
                }
                seedCloudTenantIfNeeded(uid, member)
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
        // Shared device: never let the next user inherit this session's role.
        memberCacheRepository?.clear()
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AppAccessState.SignedOut
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        val state = _uiState.value
        val user = (state as? AppAccessState.PasswordChangeRequired)?.user ?: return
        if (new.length < MIN_PASSWORD_LENGTH) {
            _changePasswordError.value = "La nueva contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            return
        }
        if (new != confirm) {
            _changePasswordError.value = "Las contraseñas no coinciden."
            return
        }
        if (new == current) {
            _changePasswordError.value = "La nueva contraseña debe ser diferente de la actual."
            return
        }
        _isChangingPassword.value = true
        _changePasswordError.value = null
        viewModelScope.launch {
            val result = authRepository.changePassword(current, new)
            _isChangingPassword.value = false
            result.fold(
                onSuccess = {
                    runCatching { signupRepository?.setMustChangePassword(user.uid, false) }
                    _uiState.value = AppAccessState.Approved(user)
                },
                onFailure = { error ->
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
            val result = authRepository.signInWithEmail(normalizedEmail, tempPassword)
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
                        _signUpError.value = e.localizedMessage ?: "Error al enviar la solicitud de registro"
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
