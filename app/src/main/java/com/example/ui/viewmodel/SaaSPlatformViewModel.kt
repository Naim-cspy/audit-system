package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.PlatformMetricsSummary
import com.example.data.model.SecurityEventEntity
import com.example.data.repository.SupermarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel exclusively for SaaS Platform Owner & Global Command Center.
 * Access is protected by security checks and Firebase custom claims.
 */
class SaaSPlatformViewModel(
    private val repository: SupermarketRepository
) : ViewModel() {

    val isPlatformAdmin: StateFlow<Boolean> = repository.isPlatformAdmin
    val platformMetrics: StateFlow<PlatformMetricsSummary?> = repository.platformMetrics
    val securityAlerts: StateFlow<List<SecurityEventEntity>> = repository.platformSecurityAlerts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successNotice = MutableStateFlow<String?>(null)
    val successNotice: StateFlow<String?> = _successNotice.asStateFlow()

    init {
        // Automatically attempt load if user is a platform admin
        if (repository.isPlatformAdmin.value) {
            refreshMetrics()
        }
    }

    fun refreshMetrics() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.loadPlatformMetrics()
            result.onSuccess {
                _successNotice.value = "Platform metrics refreshed successfully"
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Failed to load platform metrics"
            }
            _isLoading.value = false
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successNotice.value = null
    }
}

class SaaSPlatformViewModelFactory(
    private val repository: SupermarketRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SaaSPlatformViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SaaSPlatformViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
