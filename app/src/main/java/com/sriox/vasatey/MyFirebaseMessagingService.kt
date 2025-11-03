package com.sriox.vasatey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val authHelper = SupabaseAuthHelper()
    private val dbHelper = SupabaseDatabaseHelper()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        saveTokenToSupabase(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        Log.d("FCM", "Received data keys: ${data.keys}")
        Log.d("FCM", "All data: $data")
        
        val title = data["title"]
        val body = data["body"]
        val userName = data["fullName"]
        val userEmail = data["email"]
        val mobileNumber = data["phoneNumber"]
        val latitude = data["lastKnownLatitude"]
        val longitude = data["lastKnownLongitude"]

        Log.d("FCM", "Parsed values - userName: $userName, userEmail: $userEmail, mobile: $mobileNumber")

        showGuardianAlert(title, body, userName, userEmail, mobileNumber, latitude, longitude)
    }

    private fun saveTokenToSupabase(token: String) {
        val currentUser = authHelper.getCurrentUser()
        if (currentUser != null && currentUser.email != null) {
            CoroutineScope(Dispatchers.IO).launch {
                dbHelper.updateFCMToken(currentUser.id, token).fold(
                    onSuccess = { Log.d("FCM", "FCM token updated successfully.") },
                    onFailure = { e -> Log.w("FCM", "Error updating FCM token", e) }
                )
            }
        }
    }

    private fun showGuardianAlert(title: String?, body: String?, userName: String?, userEmail: String?, mobileNumber: String?, latitude: String?, longitude: String?) {
        val channelId = "guardian_alert_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Guardian Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts from people you are guarding."
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            }
            manager.createNotificationChannel(channel)
        }

        val uniqueId = System.currentTimeMillis().toInt()

        val detailsIntent = Intent(this, AlertDetailsActivity::class.java).apply {
            putExtra("USER_NAME", userName)
            putExtra("USER_EMAIL", userEmail)
            putExtra("USER_MOBILE", mobileNumber)
            putExtra("USER_LATITUDE", latitude)
            putExtra("USER_LONGITUDE", longitude)
        }

        val detailsPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(detailsIntent)
            getPendingIntent(uniqueId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        // Construct the desired notification text
        val notificationText = if (!userName.isNullOrBlank()) {
            "$userName needs help. click for more details"
        } else {
            body ?: "A person you are guarding needs help. click for more details"
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: "Guardian Alert")
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(detailsPendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))

        if (!mobileNumber.isNullOrBlank()) {
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobileNumber"))
            val callPendingIntent = PendingIntent.getActivity(this, uniqueId + 1, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(NotificationCompat.Action(0, "Call", callPendingIntent))
        }

        manager.notify(uniqueId, builder.build())
    }
}