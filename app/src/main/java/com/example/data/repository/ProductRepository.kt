package com.example.data.repository

import com.example.data.analytics.AnalyticsManager
import com.example.data.dao.AuditLogDao
import com.example.data.dao.PendingSyncDeletionDao
import com.example.data.dao.ProductDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.PendingSyncDeletionEntity
import com.example.data.model.ProductEntity
import com.example.data.remote.FirebaseProductDoc
import com.example.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Product & Inventory Repository.
 * Manages local inventory in Room (for zero-latency scanning & offline operations)
 * and synchronizes with Cloud Firestore under stores/{storeId}/products/{productId}.
 */
class ProductRepository(
    private val productDao: ProductDao,
    private val auditLogDao: AuditLogDao,
    private val pendingSyncDeletionDao: PendingSyncDeletionDao? = null,
    val firestoreService: FirestoreService = FirestoreService(),
    val analyticsManager: AnalyticsManager? = null
) {

    fun getAllProducts(storeId: String): Flow<List<ProductEntity>> {
        return productDao.getAllProducts(storeId)
    }

    fun getStockWarnings(storeId: String, threshold: Int = 50): Flow<List<ProductEntity>> {
        return productDao.getStockWarnings(storeId, threshold)
    }

    fun searchProducts(storeId: String, query: String): Flow<List<ProductEntity>> {
        return productDao.searchProducts(storeId, query)
    }

    suspend fun lookupProduct(storeId: String, code: String): ProductEntity? = withContext(Dispatchers.IO) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return@withContext null
        productDao.getProductById(storeId, trimmed)
    }

    suspend fun addProduct(
        storeId: String,
        id: String,
        name: String,
        price: Double,
        amountLeft: Int,
        amountSold: Int = 0,
        dateFilled: String = "",
        type: String = "General"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = id.trim()
        val cleanName = name.trim()

        if (cleanId.isEmpty() || cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Product ID and Name cannot be empty"))
        }
        if (price <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Price must be greater than zero"))
        }
        if (amountLeft < 0) {
            return@withContext Result.failure(IllegalArgumentException("Amount left cannot be negative"))
        }

        val existing = productDao.getProductById(storeId, cleanId)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Product with ID '$cleanId' already exists in store '$storeId'"))
        }

        val now = System.currentTimeMillis()
        val product = ProductEntity(
            product_id = cleanId,
            product_name = cleanName,
            product_price = price,
            product_amount_left = amountLeft,
            product_amount_sold = amountSold,
            date_sold = "",
            date_filled = dateFilled,
            product_type = type,
            store_id = storeId,
            sync_status = "PENDING",
            created_at = now,
            updated_at = now
        )

        // 1. Write to local Room cache
        productDao.insert(product)

        // 2. Upload to Cloud Firestore under tenant path
        val cloudDoc = FirebaseProductDoc.fromEntity(product)
        val cloudResult = firestoreService.upsertProduct(storeId, cloudDoc)
        if (cloudResult.isSuccess) {
            productDao.update(product.copy(sync_status = "SYNCED"))
        }

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_PRODUCT",
                details = "Product added: $cleanName ($cleanId) - \$$price, qty: $amountLeft",
                store_id = storeId
            )
        )
        analyticsManager?.trackInventoryUpdate(storeId, "ADD_PRODUCT", cleanId)

        Result.success(Unit)
    }

    suspend fun updatePrice(storeId: String, productId: String, newPrice: Double): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = productId.trim()
        if (newPrice <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Price must be greater than zero"))
        }

        val product = productDao.getProductById(storeId, cleanId)
            ?: return@withContext Result.failure(IllegalArgumentException("Product not found in store '$storeId'"))

        val oldPrice = product.product_price
        val now = System.currentTimeMillis()
        val updated = product.copy(
            product_price = newPrice,
            sync_status = "PENDING",
            updated_at = now
        )

        productDao.update(updated)

        // Push to Firestore
        val cloudResult = firestoreService.upsertProduct(storeId, FirebaseProductDoc.fromEntity(updated))
        if (cloudResult.isSuccess) {
            productDao.update(updated.copy(sync_status = "SYNCED"))
        }

        auditLogDao.insert(
            AuditLogEntity(
                action = "UPDATE_PRICE",
                details = "Price updated for '${product.product_name}' ($cleanId): \$$oldPrice -> \$$newPrice",
                store_id = storeId
            )
        )
        analyticsManager?.trackInventoryUpdate(storeId, "UPDATE_PRICE", cleanId)

        Result.success(Unit)
    }

    suspend fun removeProduct(storeId: String, productId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = productId.trim()
        val product = productDao.getProductById(storeId, cleanId)
            ?: return@withContext Result.failure(IllegalArgumentException("Product not found in store '$storeId'"))

        productDao.delete(product)

        // Durable deletion queue ensures deletions survive network loss and restarts
        pendingSyncDeletionDao?.insert(
            PendingSyncDeletionEntity(
                store_id = storeId,
                entity_type = "PRODUCT",
                entity_id = cleanId,
                timestamp = System.currentTimeMillis()
            )
        )

        // Attempt immediate Firestore removal
        val cloudResult = firestoreService.deleteProduct(storeId, cleanId)
        if (cloudResult.isSuccess) {
            pendingSyncDeletionDao?.deleteById(storeId, "PRODUCT", cleanId)
        }

        auditLogDao.insert(
            AuditLogEntity(
                action = "DELETE_PRODUCT",
                details = "Deleted product '${product.product_name}' ($cleanId)",
                store_id = storeId
            )
        )
        analyticsManager?.trackInventoryUpdate(storeId, "DELETE_PRODUCT", cleanId)

        Result.success(Unit)
    }

    suspend fun updateStock(product: ProductEntity) = withContext(Dispatchers.IO) {
        productDao.update(product)
    }
}
