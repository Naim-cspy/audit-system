package com.example.data.repository

import com.example.data.dao.AuditLogDao
import com.example.data.dao.BalanceDao
import com.example.data.dao.ProductDao
import com.example.data.dao.SaleDao
import com.example.data.dao.SyncEventDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.CategoryDemandItem
import com.example.data.model.FinancialSummary
import com.example.data.model.ProfitPrediction
import com.example.data.model.RegionalMarketInsight
import com.example.data.model.StoreProfile
import com.example.data.remote.FirebaseLedgerDoc
import com.example.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Finance & Ledger Repository.
 * Tracks store accounting, bills, receipts, valuation, and predictive algorithms.
 * Synchronizes ledger records to Cloud Firestore under stores/{storeId}/ledger.
 */
class FinanceRepository(
    private val balanceDao: BalanceDao,
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val auditLogDao: AuditLogDao,
    private val syncEventDao: SyncEventDao,
    private val firestoreService: FirestoreService = FirestoreService()
) {

    val balanceHistory: Flow<List<BalanceEntity>> = balanceDao.getAllHistoryDesc()

    suspend fun addBill(storeId: String, amount: Double, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (amount <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        }
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Reason cannot be empty"))
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastBalance = balanceDao.getLatestBalance()
        val startingBudget = lastBalance?.budget_starting ?: 5000.0

        val newBalance = BalanceEntity(
            date = todayStr,
            budget_starting = startingBudget,
            money_in = 0.0,
            money_out = amount,
            reason = cleanReason,
            store_id = storeId,
            sync_status = "PENDING"
        )
        val id = balanceDao.insert(newBalance)

        // Upload to Cloud Firestore
        val cloudDoc = FirebaseLedgerDoc.fromEntity(newBalance.copy(id = id.toInt()))
        val cloudResult = firestoreService.recordLedger(storeId, cloudDoc)
        if (cloudResult.isSuccess) {
            balanceDao.update(newBalance.copy(id = id.toInt(), sync_status = "SYNCED"))
        }

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_EXPENSE",
                details = "Expense of \$$amount recorded: '$cleanReason'",
                store_id = storeId
            )
        )

        Result.success(Unit)
    }

    suspend fun addReceipt(storeId: String, amount: Double, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (amount <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        }
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Reason cannot be empty"))
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastBalance = balanceDao.getLatestBalance()
        val startingBudget = lastBalance?.budget_starting ?: 5000.0

        val newBalance = BalanceEntity(
            date = todayStr,
            budget_starting = startingBudget,
            money_in = amount,
            money_out = 0.0,
            reason = cleanReason,
            store_id = storeId,
            sync_status = "PENDING"
        )
        val id = balanceDao.insert(newBalance)

        val cloudDoc = FirebaseLedgerDoc.fromEntity(newBalance.copy(id = id.toInt()))
        val cloudResult = firestoreService.recordLedger(storeId, cloudDoc)
        if (cloudResult.isSuccess) {
            balanceDao.update(newBalance.copy(id = id.toInt(), sync_status = "SYNCED"))
        }

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_INCOME",
                details = "Income of \$$amount recorded: '$cleanReason'",
                store_id = storeId
            )
        )

        Result.success(Unit)
    }

    suspend fun getFinancialSummary(): FinancialSummary = withContext(Dispatchers.IO) {
        val balances = balanceDao.getAllHistory().first()
        val products = productDao.getAllProducts().first()
        val sales = saleDao.getAllSales().first()

        val initialBudget = balances.firstOrNull()?.budget_starting ?: 5000.0
        val totalMoneyIn = balances.sumOf { it.money_in }
        val totalMoneyOut = balances.sumOf { it.money_out }
        val currentBalance = initialBudget + totalMoneyIn - totalMoneyOut
        val netProfit = totalMoneyIn - totalMoneyOut

        val salesRevenue = sales.sumOf { it.quantity * it.price }
        val totalTransactions = sales.size
        val unitsSold = sales.sumOf { it.quantity }

        val totalProducts = products.size
        val inventoryValuation = products.sumOf { it.product_price * it.product_amount_left }
        val totalStockCount = products.sumOf { it.product_amount_left }
        val alertCount = products.count { it.product_amount_left <= 50 }

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        FinancialSummary(
            currentBalance = currentBalance,
            initialBudget = initialBudget,
            totalMoneyIn = totalMoneyIn,
            totalMoneyOut = totalMoneyOut,
            netProfit = netProfit,
            salesRevenue = salesRevenue,
            totalTransactions = totalTransactions,
            unitsSold = unitsSold,
            totalProducts = totalProducts,
            inventoryValuation = inventoryValuation,
            totalStockCount = totalStockCount,
            alertCount = alertCount,
            timestamp = ts
        )
    }

    suspend fun calculateProfitPrediction(): ProfitPrediction = withContext(Dispatchers.IO) {
        val balances = balanceDao.getAllHistory().first()
        if (balances.isEmpty()) {
            return@withContext ProfitPrediction(
                hasEnoughData = false,
                message = "No balance entries available to build prediction models"
            )
        }

        val dailyGroups = balances.groupBy { it.date }
        val sortedDates = dailyGroups.keys.sorted()

        if (sortedDates.size < 2) {
            return@withContext ProfitPrediction(
                hasEnoughData = false,
                message = "At least 2 days of financial records are required to extrapolate profit trajectory"
            )
        }

        var cumulativeProfit = 0.0
        val profitPoints = mutableListOf<Double>()
        for (date in sortedDates) {
            val entries = dailyGroups[date] ?: emptyList()
            val dayIn = entries.sumOf { it.money_in }
            val dayOut = entries.sumOf { it.money_out }
            cumulativeProfit += (dayIn - dayOut)
            profitPoints.add(cumulativeProfit)
        }

        val n = profitPoints.size
        val xValues = (0 until n).map { it.toDouble() }
        val xMean = xValues.average()
        val yMean = profitPoints.average()

        var numerator = 0.0
        var denominator = 0.0
        for (i in 0 until n) {
            val xDiff = xValues[i] - xMean
            val yDiff = profitPoints[i] - yMean
            numerator += xDiff * yDiff
            denominator += xDiff * xDiff
        }

        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean

        val trendLine = xValues.map { slope * it + intercept }
        val equation = "P(t) = ${"%.2f".format(slope)} * t + ${"%.2f".format(intercept)}"

        val lastX = (n - 1).toDouble()
        val pred30 = slope * (lastX + 30) + intercept
        val pred90 = slope * (lastX + 90) + intercept
        val pred365 = slope * (lastX + 365) + intercept

        ProfitPrediction(
            hasEnoughData = true,
            message = "Linear regression calculated over $n reporting days",
            dates = sortedDates,
            actualProfits = profitPoints,
            trendLine = trendLine,
            equation = equation,
            dailySlope = slope,
            intercept = intercept,
            prediction30d = pred30,
            prediction90d = pred90,
            prediction1yr = pred365
        )
    }

    suspend fun getRegionalMarketInsights(profile: StoreProfile): RegionalMarketInsight = withContext(Dispatchers.IO) {
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
                    percentage = pct.coerceAtLeast(5),
                    unitSales = catSoldUnits,
                    revenue = catRevenue
                )
            )
        }

        val sortedProds = products.sortedByDescending { it.product_amount_sold }
        val fastMoving = sortedProds.take(3).map { "${it.product_name} (${it.product_amount_sold} sold)" }
        val slowMoving = sortedProds.reversed().take(2).map { "${it.product_name} (${it.product_amount_left} in stock)" }

        RegionalMarketInsight(
            regionName = profile.region,
            totalTransactionsAnalyzed = sales.size,
            averageTicketSize = Math.round(avgTicket * 100.0) / 100.0,
            categoryShare = categorySales.sortedByDescending { it.percentage },
            fastMovingProducts = fastMoving,
            slowMovingProducts = slowMoving
        )
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

    suspend fun exportJsonBackup(profile: StoreProfile): String = withContext(Dispatchers.IO) {
        val products = productDao.getAllProducts().first()
        val sales = saleDao.getAllSales().first()
        val balances = balanceDao.getAllHistory().first()
        val syncs = syncEventDao.getRecentSyncEvents(50).first()

        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"store_profile\": {\n")
        sb.append("    \"store_id\": \"${profile.storeId}\",\n")
        sb.append("    \"store_name\": \"${profile.storeName}\",\n")
        sb.append("    \"region\": \"${profile.region}\",\n")
        sb.append("    \"terminal_id\": \"${profile.activeTerminalId}\",\n")
        sb.append("    \"exported_at\": \"$ts\"\n")
        sb.append("  },\n")

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
}
