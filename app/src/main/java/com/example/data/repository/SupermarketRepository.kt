package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.CartItem
import com.example.data.model.FinancialSummary
import com.example.data.model.ProductEntity
import com.example.data.model.ProfitPrediction
import com.example.data.model.Receipt
import com.example.data.model.RegionalMarketInsight
import com.example.data.model.SaleEntity
import com.example.data.model.SecurityEventEntity
import com.example.data.model.StoreProfile
import com.example.data.model.SyncEventEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified Supermarket Repository Facade.
 * Coordinates specialized domain repositories:
 * - AuthRepository (Firebase Auth + Room cache + Store membership)
 * - ProductRepository (Room inventory + stores/{storeId}/products)
 * - SalesRepository (POS checkout + stores/{storeId}/sales)
 * - FinanceRepository (Ledger + predictive analysis + stores/{storeId}/ledger)
 * - SyncRepository (Bidirectional sync + sync & audit events)
 */
class SupermarketRepository(
    private val db: AppDatabase,
    val authRepository: AuthRepository = AuthRepository(db.userDao(), db.auditLogDao()),
    val productRepository: ProductRepository = ProductRepository(db.productDao(), db.auditLogDao()),
    val salesRepository: SalesRepository = SalesRepository(db.saleDao(), db.productDao(), db.balanceDao(), db.auditLogDao(), db.syncEventDao()),
    val financeRepository: FinanceRepository = FinanceRepository(db.balanceDao(), db.productDao(), db.saleDao(), db.auditLogDao(), db.syncEventDao()),
    val syncRepository: SyncRepository = SyncRepository(db.productDao(), db.saleDao(), db.balanceDao(), db.auditLogDao(), db.syncEventDao(), db.securityEventDao())
) {

    val currentStoreProfileFlow: StateFlow<StoreProfile> = authRepository.currentStoreProfile
    val currentStoreProfile: StoreProfile
        get() = authRepository.currentStoreProfile.value

    val allProducts: Flow<List<ProductEntity>> = productRepository.allProducts
    val recentSales: Flow<List<SaleEntity>> = salesRepository.recentSales
    val balanceHistory: Flow<List<BalanceEntity>> = financeRepository.balanceHistory
    val allUsers: Flow<List<UserEntity>> = authRepository.allUsers
    val auditLogs: Flow<List<AuditLogEntity>> = syncRepository.auditLogs
    val allSyncEvents: Flow<List<SyncEventEntity>> = syncRepository.allSyncEvents
    val allSecurityEvents: Flow<List<SecurityEventEntity>> = syncRepository.allSecurityEvents
    val failedSyncCount: Flow<Int> = syncRepository.failedSyncCount
    val isSyncing: StateFlow<Boolean> = syncRepository.isSyncing

    fun getStockWarnings(threshold: Int = 50): Flow<List<ProductEntity>> {
        return productRepository.getStockWarnings(threshold)
    }

    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return productRepository.searchProducts(query)
    }

    suspend fun lookupProduct(code: String): ProductEntity? {
        return productRepository.lookupProduct(code)
    }

    suspend fun batchCheckout(cartItems: List<CartItem>, customerId: String = "C101"): Result<Receipt> {
        val profile = currentStoreProfile
        return salesRepository.batchCheckout(
            storeId = profile.storeId,
            cartItems = cartItems,
            customerId = customerId,
            terminalId = profile.activeTerminalId
        )
    }

    suspend fun addProduct(
        id: String,
        name: String,
        price: Double,
        amountLeft: Int,
        amountSold: Int = 0,
        dateFilled: String = "",
        type: String = "General"
    ): Result<Unit> {
        return productRepository.addProduct(
            storeId = currentStoreProfile.storeId,
            id = id,
            name = name,
            price = price,
            amountLeft = amountLeft,
            amountSold = amountSold,
            dateFilled = dateFilled,
            type = type
        )
    }

    suspend fun updatePrice(productId: String, newPrice: Double): Result<Unit> {
        return productRepository.updatePrice(currentStoreProfile.storeId, productId, newPrice)
    }

    suspend fun removeProduct(productId: String): Result<Unit> {
        return productRepository.removeProduct(currentStoreProfile.storeId, productId)
    }

    suspend fun addBill(amount: Double, reason: String): Result<Unit> {
        return financeRepository.addBill(currentStoreProfile.storeId, amount, reason)
    }

    suspend fun addReceipt(amount: Double, reason: String): Result<Unit> {
        return financeRepository.addReceipt(currentStoreProfile.storeId, amount, reason)
    }

    suspend fun getFinancialSummary(): FinancialSummary {
        return financeRepository.getFinancialSummary()
    }

    suspend fun calculateProfitPrediction(): ProfitPrediction {
        return financeRepository.calculateProfitPrediction()
    }

    suspend fun getRegionalMarketInsights(): RegionalMarketInsight {
        return financeRepository.getRegionalMarketInsights(currentStoreProfile)
    }

    suspend fun exportCsvSummary(): String {
        return financeRepository.exportCsvSummary()
    }

    suspend fun exportJsonBackup(): String {
        return financeRepository.exportJsonBackup(currentStoreProfile)
    }

    suspend fun verifyLogin(usernameOrEmail: String, passwordPlain: String): UserEntity? {
        return authRepository.login(usernameOrEmail, passwordPlain).getOrNull()
    }

    suspend fun createInitialAdmin(usernameOrEmail: String, passwordPlain: String): Result<UserEntity> {
        return authRepository.createInitialAdmin(usernameOrEmail, passwordPlain)
    }

    suspend fun addUser(username: String, passwordPlain: String, role: String): Result<Unit> {
        return authRepository.addUser(username, passwordPlain, role)
    }

    suspend fun deleteUser(username: String): Result<Unit> {
        return authRepository.deleteUser(username)
    }

    suspend fun changePassword(username: String, newPasswordPlain: String): Result<Unit> {
        return authRepository.changePassword(username, newPasswordPlain)
    }

    suspend fun triggerCloudSync(): Result<String> {
        val profile = currentStoreProfile
        return syncRepository.syncAll(
            storeId = profile.storeId,
            terminalId = profile.activeTerminalId
        )
    }

    fun logout() {
        authRepository.logout()
    }
}
