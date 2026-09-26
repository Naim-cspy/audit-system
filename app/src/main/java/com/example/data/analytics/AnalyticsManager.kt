package com.example.data.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Enterprise Analytics Manager.
 * Wraps Firebase Analytics and aggregates cross-store feature usage
 * while strictly adhering to tenant isolation for client dashboards.
 */
class AnalyticsManager(context: Context) {

    private val firebaseAnalytics: FirebaseAnalytics? = try {
        FirebaseAnalytics.getInstance(context).apply {
            setAnalyticsCollectionEnabled(true)
        }
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseAnalytics init notice: ${e.message}")
        null
    }

    fun trackLogin(userId: String, storeId: String, role: String, isPlatformAdmin: Boolean) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.METHOD, "email_password")
                putString("store_id", storeId)
                putString("user_role", role)
                putBoolean("is_platform_admin", isPlatformAdmin)
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
            firebaseAnalytics?.setUserProperty("store_id", storeId)
            firebaseAnalytics?.setUserProperty("role", role)
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking login event: ${e.message}")
        }
    }

    fun trackFeatureUsed(featureName: String, storeId: String) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_NAME, featureName)
                putString(FirebaseAnalytics.Param.CONTENT_TYPE, "feature")
                putString("store_id", storeId)
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking feature usage: ${e.message}")
        }
    }

    fun trackSale(storeId: String, totalAmountUsd: Double, itemCount: Int) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                putDouble(FirebaseAnalytics.Param.VALUE, totalAmountUsd)
                putLong(FirebaseAnalytics.Param.ITEMS, itemCount.toLong())
                putString("store_id", storeId)
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.PURCHASE, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking purchase event: ${e.message}")
        }
    }

    fun trackInventoryUpdate(storeId: String, action: String, productId: String) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, productId)
                putString("action_type", action)
                putString("store_id", storeId)
            }
            firebaseAnalytics?.logEvent("inventory_update", bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking inventory update: ${e.message}")
        }
    }

    fun trackSecurityAlert(storeId: String, eventType: String, reason: String) {
        try {
            val bundle = Bundle().apply {
                putString("alert_type", eventType)
                putString("store_id", storeId)
                putString("reason_code", reason.take(100))
            }
            firebaseAnalytics?.logEvent("security_alert", bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking security alert: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AnalyticsManager"
    }
}
