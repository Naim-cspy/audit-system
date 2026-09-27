package com.example.data.remote

import android.util.Log
import com.example.data.model.PlatformMetricsSummary
import com.example.data.model.SecurityEventEntity
import com.example.data.model.StorePlatformSummary
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Service for SaaS Platform Owner & Global Command Center.
 * Access is protected by Firestore Security Rules (requires `isPlatformAdmin()`).
 * Non-platform-admins receive PERMISSION_DENIED.
 */
class PlatformAnalyticsService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Fetches global platform metrics.
     * Enforced by Firestore rules: match /platform_analytics/{docId} { allow read: if isPlatformAdmin(); }
     */
    suspend fun getGlobalPlatformSummary(): Result<PlatformMetricsSummary> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("platform_analytics").document("global_summary")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val data = snapshot.data ?: emptyMap()
                        val totalStores = (data["total_stores"] as? Long)?.toInt() ?: 0
                        val activeStores = (data["active_stores"] as? Long)?.toInt() ?: 0
                        val totalUsers = (data["total_users"] as? Long)?.toInt() ?: 0
                        val totalSalesUsd = (data["total_sales_volume_usd"] as? Double)
                            ?: ((data["total_sales_volume_usd"] as? Long)?.toDouble() ?: 0.0)
                        val totalTx = (data["total_transactions_count"] as? Long)?.toInt() ?: 0
                        val totalSkus = (data["total_inventory_skus"] as? Long)?.toInt() ?: 0
                        val lowStock = (data["total_low_stock_alerts"] as? Long)?.toInt() ?: 0
                        val auditCount = (data["total_audit_events"] as? Long)?.toInt() ?: 0
                        val sessions = (data["total_sessions_count"] as? Long)?.toInt()
                        val logins = (data["total_logins_count"] as? Long)?.toInt()
                        val gaConfigured = (data["ga_reporting_configured"] as? Boolean) ?: (sessions != null)
                        val suspicious = (data["suspicious_events_count"] as? Long)?.toInt() ?: 0

                        @Suppress("UNCHECKED_CAST")
                        val featureUsageRaw = data["feature_usage"] as? Map<String, Long> ?: emptyMap()

                        continuation.resume(
                            Result.success(
                                PlatformMetricsSummary(
                                    totalStores = totalStores,
                                    activeStores = activeStores,
                                    totalUsers = totalUsers,
                                    totalSalesVolumeUsd = totalSalesUsd,
                                    totalTransactionsCount = totalTx,
                                    totalInventorySkus = totalSkus,
                                    totalLowStockAlerts = lowStock,
                                    totalAuditEventsCount = auditCount,
                                    totalSessionsCount = sessions,
                                    totalLoginsCount = logins,
                                    gaReportingConfigured = gaConfigured,
                                    featureUsageMap = featureUsageRaw,
                                    suspiciousEventsCount = suspicious,
                                    lastUpdated = snapshot.getTimestamp("last_updated")?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            )
                        )
                    } else {
                        // Return genuine empty platform summary when document is not initialized
                        continuation.resume(
                            Result.success(
                                PlatformMetricsSummary(
                                    totalStores = 0,
                                    activeStores = 0,
                                    totalUsers = 0,
                                    totalSalesVolumeUsd = 0.0,
                                    totalTransactionsCount = 0,
                                    totalInventorySkus = 0,
                                    totalLowStockAlerts = 0,
                                    totalAuditEventsCount = 0,
                                    totalSessionsCount = null,
                                    totalLoginsCount = null,
                                    gaReportingConfigured = false,
                                    featureUsageMap = emptyMap(),
                                    suspiciousEventsCount = 0,
                                    storesList = emptyList(),
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    if (exception is FirebaseFirestoreException && exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        continuation.resume(
                            Result.failure(
                                SecurityException("PERMISSION_DENIED: Only SaaS Platform Owners can read global platform analytics.")
                            )
                        )
                    } else {
                        continuation.resume(Result.failure(exception))
                    }
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Updates global platform summary in Firestore.
     * Enforced by Firestore rules: match /platform_analytics/{docId} { allow write: if isPlatformAdmin(); }
     */
    suspend fun saveGlobalPlatformSummary(summary: PlatformMetricsSummary): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            val map = hashMapOf(
                "total_stores" to summary.totalStores,
                "active_stores" to summary.activeStores,
                "total_users" to summary.totalUsers,
                "total_sales_volume_usd" to summary.totalSalesVolumeUsd,
                "total_transactions_count" to summary.totalTransactionsCount,
                "total_inventory_skus" to summary.totalInventorySkus,
                "total_low_stock_alerts" to summary.totalLowStockAlerts,
                "total_audit_events" to summary.totalAuditEventsCount,
                "total_sessions_count" to summary.totalSessionsCount,
                "total_logins_count" to summary.totalLoginsCount,
                "ga_reporting_configured" to summary.gaReportingConfigured,
                "suspicious_events_count" to summary.suspiciousEventsCount,
                "feature_usage" to summary.featureUsageMap,
                "last_updated" to com.google.firebase.Timestamp.now()
            )

            firestore.collection("platform_analytics").document("global_summary")
                .set(map, SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { continuation.resume(Result.failure(it)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Retrieves recent security events across the platform.
     * Protected by `match /security_events/{eventId} { allow read: if isPlatformAdmin(); }`
     */
    suspend fun getPlatformSecurityEvents(): Result<List<SecurityEventEntity>> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("security_events")
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    val list = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        SecurityEventEntity(
                            id = (data["id"] as? Long) ?: 0L,
                            store_id = data["store_id"] as? String ?: "UNKNOWN",
                            user_id = data["user_id"] as? String ?: "anonymous",
                            device_id = data["device_id"] as? String ?: "SYS",
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            event_type = data["event_type"] as? String ?: "SECURITY_LOG",
                            severity = data["severity"] as? String ?: "INFO",
                            endpoint_or_action = data["endpoint_or_action"] as? String ?: "API",
                            result = data["result"] as? String ?: "BLOCKED",
                            reason = data["reason"] as? String ?: ""
                        )
                    }
                    continuation.resume(Result.success(list))
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Lists registered stores for the SaaS Owner view.
     * Protected by `match /stores/{storeId} { allow read: if isPlatformAdmin(); }`
     */
    suspend fun getRegisteredStores(): Result<List<StorePlatformSummary>> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores")
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    val stores = snapshot.documents.map { doc ->
                        val data = doc.data ?: emptyMap()
                        StorePlatformSummary(
                            storeId = doc.id,
                            storeName = data["store_name"] as? String ?: "Store ${doc.id}",
                            region = data["region"] as? String ?: "Central District",
                            subscriptionStatus = data["subscription_status"] as? String ?: "ACTIVE",
                            userCount = (data["user_count"] as? Long)?.toInt() ?: 1,
                            salesCount = (data["sales_count"] as? Long)?.toInt() ?: 0,
                            revenueUsd = (data["revenue_usd"] as? Double) ?: 0.0,
                            inventoryCount = (data["inventory_count"] as? Long)?.toInt() ?: 0,
                            lastActiveTimestamp = (data["last_active"] as? Long) ?: System.currentTimeMillis()
                        )
                    }
                    continuation.resume(Result.success(stores))
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    companion object {
        private const val TAG = "PlatformAnalyticsService"
    }
}
