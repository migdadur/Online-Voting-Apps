package com.example.demoapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demoapplication.models.User
import com.example.demoapplication.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class Success(val user: FirebaseUser) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState: StateFlow<AuthState> = _loginState

    private val _signUpState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signUpState: StateFlow<AuthState> = _signUpState

    private val _currentUser = MutableStateFlow<FirebaseUser?>(authRepository.getCurrentUser())
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _userData = MutableStateFlow<User?>(null)
    val userData: StateFlow<User?> = _userData

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = AuthState.Loading
            val result = authRepository.signIn(email, password)
            result.onSuccess { user ->
                _currentUser.value = user
                _loginState.value = AuthState.Success(user)
                loadUserData()
            }.onFailure { exception ->
                _loginState.value = AuthState.Error(exception.message ?: "Login failed")
            }
        }
    }

    fun signUp(email: String, password: String, fullName: String) {
        viewModelScope.launch {
            _signUpState.value = AuthState.Loading
            val result = authRepository.signUp(email, password, fullName)
            result.onSuccess { user ->
                _currentUser.value = user
                _signUpState.value = AuthState.Success(user)
                loadUserData()
            }.onFailure { exception ->
                _signUpState.value = AuthState.Error(exception.message ?: "Sign up failed")
            }
        }
    }

    fun loadUserData() {
        viewModelScope.launch {
            val userData = authRepository.getUserData()
            _userData.value = userData
        }
    }

    fun signOut() {
        authRepository.signOut()
        _currentUser.value = null
        _userData.value = null
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            authRepository.resetPassword(email)
        }
    }
}