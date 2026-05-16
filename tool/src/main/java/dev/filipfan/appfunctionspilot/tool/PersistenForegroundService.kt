package dev.filipfan.appfunctionspilot.tool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat

class PersistentForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = intent?.getStringExtra("source") ?: "unknown"
        Log.i("PoCFGS", "Service started from source: $source")

        startForeground(1, buildNotification(source))

        // Persistent background work here
        Log.i("PoCFGS", "Foreground service running — source=$source pid=${Process.myPid()}")

        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(source: String): Notification {
        val channelId = "poc_channel"
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(channelId, "Assistant", NotificationManager.IMPORTANCE_LOW)
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistant: processing in background...")
            .setContentText("source: $source")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}