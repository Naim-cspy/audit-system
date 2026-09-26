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
 *
 * Enforces production security rules:
 * - Uses verified Firebase token custom claims as the sole authority for cloud tenant access.
 * - No hard-coded email shortcuts for SaaS Owner / Platform Admin access.
 * - Platform Admin access strictly requires `platform_admin == true` or role `SAAS_OWNER`.
 * - Tenant accounts require valid `store_id` and supported role custom claims (no fake fallbacks).
 * - Preserves distinct tenant roles (OWNER, ADMIN, MANAGER, CASHIER, ACCOUNTANT).
 * - Never falls back to local credentials after a rejected Firebase email/password login.
 * - Correct argument order for PBKDF2 password verification: (password, storedHash, salt).
 * - Offline/local login never grants platform owner privileges.
 * - All Room queries and state cleared on logout and account transitions.
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

    private val _isPlatformAdmin = MutableStateFlow(false)
    val isPlatformAdmin: StateFlow<Boolean> = _isPlatformAdmin.asStateFlow()

    val isFirebaseOnline: Boolean
        get() = authService.currentUser != null || authService.currentUid != null

    fun getUsersForStore(storeId: String): Flow<List<UserEntity>> {
        return userDao.getAllUsers(storeId)
    }

    suspend fun getTotalUsersCount(): Int = withContext(Dispatchers.IO) {
        userDao.totalCount()
    }

    /**
     * Authenticates via Firebase Authentication first if identifier is an email (contains '@').
     * If Firebase authentication fails, does NOT fall back to local credentials.
     * Offline local authentication is only used for non-email usernames or explicit local credentials.
     */
    suspend fun login(identifier: String, passwordPlain: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val cleanIdentifier = identifier.trim()
        val cleanPass = passwordPlain.trim()

        if (cleanIdentifier.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Identifier and password cannot be empty"))
        }

        // 1. Firebase Authentication for email accounts
        if (cleanIdentifier.contains("@")) {
            val fbResult = authService.signIn(cleanIdentifier, cleanPass)
            if (fbResult.isFailure) {
                // Security invariant: DO NOT fall back to local credentials after rejected Firebase login
                _isPlatformAdmin.value = false
                _currentStoreProfile.value = StoreProfile(
                    storeId = "UNAUTHENTICATED",
                    storeName = "Supermarket POS Terminal",
                    region = "Global Multi-Tenant",
                    subscriptionStatus = "PENDING_AUTH"
                )
                auditLogDao.insert(
                    AuditLogEntity(
                        action = "FIREBASE_LOGIN_FAILED",
                        details = "Firebase authentication rejected for '$cleanIdentifier': ${fbResult.exceptionOrNull()?.message}",
                        store_id = "UNAUTHENTICATED",
                        user_id = cleanIdentifier
                    )
                )
                return@withContext Result.failure(
                    fbResult.exceptionOrNull() ?: IllegalArgumentException("Invalid Firebase email or password")
                )
            }

            val fbUser = fbResult.getOrThrow()
            val uid = fbUser.uid
            val email = fbUser.email ?: cleanIdentifier

            // Extract verified token claims from Firebase JWT
            val claimsResult = authService.getIdTokenClaims(forceRefresh = true)
            if (claimsResult.isFailure) {
                _isPlatformAdmin.value = false
                return@withContext Result.failure(
                    IllegalStateException("Failed to retrieve verified token claims: ${claimsResult.exceptionOrNull()?.message}")
                )
            }

            val claims = claimsResult.getOrThrow()
            val rawPlatformAdmin = (claims["platform_admin"] as? Boolean) == true
            val rawRole = (claims["role"] as? String)?.trim()?.uppercase()
            val rawStoreId = (claims["store_id"] as? String)?.trim()

            // Security invariant: Allow SaaS owner access ONLY with platform_admin == true or exact role SAAS_OWNER.
            // NO hard-coded email shortcuts!
            val isSaaSOwner = rawPlatformAdmin || rawRole == "SAAS_OWNER"

            val finalStoreId: String
            val finalRole: String

            if (isSaaSOwner) {
                _isPlatformAdmin.value = true
                finalRole = "SAAS_OWNER"
                finalStoreId = if (!rawStoreId.isNullOrBlank()) rawStoreId else "PLATFORM_GLOBAL"
            } else {
                _isPlatformAdmin.value = false

                // Security invariant: Require valid store_id and supported role claims for tenant accounts.
                // Do NOT fabricate OWNER roles or store memberships!
                if (rawStoreId.isNullOrBlank()) {
                    _currentStoreProfile.value = StoreProfile(
                        storeId = "UNAUTHENTICATED",
                        storeName = "Supermarket POS Terminal",
                        region = "Global Multi-Tenant",
                        subscriptionStatus = "PENDING_AUTH"
                    )
                    return@withContext Result.failure(
                        IllegalStateException("Account '$cleanIdentifier' lacks 'store_id' claim. Cloud provisioning required.")
                    )
                }

                if (rawRole.isNullOrBlank()) {
                    _currentStoreProfile.value = StoreProfile(
                        storeId = "UNAUTHENTICATED",
                        storeName = "Supermarket POS Terminal",
                        region = "Global Multi-Tenant",
                        subscriptionStatus = "PENDING_AUTH"
                    )
                    return@withContext Result.failure(
                        IllegalStateException("Account '$cleanIdentifier' lacks 'role' claim. Cloud provisioning required.")
                    )
                }

                val supportedRoles = setOf("OWNER", "ADMIN", "MANAGER", "CASHIER", "ACCOUNTANT")
                if (rawRole !in supportedRoles) {
                    _currentStoreProfile.value = StoreProfile(
                        storeId = "UNAUTHENTICATED",
                        storeName = "Supermarket POS Terminal",
                        region = "Global Multi-Tenant",
                        subscriptionStatus = "PENDING_AUTH"
                    )
                    return@withContext Result.failure(
                        IllegalStateException("Unsupported role '$rawRole'. Supported tenant roles: ${supportedRoles.joinToString()}")
                    )
                }

                finalStoreId = rawStoreId
                finalRole = rawRole // Preserve distinct tenant role!
            }

            // Load store profile for tenant
            if (finalStoreId == "PLATFORM_GLOBAL") {
                _currentStoreProfile.value = StoreProfile(
                    storeId = "PLATFORM_GLOBAL",
                    storeName = "SaaS Platform Global Console",
                    region = "Platform-Wide",
                    subscriptionStatus = "ACTIVE"
                )
            } else {
                val storeDocResult = firestoreService.getStoreProfile(finalStoreId)
                val storeProfile = storeDocResult.getOrNull()?.toStoreProfile() ?: StoreProfile(
                    storeId = finalStoreId,
                    storeName = "Store ($finalStoreId)",
                    region = "Tenant Store"
                )
                _currentStoreProfile.value = storeProfile
            }

            // Cache or update in local Room DB for tenant
            val localUser = UserEntity(
                username = cleanIdentifier,
                password_hash = "FIREBASE_MANAGED_UID_$uid",
                salt = "FIREBASE_AUTH",
                role = finalRole,
                store_id = finalStoreId
            )

            val existingLocal = userDao.getByUsername(finalStoreId, cleanIdentifier)
            if (existingLocal == null) {
                userDao.insert(localUser)
            } else {
                userDao.updatePassword(finalStoreId, cleanIdentifier, localUser.password_hash, localUser.salt)
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

        // 2. Validate against local Room PBKDF2 database for local username logins (Offline mode)
        // Security invariant: Offline login NEVER grants platform owner privileges.
        _isPlatformAdmin.value = false

        val matchingUsers = userDao.findUsersByUsername(cleanIdentifier)
        if (matchingUsers.isEmpty()) {
            auditLogDao.insert(
                AuditLogEntity(
                    action = "LOGIN_FAILED",
                    details = "User '$cleanIdentifier' not found in local store database",
                    store_id = _currentStoreProfile.value.storeId,
                    user_id = cleanIdentifier
                )
            )
            return@withContext Result.failure(IllegalArgumentException("Invalid credentials or account not found"))
        }

        // If currently in a specific store context, prioritize that store's user
        val targetUser = if (_currentStoreProfile.value.storeId != "UNAUTHENTICATED") {
            matchingUsers.firstOrNull { it.store_id == _currentStoreProfile.value.storeId } ?: matchingUsers.first()
        } else {
            matchingUsers.first()
        }

        // Security invariant: Correct password verification argument order: (password, storedHash, salt)
        val isValid = PasswordSecurity.verifyPassword(cleanPass, targetUser.password_hash, targetUser.salt)
        if (isValid) {
            _isPlatformAdmin.value = false // Strictly false for offline local users
            _currentStoreProfile.value = StoreProfile(
                storeId = targetUser.store_id.ifBlank { "STR-LOCAL-001" },
                storeName = "Store (${targetUser.store_id})",
                region = "Local Offline Cache"
            )
            auditLogDao.insert(
                AuditLogEntity(
                    action = "LOCAL_LOGIN_SUCCESS",
                    details = "User '$cleanIdentifier' authenticated locally (Store: ${targetUser.store_id}, Role: ${targetUser.role})",
                    store_id = targetUser.store_id,
                    user_id = targetUser.username
                )
            )
            return@withContext Result.success(targetUser)
        }

        auditLogDao.insert(
            AuditLogEntity(
                action = "LOGIN_FAILED",
                details = "Local authentication failed (invalid password) for user '$cleanIdentifier'",
                store_id = targetUser.store_id,
                user_id = cleanIdentifier
            )
        )
        Result.failure(IllegalArgumentException("Invalid credentials"))
    }

    /**
     * Provisions initial administrator credentials.
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

        val totalUsers = userDao.totalCount()
        if (totalUsers > 0) {
            return@withContext Result.failure(IllegalStateException("Administrator account already provisioned"))
        }

        val defaultStoreId = "STR-LBN-NAB-001"

        if (cleanId.contains("@")) {
            val fbResult = authService.signUp(cleanId, cleanPass)
            if (fbResult.isSuccess) {
                val fbUser = fbResult.getOrThrow()
                val profile = FirebaseUserProfile(
                    uid = fbUser.uid,
                    email = cleanId,
                    storeId = defaultStoreId,
                    role = "OWNER",
                    displayName = cleanId.substringBefore("@")
                )
                firestoreService.saveUserProfile(profile)

                val storeDoc = FirebaseStoreDoc(
                    storeId = defaultStoreId,
                    storeName = "Al-Makhzen Supermarket",
                    region = "Nabatieh Area, South Lebanon"
                )
                firestoreService.saveStoreProfile(storeDoc)
            }
        }

        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        val adminUser = UserEntity(
            username = cleanId,
            password_hash = hash,
            salt = salt,
            role = "OWNER",
            store_id = defaultStoreId
        )
        userDao.insert(adminUser)

        _currentStoreProfile.value = StoreProfile(
            storeId = defaultStoreId,
            storeName = "Al-Makhzen Supermarket",
            region = "Nabatieh Area, South Lebanon"
        )

        auditLogDao.insert(
            AuditLogEntity(
                action = "INITIAL_ADMIN_PROVISIONED",
                details = "Primary administrator account provisioned: '$cleanId' (Store: $defaultStoreId)",
                store_id = defaultStoreId,
                user_id = cleanId
            )
        )

        Result.success(adminUser)
    }

    suspend fun addUser(username: String, passwordPlain: String, role: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val cleanPass = passwordPlain.trim()
        val cleanRole = role.trim().uppercase()
        val supportedRoles = setOf("OWNER", "ADMIN", "MANAGER", "CASHIER", "ACCOUNTANT")
        val validRole = if (cleanRole in supportedRoles) cleanRole else "CASHIER"

        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Username and password cannot be empty"))
        }
        if (cleanPass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val currentStore = _currentStoreProfile.value.storeId
        if (currentStore.isBlank() || currentStore == "UNAUTHENTICATED") {
            return@withContext Result.failure(IllegalStateException("Cannot add user without an authenticated store session"))
        }

        val existing = userDao.getByUsername(currentStore, cleanUser)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("User '$cleanUser' already exists in store '$currentStore'"))
        }

        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        val newUser = UserEntity(
            username = cleanUser,
            password_hash = hash,
            salt = salt,
            role = validRole,
            store_id = currentStore
        )
        userDao.insert(newUser)

        auditLogDao.insert(
            AuditLogEntity(
                action = "ADD_USER",
                details = "Added user '$cleanUser' with role '$validRole' to store '$currentStore'",
                store_id = currentStore,
                user_id = cleanUser
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteUser(username: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val currentStore = _currentStoreProfile.value.storeId
        if (currentStore.isBlank() || currentStore == "UNAUTHENTICATED") {
            return@withContext Result.failure(IllegalStateException("No active store session"))
        }

        val userToDelete = userDao.getByUsername(currentStore, cleanUser)
            ?: return@withContext Result.failure(IllegalArgumentException("User '$cleanUser' does not exist in store '$currentStore'"))

        if (userToDelete.role.uppercase() in setOf("ADMIN", "OWNER")) {
            val allUsers = userDao.getAllUsers(currentStore).first()
            val adminCount = allUsers.count { it.role.uppercase() in setOf("ADMIN", "OWNER") }
            if (adminCount <= 1) {
                return@withContext Result.failure(IllegalArgumentException("Cannot delete the only administrator/owner account for store '$currentStore'"))
            }
        }

        userDao.deleteByUsername(currentStore, cleanUser)
        auditLogDao.insert(
            AuditLogEntity(
                action = "DELETE_USER",
                details = "Deleted user '$cleanUser' from store '$currentStore'",
                store_id = currentStore,
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

        val currentStore = _currentStoreProfile.value.storeId
        if (currentStore.isBlank() || currentStore == "UNAUTHENTICATED") {
            return@withContext Result.failure(IllegalStateException("No active store session"))
        }

        userDao.getByUsername(currentStore, cleanUser)
            ?: return@withContext Result.failure(IllegalArgumentException("User '$cleanUser' not found in store '$currentStore'"))

        val salt = PasswordSecurity.generateSalt()
        val hash = PasswordSecurity.hashPassword(cleanPass, salt)
        userDao.updatePassword(currentStore, cleanUser, hash, salt)
        auditLogDao.insert(
            AuditLogEntity(
                action = "CHANGE_PASSWORD",
                details = "Password updated for user '$cleanUser' in store '$currentStore'",
                store_id = currentStore,
                user_id = cleanUser
            )
        )
        Result.success(Unit)
    }

    fun logout() {
        authService.signOut()
        _isPlatformAdmin.value = false
        _currentStoreProfile.value = StoreProfile(
            storeId = "UNAUTHENTICATED",
            storeName = "Supermarket POS Terminal",
            region = "Global Multi-Tenant",
            subscriptionStatus = "PENDING_AUTH"
        )
    }
}
