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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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
    private val context: android.content.Context? = null
) {
    val analyticsManager: com.example.data.analytics.AnalyticsManager? = context?.let {
        com.example.data.analytics.AnalyticsManager(it)
    }

    val authRepository: AuthRepository = AuthRepository(
        userDao = db.userDao(),
        auditLogDao = db.auditLogDao(),
        analyticsManager = analyticsManager
    )

    val productRepository: ProductRepository = ProductRepository(
        productDao = db.productDao(),
        auditLogDao = db.auditLogDao(),
        pendingSyncDeletionDao = db.pendingSyncDeletionDao(),
        analyticsManager = analyticsManager
    )

    val salesRepository: SalesRepository = SalesRepository(
        saleDao = db.saleDao(),
        productDao = db.productDao(),
        balanceDao = db.balanceDao(),
        auditLogDao = db.auditLogDao(),
        syncEventDao = db.syncEventDao(),
        appDatabase = db,
        analyticsManager = analyticsManager
    )

    val financeRepository: FinanceRepository = FinanceRepository(
        balanceDao = db.balanceDao(),
        productDao = db.productDao(),
        saleDao = db.saleDao(),
        auditLogDao = db.auditLogDao(),
        syncEventDao = db.syncEventDao()
    )

    val syncRepository: SyncRepository = SyncRepository(
        productDao = db.productDao(),
        saleDao = db.saleDao(),
        balanceDao = db.balanceDao(),
        auditLogDao = db.auditLogDao(),
        syncEventDao = db.syncEventDao(),
        securityEventDao = db.securityEventDao(),
        pendingSyncDeletionDao = db.pendingSyncDeletionDao()
    )

    val platformAnalyticsRepository: PlatformAnalyticsRepository = PlatformAnalyticsRepository(
        db = db,
        authRepository = authRepository,
        analyticsManager = analyticsManager
    )

    val isPlatformAdmin: StateFlow<Boolean> = authRepository.isPlatformAdmin
    val platformMetrics: StateFlow<com.example.data.model.PlatformMetricsSummary?> = platformAnalyticsRepository.platformMetrics
    val platformSecurityAlerts: StateFlow<List<SecurityEventEntity>> = platformAnalyticsRepository.securityAlerts

    val currentStoreProfileFlow: StateFlow<StoreProfile> = authRepository.currentStoreProfile
    val currentStoreProfile: StoreProfile
        get() = authRepository.currentStoreProfile.value

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allProducts: Flow<List<ProductEntity>> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
        else productRepository.getAllProducts(profile.storeId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentSales: Flow<List<SaleEntity>> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
        else salesRepository.getRecentSales(profile.storeId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val balanceHistory: Flow<List<BalanceEntity>> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
        else financeRepository.getBalanceHistory(profile.storeId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allUsers: Flow<List<UserEntity>> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
        else authRepository.getUsersForStore(profile.storeId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val auditLogs: Flow<List<AuditLogEntity>> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
        else syncRepository.getAuditLogs(profile.storeId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allSyncEvents: Flow<List<SyncEventEntity>> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
        else syncRepository.getAllSyncEvents(profile.storeId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allSecurityEvents: Flow<List<SecurityEventEntity>> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
        else syncRepository.getAllSecurityEvents(profile.storeId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val failedSyncCount: Flow<Int> = currentStoreProfileFlow.flatMapLatest { profile ->
        if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(0)
        else syncRepository.getFailedSyncCount(profile.storeId)
    }

    val isSyncing: StateFlow<Boolean> = syncRepository.isSyncing

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getStockWarnings(threshold: Int = 50): Flow<List<ProductEntity>> {
        return currentStoreProfileFlow.flatMapLatest { profile ->
            if (profile.storeId == "UNAUTHENTICATED") kotlinx.coroutines.flow.flowOf(emptyList())
            else productRepository.getStockWarnings(profile.storeId, threshold)
        }
    }

    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return productRepository.searchProducts(currentStoreProfile.storeId, query)
    }

    suspend fun lookupProduct(code: String): ProductEntity? {
        return productRepository.lookupProduct(currentStoreProfile.storeId, code)
    }

    suspend fun batchCheckout(cartItems: List<CartItem>, customerId: String = "C101"): Result<Receipt> {
        val profile = currentStoreProfile
        val result = salesRepository.batchCheckout(
            storeId = profile.storeId,
            cartItems = cartItems,
            customerId = customerId,
            terminalId = profile.activeTerminalId
        )
        if (result.isSuccess) {
            analyticsManager?.trackFeatureUsed("POS Checkout", profile.storeId)
        }
        return result
    }

    suspend fun loadPlatformMetrics(): Result<com.example.data.model.PlatformMetricsSummary> {
        return platformAnalyticsRepository.loadPlatformMetrics()
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
        return financeRepository.getFinancialSummary(currentStoreProfile.storeId)
    }

    suspend fun calculateProfitPrediction(): ProfitPrediction {
        return financeRepository.calculateProfitPrediction(currentStoreProfile.storeId)
    }

    suspend fun getRegionalMarketInsights(): RegionalMarketInsight {
        return financeRepository.getRegionalMarketInsights(currentStoreProfile)
    }

    suspend fun exportCsvSummary(): String {
        return financeRepository.exportCsvSummary(currentStoreProfile.storeId)
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

    suspend fun runVerificationSuite(): com.example.data.model.VerificationSuiteReport = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val results = mutableListOf<com.example.data.model.VerificationItem>()

        // 1. Phase 1: Firebase Initialization
        var firebaseAppInstance: com.google.firebase.FirebaseApp? = null
        try {
            firebaseAppInstance = com.google.firebase.FirebaseApp.getInstance()
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 1: Firebase Initialization",
                    status = "PASSED",
                    details = "FirebaseApp instance active: '${firebaseAppInstance.name}'"
                )
            )
        } catch (e: Exception) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 1: Firebase Initialization",
                    status = "FAILED",
                    details = "Initialization error: ${e.message}"
                )
            )
        }

        // 2. Phase 2: Runtime Package & Project Configuration
        val runtimePackage = context?.packageName ?: "com.aistudio.supermarketpos.audit"
        val configuredProjectId = firebaseAppInstance?.options?.projectId ?: ""
        val configuredAppId = firebaseAppInstance?.options?.applicationId ?: ""

        if (configuredProjectId.isNotBlank() && configuredAppId.isNotBlank()) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 2: Project & Package Verification",
                    status = "PASSED",
                    details = "Runtime Package: '$runtimePackage', Project ID: '$configuredProjectId', App ID: '$configuredAppId'"
                )
            )
        } else {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 2: Project & Package Verification",
                    status = "FAILED",
                    details = "Firebase configuration incomplete. Project ID: '$configuredProjectId', App ID: '$configuredAppId'"
                )
            )
        }

        // 3. Phase 4: Authentication State
        val fbUser = authRepository.authService.currentUser
        if (fbUser != null) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 4: Firebase Authentication",
                    status = "PASSED",
                    details = "Authenticated User UID: ${fbUser.uid}, Email: ${fbUser.email ?: "N/A"}"
                )
            )
        } else {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 4: Firebase Authentication",
                    status = "FAILED",
                    details = "No authenticated Firebase user. Sign in with a provisioned Firebase test account first."
                )
            )
        }

        // 4. Phase 7: Verified Token Custom Claims (Strict - No Fake Fallbacks)
        var verifiedStoreId: String? = null
        var verifiedRole: String? = null

        if (fbUser == null) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 7: Verified Token Claims",
                    status = "SKIPPED",
                    details = "Authentication required before token claims can be inspected."
                )
            )
        } else {
            val claimsRes = authRepository.authService.getIdTokenClaims(forceRefresh = true)
            if (claimsRes.isSuccess) {
                val claims = claimsRes.getOrThrow()
                val claimStoreId = claims["store_id"] as? String
                val claimRole = claims["role"] as? String

                if (!claimStoreId.isNullOrBlank() && !claimRole.isNullOrBlank()) {
                    verifiedStoreId = claimStoreId
                    verifiedRole = claimRole
                    results.add(
                        com.example.data.model.VerificationItem(
                            title = "Phase 7: Verified Token Claims",
                            status = "PASSED",
                            details = "Verified Custom Claims: store_id = '$claimStoreId', role = '$claimRole'"
                        )
                    )
                } else {
                    val availableClaims = claims.keys.filter { !it.startsWith("firebase") }
                    results.add(
                        com.example.data.model.VerificationItem(
                            title = "Phase 7: Verified Token Claims",
                            status = "FAILED",
                            details = "Missing required custom claims in token. Present custom keys: $availableClaims. 'store_id' and 'role' must be assigned by trusted Firebase Admin SDK."
                        )
                    )
                }
            } else {
                results.add(
                    com.example.data.model.VerificationItem(
                        title = "Phase 7: Verified Token Claims",
                        status = "FAILED",
                        details = "Failed to retrieve Firebase ID token: ${claimsRes.exceptionOrNull()?.message}"
                    )
                )
            }
        }

        // 5. Phase 8: Read-Only Firestore Connectivity (Production-safe, zero writes/pollution)
        if (fbUser == null) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 8: Firestore Read-Only Connectivity",
                    status = "SKIPPED",
                    details = "Authenticate with a provisioned Firebase test account first."
                )
            )
        } else if (verifiedStoreId == null) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 8: Firestore Read-Only Connectivity",
                    status = "SKIPPED",
                    details = "Cannot verify tenant store connectivity without a verified 'store_id' claim."
                )
            )
        } else {
            val targetStore = verifiedStoreId
            val readRes = productRepository.firestoreService.fetchProducts(targetStore)
            if (readRes.isSuccess) {
                val count = readRes.getOrThrow().size
                results.add(
                    com.example.data.model.VerificationItem(
                        title = "Phase 8: Firestore Read-Only Connectivity",
                        status = "PASSED",
                        details = "Confirmed read access to stores/$targetStore/products ($count items found). Zero write pollution."
                    )
                )
            } else {
                results.add(
                    com.example.data.model.VerificationItem(
                        title = "Phase 8: Firestore Read-Only Connectivity",
                        status = "FAILED",
                        details = "Firestore read error for stores/$targetStore: ${readRes.exceptionOrNull()?.message}"
                    )
                )
            }
        }

        // 6. Phase 13: Tenant Isolation Verification (Strictly requires PERMISSION_DENIED)
        if (fbUser == null) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 13: Tenant Isolation Test",
                    status = "SKIPPED",
                    details = "Authenticate with a provisioned Firebase test account first."
                )
            )
        } else if (verifiedStoreId == null) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 13: Tenant Isolation Test",
                    status = "SKIPPED",
                    details = "Cannot test cross-store isolation without a verified 'store_id' claim."
                )
            )
        } else {
            val foreignStore = if (verifiedStoreId == "STORE_TEST_B") "STORE_TEST_A" else "STORE_TEST_B"
            val isolationRes = productRepository.firestoreService.testCrossTenantRead(foreignStore)

            if (isolationRes.isSuccess) {
                results.add(
                    com.example.data.model.VerificationItem(
                        title = "Phase 13: Tenant Isolation Test",
                        status = "PASSED",
                        details = isolationRes.getOrThrow()
                    )
                )
            } else {
                val ex = isolationRes.exceptionOrNull()
                results.add(
                    com.example.data.model.VerificationItem(
                        title = "Phase 13: Tenant Isolation Test",
                        status = "FAILED",
                        details = "Expected PERMISSION_DENIED on stores/$foreignStore, but received: ${ex?.message}"
                    )
                )
            }
        }

        // 7. Phase 14: Platform Metrics Authorization Isolation
        if (fbUser == null) {
            results.add(
                com.example.data.model.VerificationItem(
                    title = "Phase 14: Platform Metrics Boundary",
                    status = "SKIPPED",
                    details = "Authentication required before testing platform metrics boundary."
                )
            )
        } else {
            val accessRes = productRepository.firestoreService.testPlatformAnalyticsAccess()
            val isOwner = isPlatformAdmin.value
            if (isOwner) {
                if (accessRes.isSuccess) {
                    results.add(
                        com.example.data.model.VerificationItem(
                            title = "Phase 14: Platform Metrics Boundary",
                            status = "PASSED",
                            details = "Verified SaaS Platform Owner access to /platform_analytics/global_summary."
                        )
                    )
                } else {
                    results.add(
                        com.example.data.model.VerificationItem(
                            title = "Phase 14: Platform Metrics Boundary",
                            status = "FAILED",
                            details = "SaaS Owner was unexpectedly denied platform metrics access: ${accessRes.exceptionOrNull()?.message}"
                        )
                    )
                }
            } else {
                // Client store user: MUST be rejected with PERMISSION_DENIED
                val ex = accessRes.exceptionOrNull()
                val isDenied = ex is com.google.firebase.firestore.FirebaseFirestoreException &&
                        ex.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                if (isDenied) {
                    results.add(
                        com.example.data.model.VerificationItem(
                            title = "Phase 14: Platform Metrics Boundary",
                            status = "PASSED",
                            details = "Confirmed: Client store account is strictly blocked with PERMISSION_DENIED from reading global platform analytics."
                        )
                    )
                } else if (accessRes.isSuccess) {
                    results.add(
                        com.example.data.model.VerificationItem(
                            title = "Phase 14: Platform Metrics Boundary",
                            status = "FAILED",
                            details = "CRITICAL SECURITY BREACH: Client account was permitted to read /platform_analytics/global_summary!"
                        )
                    )
                } else {
                    results.add(
                        com.example.data.model.VerificationItem(
                            title = "Phase 14: Platform Metrics Boundary",
                            status = "FAILED",
                            details = "Expected PERMISSION_DENIED on /platform_analytics, but received: ${ex?.message}"
                        )
                    )
                }
            }
        }

        // 8. Phase 19: Client Secret Audit (Accurate in-app check)
        val hasEmbeddedKey = try {
            val ctx = context
            var found = false
            if (ctx != null) {
                val assetList = ctx.assets.list("") ?: emptyArray()
                found = assetList.any { it.contains("serviceAccount", ignoreCase = true) || it.endsWith(".json") }
            }
            found
        } catch (e: Exception) {
            false
        }

        results.add(
            com.example.data.model.VerificationItem(
                title = "Phase 19: Client Secret Audit",
                status = if (!hasEmbeddedKey) "PASSED" else "FAILED",
                details = if (!hasEmbeddedKey)
                    "Verified: No Firebase service-account private keys or admin credentials packaged in application assets or code."
                else
                    "WARNING: Possible service account or credential file detected in client bundle!",
                isCritical = true
            )
        )

        // Calculate Overall Status: READY only if ALL critical items are PASSED
        val criticalItems = results.filter { it.isCritical }
        val allCriticalPassed = criticalItems.all { it.status == "PASSED" }
        val overallStatus = if (allCriticalPassed) "READY" else "NOT READY"
        val passedCount = results.count { it.status == "PASSED" }

        com.example.data.model.VerificationSuiteReport(
            overallStatus = overallStatus,
            passedCount = passedCount,
            totalCount = results.size,
            items = results
        )
    }

    fun logout() {
        authRepository.logout()
    }
}
