package com.example.data.repository

import com.example.data.dao.AuditLogDao
import com.example.data.dao.ProductDao
import com.example.data.model.AuditLogEntity
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
    val firestoreService: FirestoreService = FirestoreService()
) {

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    fun getStockWarnings(threshold: Int = 50): Flow<List<ProductEntity>> {
        return productDao.getStockWarnings(threshold)
    }

    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return productDao.searchProducts(query)
    }

    suspend fun lookupProduct(code: String): ProductEntity? = withContext(Dispatchers.IO) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return@withContext null
        productDao.getProductById(trimmed)
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

        val existing = productDao.getProductById(cleanId)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Product with ID '$cleanId' already exists"))
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

        Result.success(Unit)
    }

    suspend fun updatePrice(storeId: String, productId: String, newPrice: Double): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = productId.trim()
        if (newPrice <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Price must be greater than zero"))
        }

        val product = productDao.getProductById(cleanId)
            ?: return@withContext Result.failure(IllegalArgumentException("Product not found"))

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

        Result.success(Unit)
    }

    suspend fun removeProduct(storeId: String, productId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = productId.trim()
        val product = productDao.getProductById(cleanId)
            ?: return@withContext Result.failure(IllegalArgumentException("Product not found"))

        productDao.delete(product)

        // Remove from Firestore
        firestoreService.deleteProduct(storeId, cleanId)

        auditLogDao.insert(
            AuditLogEntity(
                action = "DELETE_PRODUCT",
                details = "Deleted product '${product.product_name}' ($cleanId)",
                store_id = storeId
            )
        )

        Result.success(Unit)
    }

    suspend fun updateStock(product: ProductEntity) = withContext(Dispatchers.IO) {
        productDao.update(product)
    }
}
