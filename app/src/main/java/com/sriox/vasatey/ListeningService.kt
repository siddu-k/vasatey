package com.sriox.vasatey

import ai.picovoice.porcupine.*
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sriox.vasatey.models.VercelNotificationRequest
import com.sriox.vasatey.network.RetrofitInstance
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class ListeningService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val dbHelper = SupabaseDatabaseHelper()
    private val authHelper = SupabaseAuthHelper()
    private lateinit var alertHistoryManager: AlertHistoryManager

    override fun onCreate() {
        super.onCreate()
        alertHistoryManager = AlertHistoryManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        acquireWakeLock()
        startForegroundService()
        startPorcupineListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        stopPorcupine()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart service if killed by system
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vasatey::listening_wake_lock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "vasatey_listen_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Vasatey Listening Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Vasatey")
            .setContentText("Listening for wake word…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(2, notification)
    }

    private fun startPorcupineListening() {
        serviceScope.launch {
            try {
                // Get current user
                val currentUser = authHelper.getCurrentUser()
                if (currentUser == null) {
                    showErrorNotification("User not logged in.")
                    stopSelf()
                    return@launch
                }

                // Get user settings to get access key and wake word
                val userSettingsResult = dbHelper.getUserSettings(currentUser.id)
                val userSettings = userSettingsResult.getOrNull()
                if (userSettings == null) {
                    showErrorNotification("Unable to load user settings. Please save your Picovoice access key in settings first.")
                    stopSelf()
                    return@launch
                }

                val accessKey = userSettings.picovoiceAccessKey
                val wakeWord = userSettings.wakeWord
                val sensitivity = userSettings.voiceSensitivity

                if (accessKey.isNullOrEmpty()) {
                    showErrorNotification("Picovoice Access Key not found. Please add it in Settings and try again.")
                    stopSelf()
                    return@launch
                }

                Log.d("ListeningService", "Starting Porcupine with wake word: '$wakeWord', sensitivity: $sensitivity")

                // Map wake word to corresponding .ppn file
                val keywordFileName = when {
                    wakeWord.contains("help", ignoreCase = true) -> "help.ppn"
                    wakeWord.contains("leave", ignoreCase = true) -> "leave-me-alone.ppn"
                    wakeWord.contains("vasatey", ignoreCase = true) -> "help.ppn" // Map "hey vasatey" to help.ppn
                    else -> "help.ppn" // Default fallback
                }
                
                val keywordPath = FileUtils.extractAsset(this@ListeningService, keywordFileName)

                val callback = PorcupineManagerCallback { _ ->
                    Log.d("ListeningService", "Wake word '$wakeWord' detected!")
                    triggerHelpAlertToGuardians()
                }

                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPath(keywordPath)
                    .setSensitivity(sensitivity)
                    .build(applicationContext, callback)

                porcupineManager?.start()
                Log.d("ListeningService", "Porcupine started successfully for '$wakeWord' with sensitivity $sensitivity.")

            } catch (e: Exception) {
                Log.e("ListeningService", "Error starting Porcupine: ${e.message}", e)
                showErrorNotification("A critical error occurred in the listening service: ${e.message}")
                stopSelf()
            }
        }
    }

    private suspend fun getUserSettingsFromSupabase(): UserProfile? {
        val currentUser = authHelper.getCurrentUser() ?: return null
        val result = authHelper.getUserProfile(currentUser.email!!)
        return result.getOrNull()
    }

    private fun triggerHelpAlertToGuardians() = serviceScope.launch {
        val location = getCurrentLocation()
        if (location == null) {
            Log.w("ListeningService", "Proceeding with alert but without location data.")
        }
        
        val currentUser = authHelper.getCurrentUser() ?: return@launch
        val userEmail = currentUser.email ?: return@launch
        
        try {
            // Get guardians from Supabase
            val guardiansResult = dbHelper.getGuardiansForUser(userEmail)
            val guardians = guardiansResult.getOrElse { emptyList() }

            if (guardians.isEmpty()) {
                showErrorNotification("You have no guardians to alert.")
                return@launch
            }

            // Get user profile
            val userProfile = authHelper.getUserProfile(userEmail).getOrNull()
            val userName = userProfile?.fullName ?: userEmail
            val mobileNumber = userProfile?.phoneNumber

            val notificationTasks = guardians.map { guardian ->
                async(Dispatchers.IO) {
                    sendNotificationToGuardian(guardian.guardianEmail ?: "", userName, userEmail, mobileNumber, location?.latitude, location?.longitude)
                }
            }

            val results = notificationTasks.awaitAll()
            val successCount = results.count { it }

            withContext(Dispatchers.Main) {
                if (successCount > 0) {
                    showDetectedNotification(successCount, guardians.size)
                } else {
                    showErrorNotification("Help alert failed to send. Check connection.")
                }
            }
        } catch (e: Exception) {
            Log.e("ListeningService", "Error triggering help alerts", e)
            showErrorNotification("A critical error occurred while sending alerts.")
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("ListeningService", "Location permission not granted. Cannot get location.")
            return null
        }
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (e: Exception) {
            Log.e("ListeningService", "Could not get location", e)
            null
        }
    }

    private suspend fun sendNotificationToGuardian(guardianEmail: String, fromUserName: String, fromUserEmail: String, fromUserMobile: String?, lat: Double?, lon: Double?): Boolean {
        return try {
            // Get guardian's FCM token from Supabase
            val guardianProfile = authHelper.getUserProfile(guardianEmail).getOrNull()
            val guardianToken = guardianProfile?.fcmToken

            if (guardianToken == null) {
                Log.w("ListeningService", "Guardian $guardianEmail has no FCM token.")
                return false
            }

            val request = VercelNotificationRequest(
                token = guardianToken,
                title = "vasatey alert",
                body = "$fromUserName needs help",
                fullName = fromUserName,
                email = fromUserEmail,
                phoneNumber = fromUserMobile,
                lastKnownLatitude = lat,
                lastKnownLongitude = lon
            )

            val response = RetrofitInstance.api.sendNotification(request)
            if (response.isSuccessful) {
                logAlertToSupabase(fromUserName, fromUserEmail, fromUserMobile, lat, lon)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun logAlertToSupabase(fromUserName: String, fromUserEmail: String, fromUserMobile: String?, lat: Double?, lon: Double?) {
        try {
            val currentUser = authHelper.getCurrentUser()
            if (currentUser != null) {
                // Get the user profile ID instead of using auth ID
                val profileIdResult = dbHelper.getUserProfileId(currentUser.id)
                val profileId = profileIdResult.getOrNull()
                
                if (profileId != null) {
                    val alert = Alert(
                        userId = profileId,
                        alertType = "emergency",
                        severity = "high",
                        triggerMethod = "voice",
                        locationLatitude = lat,
                        locationLongitude = lon,
                        alertMessage = "Emergency alert from $fromUserName - Mobile: ${fromUserMobile ?: "Not available"}"
                    )
                    
                    // Use AlertHistoryManager for smart storage management
                    alertHistoryManager.saveAlert(alert, profileId)
                }
            }
        } catch (e: Exception) {
            Log.e("ListeningService", "Failed to log alert to Supabase", e)
        }
    }

    private fun stopPorcupine() {
        porcupineManager?.stop()
        porcupineManager?.delete()
    }

    private fun showDetectedNotification(successCount: Int, totalCount: Int) {
        val channelId = "vasatey_alert_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Help Alerts Sent", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Help Alert Sent!")
            .setContentText("Notified $successCount of $totalCount guardians.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showErrorNotification(message: String) {
        val channelId = "vasatey_error_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Service Errors", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Vasatey Alert Error")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
