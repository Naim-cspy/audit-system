package com.example.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Remote Authentication Service powered by Firebase Authentication.
 * Handles production cloud logins and account provisioning without storing plain credentials locally.
 */
class FirebaseAuthService(
    private val auth: FirebaseAuth = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        // Fallback for offline/test environments where FirebaseApp might not be initialized
        FirebaseAuth.getInstance()
    }
) {

    val currentUser: FirebaseUser?
        get() = try {
            auth.currentUser
        } catch (e: Exception) {
            null
        }

    val currentUid: String?
        get() = currentUser?.uid

    val isUserLoggedIn: Boolean
        get() = currentUser != null

    suspend fun signIn(email: String, password: String):Result<FirebaseUser> = suspendCancellableCoroutine { continuation ->
        try {
            auth.signInWithEmailAndPassword(email.trim(), password.trim())
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null) {
                        continuation.resume(Result.success(user))
                    } else {
                        continuation.resume(Result.failure(IllegalStateException("Authentication succeeded but user was null")))
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = suspendCancellableCoroutine { continuation ->
        try {
            auth.createUserWithEmailAndPassword(email.trim(), password.trim())
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null) {
                        continuation.resume(Result.success(user))
                    } else {
                        continuation.resume(Result.failure(IllegalStateException("Registration succeeded but user was null")))
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun getIdTokenClaims(forceRefresh: Boolean = false): Result<Map<String, Any>> = suspendCancellableCoroutine { continuation ->
        val user = currentUser
        if (user == null) {
            continuation.resume(Result.failure(IllegalStateException("No authenticated user")))
            return@suspendCancellableCoroutine
        }
        try {
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { tokenResult ->
                    val claims = tokenResult.claims
                    continuation.resume(Result.success(claims))
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // Log or ignore
        }
    }
}
