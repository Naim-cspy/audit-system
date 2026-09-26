package com.example.data.repository

import com.example.data.dao.AuditLogDao
import com.example.data.dao.BalanceDao
import com.example.data.dao.ProductDao
import com.example.data.dao.SaleDao
import com.example.data.dao.SecurityEventDao
import com.example.data.dao.SyncEventDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.SecurityEventEntity
import com.example.data.model.SyncEventEntity
import com.example.data.remote.FirebaseLedgerDoc
import com.example.data.remote.FirebaseProductDoc
import com.example.data.remote.FirebaseSaleDoc
import com.example.data.remote.FirebaseSyncEventDoc
import com.example.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Cloud Synchronization Repository.
 * Orchestrates bidirectional replication between Room local database and Cloud Firestore.
 * Isolates all tenant data within stores/{storeId}/...
 */
class SyncRepository(
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val balanceDao: BalanceDao,
    private val auditLogDao: AuditLogDao,
    private val syncEventDao: SyncEventDao,
    private val securityEventDao: SecurityEventDao,
    private val firestoreService: FirestoreService = FirestoreService()
) {

    val allSyncEvents: Flow<List<SyncEventEntity>> = syncEventDao.getRecentSyncEvents(40)
    val allSecurityEvents: Flow<List<SecurityEventEntity>> = securityEventDao.getRecentSecurityEvents(40)
    val failedSyncCount: Flow<Int> = syncEventDao.getFailedCount()
    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getRecentLogs(30)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    /**
     * Executes full bidirectional synchronization for the authenticated store:
     * 1. Pushes pending local changes (Products, Sales, Ledger) to Firestore
     * 2. Pulls remote products and ledger entries from Firestore into Room
     * 3. Emits comprehensive SyncEvent records
     */
    suspend fun syncAll(storeId: String, terminalId: String = "TERM-01", userId: String = "admin"): Result<String> = withContext(Dispatchers.IO) {
        if (storeId.isBlank() || storeId == "UNAUTHENTICATED") {
            return@withContext Result.failure(IllegalStateException("Cannot synchronize without an authenticated store session"))
        }

        _isSyncing.value = true
        val syncId = "SYNC-${UUID.randomUUID().toString().take(12)}"
        var pushSuccessCount = 0
        var pullSuccessCount = 0
        var operationFailures = 0
        val failureMessages = mutableListOf<String>()

        try {
            // --- 1. Push Pending Local Products to Firestore ---
            val allLocalProducts = productDao.getAllProducts().first()
            val pendingProducts = allLocalProducts.filter { it.sync_status == "PENDING" && it.store_id == storeId }
            for (p in pendingProducts) {
                val cloudProduct = FirebaseProductDoc.fromEntity(p)
                val res = firestoreService.upsertProduct(storeId, cloudProduct)
                if (res.isSuccess) {
                    productDao.update(p.copy(sync_status = "SYNCED"))
                    pushSuccessCount++
                } else {
                    operationFailures++
                    failureMessages.add(res.exceptionOrNull()?.message ?: "Product upload failed: ${p.product_id}")
                }
            }

            // --- 2. Push Pending Sales to Firestore ---
            val allLocalSales = saleDao.getAllSales().first()
            val pendingSales = allLocalSales.filter { it.sync_status == "PENDING" && it.store_id == storeId }
            for (s in pendingSales) {
                val cloudSale = FirebaseSaleDoc.fromEntity(s)
                val res = firestoreService.recordSale(storeId, cloudSale)
                if (res.isSuccess) {
                    saleDao.update(s.copy(sync_status = "SYNCED"))
                    pushSuccessCount++
                } else {
                    operationFailures++
                    failureMessages.add(res.exceptionOrNull()?.message ?: "Sale upload failed: ${s.sale_id}")
                }
            }

            // --- 3. Push Pending Ledger to Firestore ---
            val allLocalLedger = balanceDao.getAllHistory().first()
            val pendingLedger = allLocalLedger.filter { it.sync_status == "PENDING" && it.store_id == storeId }
            for (b in pendingLedger) {
                val cloudLedger = FirebaseLedgerDoc.fromEntity(b)
                val res = firestoreService.recordLedger(storeId, cloudLedger)
                if (res.isSuccess) {
                    balanceDao.update(b.copy(sync_status = "SYNCED"))
                    pushSuccessCount++
                } else {
                    operationFailures++
                    failureMessages.add(res.exceptionOrNull()?.message ?: "Ledger upload failed: ${b.id}")
                }
            }

            // --- 4. Pull Remote Cloud Products from Firestore into Room ---
            val cloudProductsResult = firestoreService.fetchProducts(storeId)
            if (cloudProductsResult.isSuccess) {
                val cloudProducts = cloudProductsResult.getOrThrow()
                for (cp in cloudProducts) {
                    val existing = productDao.getProductById(cp.productId)
                    if (existing == null) {
                        productDao.insert(cp.toEntity(syncStatus = "SYNCED"))
                        pullSuccessCount++
                    } else if (existing.store_id == storeId && cp.updatedAt > existing.updated_at) {
                        productDao.update(cp.toEntity(syncStatus = "SYNCED"))
                        pullSuccessCount++
                    }
                }
            } else {
                operationFailures++
                failureMessages.add(cloudProductsResult.exceptionOrNull()?.message ?: "Product download failed")
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()

            val syncEvent = SyncEventEntity(
                sync_id = syncId,
                store_id = storeId,
                device_id = terminalId,
                user_id = userId,
                operation = "BIDIRECTIONAL_SYNC",
                entity_type = "STORE_DATA",
                entity_id = storeId,
                status = if (operationFailures == 0) "SUCCESS" else "FAILED",
                error_code = if (operationFailures == 0) null else "SYNC_PARTIAL_FAILURE",
                error_message = failureMessages.take(5).joinToString("; ").takeIf { it.isNotBlank() },
                timestamp = System.currentTimeMillis()
            )
            syncEventDao.insert(syncEvent)
            firestoreService.recordSyncEvent(storeId, FirebaseSyncEventDoc.fromEntity(syncEvent))

            auditLogDao.insert(
                AuditLogEntity(
                    action = if (operationFailures == 0) "CLOUD_SYNC_SUCCESS" else "CLOUD_SYNC_PARTIAL_FAILURE",
                    details = if (operationFailures == 0) {
                        "Synced with stores/$storeId: $pushSuccessCount pushed, $pullSuccessCount pulled"
                    } else {
                        "Sync incomplete for stores/$storeId: $pushSuccessCount pushed, $pullSuccessCount pulled; ${failureMessages.take(3).joinToString("; ")}"
                    },
                    store_id = storeId,
                    user_id = userId
                )
            )

            if (operationFailures == 0) {
                Result.success("Synchronized successfully ($pushSuccessCount uploaded, $pullSuccessCount pulled)")
            } else {
                Result.failure(IllegalStateException("Sync incomplete: $operationFailures operations failed. " +
                    failureMessages.take(3).joinToString("; ")))
            }
        } catch (e: Exception) {
            val syncEvent = SyncEventEntity(
                sync_id = syncId,
                store_id = storeId,
                device_id = terminalId,
                user_id = userId,
                operation = "BIDIRECTIONAL_SYNC",
                entity_type = "STORE_DATA",
                entity_id = storeId,
                status = "FAILED",
                error_code = "SYNC_NETWORK_ERROR",
                error_message = e.message ?: "Unknown sync error",
                timestamp = System.currentTimeMillis()
            )
            syncEventDao.insert(syncEvent)

            auditLogDao.insert(
                AuditLogEntity(
                    action = "CLOUD_SYNC_FAILED",
                    details = "Sync failed for stores/$storeId: ${e.message}",
                    store_id = storeId,
                    user_id = userId
                )
            )

            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
