package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditLogEntity
import com.example.data.model.SyncEventEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SupermarketGreen
import com.example.ui.theme.SupermarketGreenDark
import com.example.ui.theme.SupermarketGreenLight
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val users by authViewModel.allUsers.collectAsState()
    val auditLogs by adminViewModel.auditLogs.collectAsState()
    val syncEvents by adminViewModel.syncEvents.collectAsState()
    val storeProfile by adminViewModel.storeProfileFlow.collectAsState()
    val isSyncing by adminViewModel.isSyncing.collectAsState()
    val verificationReport by adminViewModel.verificationReport.collectAsState()
    val isRunningVerification by adminViewModel.isRunningVerification.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddUserDialog by remember { mutableStateOf(false) }
    var changePasswordUser by remember { mutableStateOf<String?>(null) }
    var exportCsvContent by remember { mutableStateOf<String?>(null) }
    var exportJsonContent by remember { mutableStateOf<String?>(null) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var selectedSyncFilter by remember { mutableStateOf("ALL") }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    val currentRole = currentUser?.role?.trim()?.uppercase() ?: ""
    val hasAdminPrivileges = currentRole == "ADMIN" || currentRole == "OWNER" || currentRole == "SAAS_OWNER"

    // Role check: If not admin/owner, show access denied
    if (!hasAdminPrivileges) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("access_denied_card")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Denied",
                        tint = CriticalRed,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Access Denied",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = CriticalRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your account (${currentUser?.username}) has the role '${currentUser?.role}'. The Admin Intelligence Center requires Store Administrator or Owner privileges.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { authViewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                        modifier = Modifier.testTag("switch_to_admin_btn")
                    ) {
                        Text("Sign Out to Switch Account")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Column {
                Text(
                    text = "Admin Intelligence & Security Center",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = SupermarketGreenDark
                )
                Text(
                    text = "System diagnostics, credential governance & CSV backup synchronization",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        // Status feedback banner
        if (statusFeedback != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SupermarketGreenLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusFeedback!!,
                            color = SupermarketGreenDark,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { statusFeedback = null }) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // System Diagnostics Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("system_health_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = SupermarketGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "System Diagnostics & Database",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Store Tenant ID:", style = MaterialTheme.typography.bodyMedium)
                        Text(storeProfile.storeId, fontWeight = FontWeight.Bold, color = SupermarketGreenDark)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Store / Region:", style = MaterialTheme.typography.bodyMedium)
                        Text("${storeProfile.storeName} (${storeProfile.region})", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Terminal ID:", style = MaterialTheme.typography.bodyMedium)
                        Text(storeProfile.activeTerminalId, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Database Engine:", style = MaterialTheme.typography.bodyMedium)
                        Text("SQLite / Room (WAL Mode)", fontWeight = FontWeight.Bold, color = SupermarketGreenDark)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tenant Cloud State:", style = MaterialTheme.typography.bodyMedium)
                        Text("ISOLATED / READY", fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    exportCsvContent = adminViewModel.getExportData()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("export_csv_btn")
                        ) {
                            Text("Export CSV")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    exportJsonContent = adminViewModel.getJsonBackup()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("export_json_btn")
                        ) {
                            Text("Export JSON")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { adminViewModel.triggerCloudSync() },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreenDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("trigger_cloud_sync_btn")
                    ) {
                        if (isSyncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing with Cloud...")
                        } else {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync with Firestore (${storeProfile.storeId})")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showPrivacyPolicyDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("privacy_policy_btn")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cloud Privacy & Support Terms")
                    }
                }
            }
        }

        // Firebase End-to-End Verification Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("firebase_verification_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AccentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Firebase Verification Suite",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Automated E2E check (Auth, Firestore Read/Write, Claims & Isolation)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { adminViewModel.runVerificationSuite() },
                        enabled = !isRunningVerification,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("run_verification_suite_btn")
                    ) {
                        if (isRunningVerification) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Verification Checks...")
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Live Verification Suite")
                        }
                    }

                    if (verificationReport != null) {
                        val report = verificationReport!!
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Overall Status Banner
                        val isReady = report.overallStatus == "READY"
                        val overallColor = if (isReady) SuccessGreen else CriticalRed

                        Card(
                            colors = CardDefaults.cardColors(containerColor = overallColor.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "System Cloud Status:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = if (isReady) "READY FOR PRODUCTION" else "NOT READY FOR PRODUCTION",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = overallColor
                                    )
                                }
                                Surface(
                                    color = overallColor,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = report.overallStatus,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        report.items.forEach { item ->
                            val badgeColor = when (item.status) {
                                "PASSED" -> SuccessGreen
                                "FAILED" -> CriticalRed
                                "WARNING" -> Color(0xFFF57C00)
                                else -> Color(0xFF757575) // SKIPPED
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = badgeColor,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = item.status,
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // User Management Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "User Accounts (${users.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Button(
                    onClick = { showAddUserDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("add_user_btn")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add User")
                }
            }
        }

        // Users List
        items(users, key = { it.id }) { user ->
            UserCard(
                user = user,
                onChangePassword = { changePasswordUser = user.username },
                onDelete = {
                    authViewModel.deleteUser(user.username) { success, msg ->
                        statusFeedback = if (success) "User ${user.username} deleted" else msg
                    }
                }
            )
        }

        // Audit Logs Section
        item {
            Text(
                text = "Live Audit Trail (Last 30 Events)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(auditLogs, key = { it.id }) { log ->
            AuditLogRow(log = log)
        }

        // Cloud Synchronization & Support Inspector (Section 4 & 5)
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cloud Sync & Support Inspector",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SupermarketGreenLight
                    ) {
                        Text(
                            text = "${syncEvents.size} Events Logged",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SupermarketGreenDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Verify transaction replication states, investigate rejected/duplicate data",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Sync filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "SUCCESS", "FAILED", "PENDING").forEach { filter ->
                        val isSelected = selectedSyncFilter == filter
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) SupermarketGreen else Color.LightGray.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f)
                        ) {
                            TextButton(
                                onClick = { selectedSyncFilter = filter },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = filter,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) Color.White else Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }

        val filteredSyncs = when (selectedSyncFilter) {
            "SUCCESS" -> syncEvents.filter { it.status == "SUCCESS" }
            "FAILED" -> syncEvents.filter { it.status in listOf("FAILED", "REJECTED", "CONFLICT") }
            "PENDING" -> syncEvents.filter { it.status == "PENDING" }
            else -> syncEvents
        }

        items(filteredSyncs, key = { it.id }) { event ->
            SyncEventRow(event = event)
        }
    }

    // Add User Dialog
    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { uname, pass, role ->
                authViewModel.addUser(uname, pass, role) { success, msg ->
                    statusFeedback = if (success) "User '$uname' created as $role" else msg
                }
                showAddUserDialog = false
            }
        )
    }

    // Change Password Dialog
    if (changePasswordUser != null) {
        ChangePasswordDialog(
            username = changePasswordUser!!,
            onDismiss = { changePasswordUser = null },
            onConfirm = { newPass ->
                authViewModel.changePassword(changePasswordUser!!, newPass) { success, msg ->
                    statusFeedback = if (success) "Password updated for ${changePasswordUser!!}" else msg
                }
                changePasswordUser = null
            }
        )
    }

    // CSV Export View Dialog
    if (exportCsvContent != null) {
        AlertDialog(
            onDismissRequest = { exportCsvContent = null },
            title = { Text("Database CSV Backup") },
            text = {
                SelectionContainer {
                    Text(
                        text = exportCsvContent!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.height(300.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { exportCsvContent = null },
                    colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen)
                ) {
                    Text("Done")
                }
            }
        )
    }

    // JSON Cloud Backup View Dialog (Section 4 & 11)
    if (exportJsonContent != null) {
        AlertDialog(
            onDismissRequest = { exportJsonContent = null },
            title = { Text("Store Cloud JSON Backup (${storeProfile.storeId})") },
            text = {
                SelectionContainer {
                    Text(
                        text = exportJsonContent!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.height(320.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { exportJsonContent = null },
                    colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen)
                ) {
                    Text("Done")
                }
            }
        )
    }

    // Cloud Privacy & Data Support Policy Dialog (Section 8)
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("SaaS Cloud Privacy & Support Disclosure") },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1. Secure Cloud Infrastructure",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SupermarketGreenDark
                        )
                        Text(
                            text = "Store inventory, sales, financial ledgers, and audit logs are safely synchronized to tenant-isolated cloud storage.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = "2. Support Access Defaults to Read-Only",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SupermarketGreenDark
                        )
                        Text(
                            text = "Authorized platform personnel may inspect store sync events and records strictly in read-only mode for technical support and troubleshooting. Customer data is never modified during support calls.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = "3. Aggregated Regional Analytics",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SupermarketGreenDark
                        )
                        Text(
                            text = "Purchasing patterns and category turnover are analyzed across regions (e.g., Nabatieh Area) strictly in aggregated, anonymized form to benchmark store demand without exposing confidential trade data.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = "4. Zero Third-Party Selling or Ad Sharing",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SupermarketGreenDark
                        )
                        Text(
                            text = "Customer and store operational records are NEVER sold or shared with third parties, data brokers, or advertising networks.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = "5. Cryptographic Credential Protection",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SupermarketGreenDark
                        )
                        Text(
                            text = "User passwords and cryptographic salts are derived via PBKDF2 and strictly excluded from analytics and telemetry.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyPolicyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen)
                ) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
fun UserCard(
    user: UserEntity,
    onChangePassword: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("user_row_${user.username}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.username,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (user.role == "admin") SupermarketGreenLight else AccentBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = user.role.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (user.role == "admin") SupermarketGreenDark else AccentBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "ID: ${user.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Row {
                IconButton(
                    onClick = onChangePassword,
                    modifier = Modifier.size(36.dp).testTag("change_pw_${user.username}")
                ) {
                    Icon(Icons.Default.LockReset, contentDescription = "Change Password", tint = AccentBlue, modifier = Modifier.size(18.dp))
                }
                if (user.username != "admin") {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).testTag("delete_user_${user.username}")
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = "Delete User", tint = CriticalRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogRow(log: AuditLogEntity) {
    ElevatedCard(
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.action,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = SupermarketGreen)
                )
                Text(
                    text = "#${log.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Text(
                text = log.details,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("cashier") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create User Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorText != null) {
                    Text(errorText!!, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("new_password_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { role = "cashier" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (role == "cashier") SupermarketGreen else Color.LightGray
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cashier")
                    }
                    Button(
                        onClick = { role = "admin" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (role == "admin") SupermarketGreen else Color.LightGray
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Admin")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorText = "Username and password cannot be empty"
                        return@Button
                    }
                    onConfirm(username, password, role)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                modifier = Modifier.testTag("confirm_create_user_btn")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    username: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password: $username") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorText != null) {
                    Text(errorText!!, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("change_pw_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.isBlank()) {
                        errorText = "Password cannot be empty"
                        return@Button
                    }
                    onConfirm(newPassword)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                modifier = Modifier.testTag("confirm_change_pw_btn")
            ) {
                Text("Update Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SyncEventRow(event: SyncEventEntity) {
    val statusColor = when (event.status) {
        "SUCCESS" -> SuccessGreen
        "PENDING" -> AccentBlue
        "REJECTED" -> Color(0xFF9C27B0)
        else -> CriticalRed
    }

    ElevatedCard(
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = event.status,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.operation,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = event.sync_id,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${event.entity_type} • ID: ${event.entity_id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            if (event.error_message != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Reason: ${event.error_message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CriticalRed
                )
            }
        }
    }
}
