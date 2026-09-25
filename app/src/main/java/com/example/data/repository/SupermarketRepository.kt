package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.CartItem
import com.example.data.model.CategoryDemandItem
import com.example.data.model.FinancialSummary
import com.example.data.model.ProductEntity
import com.example.data.model.ProfitPrediction
import com.example.data.model.Receipt
import com.example.data.model.ReceiptItem
import com.example.data.model.RegionalMarketInsight
import com.example.data.model.SaleEntity
import com.example.data.model.SecurityEventEntity
import com.example.data.model.StoreProfile
import com.example.data.model.SyncEventEntity
import com.example.data.model.UserEntity
import com.example.data.security.PasswordSecurity
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
    private val syncEventDao = db.syncEventDao()
    private val securityEventDao = db.securityEventDao()

    val currentStoreProfile = StoreProfile()

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()
    val recentSales: Flow<List<SaleEntity>> = saleDao.getRecentSales(15)
    val balanceHistory: Flow<List<BalanceEntity>> = balanceDao.getAllHistoryDesc()
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()
    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getRecentLogs(30)
    val allSyncEvents: Flow<List<SyncEventEntity>> = syncEventDao.getRecentSyncEvents(40)
    val allSecurityEvents: Flow<List<SecurityEventEntity>> = securityEventDao.getRecentSecurityEvents(40)
    val failedSyncCount: Flow<Int> = syncEventDao.getFailedCount()

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

        // Log cloud sync event for transaction
        syncEventDao.insert(
            SyncEventEntity(
                sync_id = "SYNC-${UUID.randomUUID().toString().take(8).uppercase()}",
                store_id = currentStoreProfile.storeId,
                device_id = currentStoreProfile.activeTerminalId,
                user_id = customerId,
                timestamp = nowMs,
                operation = "CHECKOUT_TRANSACTION",
                entity_type = "SALE",
                entity_id = receipt.receiptId,
                status = "SUCCESS"
            )
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
        val cleanUser = username.trim()
        val cleanPass = passwordPlain.trim()
        if (cleanUser.isEmpty() || cleanPass.isEmpty()) return@withContext null

        val user = userDao.getByUsername(cleanUser)
        if (user == null) {
            securityEventDao.insert(
                SecurityEventEntity(
                    store_id = currentStoreProfile.storeId,
                    user_id = cleanUser,
                    device_id = currentStoreProfile.activeTerminalId,
                    event_type = "FAILED_LOGIN_UNKNOWN_USER",
                    severity = "WARNING",
                    endpoint_or_action = "AUTH_LOGIN",
                    result = "BLOCKED",
                    reason = "User '$cleanUser' not found in store registry"
                )
            )
            return@withContext null
        }

        if (PasswordSecurity.verifyPassword(cleanPass, user.password_hash, user.salt)) {
            user
        } else {
            securityEventDao.insert(
                SecurityEventEntity(
                    store_id = currentStoreProfile.storeId,
                    user_id = cleanUser,
                    device_id = currentStoreProfile.activeTerminalId,
                    event_type = "FAILED_LOGIN_BAD_CREDENTIALS",
                    severity = "WARNING",
                    endpoint_or_action = "AUTH_LOGIN",
                    result = "BLOCKED",
                    reason = "Invalid password attempt for account '$cleanUser'"
                )
            )
            null
        }
    }

    suspend fun createInitialAdmin(username: String, passwordPlain: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val cleanPass = passwordPlain.trim()
        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Username and password cannot be empty"))
        }
        if (cleanPass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        val existing = userDao.getByUsername(cleanUser)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Account '$cleanUser' already exists"))
        }
        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        val adminUser = UserEntity(
            username = cleanUser,
            password_hash = hash,
            salt = salt,
            role = "admin"
        )
        val id = userDao.insert(adminUser)
        auditLogDao.insert(
            AuditLogEntity(
                action = "INITIAL_ADMIN_PROVISIONED",
                details = "Administrator account '$cleanUser' initialized"
            )
        )
        Result.success(adminUser.copy(id = id.toInt()))
    }

    suspend fun addUser(username: String, passwordPlain: String, role: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val cleanPass = passwordPlain.trim()
        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Username and password cannot be empty"))
        }
        if (cleanPass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        val existing = userDao.getByUsername(cleanUser)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Username $cleanUser already exists"))
        }
        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        userDao.insert(
            UserEntity(
                username = cleanUser,
                password_hash = hash,
                salt = salt,
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
        val userToDelete = userDao.getByUsername(cleanUser)
            ?: return@withContext Result.failure(IllegalArgumentException("User $cleanUser does not exist"))
        
        // Prevent deleting the last remaining admin
        if (userToDelete.role == "admin") {
            val allUsers = userDao.getAllUsers().first()
            val adminCount = allUsers.count { it.role == "admin" }
            if (adminCount <= 1) {
                return@withContext Result.failure(IllegalArgumentException("Cannot delete the only administrator account"))
            }
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
        val cleanPass = newPasswordPlain.trim()
        if (cleanPass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("New password must be at least 6 characters"))
        }
        userDao.getByUsername(cleanUser)
            ?: return@withContext Result.failure(IllegalArgumentException("User $cleanUser not found"))

        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        userDao.updatePassword(cleanUser, hash, salt)
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

    suspend fun exportJsonBackup(): String = withContext(Dispatchers.IO) {
        val products = productDao.getAllProducts().first()
        val sales = saleDao.getAllSales().first()
        val balances = balanceDao.getAllHistory().first()
        val audits = auditLogDao.getRecentLogs(50).first()
        val syncs = syncEventDao.getRecentSyncEvents(50).first()

        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"store_profile\": {\n")
        sb.append("    \"store_id\": \"${currentStoreProfile.storeId}\",\n")
        sb.append("    \"store_name\": \"${currentStoreProfile.storeName}\",\n")
        sb.append("    \"region\": \"${currentStoreProfile.region}\",\n")
        sb.append("    \"terminal_id\": \"${currentStoreProfile.activeTerminalId}\",\n")
        sb.append("    \"exported_at\": \"$ts\"\n")
        sb.append("  },\n")

        // Products
        sb.append("  \"products\": [\n")
        products.forEachIndexed { i, p ->
            sb.append("    {\n")
            sb.append("      \"product_id\": \"${p.product_id}\",\n")
            sb.append("      \"name\": \"${p.product_name.replace("\"", "\\\"")}\",\n")
            sb.append("      \"price\": ${p.product_price},\n")
            sb.append("      \"stock_left\": ${p.product_amount_left},\n")
            sb.append("      \"stock_sold\": ${p.product_amount_sold},\n")
            sb.append("      \"category\": \"${p.product_type}\"\n")
            sb.append("    }${if (i < products.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")

        // Sales
        sb.append("  \"sales\": [\n")
        sales.forEachIndexed { i, s ->
            sb.append("    {\n")
            sb.append("      \"sale_id\": \"${s.sale_id}\",\n")
            sb.append("      \"product_id\": \"${s.product_id}\",\n")
            sb.append("      \"quantity\": ${s.quantity},\n")
            sb.append("      \"price\": ${s.price},\n")
            sb.append("      \"date\": \"${s.sale_date}\",\n")
            sb.append("      \"customer_id\": \"${s.customer_id}\"\n")
            sb.append("    }${if (i < sales.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")

        // Ledger
        sb.append("  \"ledger_entries\": [\n")
        balances.forEachIndexed { i, b ->
            sb.append("    {\n")
            sb.append("      \"id\": ${b.id},\n")
            sb.append("      \"date\": \"${b.date}\",\n")
            sb.append("      \"starting\": ${b.budget_starting},\n")
            sb.append("      \"in\": ${b.money_in},\n")
            sb.append("      \"out\": ${b.money_out},\n")
            sb.append("      \"reason\": \"${b.reason.replace("\"", "\\\"")}\"\n")
            sb.append("    }${if (i < balances.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")

        // Sync Events
        sb.append("  \"sync_events\": [\n")
        syncs.forEachIndexed { i, sy ->
            sb.append("    {\n")
            sb.append("      \"sync_id\": \"${sy.sync_id}\",\n")
            sb.append("      \"operation\": \"${sy.operation}\",\n")
            sb.append("      \"entity_type\": \"${sy.entity_type}\",\n")
            sb.append("      \"entity_id\": \"${sy.entity_id}\",\n")
            sb.append("      \"status\": \"${sy.status}\",\n")
            sb.append("      \"timestamp\": ${sy.timestamp}\n")
            sb.append("    }${if (i < syncs.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n")

        sb.append("}\n")
        sb.toString()
    }

    suspend fun getRegionalMarketInsights(): RegionalMarketInsight = withContext(Dispatchers.IO) {
        val products = productDao.getAllProducts().first()
        val sales = saleDao.getAllSales().first()

        val totalRevenue = sales.sumOf { it.quantity * it.price }
        val avgTicket = if (sales.isNotEmpty()) totalRevenue / sales.size else 0.0

        val categoryGroups = products.groupBy { it.product_type }
        val categorySales = mutableListOf<CategoryDemandItem>()

        val totalUnitsSold = sales.sumOf { it.quantity }.coerceAtLeast(1)

        categoryGroups.forEach { (cat, prods) ->
            val pIds = prods.map { it.product_id.uppercase() }.toSet()
            val catSoldUnits = sales.filter { it.product_id.uppercase() in pIds }.sumOf { it.quantity }
            val catRevenue = sales.filter { it.product_id.uppercase() in pIds }.sumOf { it.quantity * it.price }
            val pct = ((catSoldUnits.toDouble() / totalUnitsSold.toDouble()) * 100).toInt()
            categorySales.add(
                CategoryDemandItem(
                    category = cat,
                    percentage = pct.coerceAtLeast(5), // floor for visualization
                    unitSales = catSoldUnits,
                    revenue = catRevenue
                )
            )
        }

        val sortedProds = products.sortedByDescending { it.product_amount_sold }
        val fastMoving = sortedProds.take(3).map { "${it.product_name} (${it.product_amount_sold} sold)" }
        val slowMoving = sortedProds.reversed().take(2).map { "${it.product_name} (${it.product_amount_left} in stock)" }

        RegionalMarketInsight(
            regionName = currentStoreProfile.region,
            totalTransactionsAnalyzed = sales.size,
            averageTicketSize = Math.round(avgTicket * 100.0) / 100.0,
            categoryShare = categorySales.sortedByDescending { it.percentage },
            fastMovingProducts = fastMoving,
            slowMovingProducts = slowMoving
        )
    }
}
