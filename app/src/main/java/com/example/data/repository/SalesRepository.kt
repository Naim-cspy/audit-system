package com.example.data.repository

import com.example.data.dao.AuditLogDao
import com.example.data.dao.BalanceDao
import com.example.data.dao.ProductDao
import com.example.data.dao.SaleDao
import com.example.data.dao.SyncEventDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.CartItem
import com.example.data.model.ProductEntity
import com.example.data.model.Receipt
import com.example.data.model.ReceiptItem
import com.example.data.model.SaleEntity
import com.example.data.model.SyncEventEntity
import com.example.data.remote.FirebaseSaleDoc
import com.example.data.remote.FirebaseSyncEventDoc
import com.example.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Sales & Point-of-Sale (POS) Repository.
 * Executes atomic checkouts locally and pushes sales to stores/{storeId}/sales/{saleId}.
 */
class SalesRepository(
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val balanceDao: BalanceDao,
    private val auditLogDao: AuditLogDao,
    private val syncEventDao: SyncEventDao,
    private val firestoreService: FirestoreService = FirestoreService()
) {

    fun getRecentSales(storeId: String, limit: Int = 15): Flow<List<SaleEntity>> {
        return saleDao.getRecentSales(storeId, limit)
    }

    suspend fun batchCheckout(
        storeId: String,
        cartItems: List<CartItem>,
        customerId: String = "C101",
        cashierId: String = "admin",
        terminalId: String = "TERM-01"
    ): Result<Receipt> = withContext(Dispatchers.IO) {
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Cart is empty"))
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val nowMs = System.currentTimeMillis()

        // 1. Validate stock for all items
        val processedReceiptItems = mutableListOf<ReceiptItem>()
        var totalAmount = 0.0

        for (item in cartItems) {
            val freshProduct = productDao.getProductById(storeId, item.product.product_id)
                ?: return@withContext Result.failure(IllegalStateException("Product ${item.product.product_name} not found in store $storeId"))

            if (freshProduct.product_amount_left < item.quantity) {
                return@withContext Result.failure(
                    IllegalStateException("Insufficient stock for ${freshProduct.product_name}. Available: ${freshProduct.product_amount_left}, requested: ${item.quantity}")
                )
            }

            val subtotal = freshProduct.product_price * item.quantity
            totalAmount += subtotal
            processedReceiptItems.add(
                ReceiptItem(
                    productId = freshProduct.product_id,
                    productName = freshProduct.product_name,
                    price = freshProduct.product_price,
                    quantity = item.quantity,
                    subtotal = subtotal,
                    remainingStock = freshProduct.product_amount_left - item.quantity
                )
            )
        }

        val receiptId = "REC-${UUID.randomUUID().toString().take(8).uppercase()}"
        val salesToInsert = mutableListOf<SaleEntity>()

        // 2. Decrement stock & prepare sales records
        for (item in cartItems) {
            val freshProduct = productDao.getProductById(storeId, item.product.product_id)!!
            val newAmountLeft = freshProduct.product_amount_left - item.quantity
            val newAmountSold = freshProduct.product_amount_sold + item.quantity

            val updatedProduct = freshProduct.copy(
                product_amount_left = newAmountLeft,
                product_amount_sold = newAmountSold,
                date_sold = todayStr,
                updated_at = nowMs,
                sync_status = "PENDING"
            )
            productDao.update(updatedProduct)

            val sale = SaleEntity(
                sale_id = "SALE-${UUID.randomUUID().toString().take(10).uppercase()}",
                product_id = freshProduct.product_id,
                product_name = freshProduct.product_name,
                quantity = item.quantity,
                price = freshProduct.product_price,
                sale_date = todayStr,
                customer_id = customerId,
                cashier_id = cashierId,
                store_id = storeId,
                sync_status = "PENDING",
                created_at = nowMs
            )
            salesToInsert.add(sale)
        }

        // 3. Insert sales into local Room cache
        salesToInsert.forEach { saleDao.insert(it) }

        // 4. Update Financial Ledger
        val lastBalance = balanceDao.getLatestBalance(storeId)
        val startingBudget = lastBalance?.budget_starting ?: 5000.0
        val newBalance = BalanceEntity(
            date = todayStr,
            budget_starting = startingBudget,
            money_in = totalAmount,
            money_out = 0.0,
            reason = "POS Sale ($receiptId)",
            store_id = storeId,
            sync_status = "PENDING",
            created_at = nowMs
        )
        balanceDao.insert(newBalance)

        // 5. Insert Audit Log
        val audit = AuditLogEntity(
            action = "POS_CHECKOUT",
            details = "Receipt $receiptId: ${salesToInsert.size} items, total \$${"%.2f".format(totalAmount)}",
            store_id = storeId,
            user_id = cashierId,
            timestamp = nowMs
        )
        auditLogDao.insert(audit)

        // 6. Push Sales & Sync Events to Cloud Firestore
        val syncId = "SYNC-${UUID.randomUUID().toString().take(12)}"
        var cloudSuccess = true

        for (sale in salesToInsert) {
            val cloudSale = FirebaseSaleDoc.fromEntity(sale)
            val res = firestoreService.recordSale(storeId, cloudSale)
            if (!res.isSuccess) cloudSuccess = false
        }

        val syncStatus = if (cloudSuccess) "SUCCESS" else "PENDING"
        val syncEvent = SyncEventEntity(
            sync_id = syncId,
            store_id = storeId,
            device_id = terminalId,
            user_id = cashierId,
            operation = "CHECKOUT",
            entity_type = "SALE",
            entity_id = receiptId,
            status = syncStatus,
            error_code = if (!cloudSuccess) "OFFLINE_QUEUED" else null,
            error_message = if (!cloudSuccess) "Sale saved locally in Room, awaiting cloud connectivity" else null,
            timestamp = nowMs
        )
        syncEventDao.insert(syncEvent)

        if (cloudSuccess) {
            salesToInsert.forEach { saleDao.update(it.copy(sync_status = "SYNCED")) }
            firestoreService.recordSyncEvent(storeId, FirebaseSyncEventDoc.fromEntity(syncEvent))
        }

        val receipt = Receipt(
            receiptId = receiptId,
            date = todayStr,
            timestamp = timestampStr,
            customerId = customerId,
            items = processedReceiptItems,
            total = totalAmount,
            itemCount = cartItems.sumOf { it.quantity }
        )

        Result.success(receipt)
    }
}
