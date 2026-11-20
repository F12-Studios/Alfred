package com.alfred.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AlfredApplication : Application() {

    companion object {
        const val CHANNEL_ID_SERVICE = "alfred_service_channel"
        const val CHANNEL_ID_ALERTS = "alfred_alerts_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // Foreground service channel (low importance — persistent, non-intrusive)
        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            getString(R.string.notification_channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_service_description)
            setShowBadge(false)
        }

        // Alerts channel (default importance — for command confirmations, errors)
        val alertsChannel = NotificationChannel(
            CHANNEL_ID_ALERTS,
            getString(R.string.notification_channel_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_alerts_description)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(alertsChannel)
    }
}
