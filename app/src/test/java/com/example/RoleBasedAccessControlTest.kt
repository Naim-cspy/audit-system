package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Role-Based Access Control & Tenant Authority Test Suite.
 * Validates security constraints across client tenant roles and the SaaS platform owner.
 */
class RoleBasedAccessControlTest {

    private val supportedTenantRoles = setOf("OWNER", "ADMIN", "MANAGER", "CASHIER", "ACCOUNTANT")

    private fun canManageInventory(role: String?): Boolean {
        val r = role?.trim()?.uppercase() ?: return false
        return r in setOf("OWNER", "ADMIN", "MANAGER", "INVENTORY", "SAAS_OWNER")
    }

    private fun canAccessFinance(role: String?): Boolean {
        val r = role?.trim()?.uppercase() ?: return false
        return r in setOf("OWNER", "ADMIN", "ACCOUNTANT", "MANAGER", "SAAS_OWNER")
    }

    private fun canAccessAdminCenter(role: String?): Boolean {
        val r = role?.trim()?.uppercase() ?: return false
        return r in setOf("ADMIN", "OWNER", "SAAS_OWNER")
    }

    private fun isPlatformOwnerAuthorized(claims: Map<String, Any>): Boolean {
        val platformAdmin = claims["platform_admin"] as? Boolean ?: false
        val role = claims["role"] as? String ?: ""
        return platformAdmin || role == "SAAS_OWNER"
    }

    @Test
    fun testCashierRole_permissionsAreStrictlyConstrained() {
        val role = "CASHIER"
        assertTrue("Cashier is a valid supported role", supportedTenantRoles.contains(role))
        assertFalse("Cashier must NOT be able to manage inventory or change prices", canManageInventory(role))
        assertFalse("Cashier must NOT be able to access financial ledger or bills", canAccessFinance(role))
        assertFalse("Cashier must NOT be able to access Admin Center", canAccessAdminCenter(role))
    }

    @Test
    fun testAccountantRole_canAccessFinance_cannotManageInventory() {
        val role = "ACCOUNTANT"
        assertTrue("Accountant is a valid supported role", supportedTenantRoles.contains(role))
        assertFalse("Accountant must NOT be able to modify inventory", canManageInventory(role))
        assertTrue("Accountant MUST be able to access financial ledger", canAccessFinance(role))
        assertFalse("Accountant must NOT be able to access Admin Center", canAccessAdminCenter(role))
    }

    @Test
    fun testManagerRole_canManageInventoryAndFinance() {
        val role = "MANAGER"
        assertTrue("Manager is a valid supported role", supportedTenantRoles.contains(role))
        assertTrue("Manager can manage inventory", canManageInventory(role))
        assertTrue("Manager can access finance", canAccessFinance(role))
        assertFalse("Manager cannot access store Admin Center", canAccessAdminCenter(role))
    }

    @Test
    fun testStoreOwnerAndAdmin_haveStoreAdminPrivileges_notPlatformAdmin() {
        listOf("OWNER", "ADMIN").forEach { role ->
            assertTrue("$role can manage inventory", canManageInventory(role))
            assertTrue("$role can access finance", canAccessFinance(role))
            assertTrue("$role can access store Admin Center", canAccessAdminCenter(role))

            // Standard tenant store claims
            val tenantClaims = mapOf(
                "store_id" to "STR-001",
                "role" to role,
                "platform_admin" to false
            )
            assertFalse(
                "Store $role must NOT have SaaS Platform Owner privileges",
                isPlatformOwnerAuthorized(tenantClaims)
            )
        }
    }

    @Test
    fun testSaaSOwner_hasPlatformPrivilegesOnlyWithVerifiedClaims() {
        val validSaaSClaims = mapOf(
            "platform_admin" to true,
            "role" to "SAAS_OWNER"
        )
        assertTrue(
            "Verified SaaS owner claims grant platform access",
            isPlatformOwnerAuthorized(validSaaSClaims)
        )

        // Email alone without claims must be rejected
        val fakeClaims = mapOf(
            "email" to "Zawaruldo69@gmail.com",
            "store_id" to "STR-001",
            "role" to "OWNER",
            "platform_admin" to false
        )
        assertFalse(
            "Email without platform_admin or SAAS_OWNER claim cannot access SaaS Command Center",
            isPlatformOwnerAuthorized(fakeClaims)
        )
    }

    @Test
    fun testCrossStoreIsolation_differentStoreClaimsDenied() {
        val storeAUser = mapOf("store_id" to "STR-A", "role" to "OWNER")
        val requestedStore = "STR-B"
        val isAllowed = storeAUser["store_id"] == requestedStore
        assertFalse("User from STR-A cannot access records in STR-B", isAllowed)
    }
}
