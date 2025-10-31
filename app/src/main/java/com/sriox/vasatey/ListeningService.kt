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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ListeningService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.Default + Job())

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
                // Replace with your real access key from Picovoice Console
                val accessKey = "De6fTwxSusNWisaAiCX7p+Qg5R86pIwxybzrFEUaA6exeB7gFOhNIA=="

                // Keyword file in assets folder
                val keywordPath = FileUtils.extractAsset(this@ListeningService, "help.ppn")

                val callback = PorcupineManagerCallback { keywordIndex ->
                    if (keywordIndex >= 0) {
                        showDetectedNotification()
                    }
                }

                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPaths(arrayOf(keywordPath))
                    .setSensitivities(floatArrayOf(0.7f))
                    .build(applicationContext, callback)

                porcupineManager?.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopPorcupine() {
        porcupineManager?.stop()
        porcupineManager?.delete()
    }

    private fun showDetectedNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "vasatey_alert_channel"

        // Create channel for heads-up (high importance)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Vasatey Alert Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when the wake word is detected"
                enableLights(true)
                enableVibration(true)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build()
                )
            }
            manager.createNotificationChannel(channel)
        }

        // Build a heads-up notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Help Detected!")
            .setContentText("You said the wake word — help me detected!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}