package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.StoreProfile
import com.example.data.model.UserEntity
import com.example.data.repository.SupermarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: SupermarketRepository) : ViewModel() {

    // Authentication requires explicit login (no automatic bypass/admin default)
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    val currentStoreProfile: StateFlow<StoreProfile> = repository.currentStoreProfileFlow

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isFirstTimeSetup = MutableStateFlow(false)
    val isFirstTimeSetup: StateFlow<Boolean> = _isFirstTimeSetup.asStateFlow()

    init {
        checkInitialSetup()
    }

    fun checkInitialSetup() {
        viewModelScope.launch {
            try {
                _isFirstTimeSetup.value = repository.authRepository.getTotalUsersCount() == 0
            } catch (e: Exception) {
                _isFirstTimeSetup.value = false
            }
        }
    }

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasUsers: StateFlow<Boolean> = repository.allUsers
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun login(identifier: String, passwordPlain: String) {
        viewModelScope.launch {
            _loginError.value = null
            if (identifier.isBlank() || passwordPlain.isBlank()) {
                _loginError.value = "Please enter both identifier (email/username) and password"
                return@launch
            }
            _isLoading.value = true
            try {
                val res = repository.authRepository.login(identifier, passwordPlain)
                if (res.isSuccess) {
                    _currentUser.value = res.getOrThrow()
                } else {
                    _loginError.value = res.exceptionOrNull()?.message ?: "Invalid credentials or account not found"
                }
            } catch (e: Exception) {
                _loginError.value = e.message ?: "Authentication failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerInitialAdmin(usernameOrEmail: String, passwordPlain: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _loginError.value = null
            _isLoading.value = true
            try {
                val res = repository.createInitialAdmin(usernameOrEmail, passwordPlain)
                if (res.isSuccess) {
                    _currentUser.value = res.getOrNull()
                    checkInitialSetup()
                    onComplete(true, null)
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Failed to create administrator account"
                    _loginError.value = err
                    onComplete(false, err)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        repository.logout()
        _currentUser.value = null
    }

    fun addUser(username: String, pass: String, role: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.addUser(username, pass, role)
            if (res.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteUser(username: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteUser(username)
            if (res.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun changePassword(username: String, newPass: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.changePassword(username, newPass)
            if (res.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun clearError() {
        _loginError.value = null
    }
}

class AuthViewModelFactory(private val repository: SupermarketRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
