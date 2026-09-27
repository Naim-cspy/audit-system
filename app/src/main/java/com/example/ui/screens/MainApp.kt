package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.SupermarketGreen
import com.example.ui.theme.SupermarketGreenDark
import com.example.ui.theme.SupermarketGreenLight
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.PosViewModel

enum class AppTab(val title: String, val icon: ImageVector) {
    POS("POS Register", Icons.Default.PointOfSale),
    INVENTORY("Inventory", Icons.Default.Inventory2),
    FINANCE("Ledger", Icons.Default.AccountBalanceWallet),
    FORECAST("Profit AI", Icons.Default.AutoGraph),
    ADMIN("Admin Center", Icons.Default.AdminPanelSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    authViewModel: AuthViewModel,
    posViewModel: PosViewModel,
    adminViewModel: AdminViewModel,
    saasViewModel: com.example.ui.viewmodel.SaaSPlatformViewModel
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val isPlatformAdmin by saasViewModel.isPlatformAdmin.collectAsState()
    var selectedTab by remember { mutableStateOf(AppTab.POS) }
    var showSaaSOwnerDashboard by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        authViewModel.trackScreen("Screen_${selectedTab.name}")
    }

    if (currentUser == null) {
        LoginScreen(authViewModel = authViewModel)
        return
    }

    if (showSaaSOwnerDashboard && isPlatformAdmin) {
        SaaSPlatformOwnerScreen(
            viewModel = saasViewModel,
            onNavigateBack = { showSaaSOwnerDashboard = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Supermarket POS",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Audit System & Intelligence",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    // SaaS Owner Command Center Access (Strictly restricted to platform admins)
                    if (isPlatformAdmin) {
                        IconButton(
                            onClick = { showSaaSOwnerDashboard = true },
                            modifier = Modifier.testTag("saas_owner_center_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "SaaS Owner Command Center",
                                tint = Color.White
                            )
                        }
                    }

                    // User info chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentUser!!.username,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${currentUser!!.role.uppercase()})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            authViewModel.logout()
                            posViewModel.clearCart()
                            posViewModel.dismissReceipt()
                            showSaaSOwnerDashboard = false
                            selectedTab = AppTab.POS
                        },
                        modifier = Modifier.testTag("logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SupermarketGreen,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_nav")
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SupermarketGreen,
                            selectedTextColor = SupermarketGreenDark,
                            indicatorColor = SupermarketGreenLight
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.POS -> PosScreen(viewModel = posViewModel)
                AppTab.INVENTORY -> InventoryScreen(
                    adminViewModel = adminViewModel,
                    userRole = currentUser?.role
                )
                AppTab.FINANCE -> FinanceScreen(
                    adminViewModel = adminViewModel,
                    userRole = currentUser?.role
                )
                AppTab.FORECAST -> AnalyticsScreen(adminViewModel = adminViewModel)
                AppTab.ADMIN -> AdminScreen(
                    authViewModel = authViewModel,
                    adminViewModel = adminViewModel
                )
            }
        }
    }
}
