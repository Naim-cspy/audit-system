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
    val created_at: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val action: String,
    val details: String,
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
    val created_at: Long = System.currentTimeMillis()
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
