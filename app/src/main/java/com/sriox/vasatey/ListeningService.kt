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
import io.github.jan.supabase.postgrest.from
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
        Log.d("ListeningService", "=== SERVICE STARTING ===")
        alertHistoryManager = AlertHistoryManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        acquireWakeLock()
        startForegroundService()
        Log.d("ListeningService", "Starting Porcupine listening...")
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

                val callback = PorcupineManagerCallback { keywordIndex ->
                    Log.d("ListeningService", "=== WAKE WORD DETECTED ===")
                    Log.d("ListeningService", "Keyword index: $keywordIndex")
                    Log.d("ListeningService", "Wake word '$wakeWord' detected!")
                    Log.d("ListeningService", "Triggering help alert...")
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
        Log.d("ListeningService", "=== HELP ALERT TRIGGERED ===")
        
        val location = getCurrentLocation()
        if (location == null) {
            Log.w("ListeningService", "Proceeding with alert but without location data.")
        }
        
        val currentUser = authHelper.getCurrentUser()
        if (currentUser == null) {
            Log.e("ListeningService", "No current user found")
            return@launch
        }
        
        val userEmail = currentUser.email
        if (userEmail == null) {
            Log.e("ListeningService", "No user email found")
            return@launch
        }
        
        Log.d("ListeningService", "Current user: $userEmail")
        
        try {
            // Get guardians from Supabase
            Log.d("ListeningService", "Getting guardians for user: $userEmail")
            val guardiansResult = dbHelper.getGuardiansForUser(userEmail)
            val guardians = guardiansResult.getOrElse { 
                Log.e("ListeningService", "Failed to get guardians")
                emptyList() 
            }

            Log.d("ListeningService", "Found ${guardians.size} guardians")
            
            if (guardians.isEmpty()) {
                Log.w("ListeningService", "No guardians found - showing error notification")
                showErrorNotification("You have no guardians to alert. Please add guardians in settings.")
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
                    showErrorNotification("Help alert failed to send. Check connection and guardian setup.")
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
            Log.d("ListeningService", "=== SENDING NOTIFICATION ===")
            Log.d("ListeningService", "Guardian Email: $guardianEmail")
            
            var guardianToken: String? = null
            
            // If the guardian is the current user, get fresh FCM token directly from Firebase
            val currentUser = authHelper.getCurrentUser()
            if (currentUser?.email == guardianEmail) {
                Log.d("ListeningService", "Guardian is current user - getting fresh FCM token from Firebase")
                try {
                    guardianToken = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                    Log.d("ListeningService", "Got fresh FCM token: ${guardianToken.take(20)}...")
                    
                    // Update database with fresh token
                    dbHelper.updateFCMToken(currentUser.id, guardianToken)
                    Log.d("ListeningService", "Updated database with fresh token")
                } catch (e: Exception) {
                    Log.e("ListeningService", "Failed to get fresh FCM token", e)
                }
            }
            
            // If we don't have a fresh token, get from database
            if (guardianToken.isNullOrEmpty()) {
                Log.d("ListeningService", "Getting token from database")
                val supabase = SupabaseClient.client
                val allProfiles = supabase.from("user_profiles")
                    .select()
                    .decodeList<UserProfile>()
                
                Log.d("ListeningService", "Total profiles found: ${allProfiles.size}")
                
                val guardianProfile = allProfiles.firstOrNull { it.email == guardianEmail }
                guardianToken = guardianProfile?.fcmToken
                
                Log.d("ListeningService", "Guardian profile found: ${guardianProfile != null}")
                Log.d("ListeningService", "Database token: ${if (guardianToken.isNullOrEmpty()) "NONE" else guardianToken.take(20) + "..."}")
            }
            
            if (guardianToken.isNullOrEmpty()) {
                Log.e("ListeningService", "No FCM token found for guardian: $guardianEmail")
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

            Log.d("ListeningService", "=== REQUEST TO VERCEL ===")
            Log.d("ListeningService", "URL: https://vasatey-notify-msg.vercel.app/api/sendNotification")
            Log.d("ListeningService", "Token: ${guardianToken.take(20)}...")
            Log.d("ListeningService", "Title: ${request.title}")
            Log.d("ListeningService", "Body: ${request.body}")
            Log.d("ListeningService", "From Name: ${request.fullName}")
            Log.d("ListeningService", "From Email: ${request.email}")
            Log.d("ListeningService", "Phone: ${request.phoneNumber}")
            Log.d("ListeningService", "Lat: ${request.lastKnownLatitude}")
            Log.d("ListeningService", "Lng: ${request.lastKnownLongitude}")

            val response = RetrofitInstance.api.sendNotification(request)
            
            Log.d("ListeningService", "=== RESPONSE FROM VERCEL ===")
            Log.d("ListeningService", "Response Code: ${response.code()}")
            Log.d("ListeningService", "Is Successful: ${response.isSuccessful}")
            Log.d("ListeningService", "Response Message: ${response.message()}")
            
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e("ListeningService", "Error Body: $errorBody")
            } else {
                val responseBody = response.body()?.string()
                Log.d("ListeningService", "Success Body: $responseBody")
            }
            
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("ListeningService", "Exception in sendNotificationToGuardian", e)
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
