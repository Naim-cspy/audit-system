package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ProductEntity
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.SupermarketGreen
import com.example.ui.theme.SupermarketGreenDark
import com.example.ui.theme.SupermarketGreenLight
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun InventoryScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val products by adminViewModel.allProducts.collectAsState()
    val stockWarnings by adminViewModel.stockWarnings.collectAsState()
    val operationMessage by adminViewModel.operationMessage.collectAsState()
    val isError by adminViewModel.isError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val categories = listOf("All", "Low Stock", "Electronics", "Furniture", "General")

    val filteredProducts = products.filter { prod ->
        val matchesQuery = prod.product_name.contains(searchQuery, ignoreCase = true) ||
                prod.product_id.contains(searchQuery, ignoreCase = true)
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Low Stock" -> prod.product_amount_left <= 50
            else -> prod.product_type.equals(selectedCategory, ignoreCase = true)
        }
        matchesQuery && matchesCategory
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = SupermarketGreen,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status / Operation Banner
            if (operationMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else SupermarketGreenLight
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("inventory_op_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = operationMessage!!,
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else SupermarketGreenDark,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { adminViewModel.clearMessage() }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            // Low Stock Alert Banner (if any)
            if (stockWarnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFFEEBA), RoundedCornerShape(10.dp))
                            .testTag("stock_refill_warning_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFF856404),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Stock Refill Alert: ${stockWarnings.size} Products Low",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF856404)
                                )
                                Text(
                                    text = stockWarnings.joinToString(", ") { "${it.product_name} (${it.product_amount_left})" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF856404)
                                )
                            }
                        }
                    }
                }
            }

            // Search Box
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search products by name or barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("product_search_input")
                )
            }

            // Category Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            modifier = Modifier.testTag("category_chip_$cat")
                        )
                    }
                }
            }

            // Products Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Inventory Items (${filteredProducts.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Total Stock: ${filteredProducts.sumOf { it.product_amount_left }} units",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // Product List
            if (filteredProducts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No products match your filter", color = Color.Gray)
                        }
                    }
                }
            } else {
                items(filteredProducts, key = { it.product_id }) { product ->
                    ProductCard(
                        product = product,
                        onEditPrice = { editingProduct = product },
                        onDelete = { deletingProduct = product }
                    )
                }
            }
        }
    }

    // Add Product Dialog
    if (showAddDialog) {
        AddProductDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { id, name, price, stock, type ->
                adminViewModel.addProduct(
                    id = id,
                    name = name,
                    price = price,
                    amountLeft = stock,
                    type = type
                )
                showAddDialog = false
            }
        )
    }

    // Edit Price Dialog
    if (editingProduct != null) {
        EditPriceDialog(
            product = editingProduct!!,
            onDismiss = { editingProduct = null },
            onConfirm = { newPrice ->
                adminViewModel.updatePrice(editingProduct!!.product_id, newPrice)
                editingProduct = null
            }
        )
    }

    // Delete Product Dialog
    if (deletingProduct != null) {
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Remove Product") },
            text = { Text("Are you sure you want to permanently delete '${deletingProduct!!.product_name}' (${deletingProduct!!.product_id}) from inventory?") },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.removeProduct(deletingProduct!!.product_id)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                    modifier = Modifier.testTag("confirm_delete_product_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProductCard(
    product: ProductEntity,
    onEditPrice: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("product_card_${product.product_id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.product_name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = product.product_type,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AccentBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "ID: ${product.product_id} • Filled: ${product.date_filled.ifEmpty { "N/A" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Text(
                    text = "\$${"%.2f".format(product.product_price)}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = SupermarketGreen
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stock badge
                    val isCritical = product.product_amount_left <= 10
                    val isWarning = product.product_amount_left <= 50
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isCritical -> CriticalRed.copy(alpha = 0.15f)
                            isWarning -> WarningAmber.copy(alpha = 0.2f)
                            else -> SupermarketGreenLight
                        }
                    ) {
                        Text(
                            text = when {
                                isCritical -> "CRITICAL: ${product.product_amount_left} left"
                                isWarning -> "LOW: ${product.product_amount_left} left"
                                else -> "Stock: ${product.product_amount_left}"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isCritical -> CriticalRed
                                isWarning -> Color(0xFFB78103)
                                else -> SupermarketGreenDark
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Sold: ${product.product_amount_sold}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Row {
                    IconButton(
                        onClick = onEditPrice,
                        modifier = Modifier.size(36.dp).testTag("edit_price_${product.product_id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Price", tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).testTag("delete_product_${product.product_id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Product", tint = CriticalRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Int, String) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var stockStr by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("General") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorText != null) {
                    Text(errorText!!, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.uppercase() },
                    label = { Text("Product ID / Barcode (e.g. P006)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_product_id")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_product_name")
                )
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_product_price")
                )
                OutlinedTextField(
                    value = stockStr,
                    onValueChange = { stockStr = it },
                    label = { Text("Initial Stock Count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_product_stock")
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Category / Type (e.g. Electronics, Furniture)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_product_type")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceStr.toDoubleOrNull()
                    val s = stockStr.toIntOrNull()
                    if (id.isBlank() || name.isBlank() || p == null || s == null) {
                        errorText = "Please fill in all fields with valid values"
                        return@Button
                    }
                    onConfirm(id, name, p, s, type)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                modifier = Modifier.testTag("save_new_product_btn")
            ) {
                Text("Add Product")
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
fun EditPriceDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var priceStr by remember { mutableStateOf("${product.product_price}") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Price: ${product.product_name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current price: \$${"%.2f".format(product.product_price)}")
                if (errorText != null) {
                    Text(errorText!!, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("New Price ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_price_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceStr.toDoubleOrNull()
                    if (p == null || p <= 0) {
                        errorText = "Enter a valid positive price"
                        return@Button
                    }
                    onConfirm(p)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                modifier = Modifier.testTag("save_price_btn")
            ) {
                Text("Save Price")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
