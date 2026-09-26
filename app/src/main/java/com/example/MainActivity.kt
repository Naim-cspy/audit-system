package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.data.db.AppDatabase
import com.example.data.repository.SupermarketRepository
import com.example.ui.screens.MainApp
import com.example.ui.theme.SupermarketTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AdminViewModelFactory
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.AuthViewModelFactory
import com.example.ui.viewmodel.PosViewModel
import com.example.ui.viewmodel.PosViewModelFactory
import com.example.ui.viewmodel.SaaSPlatformViewModel
import com.example.ui.viewmodel.SaaSPlatformViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = SupermarketRepository(database, applicationContext)

        val authViewModel by viewModels<AuthViewModel> { AuthViewModelFactory(repository) }
        val posViewModel by viewModels<PosViewModel> { PosViewModelFactory(repository) }
        val adminViewModel by viewModels<AdminViewModel> { AdminViewModelFactory(repository) }
        val saasViewModel by viewModels<SaaSPlatformViewModel> { SaaSPlatformViewModelFactory(repository) }

        setContent {
            SupermarketTheme {
                MainApp(
                    authViewModel = authViewModel,
                    posViewModel = posViewModel,
                    adminViewModel = adminViewModel,
                    saasViewModel = saasViewModel
                )
            }
        }
    }
}
