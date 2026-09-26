package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Custom Application class for Supermarket POS & Audit System.
 * Ensures Firebase core initialization occurs reliably on application startup
 * and keeps Firebase Analytics enabled.
 */
class SupermarketApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val app = FirebaseApp.initializeApp(this)
            Log.i(TAG, "Firebase initialized successfully: ${app?.name ?: "default"}")

            // Initialize and enable Firebase Analytics
            val analytics = FirebaseAnalytics.getInstance(this)
            analytics.setAnalyticsCollectionEnabled(true)
            Log.i(TAG, "Firebase Analytics initialized and collection enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization error: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SupermarketApp"
    }
}
