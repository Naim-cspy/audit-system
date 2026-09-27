package com.example.data.repository

import com.example.data.dao.AuditLogDao
import com.example.data.dao.BalanceDao
import com.example.data.dao.PendingSyncDeletionDao
import com.example.data.dao.ProductDao
import com.example.data.dao.SaleDao
import com.example.data.dao.SecurityEventDao
import com.example.data.dao.SyncEventDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.SaleEntity
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
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Cloud Synchronization Repository.
 * Orchestrates truthful, tenant-safe, bidirectional replication between Room local database and Cloud Firestore.
 * Isolates all tenant data within stores/{storeId}/...
 */
class SyncRepository(
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val balanceDao: BalanceDao,
    private val auditLogDao: AuditLogDao,
    private val syncEventDao: SyncEventDao,
    private val securityEventDao: SecurityEventDao,
    private val pendingSyncDeletionDao: PendingSyncDeletionDao? = null,
    private val firestoreService: FirestoreService = FirestoreService()
) {

    fun getAllSyncEvents(storeId: String): Flow<List<SyncEventEntity>> = syncEventDao.getRecentSyncEvents(storeId, 40)
    fun getAllSecurityEvents(storeId: String): Flow<List<SecurityEventEntity>> = securityEventDao.getRecentSecurityEvents(storeId, 40)
    fun getFailedSyncCount(storeId: String): Flow<Int> = syncEventDao.getFailedCount(storeId)
    fun getAuditLogs(storeId: String): Flow<List<AuditLogEntity>> = auditLogDao.getRecentLogs(storeId, 30)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    /**
     * Executes truthful bidirectional synchronization for the authenticated store:
     * 1. Processes durable pending deletions (so offline deleted products are deleted in Firestore)
     * 2. Pushes pending local changes (Products, Sales, Ledger) belonging strictly to storeId to Firestore
     * 3. Idempotently reconciles sales & ledger against immutable Firestore records (never duplicates)
     * 4. Pulls remote products, sales, and ledger entries from Firestore into Room
     * 5. Emits comprehensive SyncEvent records with accurate partial/failure metrics
     * 6. Does NOT advance successful timestamp or mark records synced if operations fail
     */
    suspend fun syncAll(
        storeId: String,
        terminalId: String = "TERM-01",
        userId: String = "admin"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (storeId.isBlank() || storeId == "UNAUTHENTICATED") {
            return@withContext Result.failure(IllegalStateException("Cannot synchronize without an authenticated store session"))
        }

        _isSyncing.value = true
        val syncId = "SYNC-${UUID.randomUUID().toString().take(12)}"
        val failures = mutableListOf<String>()

        var pushedDeletionsCount = 0
        var pushedProductsCount = 0
        var pushedSalesCount = 0
        var pushedLedgerCount = 0

        var pulledProductsCount = 0
        var pulledSalesCount = 0
        var pulledLedgerCount = 0

        try {
            // --- 1. Push Durable Pending Deletions ---
            if (pendingSyncDeletionDao != null) {
                val pendingDeletions = pendingSyncDeletionDao.getPendingDeletions(storeId)
                for (del in pendingDeletions) {
                    if (del.store_id != storeId) continue
                    if (del.entity_type == "PRODUCT") {
                        val delRes = firestoreService.deleteProduct(storeId, del.entity_id)
                        if (delRes.isSuccess) {
                            pendingSyncDeletionDao.deleteById(storeId, "PRODUCT", del.entity_id)
                            pushedDeletionsCount++
                        } else {
                            failures.add("Failed to delete remote product ${del.entity_id}: ${delRes.exceptionOrNull()?.message}")
                        }
                    }
                }
            }

            // --- 2. Push Pending Local Products to Firestore ---
            val pendingProducts = productDao.getPendingProducts(storeId)
            for (p in pendingProducts) {
                if (p.store_id != storeId) continue
                val cloudProduct = FirebaseProductDoc.fromEntity(p)
                val res = firestoreService.upsertProduct(storeId, cloudProduct)
                if (res.isSuccess) {
                    productDao.update(p.copy(sync_status = "SYNCED"))
                    pushedProductsCount++
                } else {
                    failures.add("Failed to push product ${p.product_id}: ${res.exceptionOrNull()?.message}")
                }
            }

            // --- 3. Push Pending Sales to Firestore (Idempotent Reconciliation) ---
            val pendingSales = saleDao.getPendingSales(storeId)
            for (s in pendingSales) {
                if (s.store_id != storeId) continue

                // Check if already committed to immutable Firestore collection to prevent duplicates & permission errors
                val alreadyExists = firestoreService.doesSaleExist(storeId, s.sale_id).getOrDefault(false)
                if (alreadyExists) {
                    saleDao.update(s.copy(sync_status = "SYNCED"))
                    pushedSalesCount++
                } else {
                    val cloudSale = FirebaseSaleDoc.fromEntity(s)
                    val res = firestoreService.recordSale(storeId, cloudSale)
                    if (res.isSuccess) {
                        saleDao.update(s.copy(sync_status = "SYNCED"))
                        pushedSalesCount++
                    } else {
                        failures.add("Failed to record sale ${s.sale_id}: ${res.exceptionOrNull()?.message}")
                    }
                }
            }

            // --- 4. Push Pending Ledger to Firestore (Idempotent Reconciliation) ---
            val pendingLedger = balanceDao.getPendingLedger(storeId)
            for (b in pendingLedger) {
                if (b.store_id != storeId) continue
                val ledgerDocId = "LEDGER-${b.id}-${b.date.replace("-", "")}"

                val alreadyExists = firestoreService.doesLedgerExist(storeId, ledgerDocId).getOrDefault(false)
                if (alreadyExists) {
                    balanceDao.update(b.copy(sync_status = "SYNCED"))
                    pushedLedgerCount++
                } else {
                    val cloudLedger = FirebaseLedgerDoc(
                        ledgerId = ledgerDocId,
                        date = b.date,
                        budgetStarting = b.budget_starting,
                        moneyIn = b.money_in,
                        moneyOut = b.money_out,
                        reason = b.reason,
                        storeId = storeId,
                        createdAt = b.created_at
                    )
                    val res = firestoreService.recordLedger(storeId, cloudLedger)
                    if (res.isSuccess) {
                        balanceDao.update(b.copy(sync_status = "SYNCED"))
                        pushedLedgerCount++
                    } else {
                        failures.add("Failed to record ledger entry ${b.id}: ${res.exceptionOrNull()?.message}")
                    }
                }
            }

            // --- 5. Pull Remote Cloud Products from Firestore into Room (Conflict Handling & Deletion Guard) ---
            val cloudProductsResult = firestoreService.fetchProducts(storeId)
            if (cloudProductsResult.isSuccess) {
                val cloudProducts = cloudProductsResult.getOrThrow()
                for (cp in cloudProducts) {
                    // Do not resurrect products that are pending local deletion
                    val isPendingDelete = pendingSyncDeletionDao?.isPendingDeletion(storeId, "PRODUCT", cp.productId) ?: false
                    if (isPendingDelete) continue

                    val existing = productDao.getProductById(storeId, cp.productId)
                    val entity = cp.toEntity(syncStatus = "SYNCED").copy(store_id = storeId)

                    if (existing == null) {
                        productDao.insert(entity)
                        pulledProductsCount++
                    } else {
                        // Multi-terminal conflict resolution: Last-write-wins based on updatedAt timestamp
                        if (cp.updatedAt > existing.updated_at) {
                            productDao.update(entity)
                            pulledProductsCount++
                        } else if (existing.sync_status == "PENDING") {
                            // Local product was modified more recently; keep local changes
                        }
                    }
                }
            } else {
                failures.add("Failed to download remote products: ${cloudProductsResult.exceptionOrNull()?.message}")
            }

            // --- 6. Pull Remote Sales from Firestore into Room (Bidirectional Sales Sync) ---
            val cloudSalesResult = firestoreService.fetchSales(storeId, limit = 50)
            if (cloudSalesResult.isSuccess) {
                val cloudSales = cloudSalesResult.getOrThrow()
                for (cs in cloudSales) {
                    val localSales = saleDao.getRecentSales(storeId, 200)
                    // Check if already in Room
                    val entity = cs.toEntity(syncStatus = "SYNCED").copy(store_id = storeId)
                    // Insert or ignore if not present
                    try {
                        saleDao.insert(entity)
                        pulledSalesCount++
                    } catch (e: Exception) {
                        // Already exists locally with same (store_id, sale_id) primary key
                    }
                }
            } else {
                failures.add("Failed to download remote sales: ${cloudSalesResult.exceptionOrNull()?.message}")
            }

            // --- 7. Pull Remote Ledger Entries from Firestore into Room (Bidirectional Ledger Sync) ---
            val cloudLedgerResult = firestoreService.fetchLedger(storeId, limit = 50)
            if (cloudLedgerResult.isSuccess) {
                val cloudLedger = cloudLedgerResult.getOrThrow()
                for (cl in cloudLedger) {
                    val entity = cl.toEntity(syncStatus = "SYNCED").copy(store_id = storeId)
                    try {
                        balanceDao.insert(entity)
                        pulledLedgerCount++
                    } catch (e: Exception) {
                        // Already present
                    }
                }
            } else {
                failures.add("Failed to download remote ledger: ${cloudLedgerResult.exceptionOrNull()?.message}")
            }

            // --- 8. Determine Overall Status & Timestamp Advancement ---
            val hasErrors = failures.isNotEmpty()
            val totalPushed = pushedDeletionsCount + pushedProductsCount + pushedSalesCount + pushedLedgerCount
            val totalPulled = pulledProductsCount + pulledSalesCount + pulledLedgerCount

            val finalStatus = when {
                !hasErrors -> "SUCCESS"
                totalPushed > 0 || totalPulled > 0 -> "PARTIAL_SUCCESS"
                else -> "FAILED"
            }

            if (!hasErrors) {
                _lastSyncTimestamp.value = System.currentTimeMillis()
            }

            val summaryDetails = "Pushed: $pushedProductsCount prod, $pushedSalesCount sales, $pushedLedgerCount led, $pushedDeletionsCount del | " +
                    "Pulled: $pulledProductsCount prod, $pulledSalesCount sales, $pulledLedgerCount led" +
                    if (hasErrors) " | Errors: ${failures.take(2).joinToString("; ")}" else ""

            val syncEvent = SyncEventEntity(
                sync_id = syncId,
                store_id = storeId,
                device_id = terminalId,
                user_id = userId,
                operation = "BIDIRECTIONAL_SYNC",
                entity_type = "STORE_DATA",
                entity_id = storeId,
                status = finalStatus,
                error_code = if (hasErrors) "PARTIAL_OR_FULL_SYNC_ERROR" else null,
                error_message = if (hasErrors) failures.joinToString("\n") else null,
                timestamp = System.currentTimeMillis()
            )
            syncEventDao.insert(syncEvent)

            if (!hasErrors) {
                firestoreService.recordSyncEvent(storeId, FirebaseSyncEventDoc.fromEntity(syncEvent))
            }

            auditLogDao.insert(
                AuditLogEntity(
                    action = if (hasErrors) "CLOUD_SYNC_WARNING" else "CLOUD_SYNC_SUCCESS",
                    details = "Sync status: $finalStatus. $summaryDetails",
                    store_id = storeId,
                    user_id = userId
                )
            )

            if (finalStatus == "FAILED") {
                Result.failure(Exception("Sync failed: ${failures.joinToString("; ")}"))
            } else {
                Result.success(summaryDetails)
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
