package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.ProductEntity
import com.example.data.model.Receipt
import com.example.data.model.SaleEntity
import com.example.data.repository.SupermarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PosViewModel(private val repository: SupermarketRepository) : ViewModel() {

    private val _barcodeInput = MutableStateFlow("")
    val barcodeInput: StateFlow<String> = _barcodeInput.asStateFlow()

    private val _customerId = MutableStateFlow("C101")
    val customerId: StateFlow<String> = _customerId.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _receipt = MutableStateFlow<Receipt?>(null)
    val receipt: StateFlow<Receipt?> = _receipt.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    val recentSales: StateFlow<List<SaleEntity>> = repository.recentSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickBarcodes = listOf("P001", "P002", "P003", "P004", "P005")

    val totalAmount: Double
        get() = _cart.value.sumOf { it.subtotal }

    val totalItemCount: Int
        get() = _cart.value.sumOf { it.quantity }

    fun setBarcodeInput(code: String) {
        _barcodeInput.value = code
    }

    fun setCustomerId(id: String) {
        _customerId.value = id
    }

    fun scanAndAdd(code: String = _barcodeInput.value) {
        val target = code.trim().uppercase()
        if (target.isEmpty()) return

        viewModelScope.launch {
            val product = repository.lookupProduct(target)
            if (product != null) {
                addProductToCart(product)
                _barcodeInput.value = ""
                _isError.value = false
                _statusMessage.value = "Added ${product.product_name} (\$${"%.2f".format(product.product_price)})"
            } else {
                _isError.value = true
                _statusMessage.value = "Barcode '$target' not found in inventory."
            }
        }
    }

    fun addProductToCart(product: ProductEntity) {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.product_id == product.product_id }

        if (index != -1) {
            val existing = currentList[index]
            if (existing.quantity + 1 > product.product_amount_left) {
                _isError.value = true
                _statusMessage.value = "Cannot exceed available stock (${product.product_amount_left})"
                return
            }
            currentList[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            if (product.product_amount_left < 1) {
                _isError.value = true
                _statusMessage.value = "Product is out of stock"
                return
            }
            currentList.add(CartItem(product = product, quantity = 1))
        }
        _cart.value = currentList
    }

    fun updateQuantity(productId: String, newQty: Int) {
        if (newQty <= 0) {
            removeItem(productId)
            return
        }
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.product_id == productId }
        if (index != -1) {
            val item = currentList[index]
            if (newQty > item.product.product_amount_left) {
                _isError.value = true
                _statusMessage.value = "Max stock is ${item.product.product_amount_left}"
                return
            }
            currentList[index] = item.copy(quantity = newQty)
            _cart.value = currentList
        }
    }

    fun removeItem(productId: String) {
        _cart.value = _cart.value.filterNot { it.product.product_id == productId }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _statusMessage.value = null
    }

    fun checkout() {
        if (_cart.value.isEmpty()) {
            _isError.value = true
            _statusMessage.value = "Cart is empty. Scan items to checkout."
            return
        }

        viewModelScope.launch {
            val result = repository.batchCheckout(_cart.value, _customerId.value.ifEmpty { "C101" })
            if (result.isSuccess) {
                val receiptObj = result.getOrNull()
                _receipt.value = receiptObj
                _cart.value = emptyList()
                _isError.value = false
                _statusMessage.value = "Checkout successful! Receipt generated."
            } else {
                _isError.value = true
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Checkout failed"
            }
        }
    }

    fun dismissReceipt() {
        _receipt.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

class PosViewModelFactory(private val repository: SupermarketRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
