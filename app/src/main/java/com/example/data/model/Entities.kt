package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory")
data class ProductEntity(
    @PrimaryKey
    val product_id: String,
    val product_name: String,
    val product_price: Double,
    val product_amount_left: Int,
    val product_amount_sold: Int = 0,
    val date_sold: String = "",
    val date_filled: String = "",
    val product_type: String = "General",
    val store_id: String = "STR-LBN-NAB-001",
    val sync_status: String = "SYNCED",
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey
    val sale_id: String,
    val product_id: String,
    val product_name: String,
    val quantity: Int,
    val price: Double,
    val sale_date: String,
    val customer_id: String = "C101",
    val cashier_id: String = "admin",
    val store_id: String = "STR-LBN-NAB-001",
    val sync_status: String = "SYNCED",
    val created_at: Long = System.currentTimeMillis()
)

@Entity(tableName = "balance_history")
data class BalanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val budget_starting: Double,
    val money_in: Double = 0.0,
    val money_out: Double = 0.0,
    val reason: String = "",
    val store_id: String = "STR-LBN-NAB-001",
    val sync_status: String = "SYNCED",
    val created_at: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val action: String,
    val details: String,
    val store_id: String = "STR-LBN-NAB-001",
    val user_id: String = "admin",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val username: String,
    val password_hash: String,
    val salt: String = "",
    val role: String = "cashier", // "admin" or "cashier"
    val store_id: String = "STR-LBN-NAB-001",
    val created_at: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_events")
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sync_id: String,
    val store_id: String = "STR-LBN-NAB-001",
    val device_id: String = "TERM-NAB-01",
    val user_id: String = "admin",
    val timestamp: Long = System.currentTimeMillis(),
    val operation: String, // CHECKOUT, INVENTORY_SYNC, PRICE_UPDATE, USER_UPDATE
    val entity_type: String, // SALE, PRODUCT, LEDGER, USER
    val entity_id: String,
    val status: String, // SUCCESS, PENDING, REJECTED, DUPLICATE, CONFLICT, FAILED
    val error_code: String? = null,
    val error_message: String? = null
)

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val store_id: String = "STR-LBN-NAB-001",
    val user_id: String = "anonymous",
    val device_id: String = "TERM-NAB-01",
    val timestamp: Long = System.currentTimeMillis(),
    val event_type: String, // FAILED_LOGIN, UNAUTHORIZED_ACCESS, CROSS_STORE_ATTEMPT, SUSPICIOUS_PRICE_MODIFICATION, DUPLICATE_CHECKOUT
    val severity: String, // INFO, WARNING, CRITICAL
    val endpoint_or_action: String,
    val result: String, // BLOCKED, ALLOWED, FLAGGED
    val reason: String
)

data class StoreProfile(
    val storeId: String = "STR-LBN-NAB-001",
    val storeName: String = "Al-Makhzen Supermarket",
    val region: String = "Nabatieh Area, South Lebanon",
    val subscriptionStatus: String = "ACTIVE",
    val currency: String = "USD / LBP",
    val activeTerminalId: String = "TERM-NAB-01"
)

data class CategoryDemandItem(
    val category: String,
    val percentage: Int,
    val unitSales: Int,
    val revenue: Double
)

data class RegionalMarketInsight(
    val regionName: String = "Nabatieh Area",
    val totalTransactionsAnalyzed: Int = 0,
    val averageTicketSize: Double = 0.0,
    val categoryShare: List<CategoryDemandItem> = emptyList(),
    val fastMovingProducts: List<String> = emptyList(),
    val slowMovingProducts: List<String> = emptyList(),
    val privacyNote: String = "Data aggregated and anonymized across regional supermarkets"
)

data class CartItem(
    val product: ProductEntity,
    val quantity: Int = 1
) {
    val subtotal: Double
        get() = product.product_price * quantity
}

data class ReceiptItem(
    val productId: String,
    val productName: String,
    val price: Double,
    val quantity: Int,
    val subtotal: Double,
    val remainingStock: Int
)

data class Receipt(
    val receiptId: String,
    val date: String,
    val timestamp: String,
    val customerId: String,
    val items: List<ReceiptItem>,
    val total: Double,
    val itemCount: Int
)

data class FinancialSummary(
    val currentBalance: Double = 5000.0,
    val initialBudget: Double = 5000.0,
    val totalMoneyIn: Double = 0.0,
    val totalMoneyOut: Double = 0.0,
    val netProfit: Double = 0.0,
    val salesRevenue: Double = 0.0,
    val totalTransactions: Int = 0,
    val unitsSold: Int = 0,
    val totalProducts: Int = 0,
    val inventoryValuation: Double = 0.0,
    val totalStockCount: Int = 0,
    val alertCount: Int = 0,
    val timestamp: String = ""
)

data class ProfitPrediction(
    val hasEnoughData: Boolean = false,
    val message: String = "",
    val dates: List<String> = emptyList(),
    val actualProfits: List<Double> = emptyList(),
    val trendLine: List<Double> = emptyList(),
    val equation: String = "",
    val dailySlope: Double = 0.0,
    val intercept: Double = 0.0,
    val prediction30d: Double = 0.0,
    val prediction90d: Double = 0.0,
    val prediction1yr: Double = 0.0
)

data class VerificationItem(
    val title: String,
    val status: String, // PASSED, FAILED, WARNING, SKIPPED
    val details: String,
    val isCritical: Boolean = true
)

data class VerificationSuiteReport(
    val overallStatus: String = "NOT READY", // READY, NOT READY
    val passedCount: Int = 0,
    val totalCount: Int = 0,
    val items: List<VerificationItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class PlatformMetricsSummary(
    val totalStores: Int = 0,
    val totalUsers: Int = 0,
    val totalSalesVolumeUsd: Double = 0.0,
    val totalTransactionsCount: Int = 0,
    val totalInventorySkus: Int = 0,
    val totalLowStockAlerts: Int = 0,
    val totalAuditEventsCount: Int = 0,
    val totalSessionsCount: Int = 0,
    val featureUsageMap: Map<String, Long> = emptyMap(),
    val suspiciousEventsCount: Int = 0,
    val suspiciousEvents: List<SecurityEventEntity> = emptyList(),
    val storesList: List<StorePlatformSummary> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class StorePlatformSummary(
    val storeId: String,
    val storeName: String,
    val region: String,
    val subscriptionStatus: String = "ACTIVE",
    val userCount: Int = 1,
    val salesCount: Int = 0,
    val revenueUsd: Double = 0.0,
    val inventoryCount: Int = 0,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)

