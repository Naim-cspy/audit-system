package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserEntity
import com.example.data.repository.SupermarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: SupermarketRepository) : ViewModel() {

    // Default to admin so user immediately sees full app capabilities
    private val _currentUser = MutableStateFlow<UserEntity?>(
        UserEntity(id = 1, username = "admin", password_hash = "", role = "admin")
    )
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun login(username: String, passwordPlain: String) {
        viewModelScope.launch {
            _loginError.value = null
            val user = repository.verifyLogin(username, passwordPlain)
            if (user != null) {
                _currentUser.value = user
            } else {
                _loginError.value = "Invalid username or password"
            }
        }
    }

    fun quickLoginAs(username: String) {
        viewModelScope.launch {
            val user = repository.verifyLogin(username, if (username == "admin") "admin123" else if (username == "john") "password1" else "securePass99")
            if (user != null) {
                _currentUser.value = user
            } else {
                // Fallback direct entity set
                _currentUser.value = UserEntity(
                    username = username,
                    password_hash = "",
                    role = if (username == "admin") "admin" else "cashier"
                )
            }
        }
    }

    fun logout() {
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
