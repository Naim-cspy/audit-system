package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Custom Application class for Supermarket POS & Audit System.
 * Ensures Firebase core initialization occurs reliably on application startup.
 */
class SupermarketApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val app = FirebaseApp.initializeApp(this)
            Log.i(TAG, "Firebase initialized successfully: ${app?.name ?: "default"}")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization error: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SupermarketApp"
    }
}
