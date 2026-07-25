package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JumpAnimeRepository
import com.example.data.model.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: JumpAnimeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthState>(AuthState.Initial)
    val uiState = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val response = repository.login(email, password)
                if (response?.token != null) {
                    _uiState.value = AuthState.Success(response)
                } else {
                    _uiState.value = AuthState.Error(response?.error ?: "Login failed")
                }
            } catch (e: Exception) {
                _uiState.value = AuthState.Error(e.message ?: "Network error")
            }
        }
    }

    fun register(username: String, email: String, password: String, confirmPassword: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            _uiState.value = AuthState.Error("All fields are required.")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthState.Error("Password must be at least 6 characters long.")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = AuthState.Error("Passwords do not match.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val response = repository.register(username, email, password, confirmPassword)
                if (response != null && response.error == null) {
                    val token = response.token
                    if (token != null) {
                        _uiState.value = AuthState.Success(response)
                    } else {
                        // Registration was successful, but didn't supply a login token directly.
                        // Automatically log in the user using the email and password they just registered with.
                        val loginResponse = repository.login(email, password)
                        if (loginResponse?.token != null) {
                            _uiState.value = AuthState.Success(loginResponse)
                        } else {
                            _uiState.value = AuthState.Error(loginResponse?.error ?: "Account created successfully, but auto-login failed. Please sign in manually.")
                        }
                    }
                } else {
                    _uiState.value = AuthState.Error(response?.error ?: "Registration failed")
                }
            } catch (e: Exception) {
                _uiState.value = AuthState.Error(e.message ?: "Network error")
            }
        }
    }

    fun logout() {
        repository.sessionManager.clearToken()
        _uiState.value = AuthState.Initial
    }

    fun resendVerification(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val response = repository.resendVerification(email)
                if (response != null && response.error == null) {
                    _uiState.value = AuthState.VerificationSent
                } else {
                    _uiState.value = AuthState.Error(response?.error ?: "Failed to resend verification email")
                }
            } catch (e: Exception) {
                _uiState.value = AuthState.Error(e.message ?: "Network error")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthState.Initial
    }
}

sealed interface AuthState {
    data object Initial : AuthState
    data object Loading : AuthState
    data class Success(val response: AuthResponse) : AuthState
    data class Error(val message: String) : AuthState
    data class VerificationRequired(val email: String) : AuthState
    data object VerificationSent : AuthState
}
