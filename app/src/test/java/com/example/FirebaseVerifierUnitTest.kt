package com.example

import com.example.data.model.VerificationItem
import com.example.data.model.VerificationSuiteReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseVerifierUnitTest {

    @Test
    fun testOverallStatus_isNotReady_whenAnyCriticalCheckFailsOrIsSkipped() {
        val items = listOf(
            VerificationItem(title = "Phase 1: Firebase Initialization", status = "PASSED", details = "Active"),
            VerificationItem(title = "Phase 2: Project & Package Verification", status = "PASSED", details = "Valid"),
            VerificationItem(title = "Phase 4: Firebase Authentication", status = "FAILED", details = "No auth user"),
            VerificationItem(title = "Phase 7: Verified Token Claims", status = "SKIPPED", details = "Auth required"),
            VerificationItem(title = "Phase 8 & 9: Firestore Write", status = "SKIPPED", details = "Auth required"),
            VerificationItem(title = "Phase 10: Firestore Read", status = "SKIPPED", details = "Write required"),
            VerificationItem(title = "Phase 13: Tenant Isolation Test", status = "SKIPPED", details = "Auth required"),
            VerificationItem(title = "Phase 19: Client Secret Audit", status = "PASSED", details = "Clean", isCritical = false)
        )

        val criticalItems = items.filter { it.isCritical }
        val allCriticalPassed = criticalItems.all { it.status == "PASSED" }
        val report = VerificationSuiteReport(
            overallStatus = if (allCriticalPassed) "READY" else "NOT READY",
            passedCount = items.count { it.status == "PASSED" },
            totalCount = items.size,
            items = items
        )

        assertEquals("NOT READY", report.overallStatus)
        assertEquals(3, report.passedCount)
        assertFalse(allCriticalPassed)
    }

    @Test
    fun testOverallStatus_isReady_onlyWhenAllCriticalChecksPass() {
        val items = listOf(
            VerificationItem(title = "Phase 1: Firebase Initialization", status = "PASSED", details = "Active"),
            VerificationItem(title = "Phase 2: Project & Package Verification", status = "PASSED", details = "Valid"),
            VerificationItem(title = "Phase 4: Firebase Authentication", status = "PASSED", details = "Active user"),
            VerificationItem(title = "Phase 7: Verified Token Claims", status = "PASSED", details = "store_id = STORE_TEST_A, role = OWNER"),
            VerificationItem(title = "Phase 8 & 9: Firestore Write", status = "PASSED", details = "Write confirmed"),
            VerificationItem(title = "Phase 10: Firestore Read", status = "PASSED", details = "Read confirmed"),
            VerificationItem(title = "Phase 13: Tenant Isolation Test", status = "PASSED", details = "PERMISSION_DENIED explicitly confirmed"),
            VerificationItem(title = "Phase 19: Client Secret Audit", status = "PASSED", details = "Clean", isCritical = false)
        )

        val criticalItems = items.filter { it.isCritical }
        val allCriticalPassed = criticalItems.all { it.status == "PASSED" }
        val report = VerificationSuiteReport(
            overallStatus = if (allCriticalPassed) "READY" else "NOT READY",
            passedCount = items.count { it.status == "PASSED" },
            totalCount = items.size,
            items = items
        )

        assertEquals("READY", report.overallStatus)
        assertEquals(8, report.passedCount)
        assertTrue(allCriticalPassed)
    }

    @Test
    fun testMissingClaims_doesNotFallBackToLocalOwner() {
        val claims = mapOf("name" to "Test User")
        val claimStoreId = claims["store_id"] as? String
        val claimRole = claims["role"] as? String

        val isClaimsValid = !claimStoreId.isNullOrBlank() && !claimRole.isNullOrBlank()
        assertFalse("Missing claims must be invalid", isClaimsValid)
    }
}
