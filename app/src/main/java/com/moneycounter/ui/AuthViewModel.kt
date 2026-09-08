package com.moneycounter.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.toAppAccessState
import com.moneycounter.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppAccessState>(AppAccessState.Loading)
    val uiState: StateFlow<AppAccessState> = _uiState.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    init {
        checkAccess()
    }

    fun checkAccess() {
        val user = authRepository.currentUser()
        if (user == null) {
            _uiState.value = AppAccessState.SignedOut
            return
        }
        _uiState.value = AppAccessState.Loading
        viewModelScope.launch {
            try {
                val accessStatus = accessRepository.ensureUserDocument(user)
                _uiState.value = toAppAccessState(user, accessStatus)
            } catch (e: Exception) {
                _uiState.value = AppAccessState.Error(e.localizedMessage ?: "Error de conexión")
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
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AppAccessState.SignedOut
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }
}