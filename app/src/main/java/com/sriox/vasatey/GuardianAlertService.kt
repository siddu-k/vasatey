package com.sriox.vasatey

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
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class GuardianAlertService : Service() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var alertListener: ListenerRegistration? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListeningForAlerts()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        alertListener?.remove()
    }

    private fun startListeningForAlerts() {
        val currentUserEmail = auth.currentUser?.email
        if (currentUserEmail == null) {
            stopSelf() // Not logged in, no need to listen
            return
        }

        Log.d("GuardianAlertService", "Starting to listen for alerts for $currentUserEmail")

        alertListener = db.collection("users").document(currentUserEmail).collection("alerts")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("GuardianAlertService", "Listen failed.", e)
                    return@addSnapshotListener
                }

                for (dc in snapshots!!.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val fromUser = dc.document.getString("fromUser") ?: "Someone"
                        Log.d("GuardianAlertService", "New alert received from: $fromUser")
                        showGuardianAlert(fromUser)
                    }
                }
            }
    }

    private fun showGuardianAlert(fromUser: String) {
        val channelId = "guardian_alert_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Guardian Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts from people you are guarding."
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Guardian Alert!")
            .setContentText("$fromUser needs your help!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
