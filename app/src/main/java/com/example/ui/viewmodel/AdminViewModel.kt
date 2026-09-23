package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.FinancialSummary
import com.example.data.model.ProductEntity
import com.example.data.model.ProfitPrediction
import com.example.data.repository.SupermarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(private val repository: SupermarketRepository) : ViewModel() {

    val allProducts: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockWarnings: StateFlow<List<ProductEntity>> = repository.getStockWarnings(threshold = 50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balanceHistory: StateFlow<List<BalanceEntity>> = repository.balanceHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _financialSummary = MutableStateFlow(FinancialSummary())
    val financialSummary: StateFlow<FinancialSummary> = _financialSummary.asStateFlow()

    private val _profitPrediction = MutableStateFlow(ProfitPrediction())
    val profitPrediction: StateFlow<ProfitPrediction> = _profitPrediction.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    init {
        refreshAnalytics()
    }

    fun refreshAnalytics() {
        viewModelScope.launch {
            try {
                _financialSummary.value = repository.getFinancialSummary()
                _profitPrediction.value = repository.calculateProfitPrediction()
            } catch (e: Exception) {
                _isError.value = true
                _operationMessage.value = "Failed to load metrics: ${e.message}"
            }
        }
    }

    fun addBill(amount: Double, reason: String) {
        viewModelScope.launch {
            val res = repository.addBill(amount, reason)
            if (res.isSuccess) {
                _isError.value = false
                _operationMessage.value = "Expense \$${"%.2f".format(amount)} recorded for '$reason'"
                refreshAnalytics()
            } else {
                _isError.value = true
                _operationMessage.value = res.exceptionOrNull()?.message ?: "Failed to record bill"
            }
        }
    }

    fun addReceipt(amount: Double, reason: String) {
        viewModelScope.launch {
            val res = repository.addReceipt(amount, reason)
            if (res.isSuccess) {
                _isError.value = false
                _operationMessage.value = "Income \$${"%.2f".format(amount)} recorded from '$reason'"
                refreshAnalytics()
            } else {
                _isError.value = true
                _operationMessage.value = res.exceptionOrNull()?.message ?: "Failed to record receipt"
            }
        }
    }

    fun addProduct(
        id: String,
        name: String,
        price: Double,
        amountLeft: Int,
        amountSold: Int = 0,
        dateFilled: String = "",
        type: String = "General"
    ) {
        viewModelScope.launch {
            val res = repository.addProduct(id, name, price, amountLeft, amountSold, dateFilled, type)
            if (res.isSuccess) {
                _isError.value = false
                _operationMessage.value = "Product '$name' ($id) successfully added"
                refreshAnalytics()
            } else {
                _isError.value = true
                _operationMessage.value = res.exceptionOrNull()?.message ?: "Failed to add product"
            }
        }
    }

    fun updatePrice(productId: String, newPrice: Double) {
        viewModelScope.launch {
            val res = repository.updatePrice(productId, newPrice)
            if (res.isSuccess) {
                _isError.value = false
                _operationMessage.value = "Price for $productId updated to \$${"%.2f".format(newPrice)}"
                refreshAnalytics()
            } else {
                _isError.value = true
                _operationMessage.value = res.exceptionOrNull()?.message ?: "Failed to update price"
            }
        }
    }

    fun removeProduct(productId: String) {
        viewModelScope.launch {
            val res = repository.removeProduct(productId)
            if (res.isSuccess) {
                _isError.value = false
                _operationMessage.value = "Product $productId removed"
                refreshAnalytics()
            } else {
                _isError.value = true
                _operationMessage.value = res.exceptionOrNull()?.message ?: "Failed to delete product"
            }
        }
    }

    fun clearMessage() {
        _operationMessage.value = null
    }

    suspend fun getExportData(): String {
        return repository.exportCsvSummary()
    }
}

class AdminViewModelFactory(private val repository: SupermarketRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
