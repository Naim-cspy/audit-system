package com.example.data.remote

import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SecurityEventEntity
import com.example.data.model.StoreProfile
import com.example.data.model.SyncEventEntity

/**
 * Cloud Firestore Data Transfer Objects for multi-tenant supermarket isolation.
 * Path hierarchy:
 * stores/{storeId}/products/{productId}
 * stores/{storeId}/sales/{saleId}
 * stores/{storeId}/ledger/{ledgerId}
 * stores/{storeId}/users/{userId}
 * stores/{storeId}/auditLogs/{logId}
 * stores/{storeId}/syncEvents/{syncId}
 * security_events/{eventId}
 */

data class FirebaseUserProfile(
    val uid: String = "",
    val email: String = "",
    val storeId: String = "",
    val role: String = "cashier", // "admin" or "cashier"
    val displayName: String = "",
    val terminalId: String = "TERM-01",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "email" to email,
        "store_id" to storeId,
        "role" to role,
        "display_name" to displayName,
        "terminal_id" to terminalId,
        "created_at" to createdAt
    )

    companion object {
        fun fromMap(uid: String, map: Map<String, Any?>): FirebaseUserProfile {
            return FirebaseUserProfile(
                uid = uid,
                email = map["email"] as? String ?: "",
                storeId = map["store_id"] as? String ?: "",
                role = map["role"] as? String ?: "cashier",
                displayName = map["display_name"] as? String ?: "",
                terminalId = map["terminal_id"] as? String ?: "TERM-01",
                createdAt = (map["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

data class FirebaseStoreDoc(
    val storeId: String = "",
    val storeName: String = "",
    val region: String = "",
    val currency: String = "USD / LBP",
    val activeTerminalId: String = "TERM-01",
    val subscriptionStatus: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toStoreProfile(): StoreProfile = StoreProfile(
        storeId = storeId,
        storeName = storeName,
        region = region,
        currency = currency,
        activeTerminalId = activeTerminalId,
        subscriptionStatus = subscriptionStatus
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "store_id" to storeId,
        "store_name" to storeName,
        "region" to region,
        "currency" to currency,
        "active_terminal_id" to activeTerminalId,
        "subscription_status" to subscriptionStatus,
        "created_at" to createdAt
    )

    companion object {
        fun fromMap(storeId: String, map: Map<String, Any?>): FirebaseStoreDoc {
            return FirebaseStoreDoc(
                storeId = storeId,
                storeName = map["store_name"] as? String ?: "",
                region = map["region"] as? String ?: "",
                currency = map["currency"] as? String ?: "USD / LBP",
                activeTerminalId = map["active_terminal_id"] as? String ?: "TERM-01",
                subscriptionStatus = map["subscription_status"] as? String ?: "ACTIVE",
                createdAt = (map["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

data class FirebaseProductDoc(
    val productId: String = "",
    val productName: String = "",
    val productPrice: Double = 0.0,
    val amountLeft: Int = 0,
    val amountSold: Int = 0,
    val dateSold: String = "",
    val dateFilled: String = "",
    val productType: String = "General",
    val storeId: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toEntity(syncStatus: String = "SYNCED"): ProductEntity = ProductEntity(
        product_id = productId,
        product_name = productName,
        product_price = productPrice,
        product_amount_left = amountLeft,
        product_amount_sold = amountSold,
        date_sold = dateSold,
        date_filled = dateFilled,
        product_type = productType,
        store_id = storeId,
        sync_status = syncStatus,
        updated_at = updatedAt
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "product_id" to productId,
        "product_name" to productName,
        "product_price" to productPrice,
        "amount_left" to amountLeft,
        "amount_sold" to amountSold,
        "date_sold" to dateSold,
        "date_filled" to dateFilled,
        "product_type" to productType,
        "store_id" to storeId,
        "updated_at" to updatedAt
    )

    companion object {
        fun fromEntity(e: ProductEntity): FirebaseProductDoc = FirebaseProductDoc(
            productId = e.product_id,
            productName = e.product_name,
            productPrice = e.product_price,
            amountLeft = e.product_amount_left,
            amountSold = e.product_amount_sold,
            dateSold = e.date_sold,
            dateFilled = e.date_filled,
            productType = e.product_type,
            storeId = e.store_id,
            updatedAt = e.updated_at
        )

        fun fromMap(id: String, map: Map<String, Any?>): FirebaseProductDoc = FirebaseProductDoc(
            productId = id,
            productName = map["product_name"] as? String ?: "",
            productPrice = (map["product_price"] as? Number)?.toDouble() ?: 0.0,
            amountLeft = (map["amount_left"] as? Number)?.toInt() ?: 0,
            amountSold = (map["amount_sold"] as? Number)?.toInt() ?: 0,
            dateSold = map["date_sold"] as? String ?: "",
            dateFilled = map["date_filled"] as? String ?: "",
            productType = map["product_type"] as? String ?: "General",
            storeId = map["store_id"] as? String ?: "",
            updatedAt = (map["updated_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

data class FirebaseSaleDoc(
    val saleId: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
    val saleDate: String = "",
    val customerId: String = "C101",
    val cashierId: String = "admin",
    val storeId: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toEntity(syncStatus: String = "SYNCED"): SaleEntity = SaleEntity(
        sale_id = saleId,
        product_id = productId,
        product_name = productName,
        quantity = quantity,
        price = price,
        sale_date = saleDate,
        customer_id = customerId,
        cashier_id = cashierId,
        store_id = storeId,
        sync_status = syncStatus,
        created_at = createdAt
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "sale_id" to saleId,
        "product_id" to productId,
        "product_name" to productName,
        "quantity" to quantity,
        "price" to price,
        "sale_date" to saleDate,
        "customer_id" to customerId,
        "cashier_id" to cashierId,
        "store_id" to storeId,
        "created_at" to createdAt
    )

    companion object {
        fun fromEntity(e: SaleEntity): FirebaseSaleDoc = FirebaseSaleDoc(
            saleId = e.sale_id,
            productId = e.product_id,
            productName = e.product_name,
            quantity = e.quantity,
            price = e.price,
            saleDate = e.sale_date,
            customerId = e.customer_id,
            cashierId = e.cashier_id,
            storeId = e.store_id,
            createdAt = e.created_at
        )

        fun fromMap(id: String, map: Map<String, Any?>): FirebaseSaleDoc = FirebaseSaleDoc(
            saleId = id,
            productId = map["product_id"] as? String ?: "",
            productName = map["product_name"] as? String ?: "",
            quantity = (map["quantity"] as? Number)?.toInt() ?: 1,
            price = (map["price"] as? Number)?.toDouble() ?: 0.0,
            saleDate = map["sale_date"] as? String ?: "",
            customerId = map["customer_id"] as? String ?: "C101",
            cashierId = map["cashier_id"] as? String ?: "admin",
            storeId = map["store_id"] as? String ?: "",
            createdAt = (map["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

data class FirebaseLedgerDoc(
    val ledgerId: String = "",
    val date: String = "",
    val budgetStarting: Double = 0.0,
    val moneyIn: Double = 0.0,
    val moneyOut: Double = 0.0,
    val reason: String = "",
    val storeId: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toEntity(syncStatus: String = "SYNCED"): BalanceEntity = BalanceEntity(
        id = ledgerId.toIntOrNull() ?: 0,
        date = date,
        budget_starting = budgetStarting,
        money_in = moneyIn,
        money_out = moneyOut,
        reason = reason,
        store_id = storeId,
        sync_status = syncStatus,
        created_at = createdAt
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "ledger_id" to ledgerId,
        "date" to date,
        "budget_starting" to budgetStarting,
        "money_in" to moneyIn,
        "money_out" to moneyOut,
        "reason" to reason,
        "store_id" to storeId,
        "created_at" to createdAt
    )

    companion object {
        fun fromEntity(e: BalanceEntity): FirebaseLedgerDoc = FirebaseLedgerDoc(
            ledgerId = e.id.toString(),
            date = e.date,
            budgetStarting = e.budget_starting,
            moneyIn = e.money_in,
            moneyOut = e.money_out,
            reason = e.reason,
            storeId = e.store_id,
            createdAt = e.created_at
        )

        fun fromMap(id: String, map: Map<String, Any?>): FirebaseLedgerDoc = FirebaseLedgerDoc(
            ledgerId = id,
            date = map["date"] as? String ?: "",
            budgetStarting = (map["budget_starting"] as? Number)?.toDouble() ?: 0.0,
            moneyIn = (map["money_in"] as? Number)?.toDouble() ?: 0.0,
            moneyOut = (map["money_out"] as? Number)?.toDouble() ?: 0.0,
            reason = map["reason"] as? String ?: "",
            storeId = map["store_id"] as? String ?: "",
            createdAt = (map["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

data class FirebaseAuditLogDoc(
    val logId: String = "",
    val action: String = "",
    val details: String = "",
    val storeId: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toEntity(): AuditLogEntity = AuditLogEntity(
        id = logId.toIntOrNull() ?: 0,
        action = action,
        details = details,
        store_id = storeId,
        user_id = userId,
        timestamp = timestamp
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "log_id" to logId,
        "action" to action,
        "details" to details,
        "store_id" to storeId,
        "user_id" to userId,
        "timestamp" to timestamp
    )

    companion object {
        fun fromEntity(e: AuditLogEntity): FirebaseAuditLogDoc = FirebaseAuditLogDoc(
            logId = e.id.toString(),
            action = e.action,
            details = e.details,
            storeId = e.store_id,
            userId = e.user_id,
            timestamp = e.timestamp
        )

        fun fromMap(id: String, map: Map<String, Any?>): FirebaseAuditLogDoc = FirebaseAuditLogDoc(
            logId = id,
            action = map["action"] as? String ?: "",
            details = map["details"] as? String ?: "",
            storeId = map["store_id"] as? String ?: "",
            userId = map["user_id"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

data class FirebaseSyncEventDoc(
    val syncId: String = "",
    val storeId: String = "",
    val deviceId: String = "TERM-01",
    val userId: String = "",
    val operation: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val status: String = "SUCCESS",
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toEntity(): SyncEventEntity = SyncEventEntity(
        sync_id = syncId,
        store_id = storeId,
        device_id = deviceId,
        user_id = userId,
        operation = operation,
        entity_type = entityType,
        entity_id = entityId,
        status = status,
        error_code = errorCode,
        error_message = errorMessage,
        timestamp = timestamp
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "sync_id" to syncId,
        "store_id" to storeId,
        "device_id" to deviceId,
        "user_id" to userId,
        "operation" to operation,
        "entity_type" to entityType,
        "entity_id" to entityId,
        "status" to status,
        "error_code" to errorCode,
        "error_message" to errorMessage,
        "timestamp" to timestamp
    )

    companion object {
        fun fromEntity(e: SyncEventEntity): FirebaseSyncEventDoc = FirebaseSyncEventDoc(
            syncId = e.sync_id,
            storeId = e.store_id,
            deviceId = e.device_id,
            userId = e.user_id,
            operation = e.operation,
            entityType = e.entity_type,
            entityId = e.entity_id,
            status = e.status,
            errorCode = e.error_code,
            errorMessage = e.error_message,
            timestamp = e.timestamp
        )

        fun fromMap(id: String, map: Map<String, Any?>): FirebaseSyncEventDoc = FirebaseSyncEventDoc(
            syncId = id,
            storeId = map["store_id"] as? String ?: "",
            deviceId = map["device_id"] as? String ?: "TERM-01",
            userId = map["user_id"] as? String ?: "",
            operation = map["operation"] as? String ?: "",
            entityType = map["entity_type"] as? String ?: "",
            entityId = map["entity_id"] as? String ?: "",
            status = map["status"] as? String ?: "SUCCESS",
            errorCode = map["error_code"] as? String,
            errorMessage = map["error_message"] as? String,
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}
