package com.example.data.repository

import com.example.data.analytics.AnalyticsManager
import com.example.data.db.AppDatabase
import com.example.data.model.PlatformMetricsSummary
import com.example.data.model.SecurityEventEntity
import com.example.data.remote.PlatformAnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository governing SaaS Platform Owner Analytics & Metrics.
 *
 * Strict Security Enforcement:
 * - Direct authentication check on token claims (`isPlatformAdmin == true` or `role == "SAAS_OWNER"`).
 * - Firestore Security Rule checks on `/platform_analytics` and `/security_events`.
 * - Client/store dashboards cannot view or invoke global platform metrics.
 */
class PlatformAnalyticsRepository(
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
    private val platformService: PlatformAnalyticsService = PlatformAnalyticsService(),
    private val analyticsManager: AnalyticsManager? = null
) {

    private val _platformMetrics = MutableStateFlow<PlatformMetricsSummary?>(null)
    val platformMetrics: StateFlow<PlatformMetricsSummary?> = _platformMetrics.asStateFlow()

    private val _securityAlerts = MutableStateFlow<List<SecurityEventEntity>>(emptyList())
    val securityAlerts: StateFlow<List<SecurityEventEntity>> = _securityAlerts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Checks if current session is authorized as SaaS Platform Owner.
     */
    fun isPlatformOwnerAuthorized(): Boolean {
        return authRepository.isPlatformAdmin.value
    }

    /**
     * Loads live multi-store SaaS owner metrics.
     * Enforced by both code RBAC guard and Firestore Security Rules.
     */
    suspend fun loadPlatformMetrics(): Result<PlatformMetricsSummary> = withContext(Dispatchers.IO) {
        if (!isPlatformOwnerAuthorized()) {
            val err = "ACCESS_DENIED: User is not authorized as SaaS Platform Owner. Client tenants cannot access global analytics."
            _errorMessage.value = err
            return@withContext Result.failure(SecurityException(err))
        }

        _isLoading.value = true
        _errorMessage.value = null

        try {
            // 1. Fetch remote platform summary from Firestore
            val remoteResult = platformService.getGlobalPlatformSummary()
            val baseSummary = remoteResult.getOrNull() ?: PlatformMetricsSummary()

            // 2. Fetch security events
            val secEventsResult = platformService.getPlatformSecurityEvents()
            val secEvents = secEventsResult.getOrNull() ?: emptyList()
            _securityAlerts.value = secEvents

            // 3. Fetch registered stores
            val storesResult = platformService.getRegisteredStores()
            val storesList = storesResult.getOrNull() ?: emptyList()

            // 4. Combine with verified platform aggregates
            val combinedSummary = baseSummary.copy(
                totalStores = if (storesList.isNotEmpty()) storesList.size else baseSummary.totalStores.coerceAtLeast(1),
                suspiciousEventsCount = secEvents.size.coerceAtLeast(baseSummary.suspiciousEventsCount),
                suspiciousEvents = secEvents,
                storesList = storesList,
                lastUpdated = System.currentTimeMillis()
            )

            _platformMetrics.value = combinedSummary
            Result.success(combinedSummary)
        } catch (e: Exception) {
            _errorMessage.value = e.message
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Tracks feature usage locally and in Firebase Analytics.
     */
    fun recordFeatureUsage(feature: String, storeId: String) {
        analyticsManager?.trackFeatureUsed(feature, storeId)
    }

    /**
     * Tracks security alerts.
     */
    fun recordSecurityAlert(storeId: String, alertType: String, reason: String) {
        analyticsManager?.trackSecurityAlert(storeId, alertType, reason)
    }
}
