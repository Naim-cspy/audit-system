package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.CartItem
import com.example.data.model.FinancialSummary
import com.example.data.model.ProductEntity
import com.example.data.model.ProfitPrediction
import com.example.data.model.Receipt
import com.example.data.model.ReceiptItem
import com.example.data.model.SaleEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SupermarketRepository(private val db: AppDatabase) {

    private val productDao = db.productDao()
    private val saleDao = db.saleDao()
    private val balanceDao = db.balanceDao()
    private val auditLogDao = db.auditLogDao()
    private val userDao = db.userDao()

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()
    val recentSales: Flow<List<SaleEntity>> = saleDao.getRecentSales(15)
    val balanceHistory: Flow<List<BalanceEntity>> = balanceDao.getAllHistoryDesc()
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()
    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getRecentLogs(30)

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

    suspend fun batchCheckout(cartItems: List<CartItem>, customerId: String = "C101"): Result<Receipt> = withContext(Dispatchers.IO) {
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Cart is empty"))
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val nowMs = System.currentTimeMillis()

        // Validate stock
        val processedReceiptItems = mutableListOf<ReceiptItem>()
        var totalAmount = 0.0

        for (item in cartItems) {
            val freshProduct = productDao.getProductById(item.product.product_id)
                ?: return@withContext Result.failure(IllegalStateException("Product ${item.product.product_name} not found"))

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

        // Apply stock decrements and insert sales
        for (item in cartItems) {
            val freshProduct = productDao.getProductById(item.product.product_id)!!
            val updated = freshProduct.copy(
                product_amount_left = freshProduct.product_amount_left - item.quantity,
                product_amount_sold = freshProduct.product_amount_sold + item.quantity,
                date_sold = todayStr,
                updated_at = nowMs
            )
            productDao.update(updated)

            val saleId = "S${nowMs}_${UUID.randomUUID().toString().take(6)}"
            saleDao.insert(
                SaleEntity(
                    sale_id = saleId,
                    product_id = item.product.product_id,
                    product_name = item.product.product_name,
                    quantity = item.quantity,
                    price = item.product.product_price,
                    sale_date = todayStr,
                    customer_id = customerId,
                    created_at = nowMs
                )
            )
        }

        // Record Balance ledger entry
        val latestBalance = balanceDao.getLatest()
        val currentBal = if (latestBalance != null) {
            latestBalance.budget_starting + latestBalance.money_in - latestBalance.money_out
        } else {
            5000.0
        }

        balanceDao.insert(
            BalanceEntity(
                date = todayStr,
                budget_starting = currentBal,
                money_in = totalAmount,
                money_out = 0.0,
                reason = "POS Checkout (${processedReceiptItems.size} items, Cust: $customerId)",
                created_at = nowMs
            )
        )

        // Audit log
        auditLogDao.insert(
            AuditLogEntity(
                action = "CHECKOUT",
                details = "Customer $customerId purchased ${processedReceiptItems.size} products for \$${"%.2f".format(totalAmount)}"
            )
        )

        val receipt = Receipt(
            receiptId = "RCP-$nowMs-${UUID.randomUUID().toString().take(4).uppercase()}",
            date = todayStr,
            timestamp = timestampStr,
            customerId = customerId,
            items = processedReceiptItems,
            total = totalAmount,
            itemCount = processedReceiptItems.sumOf { it.quantity }
        )

        Result.success(receipt)
    }

    suspend fun addBill(amount: Double, reason: String): Result<Double> = withContext(Dispatchers.IO) {
        if (amount <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Bill amount must be positive"))
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val latest = balanceDao.getLatest()
        val currentBal = if (latest != null) {
            latest.budget_starting + latest.money_in - latest.money_out
        } else {
            5000.0
        }
        val newBal = currentBal - amount

        balanceDao.insert(
            BalanceEntity(
                date = todayStr,
                budget_starting = currentBal,
                money_in = 0.0,
                money_out = amount,
                reason = reason.trim().ifEmpty { "Expense Bill" },
                created_at = System.currentTimeMillis()
            )
        )

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_BILL",
                details = "Expense \$${"%.2f".format(amount)} for '$reason'"
            )
        )

        Result.success(newBal)
    }

    suspend fun addReceipt(amount: Double, reason: String): Result<Double> = withContext(Dispatchers.IO) {
        if (amount <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Receipt amount must be positive"))
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val latest = balanceDao.getLatest()
        val currentBal = if (latest != null) {
            latest.budget_starting + latest.money_in - latest.money_out
        } else {
            5000.0
        }
        val newBal = currentBal + amount

        balanceDao.insert(
            BalanceEntity(
                date = todayStr,
                budget_starting = currentBal,
                money_in = amount,
                money_out = 0.0,
                reason = reason.trim().ifEmpty { "Income Receipt" },
                created_at = System.currentTimeMillis()
            )
        )

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_RECEIPT",
                details = "Income \$${"%.2f".format(amount)} from '$reason'"
            )
        )

        Result.success(newBal)
    }

    suspend fun addProduct(
        productId: String,
        name: String,
        price: Double,
        amountLeft: Int,
        amountSold: Int = 0,
        dateFilled: String = "",
        type: String = "General"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = productId.trim().uppercase()
        val existing = productDao.getProductById(cleanId)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Product ID $cleanId already exists"))
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val entity = ProductEntity(
            product_id = cleanId,
            product_name = name.trim(),
            product_price = price,
            product_amount_left = amountLeft,
            product_amount_sold = amountSold,
            date_filled = dateFilled.ifEmpty { todayStr },
            product_type = type.trim().ifEmpty { "General" }
        )
        productDao.insert(entity)

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_PRODUCT",
                details = "Added new product $name ($cleanId) at \$${"%.2f".format(price)}"
            )
        )

        Result.success(Unit)
    }

    suspend fun updatePrice(productId: String, newPrice: Double): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = productId.trim().uppercase()
        val existing = productDao.getProductById(cleanId)
            ?: return@withContext Result.failure(IllegalArgumentException("Product $cleanId not found"))

        productDao.updatePrice(cleanId, newPrice)

        auditLogDao.insert(
            AuditLogEntity(
                action = "PRICE_CHANGE",
                details = "Changed ${existing.product_name} ($cleanId) price from \$${"%.2f".format(existing.product_price)} to \$${"%.2f".format(newPrice)}"
            )
        )

        Result.success(Unit)
    }

    suspend fun removeProduct(productId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = productId.trim().uppercase()
        val existing = productDao.getProductById(cleanId)
            ?: return@withContext Result.failure(IllegalArgumentException("Product $cleanId not found"))

        productDao.deleteById(cleanId)

        auditLogDao.insert(
            AuditLogEntity(
                action = "REMOVE_PRODUCT",
                details = "Deleted product ${existing.product_name} ($cleanId)"
            )
        )

        Result.success(Unit)
    }

    suspend fun getFinancialSummary(): FinancialSummary = withContext(Dispatchers.IO) {
        val balances = balanceDao.getAllHistory().first()
        val products = productDao.getAllProducts().first()
        val sales = saleDao.getAllSales().first()

        val totalIn = balances.sumOf { it.money_in }
        val totalOut = balances.sumOf { it.money_out }

        val initialBudget = balances.firstOrNull()?.budget_starting ?: 5000.0
        val currentBalance = if (balances.isNotEmpty()) {
            val last = balances.last()
            last.budget_starting + last.money_in - last.money_out
        } else {
            5000.0
        }
        val netProfit = currentBalance - initialBudget

        val salesRevenue = sales.sumOf { it.quantity * it.price }
        val unitsSold = sales.sumOf { it.quantity }

        val totalStock = products.sumOf { it.product_amount_left }
        val inventoryVal = products.sumOf { it.product_price * it.product_amount_left }
        val alertCount = products.count { it.product_amount_left <= 50 }

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        FinancialSummary(
            currentBalance = currentBalance,
            initialBudget = initialBudget,
            totalMoneyIn = totalIn,
            totalMoneyOut = totalOut,
            netProfit = netProfit,
            salesRevenue = salesRevenue,
            totalTransactions = sales.size,
            unitsSold = unitsSold,
            totalProducts = products.size,
            inventoryValuation = inventoryVal,
            totalStockCount = totalStock,
            alertCount = alertCount,
            timestamp = ts
        )
    }

    suspend fun calculateProfitPrediction(): ProfitPrediction = withContext(Dispatchers.IO) {
        val rows = balanceDao.getAllHistory().first()
        if (rows.size < 2) {
            return@withContext ProfitPrediction(
                hasEnoughData = false,
                message = "At least 2 financial history entries required for statistical prediction."
            )
        }

        val initialBudget = rows.first().budget_starting
        val dates = mutableListOf<String>()
        val actualProfits = mutableListOf<Double>()

        for (r in rows) {
            dates.add(r.date)
            val currentVal = r.budget_starting + r.money_in - r.money_out
            actualProfits.add(Math.round((currentVal - initialBudget) * 100.0) / 100.0)
        }

        val n = rows.size
        val x = (0 until n).map { it.toDouble() }
        val y = actualProfits

        // Calculate Linear Regression (slope & intercept)
        val xMean = x.average()
        val yMean = y.average()

        var numerator = 0.0
        var denominator = 0.0
        for (i in 0 until n) {
            val xDiff = x[i] - xMean
            numerator += xDiff * (y[i] - yMean)
            denominator += xDiff * xDiff
        }

        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean

        val trendLine = x.map { xi ->
            Math.round((slope * xi + intercept) * 100.0) / 100.0
        }

        val future30d = (n + 30).toDouble()
        val future90d = (n + 90).toDouble()
        val future1yr = (n + 365).toDouble()

        val pred30d = Math.round((slope * future30d + intercept) * 100.0) / 100.0
        val pred90d = Math.round((slope * future90d + intercept) * 100.0) / 100.0
        val pred1yr = Math.round((slope * future1yr + intercept) * 100.0) / 100.0

        ProfitPrediction(
            hasEnoughData = true,
            dates = dates,
            actualProfits = actualProfits,
            trendLine = trendLine,
            equation = "Profit = ${"%.2f".format(slope)} * Day + ${"%.2f".format(intercept)}",
            dailySlope = Math.round(slope * 100.0) / 100.0,
            intercept = Math.round(intercept * 100.0) / 100.0,
            prediction30d = pred30d,
            prediction90d = pred90d,
            prediction1yr = pred1yr
        )
    }

    suspend fun verifyLogin(username: String, passwordPlain: String): UserEntity? = withContext(Dispatchers.IO) {
        val user = userDao.getByUsername(username.trim()) ?: return@withContext null
        val hash = AppDatabase.sha256(passwordPlain.trim())
        if (user.password_hash.equals(hash, ignoreCase = true) || user.password_hash == passwordPlain) {
            user
        } else {
            null
        }
    }

    suspend fun addUser(username: String, passwordPlain: String, role: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || passwordPlain.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Username and password cannot be empty"))
        }
        val existing = userDao.getByUsername(cleanUser)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Username $cleanUser already exists"))
        }
        userDao.insert(
            UserEntity(
                username = cleanUser,
                password_hash = AppDatabase.sha256(passwordPlain.trim()),
                role = if (role.lowercase() == "admin") "admin" else "cashier"
            )
        )
        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_USER",
                details = "Added user '$cleanUser' with role '$role'"
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteUser(username: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.equals("admin", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Root admin account cannot be deleted"))
        }
        userDao.deleteByUsername(cleanUser)
        auditLogDao.insert(
            AuditLogEntity(
                action = "DELETE_USER",
                details = "Deleted user '$cleanUser'"
            )
        )
        Result.success(Unit)
    }

    suspend fun changePassword(username: String, newPasswordPlain: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (newPasswordPlain.trim().isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Password cannot be empty"))
        }
        userDao.updatePassword(cleanUser, AppDatabase.sha256(newPasswordPlain.trim()))
        auditLogDao.insert(
            AuditLogEntity(
                action = "CHANGE_PASSWORD",
                details = "Password updated for user '$cleanUser'"
            )
        )
        Result.success(Unit)
    }

    suspend fun exportCsvSummary(): String = withContext(Dispatchers.IO) {
        val products = productDao.getAllProducts().first()
        val sales = saleDao.getAllSales().first()
        val balances = balanceDao.getAllHistory().first()

        val sb = StringBuilder()
        sb.append("=== SUPERMARKET AUDIT & POS EXPORT ===\n")
        sb.append("Exported At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        sb.append("--- INVENTORY (${products.size} products) ---\n")
        sb.append("product_id,product_name,product_price,product_amount_left,product_amount_sold,date_sold,date_filled,product_type\n")
        products.forEach {
            sb.append("${it.product_id},${it.product_name},${it.product_price},${it.product_amount_left},${it.product_amount_sold},${it.date_sold},${it.date_filled},${it.product_type}\n")
        }

        sb.append("\n--- SALES (${sales.size} transactions) ---\n")
        sb.append("sale_id,product_id,product_name,quantity,price,sale_date,customer_id\n")
        sales.forEach {
            sb.append("${it.sale_id},${it.product_id},${it.product_name},${it.quantity},${it.price},${it.sale_date},${it.customer_id}\n")
        }

        sb.append("\n--- BALANCE HISTORY (${balances.size} entries) ---\n")
        sb.append("date,budget_starting,money_in,money_out,reason\n")
        balances.forEach {
            sb.append("${it.date},${it.budget_starting},${it.money_in},${it.money_out},${it.reason}\n")
        }

        sb.toString()
    }
}
