package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.dao.AuditLogDao
import com.example.data.dao.BalanceDao
import com.example.data.dao.ProductDao
import com.example.data.dao.SaleDao
import com.example.data.dao.SyncEventDao
import com.example.data.db.AppDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.CartItem
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
import kotlinx.coroutines.CancellationException

/**
 * Sales & Point-of-Sale (POS) Repository.
 * Executes atomic local checkouts and pushes sales to stores/{storeId}/sales/{saleId}.
 */
class SalesRepository(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val balanceDao: BalanceDao,
    private val auditLogDao: AuditLogDao,
    private val syncEventDao: SyncEventDao,
    private val firestoreService: FirestoreService = FirestoreService()
) {

    val recentSales: Flow<List<SaleEntity>> = saleDao.getRecentSales(15)

    suspend fun batchCheckout(
        storeId: String,
        cartItems: List<CartItem>,
        customerId: String = "C101",
        cashierId: String = "admin",
        terminalId: String = "TERM-01"
    ): Result<Receipt> = withContext(Dispatchers.IO) {
        if (storeId.isBlank() || storeId == "UNAUTHENTICATED") {
            return@withContext Result.failure(IllegalStateException("Cannot checkout without an authenticated store session"))
        }
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Cart is empty"))
        }

        val normalizedCart = cartItems
            .groupBy { it.product.product_id.trim().uppercase() }
            .map { (_, lines) -> lines.first().copy(quantity = lines.sumOf { it.quantity }) }
        if (normalizedCart.any { it.quantity <= 0 }) {
            return@withContext Result.failure(IllegalArgumentException("Item quantities must be positive"))
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val nowMs = System.currentTimeMillis()

        val committed = try {
            db.withTransaction {
                val freshItems = normalizedCart.map { item ->
                    val freshProduct = productDao.getProductById(item.product.product_id)
                        ?: throw IllegalStateException("Product ${item.product.product_name} not found")
                    if (freshProduct.store_id != storeId) {
                        throw SecurityException("Product ${freshProduct.product_id} is not assigned to the active store")
                    }
                    if (freshProduct.product_amount_left < item.quantity) {
                        throw IllegalStateException(
                            "Insufficient stock for ${freshProduct.product_name}. Available: ${freshProduct.product_amount_left}, requested: ${item.quantity}"
                        )
                    }
                    freshProduct to item.quantity
                }

                val totalAmount = freshItems.sumOf { (product, quantity) -> product.product_price * quantity }
                val receiptId = "REC-${UUID.randomUUID().toString().take(8).uppercase()}"
                val receiptItems = freshItems.map { (product, quantity) ->
                    ReceiptItem(
                        productId = product.product_id,
                        productName = product.product_name,
                        price = product.product_price,
                        quantity = quantity,
                        subtotal = product.product_price * quantity,
                        remainingStock = product.product_amount_left - quantity
                    )
                }

                val salesToInsert = freshItems.map { (freshProduct, quantity) ->
                    productDao.update(
                        freshProduct.copy(
                            product_amount_left = freshProduct.product_amount_left - quantity,
                            product_amount_sold = freshProduct.product_amount_sold + quantity,
                            date_sold = todayStr,
                            updated_at = nowMs,
                            sync_status = "PENDING"
                        )
                    )
                    SaleEntity(
                        sale_id = "SALE-${UUID.randomUUID().toString().take(10).uppercase()}",
                        product_id = freshProduct.product_id,
                        product_name = freshProduct.product_name,
                        quantity = quantity,
                        price = freshProduct.product_price,
                        sale_date = todayStr,
                        customer_id = customerId,
                        cashier_id = cashierId,
                        store_id = storeId,
                        sync_status = "PENDING",
                        created_at = nowMs
                    )
                }
                salesToInsert.forEach { saleDao.insert(it) }

                val lastBalance = balanceDao.getLatestBalance()
                val startingBudget = lastBalance?.let {
                    it.budget_starting + it.money_in - it.money_out
                } ?: 5000.0
                balanceDao.insert(
                    BalanceEntity(
                        date = todayStr,
                        budget_starting = startingBudget,
                        money_in = totalAmount,
                        money_out = 0.0,
                        reason = "POS Sale ($receiptId)",
                        store_id = storeId,
                        sync_status = "PENDING",
                        created_at = nowMs
                    )
                )

                auditLogDao.insert(
                    AuditLogEntity(
                        action = "POS_CHECKOUT",
                        details = "Receipt " + receiptId + ": " + salesToInsert.size + " items, total " + "$" + "%.2f".format(totalAmount),
                        store_id = storeId,
                        user_id = cashierId,
                        timestamp = nowMs
                    )
                )

                val receipt = Receipt(
                    receiptId = receiptId,
                    date = todayStr,
                    timestamp = timestampStr,
                    customerId = customerId,
                    items = receiptItems,
                    total = totalAmount,
                    itemCount = normalizedCart.sumOf { it.quantity }
                )
                receipt to salesToInsert
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val (receipt, salesToInsert) = committed
        var cloudSuccess = true
        for (sale in salesToInsert) {
            val cloudResult = firestoreService.recordSale(storeId, FirebaseSaleDoc.fromEntity(sale))
            if (!cloudResult.isSuccess) cloudSuccess = false
        }

        val syncId = "SYNC-${UUID.randomUUID().toString().take(12)}"
        val syncEvent = SyncEventEntity(
            sync_id = syncId,
            store_id = storeId,
            device_id = terminalId,
            user_id = cashierId,
            operation = "CHECKOUT",
            entity_type = "SALE",
            entity_id = receipt.receiptId,
            status = if (cloudSuccess) "SUCCESS" else "PENDING",
            error_code = if (cloudSuccess) null else "OFFLINE_QUEUED",
            error_message = if (cloudSuccess) null else "Sale saved locally in Room, awaiting cloud connectivity",
            timestamp = nowMs
        )
        syncEventDao.insert(syncEvent)

        if (cloudSuccess) {
            salesToInsert.forEach { saleDao.update(it.copy(sync_status = "SYNCED")) }
            firestoreService.recordSyncEvent(storeId, FirebaseSyncEventDoc.fromEntity(syncEvent))
        }

        Result.success(receipt)
    }
}
