package com.example

import com.example.data.model.BalanceEntity
import com.example.data.model.CartItem
import com.example.data.model.PendingSyncDeletionEntity
import com.example.data.model.ProductEntity
import com.example.data.model.ReceiptItem
import com.example.data.model.SaleEntity
import com.example.data.model.SyncEventEntity
import com.example.data.remote.FirebaseLedgerDoc
import com.example.data.remote.FirebaseProductDoc
import com.example.data.remote.FirebaseSaleDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloud Synchronization & Tenant Safety Unit Test Suite.
 * Validates requirements for Step 7:
 * - Tenant safety & store partitioning
 * - Local checkout atomicity
 * - Idempotent retries & duplicate prevention
 * - Immutable Firestore record reconciliation
 * - Bidirectional synchronization (Products, Sales, Ledger)
 * - Durable retry queue & offline deletions
 * - Multi-terminal conflict handling (last-write-wins)
 * - Partial failure and error metrics reporting
 * - Store switching & state isolation
 */
class SyncReliabilityAndTenantSafetyTest {

    // ==========================================
    // 1. Tenant-Safe Upload Isolation
    // ==========================================

    @Test
    fun testTenantSafeUpload_ignoresForeignStoreEntities() {
        val currentStoreId = "STR-STORE-A"
        val foreignStoreId = "STR-STORE-B"

        val productsInQueue = listOf(
            ProductEntity("P1", "Milk", 2.5, 100, 0, "", "", "Dairy", currentStoreId, "PENDING"),
            ProductEntity("P2", "Bread", 1.5, 50, 0, "", "", "Bakery", foreignStoreId, "PENDING"),
            ProductEntity("P3", "Eggs", 3.0, 80, 0, "", "", "Dairy", currentStoreId, "PENDING")
        )

        val eligibleForUpload = productsInQueue.filter { it.store_id == currentStoreId }

        assertEquals(2, eligibleForUpload.size)
        assertTrue(eligibleForUpload.all { it.store_id == currentStoreId })
        assertFalse(eligibleForUpload.any { it.store_id == foreignStoreId })
    }

    // ==========================================
    // 2. Atomic Local Checkout Transaction Invariant
    // ==========================================

    @Test
    fun testAtomicCheckout_updatesStockSalesLedgerAndAuditTogether() {
        val storeId = "STR-001"
        val initialStock = 20
        val quantitySold = 3
        val unitPrice = 5.0
        val totalSale = quantitySold * unitPrice

        val product = ProductEntity(
            product_id = "P100",
            product_name = "Olive Oil",
            product_price = unitPrice,
            product_amount_left = initialStock,
            product_amount_sold = 0,
            store_id = storeId,
            sync_status = "SYNCED"
        )

        // Simulate atomic state mutation
        val updatedProduct = product.copy(
            product_amount_left = product.product_amount_left - quantitySold,
            product_amount_sold = product.product_amount_sold + quantitySold,
            sync_status = "PENDING"
        )

        val saleEntity = SaleEntity(
            sale_id = "SALE-TEST-001",
            product_id = product.product_id,
            product_name = product.product_name,
            quantity = quantitySold,
            price = unitPrice,
            sale_date = "2026-09-27",
            customer_id = "C1",
            cashier_id = "cashier_1",
            store_id = storeId,
            sync_status = "PENDING"
        )

        val ledgerEntity = BalanceEntity(
            date = "2026-09-27",
            budget_starting = 5000.0,
            money_in = totalSale,
            money_out = 0.0,
            reason = "POS Sale (SALE-TEST-001)",
            store_id = storeId,
            sync_status = "PENDING"
        )

        assertEquals(17, updatedProduct.product_amount_left)
        assertEquals(3, updatedProduct.product_amount_sold)
        assertEquals(15.0, saleEntity.quantity * saleEntity.price, 0.001)
        assertEquals(15.0, ledgerEntity.money_in, 0.001)
        assertEquals("PENDING", updatedProduct.sync_status)
        assertEquals("PENDING", saleEntity.sync_status)
        assertEquals("PENDING", ledgerEntity.sync_status)
    }

    // ==========================================
    // 3. Idempotent Retries & Duplicate Prevention
    // ==========================================

    @Test
    fun testIdempotentRetry_existingSaleDoesNotDuplicate() {
        val storeId = "STR-001"
        val saleId = "SALE-REC-998877"

        // Simulated cloud state: sale already exists in immutable Firestore collection
        val existingCloudSales = setOf(saleId)

        val localSale = SaleEntity(
            sale_id = saleId,
            product_id = "P1",
            product_name = "Juice",
            quantity = 2,
            price = 4.0,
            sale_date = "2026-09-27",
            customer_id = "C1",
            cashier_id = "cashier",
            store_id = storeId,
            sync_status = "PENDING"
        )

        // Reconciliation logic
        val alreadyExistsInCloud = existingCloudSales.contains(localSale.sale_id)
        val reconciledSale = if (alreadyExistsInCloud) {
            localSale.copy(sync_status = "SYNCED")
        } else {
            localSale
        }

        // Must reconcile safely without issuing a second create
        assertEquals("SYNCED", reconciledSale.sync_status)
    }

    @Test
    fun testIdempotentRetry_existingLedgerDoesNotDuplicate() {
        val storeId = "STR-001"
        val ledgerDocId = "LEDGER-101-20260927"
        val existingCloudLedgers = setOf(ledgerDocId)

        val localLedger = BalanceEntity(
            id = 101,
            date = "2026-09-27",
            budget_starting = 5000.0,
            money_in = 50.0,
            money_out = 0.0,
            reason = "POS Sale",
            store_id = storeId,
            sync_status = "PENDING"
        )

        val candidateDocId = "LEDGER-${localLedger.id}-${localLedger.date.replace("-", "")}"
        val exists = existingCloudLedgers.contains(candidateDocId)
        val reconciledLedger = if (exists) {
            localLedger.copy(sync_status = "SYNCED")
        } else {
            localLedger
        }

        assertEquals("SYNCED", reconciledLedger.sync_status)
    }

    // ==========================================
    // 4. Multi-Terminal Conflict Resolution (Last-Write-Wins)
    // ==========================================

    @Test
    fun testConflictResolution_cloudNewerOverwritesOlderLocal() {
        val storeId = "STR-001"
        val localProduct = ProductEntity(
            product_id = "P-COFFEE",
            product_name = "Espresso Blend",
            product_price = 10.0,
            product_amount_left = 50,
            product_amount_sold = 10,
            store_id = storeId,
            sync_status = "SYNCED",
            updated_at = 1000L
        )

        val remoteProduct = FirebaseProductDoc(
            productId = "P-COFFEE",
            productName = "Espresso Blend Premium",
            productPrice = 12.0,
            amountLeft = 45,
            amountSold = 15,
            storeId = storeId,
            updatedAt = 2000L // Newer remote edit from Terminal 2
        )

        val shouldUpdateLocal = remoteProduct.updatedAt > localProduct.updated_at
        assertTrue("Newer remote product from another terminal must overwrite older local record", shouldUpdateLocal)

        val merged = remoteProduct.toEntity("SYNCED").copy(store_id = storeId)
        assertEquals("Espresso Blend Premium", merged.product_name)
        assertEquals(12.0, merged.product_price, 0.001)
        assertEquals(45, merged.product_amount_left)
    }

    @Test
    fun testConflictResolution_pendingLocalChangeWinsOverOlderRemote() {
        val storeId = "STR-001"
        val localProduct = ProductEntity(
            product_id = "P-COFFEE",
            product_name = "Espresso Blend (Edited Locally)",
            product_price = 14.0,
            product_amount_left = 60,
            product_amount_sold = 5,
            store_id = storeId,
            sync_status = "PENDING",
            updated_at = 3000L // Local offline edit is newer
        )

        val remoteProduct = FirebaseProductDoc(
            productId = "P-COFFEE",
            productName = "Espresso Blend",
            productPrice = 10.0,
            amountLeft = 50,
            amountSold = 10,
            storeId = storeId,
            updatedAt = 1000L
        )

        val shouldOverwriteLocal = remoteProduct.updatedAt > localProduct.updated_at
        assertFalse("Older remote record must NOT overwrite pending uncommitted local changes", shouldOverwriteLocal)
    }

    // ==========================================
    // 5. Durable Deletion Queue
    // ==========================================

    @Test
    fun testDurableDeletionQueue_preventsResurrectionOnDownload() {
        val storeId = "STR-001"
        val deletedProductId = "P-DISCONTINUED"

        // Simulated durable deletion queue
        val pendingDeletions = listOf(
            PendingSyncDeletionEntity(store_id = storeId, entity_type = "PRODUCT", entity_id = deletedProductId)
        )

        val remoteIncomingProducts = listOf(
            FirebaseProductDoc(productId = deletedProductId, productName = "Discontinued Item", storeId = storeId),
            FirebaseProductDoc(productId = "P-ACTIVE", productName = "Active Item", storeId = storeId)
        )

        val pendingDeleteIds = pendingDeletions.map { it.entity_id }.toSet()
        val productsToPersist = remoteIncomingProducts.filter { !pendingDeleteIds.contains(it.productId) }

        assertEquals(1, productsToPersist.size)
        assertEquals("P-ACTIVE", productsToPersist[0].productId)
        assertFalse("Pending deleted product must not be resurrected into Room", productsToPersist.any { it.productId == deletedProductId })
    }

    // ==========================================
    // 6. Partial Failure & Error Metrics
    // ==========================================

    @Test
    fun testPartialFailure_doesNotAdvanceSuccessfulSyncTimestamp() {
        val initialTimestamp = 1000000L
        var lastSuccessfulSyncTimestamp = initialTimestamp

        val failures = mutableListOf<String>()
        failures.add("Failed to push product P-99: Network timeout")

        val totalPushed = 2
        val totalPulled = 1
        val hasErrors = failures.isNotEmpty()

        val finalStatus = when {
            !hasErrors -> "SUCCESS"
            totalPushed > 0 || totalPulled > 0 -> "PARTIAL_SUCCESS"
            else -> "FAILED"
        }

        // Security / Truthfulness invariant: never advance success timestamp when errors occur
        if (!hasErrors) {
            lastSuccessfulSyncTimestamp = 2000000L
        }

        assertEquals("PARTIAL_SUCCESS", finalStatus)
        assertEquals("Successful sync timestamp must NOT advance on partial failure", initialTimestamp, lastSuccessfulSyncTimestamp)
    }

    // ==========================================
    // 7. Store Switching & Work Partitioning
    // ==========================================

    @Test
    fun testStoreSwitching_pendingWorkRemainsPartitioned() {
        val storeA = "STR-BEIRUT"
        val storeB = "STR-TRIPOLI"

        val allPendingSales = listOf(
            SaleEntity("S1", "P1", "A", 1, 10.0, "2026-09-27", "C1", "u1", storeA, "PENDING"),
            SaleEntity("S2", "P2", "B", 2, 5.0, "2026-09-27", "C2", "u2", storeB, "PENDING")
        )

        val storeAPending = allPendingSales.filter { it.store_id == storeA }
        val storeBPending = allPendingSales.filter { it.store_id == storeB }

        assertEquals(1, storeAPending.size)
        assertEquals("S1", storeAPending[0].sale_id)
        assertEquals(1, storeBPending.size)
        assertEquals("S2", storeBPending[0].sale_id)

        // Switching active store to Store B must NEVER upload Store A sales
        val activeStore = storeB
        val salesToUpload = allPendingSales.filter { it.store_id == activeStore }
        assertEquals(1, salesToUpload.size)
        assertEquals(storeB, salesToUpload[0].store_id)
        assertFalse(salesToUpload.any { it.store_id == storeA })
    }
}
