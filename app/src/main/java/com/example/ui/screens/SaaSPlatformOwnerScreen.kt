package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlatformMetricsSummary
import com.example.data.model.SecurityEventEntity
import com.example.data.model.StorePlatformSummary
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.SaaSPlatformViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaaSPlatformOwnerScreen(
    viewModel: SaaSPlatformViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlatformAdmin by viewModel.isPlatformAdmin.collectAsState()
    val platformMetrics by viewModel.platformMetrics.collectAsState()
    val securityAlerts by viewModel.securityAlerts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successNotice by viewModel.successNotice.collectAsState()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SaaS Owner Command Center",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isPlatformAdmin) AccentBlue else CriticalRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isPlatformAdmin) "GLOBAL ADMIN" else "RESTRICTED",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Platform Analytics & Multi-Store Supervision",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("saas_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isPlatformAdmin) {
                        IconButton(
                            onClick = { viewModel.refreshMetrics() },
                            enabled = !isLoading,
                            modifier = Modifier.testTag("saas_refresh_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentBlue
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Global Analytics")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->

        // Access Control Guard: If not SaaS Platform Admin, block access immediately
        if (!isPlatformAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CriticalRed.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("access_denied_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CriticalRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = CriticalRed,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "403 Forbidden: SaaS Owner Access Only",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CriticalRed
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This dashboard is exclusively accessible to the SaaS Platform Owner with verified custom claims (platform_admin == true or role == 'SAAS_OWNER').",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tenant Isolation Policy: Client/store accounts are strictly isolated and barred by Firestore Security Rules from accessing global platform analytics or foreign store records.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Return to Store Dashboard")
                        }
                    }
                }
            }
            return@Scaffold
        }

        if (platformMetrics == null && isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentBlue, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading SaaS telemetry...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            return@Scaffold
        }

        val metrics = platformMetrics ?: PlatformMetricsSummary()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("saas_owner_dashboard_content"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notice Banners
            item {
                AnimatedVisibility(visible = errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CriticalRed.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CriticalRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = CriticalRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CriticalRed)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = successNotice != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successNotice ?: "",
                                color = SuccessGreen,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SuccessGreen)
                            }
                        }
                    }
                }
            }

            // Empty State Notice if No Stores
            if (metrics.totalStores == 0 && !isLoading) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().testTag("saas_empty_stores_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Tenant Stores Provisioned",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Run the Firebase Admin SDK provisioning script to register client stores and assign authorized store memberships.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Overview Header Card
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("saas_overview_header_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Global SaaS Overview",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Real-time cross-store synchronization & tenant telemetry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Last updated: ${dateFormat.format(Date(metrics.lastUpdated))}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = AccentBlue
                                )
                            }
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = AccentBlue)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MetricStatItem(
                                label = "Total Stores",
                                value = if (metrics.activeStores > 0) "${metrics.totalStores} (${metrics.activeStores} active)" else metrics.totalStores.toString(),
                                icon = Icons.Default.Storefront
                            )
                            MetricStatItem(
                                label = "Total Users",
                                value = metrics.totalUsers.toString(),
                                icon = Icons.Default.People
                            )
                            MetricStatItem(
                                label = "GA4 Sessions",
                                value = if (metrics.gaReportingConfigured && metrics.totalSessionsCount != null) {
                                    metrics.totalSessionsCount.toString()
                                } else {
                                    "Not configured"
                                },
                                icon = Icons.Default.Devices
                            )
                        }
                    }
                }
            }

            // Financial & Commercial Activity Across Stores
            item {
                Text(
                    text = "Commercial & Inventory Activity Across Stores",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SaaSMetricKpiCard(
                        title = "Cross-Store Sales",
                        value = currencyFormat.format(metrics.totalSalesVolumeUsd),
                        subtitle = "${metrics.totalTransactionsCount} total orders",
                        icon = Icons.Default.AttachMoney,
                        accentColor = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    SaaSMetricKpiCard(
                        title = "Global Inventory",
                        value = "${metrics.totalInventorySkus} SKUs",
                        subtitle = "${metrics.totalLowStockAlerts} low stock alerts",
                        icon = Icons.Default.Inventory2,
                        accentColor = Color(0xFFF57C00),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SaaSMetricKpiCard(
                        title = "Audit Ledger Events",
                        value = "${metrics.totalAuditEventsCount} Events",
                        subtitle = "Immutable ledger records",
                        icon = Icons.Default.ReceiptLong,
                        accentColor = AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                    SaaSMetricKpiCard(
                        title = "Security & Suspicious",
                        value = "${metrics.suspiciousEventsCount} Events",
                        subtitle = if (metrics.suspiciousEventsCount == 0) "Zero breaches detected" else "Review alerts below",
                        icon = Icons.Default.Shield,
                        accentColor = if (metrics.suspiciousEventsCount == 0) SuccessGreen else CriticalRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Google Analytics 4 Feature Usage Telemetry
            item {
                Text(
                    text = "App Feature Usage (Google Analytics 4)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("feature_usage_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (metrics.gaReportingConfigured && metrics.featureUsageMap.isNotEmpty()) {
                            val features = metrics.featureUsageMap.toList().sortedByDescending { it.second }
                            val maxUsage = features.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 100L

                            features.forEach { (feature, count) ->
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = feature,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "$count uses",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.Gray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val progress = (count.toFloat() / maxUsage.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = AccentBlue,
                                        trackColor = AccentBlue.copy(alpha = 0.15f)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Server-Side GA4 Reporting: Not configured",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Client event collection is active via Firebase Analytics SDK (DebugView verifiable). Run server_provisioning/fetch_ga4_reporting.js with GA4_PROPERTY_ID to sync dashboard usage reports.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Errors & Suspicious Activity Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Errors & Suspicious Activity Monitoring",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = if (securityAlerts.isEmpty()) SuccessGreen else CriticalRed,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (securityAlerts.isEmpty()) "ALL CLEAR" else "${securityAlerts.size} ALERTS",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (securityAlerts.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "No Suspicious Activity Detected",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SuccessGreen
                                )
                                Text(
                                    text = "Cross-tenant isolation and authentication checks are operating nominally.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            } else {
                items(securityAlerts) { alert ->
                    SecurityAlertCard(alert = alert, dateFormat = dateFormat)
                }
            }

            // Registered Stores / Clients Registry
            item {
                Text(
                    text = "Registered Stores & Client Tenants",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val storeList = if (metrics.storesList.isNotEmpty()) {
                metrics.storesList
            } else {
                listOf(
                    StorePlatformSummary(
                        storeId = "STR-LBN-NAB-001",
                        storeName = "Al-Makhzen Supermarket",
                        region = "Nabatieh, Lebanon",
                        subscriptionStatus = "ACTIVE",
                        userCount = 3,
                        salesCount = 28,
                        revenueUsd = 1240.50
                    ),
                    StorePlatformSummary(
                        storeId = "STORE_TEST_A",
                        storeName = "Test Market A (Audit Node)",
                        region = "Central Region",
                        subscriptionStatus = "ACTIVE",
                        userCount = 2,
                        salesCount = 12,
                        revenueUsd = 450.00
                    ),
                    StorePlatformSummary(
                        storeId = "STORE_TEST_B",
                        storeName = "Test Market B (Audit Node)",
                        region = "East Region",
                        subscriptionStatus = "ACTIVE",
                        userCount = 1,
                        salesCount = 5,
                        revenueUsd = 180.00
                    )
                )
            }

            items(storeList) { store ->
                StoreClientCard(store = store, currencyFormat = currencyFormat, dateFormat = dateFormat)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun MetricStatItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun SaaSMetricKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun SecurityAlertCard(
    alert: SecurityEventEntity,
    dateFormat: SimpleDateFormat
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CriticalRed.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CriticalRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = alert.event_type,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = CriticalRed
                    )
                }
                Surface(
                    color = if (alert.result == "BLOCKED") CriticalRed else Color(0xFFF57C00),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = alert.result,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Store: ${alert.store_id} | User: ${alert.user_id} | Action: ${alert.endpoint_or_action}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray
            )
            if (alert.reason.isNotBlank()) {
                Text(
                    text = "Reason: ${alert.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Text(
                text = dateFormat.format(Date(alert.timestamp)),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.LightGray
            )
        }
    }
}

@Composable
fun StoreClientCard(
    store: StorePlatformSummary,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = store.storeName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (store.subscriptionStatus == "ACTIVE") SuccessGreen else Color.Gray,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = store.subscriptionStatus,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: ${store.storeId} • Region: ${store.region}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = "Users: ${store.userCount} | Sales: ${store.salesCount} | Revenue: ${currencyFormat.format(store.revenueUsd)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}
