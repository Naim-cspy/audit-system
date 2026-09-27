package com.example.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Remote Firestore Database Service.
 * Implements multi-tenant isolation adhering to stores/{storeId}/... hierarchy.
 */
class FirestoreService(
    private val firestore: FirebaseFirestore = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        FirebaseFirestore.getInstance()
    }
) {

    // --- User Profile & Store Membership (stores/{storeId}/users/{uid}) ---

    suspend fun getUserProfile(storeId: String, uid: String): Result<FirebaseUserProfile?> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("users").document(uid).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val profile = FirebaseUserProfile.fromMap(uid, snapshot.data ?: emptyMap())
                        continuation.resume(Result.success(profile))
                    } else {
                        continuation.resume(Result.success(null))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun saveUserProfile(storeId: String, profile: FirebaseUserProfile): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("users").document(profile.uid)
                .set(profile.toMap(), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Store Profile ---

    suspend fun getStoreProfile(storeId: String): Result<FirebaseStoreDoc?> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val doc = FirebaseStoreDoc.fromMap(storeId, snapshot.data ?: emptyMap())
                        continuation.resume(Result.success(doc))
                    } else {
                        continuation.resume(Result.success(null))
                    }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun saveStoreProfile(doc: FirebaseStoreDoc): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(doc.storeId)
                .set(doc.toMap(), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Products (stores/{storeId}/products/{productId}) ---

    suspend fun fetchProducts(storeId: String): Result<List<FirebaseProductDoc>> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("products")
                .get()
                .addOnSuccessListener { snapshot ->
                    val products = snapshot.documents.mapNotNull { doc ->
                        FirebaseProductDoc.fromMap(doc.id, doc.data ?: emptyMap())
                    }
                    continuation.resume(Result.success(products))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun upsertProduct(storeId: String, product: FirebaseProductDoc): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("products").document(product.productId)
                .set(product.toMap(), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun deleteProduct(storeId: String, productId: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("products").document(productId)
                .delete()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Sales (stores/{storeId}/sales/{saleId}) ---

    suspend fun recordSale(storeId: String, sale: FirebaseSaleDoc): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("sales").document(sale.saleId)
                .set(sale.toMap())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun doesSaleExist(storeId: String, saleId: String): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("sales").document(saleId)
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot != null && snapshot.exists()))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun fetchSales(storeId: String, limit: Long = 50): Result<List<FirebaseSaleDoc>> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("sales")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener { snapshot ->
                    val sales = snapshot.documents.mapNotNull { doc ->
                        FirebaseSaleDoc.fromMap(doc.id, doc.data ?: emptyMap())
                    }
                    continuation.resume(Result.success(sales))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Ledger (stores/{storeId}/ledger/{ledgerId}) ---

    suspend fun doesLedgerExist(storeId: String, ledgerId: String): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("ledger").document(ledgerId)
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot != null && snapshot.exists()))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun recordLedger(storeId: String, ledger: FirebaseLedgerDoc): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("ledger").document(ledger.ledgerId)
                .set(ledger.toMap())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun fetchLedger(storeId: String, limit: Long = 50): Result<List<FirebaseLedgerDoc>> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("ledger")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener { snapshot ->
                    val entries = snapshot.documents.mapNotNull { doc ->
                        FirebaseLedgerDoc.fromMap(doc.id, doc.data ?: emptyMap())
                    }
                    continuation.resume(Result.success(entries))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Audit Logs (stores/{storeId}/auditLogs/{logId}) ---

    suspend fun recordAuditLog(storeId: String, log: FirebaseAuditLogDoc): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("auditLogs").document(log.logId)
                .set(log.toMap())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Sync Events (stores/{storeId}/syncEvents/{syncId}) ---

    suspend fun recordSyncEvent(storeId: String, event: FirebaseSyncEventDoc): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("syncEvents").document(event.syncId)
                .set(event.toMap())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Cross-Tenant Isolation Verification ---

    suspend fun getProduct(storeId: String, productId: String): Result<FirebaseProductDoc?> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(storeId)
                .collection("products").document(productId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        continuation.resume(Result.success(FirebaseProductDoc.fromMap(productId, snapshot.data ?: emptyMap())))
                    } else {
                        continuation.resume(Result.success(null))
                    }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun testCrossTenantRead(targetStoreId: String): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("stores").document(targetStoreId)
                .collection("products").limit(1)
                .get()
                .addOnSuccessListener {
                    continuation.resume(Result.failure(SecurityException("CRITICAL TENANT ISOLATION FAILURE: Successfully accessed stores/$targetStoreId/products! Cross-tenant isolation must reject foreign store access.")))
                }
                .addOnFailureListener { exception ->
                    if (exception is com.google.firebase.firestore.FirebaseFirestoreException &&
                        exception.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        continuation.resume(Result.success("ISOLATION VERIFIED: Request to stores/$targetStoreId/products was explicitly rejected with PERMISSION_DENIED at Firestore security boundary."))
                    } else {
                        continuation.resume(Result.failure(exception))
                    }
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    // --- Security Events (security_events/{eventId}) ---

    suspend fun recordSecurityEvent(event: FirebaseSyncEventDoc): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("security_events").document(event.syncId)
                .set(event.toMap())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun testPlatformAnalyticsAccess(): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        try {
            firestore.collection("platform_analytics").document("global_summary")
                .get()
                .addOnSuccessListener {
                    continuation.resume(Result.success(true))
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }
}
