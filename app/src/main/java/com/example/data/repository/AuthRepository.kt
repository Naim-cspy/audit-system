package com.example.data.repository

import com.example.data.dao.AuditLogDao
import com.example.data.dao.UserDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.StoreProfile
import com.example.data.model.UserEntity
import com.example.data.remote.FirebaseAuthService
import com.example.data.remote.FirebaseStoreDoc
import com.example.data.remote.FirebaseUserProfile
import com.example.data.remote.FirestoreService
import com.example.data.security.PasswordSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Authentication and Identity Repository.
 * Integrates Firebase Authentication as the cloud source of truth,
 * with Room local caching and secure offline fallback.
 * Strictly derives storeId from the authenticated account to prevent cross-tenant access.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val auditLogDao: AuditLogDao,
    val authService: FirebaseAuthService = FirebaseAuthService(),
    val firestoreService: FirestoreService = FirestoreService()
) {

    private val _currentStoreProfile = MutableStateFlow(
        StoreProfile(
            storeId = "UNAUTHENTICATED",
            storeName = "Supermarket POS Terminal",
            region = "Global Multi-Tenant",
            subscriptionStatus = "PENDING_AUTH"
        )
    )
    val currentStoreProfile: StateFlow<StoreProfile> = _currentStoreProfile.asStateFlow()

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    val isFirebaseOnline: Boolean
        get() = authService.currentUser != null || authService.currentUid != null

    /**
     * Authenticates via Firebase Authentication first.
     * On success, resolves store membership from Firestore, verifies role,
     * updates the active StoreProfile, and caches the user session in Room.
     * If the login is a local username or offline, validates against local Room PBKDF2 hash.
     */
    suspend fun login(identifier: String, passwordPlain: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val cleanIdentifier = identifier.trim()
        val cleanPass = passwordPlain.trim()

        if (cleanIdentifier.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Identifier and password cannot be empty"))
        }

        // 1. If identifier contains '@', try Firebase Authentication directly
        if (cleanIdentifier.contains("@")) {
            val fbResult = authService.signIn(cleanIdentifier, cleanPass)
            if (fbResult.isSuccess) {
                val fbUser = fbResult.getOrThrow()
                val uid = fbUser.uid
                val email = fbUser.email ?: cleanIdentifier

                // 1. Extract trusted token claims from Firebase JWT
                val claimsResult = authService.getIdTokenClaims(forceRefresh = true)
                val claims = claimsResult.getOrNull() ?: emptyMap()
                val claimStoreId = claims["store_id"] as? String
                val claimRole = claims["role"] as? String

                android.util.Log.i("AuthRepository", "Firebase login success UID: $uid")
                if (!claimStoreId.isNullOrBlank()) {
                    android.util.Log.i("AuthRepository", "Authenticated store: $claimStoreId")
                    android.util.Log.i("AuthRepository", "Role: ${claimRole ?: "OWNER"}")
                }

                // Resolve store membership (trusted token claims take priority, then Firestore doc fallback)
                val finalStoreId: String
                val finalRole: String

                if (!claimStoreId.isNullOrBlank()) {
                    finalStoreId = claimStoreId
                    finalRole = claimRole ?: "OWNER"
                } else {
                    val profileResult = firestoreService.getUserProfile(uid)
                    var userProfile = profileResult.getOrNull()
                    if (userProfile == null || userProfile.storeId.isBlank()) {
                        val defaultStoreId = "STR-" + uid.take(6).uppercase()
                        userProfile = FirebaseUserProfile(
                            uid = uid,
                            email = email,
                            storeId = defaultStoreId,
                            role = "OWNER",
                            displayName = cleanIdentifier.substringBefore("@")
                        )
                        firestoreService.saveUserProfile(userProfile)
                    }
                    finalStoreId = userProfile.storeId
                    finalRole = userProfile.role
                }

                // Load or sync store document
                val storeDocResult = firestoreService.getStoreProfile(finalStoreId)
                val storeDoc = storeDocResult.getOrNull() ?: FirebaseStoreDoc(
                    storeId = finalStoreId,
                    storeName = "Supermarket Terminal ($finalStoreId)",
                    region = "Central District"
                ).also { firestoreService.saveStoreProfile(it) }

                _currentStoreProfile.value = storeDoc.toStoreProfile()

                // Cache or update in local Room
                val localUser = UserEntity(
                    username = cleanIdentifier,
                    password_hash = "FIREBASE_MANAGED_UID_$uid",
                    salt = "FIREBASE_AUTH",
                    role = if (finalRole.equals("cashier", ignoreCase = true)) "cashier" else "admin",
                    store_id = finalStoreId
                )
                val existingLocal = userDao.getByUsername(cleanIdentifier)
                if (existingLocal == null) {
                    userDao.insert(localUser)
                } else {
                    userDao.updatePassword(cleanIdentifier, localUser.password_hash, localUser.salt)
                }

                auditLogDao.insert(
                    AuditLogEntity(
                        action = "FIREBASE_LOGIN_SUCCESS",
                        details = "User '$cleanIdentifier' signed in via Firebase Auth (UID: $uid, Store: $finalStoreId, Role: $finalRole)",
                        store_id = finalStoreId,
                        user_id = uid
                    )
                )

                return@withContext Result.success(localUser)
            }
        }

        // 2. Validate against local Room PBKDF2 database
        val localUser = userDao.getByUsername(cleanIdentifier)
        if (localUser != null) {
            val isValid = PasswordSecurity.verifyPassword(cleanPass, localUser.salt, localUser.password_hash)
            if (isValid) {
                // Update active store profile based on local user's assigned store_id
                _currentStoreProfile.value = StoreProfile(
                    storeId = localUser.store_id.ifBlank { "STR-LOCAL-001" },
                    storeName = "Supermarket Terminal",
                    region = "Local Offline Cache"
                )
                auditLogDao.insert(
                    AuditLogEntity(
                        action = "LOCAL_LOGIN_SUCCESS",
                        details = "User '$cleanIdentifier' authenticated locally against secure offline cache",
                        store_id = localUser.store_id,
                        user_id = localUser.username
                    )
                )
                return@withContext Result.success(localUser)
            }
        }

        auditLogDao.insert(
            AuditLogEntity(
                action = "LOGIN_FAILED",
                details = "Authentication failed for identifier '$cleanIdentifier'",
                store_id = _currentStoreProfile.value.storeId,
                user_id = cleanIdentifier
            )
        )
        Result.failure(IllegalArgumentException("Invalid credentials or account not found"))
    }

    /**
     * Provisions initial administrator credentials.
     * Registers both on Firebase (if email format provided) and local Room database.
     */
    suspend fun createInitialAdmin(usernameOrEmail: String, passwordPlain: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val cleanId = usernameOrEmail.trim()
        val cleanPass = passwordPlain.trim()

        if (cleanId.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Username/email and password cannot be empty"))
        }
        if (cleanPass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val allUsersNow = userDao.getAllUsers().first()
        if (allUsersNow.isNotEmpty()) {
            return@withContext Result.failure(IllegalStateException("Administrator account already provisioned"))
        }

        var storeId = "STR-LBN-NAB-001"
        var uid = "admin"

        // If email, register with Firebase Auth
        if (cleanId.contains("@")) {
            val fbResult = authService.signUp(cleanId, cleanPass)
            if (fbResult.isSuccess) {
                val fbUser = fbResult.getOrThrow()
                uid = fbUser.uid
                storeId = "STR-" + uid.take(6).uppercase()

                val profile = FirebaseUserProfile(
                    uid = uid,
                    email = cleanId,
                    storeId = storeId,
                    role = "admin",
                    displayName = cleanId.substringBefore("@")
                )
                firestoreService.saveUserProfile(profile)

                val storeDoc = FirebaseStoreDoc(
                    storeId = storeId,
                    storeName = "Al-Makhzen Supermarket",
                    region = "Nabatieh Area, South Lebanon"
                )
                firestoreService.saveStoreProfile(storeDoc)
                _currentStoreProfile.value = storeDoc.toStoreProfile()
            }
        } else {
            _currentStoreProfile.value = StoreProfile(
                storeId = storeId,
                storeName = "Al-Makhzen Supermarket",
                region = "Nabatieh Area, South Lebanon"
            )
        }

        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        val adminUser = UserEntity(
            username = cleanId,
            password_hash = hash,
            salt = salt,
            role = "admin",
            store_id = storeId
        )
        userDao.insert(adminUser)

        auditLogDao.insert(
            AuditLogEntity(
                action = "INITIAL_ADMIN_PROVISIONED",
                details = "Primary administrator account provisioned: '$cleanId' (Store: $storeId)",
                store_id = storeId,
                user_id = cleanId
            )
        )

        Result.success(adminUser)
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

        val currentStore = _currentStoreProfile.value.storeId
        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        val newUser = UserEntity(
            username = cleanUser,
            password_hash = hash,
            salt = salt,
            role = if (role.lowercase() == "admin") "admin" else "cashier",
            store_id = currentStore
        )
        userDao.insert(newUser)

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_USER",
                details = "Added user '$cleanUser' with role '$role' to store '$currentStore'",
                store_id = currentStore,
                user_id = cleanUser
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteUser(username: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val userToDelete = userDao.getByUsername(cleanUser)
            ?: return@withContext Result.failure(IllegalArgumentException("User $cleanUser does not exist"))

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
                details = "Deleted user '$cleanUser'",
                store_id = _currentStoreProfile.value.storeId,
                user_id = cleanUser
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
                details = "Password updated for user '$cleanUser'",
                store_id = _currentStoreProfile.value.storeId,
                user_id = cleanUser
            )
        )
        Result.success(Unit)
    }

    fun logout() {
        authService.signOut()
        _currentStoreProfile.value = StoreProfile(
            storeId = "UNAUTHENTICATED",
            storeName = "Supermarket POS Terminal",
            region = "Global Multi-Tenant",
            subscriptionStatus = "PENDING_AUTH"
        )
    }
}
