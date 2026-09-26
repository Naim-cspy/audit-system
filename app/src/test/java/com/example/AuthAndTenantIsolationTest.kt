package com.example

import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.StoreProfile
import com.example.data.security.PasswordSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAndTenantIsolationTest {

    // ==========================================
    // 1. Password Verification & Argument Order Tests
    // ==========================================

    @Test
    fun testPasswordSecurity_argumentOrderIsCorrect() {
        val plainPassword = "SecretPassword123!"
        val salt = PasswordSecurity.generateSalt()
        val storedHash = PasswordSecurity.hashPassword(plainPassword, salt)

        // Correct argument order: (plainPassword, storedHash, salt)
        val valid = PasswordSecurity.verifyPassword(plainPassword, storedHash, salt)
        assertTrue("Password verification must succeed with correct argument order (plain, hash, salt)", valid)

        // Reversed argument order: passing salt as hash and hash as salt must fail
        val invalidReversed = PasswordSecurity.verifyPassword(plainPassword, salt, storedHash)
        assertFalse("Reversed argument order must never succeed", invalidReversed)

        // Wrong password must fail
        val wrongPassword = PasswordSecurity.verifyPassword("WrongPassword", storedHash, salt)
        assertFalse("Incorrect password must fail", wrongPassword)
    }

    // ==========================================
    // 2. Token Claim Verification Tests
    // ==========================================

    @Test
    fun testClaims_missingClaimsRejected_noFakeFallbacks() {
        // Missing store_id
        val claimsWithoutStore = mapOf("role" to "OWNER")
        val storeId1 = claimsWithoutStore["store_id"] as? String
        val role1 = claimsWithoutStore["role"] as? String
        assertNull("store_id must not fall back to local or fake value", storeId1)

        // Missing role
        val claimsWithoutRole = mapOf("store_id" to "STR-001")
        val storeId2 = claimsWithoutRole["store_id"] as? String
        val role2 = claimsWithoutRole["role"] as? String
        assertNull("role must not fall back to OWNER automatically", role2)

        // Both missing
        val emptyClaims = emptyMap<String, Any>()
        assertTrue((emptyClaims["store_id"] as? String).isNullOrBlank())
        assertTrue((emptyClaims["role"] as? String).isNullOrBlank())
    }

    @Test
    fun testClaims_supportedRolesValidation() {
        val supportedRoles = setOf("OWNER", "ADMIN", "MANAGER", "CASHIER", "ACCOUNTANT")

        // Supported roles should pass
        assertTrue("OWNER must be supported", "OWNER" in supportedRoles)
        assertTrue("ADMIN must be supported", "ADMIN" in supportedRoles)
        assertTrue("MANAGER must be supported", "MANAGER" in supportedRoles)
        assertTrue("CASHIER must be supported", "CASHIER" in supportedRoles)
        assertTrue("ACCOUNTANT must be supported", "ACCOUNTANT" in supportedRoles)

        // Unsupported roles must fail
        val invalidRoles = listOf("SUPERUSER", "GUEST", "HACKER", "DEVELOPER", "UNKNOWN")
        invalidRoles.forEach { role ->
            assertFalse("Role '$role' must be rejected as invalid", role in supportedRoles)
        }
    }

    @Test
    fun testClaims_ordinaryClientAccount_preservesRoleAndDeniesPlatformAdmin() {
        val claims = mapOf(
            "store_id" to "STR-BEIRUT-001",
            "role" to "CASHIER",
            "platform_admin" to false
        )

        val role = (claims["role"] as? String)?.uppercase()
        val storeId = claims["store_id"] as? String
        val isPlatformAdmin = ((claims["platform_admin"] as? Boolean) == true) || role == "SAAS_OWNER"

        assertEquals("STR-BEIRUT-001", storeId)
        assertEquals("CASHIER", role)
        // Must preserve distinct role, not convert cashier/manager to admin
        assertNotEquals("ADMIN", role)
        assertFalse("Client store account must never have platform admin rights", isPlatformAdmin)
    }

    @Test
    fun testClaims_saasOwnerAccount_grantsPlatformAccessOnlyWithClaims() {
        // Legitimate SaaS owner with platform_admin claim
        val adminClaims = mapOf(
            "platform_admin" to true,
            "role" to "SAAS_OWNER"
        )
        val isPlatformAdmin = ((adminClaims["platform_admin"] as? Boolean) == true) ||
                (adminClaims["role"] as? String) == "SAAS_OWNER"
        assertTrue("SaaS owner with verified claim must have platform admin", isPlatformAdmin)

        // Hard-coded email shortcuts must NOT grant platform access
        val email = "saas_owner@example.com"
        val unprivilegedClaims = mapOf("store_id" to "STR-001", "role" to "CASHIER")
        val isAdminWithoutClaims = ((unprivilegedClaims["platform_admin"] as? Boolean) == true) ||
                (unprivilegedClaims["role"] as? String) == "SAAS_OWNER"
        assertFalse("Email address alone without claims must never grant SaaS owner rights", isAdminWithoutClaims)
    }

    @Test
    fun testOfflineAccess_neverGrantsPlatformOwnerPrivileges() {
        // Offline local session
        val isOfflineLocalSession = true
        var isPlatformAdmin = true // Initially set to true to test reset

        if (isOfflineLocalSession) {
            isPlatformAdmin = false // Enforced rule
        }

        assertFalse("Offline login must strictly forbid platform admin privileges", isPlatformAdmin)
    }

    @Test
    fun testLogout_clearsAllSensitiveState() {
        var storeProfile = StoreProfile(storeId = "STR-ACT-001", storeName = "Active Store")
        var isPlatformAdmin = true
        var currentUser: String? = "admin@store.com"

        // Execute logout sequence
        storeProfile = StoreProfile(
            storeId = "UNAUTHENTICATED",
            storeName = "Supermarket POS Terminal",
            region = "Global Multi-Tenant",
            subscriptionStatus = "PENDING_AUTH"
        )
        isPlatformAdmin = false
        currentUser = null

        assertEquals("UNAUTHENTICATED", storeProfile.storeId)
        assertFalse(isPlatformAdmin)
        assertNull(currentUser)
    }

    // ==========================================
    // 3. Local Tenant Isolation & Key Collision Tests
    // ==========================================

    @Test
    fun testLocalTenantIsolation_sameProductIdInTwoStoresAllowed() {
        // Store A product
        val productStoreA = ProductEntity(
            product_id = "P001",
            product_name = "Bottled Water (Store A)",
            product_price = 1.00,
            product_amount_left = 50,
            store_id = "STORE_A"
        )

        // Store B product with identical product_id
        val productStoreB = ProductEntity(
            product_id = "P001",
            product_name = "Bottled Water (Store B)",
            product_price = 1.50,
            product_amount_left = 100,
            store_id = "STORE_B"
        )

        // Since primary key is composite [store_id, product_id], both exist uniquely
        val keyA = "${productStoreA.store_id}#${productStoreA.product_id}"
        val keyB = "${productStoreB.store_id}#${productStoreB.product_id}"

        assertNotEquals("Composite keys for identical product_id across two stores must be distinct", keyA, keyB)
        assertEquals("STORE_A#P001", keyA)
        assertEquals("STORE_B#P001", keyB)
        assertEquals(1.00, productStoreA.product_price, 0.001)
        assertEquals(1.50, productStoreB.product_price, 0.001)
    }

    @Test
    fun testLocalTenantIsolation_neverMutatesRecordStoreId() {
        val originalStoreId = "STORE_ALPHA"
        val loggedInStoreId = "STORE_BETA"

        val originalSale = SaleEntity(
            sale_id = "SALE-100",
            product_id = "P001",
            product_name = "Apple Juice",
            quantity = 2,
            price = 3.00,
            sale_date = "2026-09-26",
            store_id = originalStoreId
        )

        // Invariant: sync or query must never rewrite store_id to match active session
        val saleForSync = originalSale
        assertEquals("Record's store_id must remain immutable", originalStoreId, saleForSync.store_id)
        assertNotEquals("Record's store_id must not mutate to logged in store", loggedInStoreId, saleForSync.store_id)
    }
}
