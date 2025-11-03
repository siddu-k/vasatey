package com.sriox.vasatey

import ai.picovoice.porcupine.*
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sriox.vasatey.models.VercelNotificationRequest
import com.sriox.vasatey.network.RetrofitInstance
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class ListeningService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startPorcupineListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPorcupine()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "vasatey_listen_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Vasatey Listening Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
                val accessKey = getAccessKeyFromFirestore()
                if (accessKey == null) {
                    Log.e("ListeningService", "Access Key is null. Stopping service.")
                    stopSelf()
                    return@launch
                }
                Log.d("ListeningService", "Access Key loaded.")

                val keywordPath = FileUtils.extractAsset(this@ListeningService, "help.ppn")
                Log.d("ListeningService", "Keyword path: $keywordPath")

                val callback = PorcupineManagerCallback { keywordIndex ->
                    Log.d("ListeningService", "Wake word detected with index: $keywordIndex")
                    triggerHelpAlertToGuardians()
                }

                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPath(keywordPath)
                    .setSensitivity(0.7f)
                    .build(applicationContext, callback)

                porcupineManager?.start()
                Log.d("ListeningService", "Porcupine started successfully.")

            } catch (e: Exception) {
                Log.e("ListeningService", "Error starting Porcupine", e)
            }
        }
    }

    private fun triggerHelpAlertToGuardians() = serviceScope.launch {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.email == null) {
            Log.e("ListeningService", "User not logged in, cannot trigger help alert.")
            return@launch
        }
        val userEmail = currentUser.email!!

        try {
            val userDoc = db.collection("users").document(userEmail).get().await()
            val guardians = userDoc.get("guardians") as? List<String> ?: emptyList()
            
            val userName = userDoc.getString("username")
            val userMobile = userDoc.getString("mobileNumber")

            if (guardians.isEmpty()) {
                Log.d("ListeningService", "User has no guardians to alert.")
                return@launch
            }

            val displayName = userName ?: userEmail

            val helpRequest = hashMapOf(
                "fromUserName" to displayName,
                "fromUserEmail" to userEmail,
                "fromUserMobile" to (userMobile ?: ""),
                "timestamp" to FieldValue.serverTimestamp()
            )

            for (guardianEmail in guardians) {
                db.collection("users").document(guardianEmail).collection("alerts").add(helpRequest)

                val guardianDoc = db.collection("users").document(guardianEmail).get().await()
                val guardianToken = guardianDoc.getString("fcmToken")

                if (guardianToken != null) {
                    val notificationBody = "$displayName needs help"
                    
                    val request = VercelNotificationRequest(
                        token = guardianToken,
                        title = "vasatey alert",
                        body = notificationBody
                    )
                    sendNotificationToVercel(request)
                }
            }

            withContext(Dispatchers.Main) {
                showDetectedNotification()
            }

        } catch (e: Exception) {
            Log.e("ListeningService", "Failed to trigger help alerts", e)
        }
    }

    private fun sendNotificationToVercel(request: VercelNotificationRequest) = CoroutineScope(Dispatchers.IO).launch {
        try {
            val response = RetrofitInstance.api.sendNotification(request)
            if (response.isSuccessful) {
                Log.d("ListeningService", "Notification request sent to Vercel successfully.")
            } else {
                Log.e("ListeningService", "Failed to send notification request to Vercel: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("ListeningService", "Error sending notification request to Vercel", e)
        }
    }

    private suspend fun getAccessKeyFromFirestore(): String? {
        val currentUser = auth.currentUser ?: return null
        val userDoc = db.collection("users").document(currentUser.email!!).get().await()
        return userDoc.getString("accessKey") 
    }

    private fun stopPorcupine() {
        porcupineManager?.stop()
        porcupineManager?.delete()
    }

    private fun showDetectedNotification() {
        val channelId = "vasatey_alert_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Help Alerts Sent",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Confirmation that your help alert has been sent."
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Help Alert Sent!")
            .setContentText("Your registered guardians have been notified.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
