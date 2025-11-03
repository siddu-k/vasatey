package com.sriox.vasatey

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AlertHistoryManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("alert_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dbHelper = SupabaseDatabaseHelper()
    
    companion object {
        private const val LOCAL_ALERTS_KEY = "local_alerts"
        private const val MAX_DB_ALERTS = 10
    }
    
    // Save alert and manage storage limits
    suspend fun saveAlert(alert: Alert, userId: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                // Save to database
                val dbResult = dbHelper.saveAlert(alert)
                if (dbResult.isFailure) {
                    return@withContext dbResult
                }
                
                // Check and manage database alert count
                manageAlertLimits(userId)
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("AlertHistoryManager", "Failed to save alert", e)
            Result.failure(e)
        }
    }
    
    // Get all alerts (DB + Local)
    suspend fun getAllAlerts(userId: String): Result<List<Alert>> {
        return try {
            withContext(Dispatchers.IO) {
                // Get alerts from database
                val dbAlertsResult = dbHelper.getAlertsForUser(userId)
                val dbAlerts = dbAlertsResult.getOrElse { emptyList() }
                
                // Get alerts from local storage
                val localAlerts = getLocalAlerts()
                
                // Combine and sort by date (newest first)
                val allAlerts = (dbAlerts + localAlerts)
                    .sortedByDescending { parseDate(it.createdAt) }
                
                Result.success(allAlerts)
            }
        } catch (e: Exception) {
            Log.e("AlertHistoryManager", "Failed to get all alerts", e)
            Result.failure(e)
        }
    }
    
    // Manage alert limits (keep 10 in DB, move others to local)
    private suspend fun manageAlertLimits(userId: String) {
        try {
            val dbAlertsResult = dbHelper.getAlertsForUser(userId)
            val dbAlerts = dbAlertsResult.getOrElse { return }
            
            if (dbAlerts.size > MAX_DB_ALERTS) {
                // Sort by date and get excess alerts
                val sortedAlerts = dbAlerts.sortedByDescending { parseDate(it.createdAt) }
                val alertsToKeep = sortedAlerts.take(MAX_DB_ALERTS)
                val alertsToMoveToLocal = sortedAlerts.drop(MAX_DB_ALERTS)
                
                // Move excess alerts to local storage
                if (alertsToMoveToLocal.isNotEmpty()) {
                    val existingLocalAlerts = getLocalAlerts()
                    val updatedLocalAlerts = (existingLocalAlerts + alertsToMoveToLocal)
                        .sortedByDescending { parseDate(it.createdAt) }
                        .take(50) // Keep max 50 in local storage
                    
                    saveLocalAlerts(updatedLocalAlerts)
                    
                    // Delete excess alerts from database
                    alertsToMoveToLocal.forEach { alert ->
                        if (alert.id.isNotEmpty()) {
                            // Note: You might need to implement deleteAlert in SupabaseDatabaseHelper
                            // dbHelper.deleteAlert(alert.id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlertHistoryManager", "Failed to manage alert limits", e)
        }
    }
    
    // Local storage operations
    private fun getLocalAlerts(): List<Alert> {
        return try {
            val json = prefs.getString(LOCAL_ALERTS_KEY, null) ?: return emptyList()
            val type = object : TypeToken<List<Alert>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("AlertHistoryManager", "Failed to get local alerts", e)
            emptyList()
        }
    }
    
    private fun saveLocalAlerts(alerts: List<Alert>) {
        try {
            val json = gson.toJson(alerts)
            prefs.edit().putString(LOCAL_ALERTS_KEY, json).apply()
        } catch (e: Exception) {
            Log.e("AlertHistoryManager", "Failed to save local alerts", e)
        }
    }
    
    // Clear all local alerts
    fun clearLocalAlerts() {
        prefs.edit().remove(LOCAL_ALERTS_KEY).apply()
    }
    
    // Helper method to parse date strings
    private fun parseDate(dateString: String?): Date {
        if (dateString == null) return Date(0)
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            format.parse(dateString) ?: Date(0)
        } catch (e: Exception) {
            Date(0)
        }
    }
}