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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BalanceEntity
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SupermarketGreen
import com.example.ui.theme.SupermarketGreenDark
import com.example.ui.theme.SupermarketGreenLight
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun FinanceScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val summary by adminViewModel.financialSummary.collectAsState()
    val balanceHistory by adminViewModel.balanceHistory.collectAsState()

    var showAddBillDialog by remember { mutableStateOf(false) }
    var showAddReceiptDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Financial Health & Ledger",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = SupermarketGreenDark
                    )
                    Text(
                        text = "Last synced: ${summary.timestamp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                IconButton(
                    onClick = { adminViewModel.refreshAnalytics() },
                    modifier = Modifier.testTag("refresh_finance_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SupermarketGreen)
                }
            }
        }

        // Action Buttons: Add Bill (Expense) & Add Receipt (Income)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showAddBillDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("open_add_bill_btn")
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Bill (Expense)")
                }

                Button(
                    onClick = { showAddReceiptDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("open_add_receipt_btn")
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Receipt (Income)")
                }
            }
        }

        // Financial KPI Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Current Balance",
                        value = "\$${"%.2f".format(summary.currentBalance)}",
                        subtitle = "Initial: \$${"%.2f".format(summary.initialBudget)}",
                        icon = Icons.Default.AccountBalance,
                        accentColor = SupermarketGreen,
                        modifier = Modifier.weight(1f).testTag("kpi_balance")
                    )
                    KpiCard(
                        title = "Net Profit / Loss",
                        value = "${if (summary.netProfit >= 0) "+" else ""}\$${"%.2f".format(summary.netProfit)}",
                        subtitle = if (summary.netProfit >= 0) "Profitable Trajectory" else "Deficit",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accentColor = if (summary.netProfit >= 0) SuccessGreen else CriticalRed,
                        modifier = Modifier.weight(1f).testTag("kpi_profit")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Money In (Total)",
                        value = "\$${"%.2f".format(summary.totalMoneyIn)}",
                        subtitle = "Income & Sales",
                        icon = Icons.Default.ArrowUpward,
                        accentColor = AccentBlue,
                        modifier = Modifier.weight(1f).testTag("kpi_money_in")
                    )
                    KpiCard(
                        title = "Money Out (Total)",
                        value = "\$${"%.2f".format(summary.totalMoneyOut)}",
                        subtitle = "Bills & Expenses",
                        icon = Icons.Default.ArrowDownward,
                        accentColor = CriticalRed,
                        modifier = Modifier.weight(1f).testTag("kpi_money_out")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Sales Revenue",
                        value = "\$${"%.2f".format(summary.salesRevenue)}",
                        subtitle = "${summary.unitsSold} units sold (${summary.totalTransactions} orders)",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        accentColor = SupermarketGreenDark,
                        modifier = Modifier.weight(1f).testTag("kpi_sales_rev")
                    )
                    KpiCard(
                        title = "Inventory Valuation",
                        value = "\$${"%.2f".format(summary.inventoryValuation)}",
                        subtitle = "${summary.totalStockCount} units in stock",
                        icon = Icons.Default.Payments,
                        accentColor = Color(0xFF6A1B9A),
                        modifier = Modifier.weight(1f).testTag("kpi_inv_val")
                    )
                }
            }
        }

        // Ledger Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = SupermarketGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Balance History & General Ledger",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "${balanceHistory.size} entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        // Ledger Entries
        if (balanceHistory.isEmpty()) {
            item {
                Text(
                    text = "No ledger entries recorded yet.",
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(balanceHistory, key = { it.id }) { item ->
                LedgerEntryRow(item = item)
            }
        }
    }

    // Add Bill Dialog
    if (showAddBillDialog) {
        AddLedgerEntryDialog(
            title = "Record Expense Bill",
            isExpense = true,
            onDismiss = { showAddBillDialog = false },
            onConfirm = { amt, reason ->
                adminViewModel.addBill(amt, reason)
                showAddBillDialog = false
            }
        )
    }

    // Add Receipt Dialog
    if (showAddReceiptDialog) {
        AddLedgerEntryDialog(
            title = "Record Income Receipt",
            isExpense = false,
            onDismiss = { showAddReceiptDialog = false },
            onConfirm = { amt, reason ->
                adminViewModel.addReceipt(amt, reason)
                showAddReceiptDialog = false
            }
        )
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
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
fun LedgerEntryRow(item: BalanceEntity) {
    val isIncome = item.money_in > 0
    val amount = if (isIncome) item.money_in else item.money_out
    val effectiveBal = item.budget_starting + item.money_in - item.money_out

    ElevatedCard(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("ledger_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.reason.ifEmpty { if (isIncome) "Income Deposit" else "Expense Bill" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "${item.date} • Base: \$${"%.2f".format(item.budget_starting)} • Result: \$${"%.2f".format(effectiveBal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Text(
                text = "${if (isIncome) "+" else "-"}\$${"%.2f".format(amount)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) SuccessGreen else CriticalRed
                )
            )
        }
    }
}

@Composable
fun AddLedgerEntryDialog(
    title: String,
    isExpense: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorText != null) {
                    Text(errorText!!, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ledger_amount_input")
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Description (e.g. ${if (isExpense) "Utilities, Store Supplies" else "Deposit, Revenue"})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ledger_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        errorText = "Enter a valid positive amount"
                        return@Button
                    }
                    if (reason.trim().isEmpty()) {
                        errorText = "Enter a reason"
                        return@Button
                    }
                    onConfirm(amt, reason)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isExpense) CriticalRed else SupermarketGreen
                ),
                modifier = Modifier.testTag("save_ledger_entry_btn")
            ) {
                Text(if (isExpense) "Record Bill" else "Record Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
