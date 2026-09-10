package com.moneycounter.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.UserProfileData
import com.moneycounter.access.toAppAccessState
import com.moneycounter.auth.AuthRepository
import com.moneycounter.domain.Member
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRepository,
    private val membershipRepository: MembershipRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppAccessState>(AppAccessState.Loading)
    val uiState: StateFlow<AppAccessState> = _uiState.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _profile = MutableStateFlow<UserProfileData?>(null)
    val profile: StateFlow<UserProfileData?> = _profile.asStateFlow()

    private val _member = MutableStateFlow<Member?>(null)
    val member: StateFlow<Member?> = _member.asStateFlow()

    private var profileJob: Job? = null
    private var memberJob: Job? = null

    init {
        checkAccess()
    }

    fun loadProfile() {
        val user = authRepository.currentUser()
        if (user == null) {
            profileJob?.cancel()
            profileJob = null
            _profile.value = null
            return
        }
        if (profileJob?.isActive == true) return
        profileJob = viewModelScope.launch {
            accessRepository.observeUserProfile(user.uid).collect { profile ->
                _profile.value = profile
            }
        }
    }

    fun checkAccess() {
        val user = authRepository.currentUser()
        if (user == null) {
            _uiState.value = AppAccessState.SignedOut
            memberJob?.cancel()
            memberJob = null
            _member.value = null
            return
        }
        _uiState.value = AppAccessState.Loading
        viewModelScope.launch {
            try {
                val accessStatus = accessRepository.ensureUserDocument(user)
                observeMember(user.uid)
                _uiState.value = toAppAccessState(user, accessStatus)
            } catch (e: Exception) {
                _uiState.value = AppAccessState.Error(e.localizedMessage ?: "Error de conexión")
            }
        }
    }

    private fun observeMember(uid: String) {
        if (memberJob?.isActive == true) return
        memberJob = viewModelScope.launch {
            membershipRepository.observeMember(uid).collect { member ->
                _member.value = member
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        _isLoggingIn.value = true
        _loginError.value = null
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(activity)
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
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AppAccessState.SignedOut
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }
}