package com.alfred.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alfred.assistant.AlfredApplication
import com.alfred.assistant.R
import com.alfred.assistant.commands.CommandExecutor
import com.alfred.assistant.commands.CommandParser
import com.alfred.assistant.commands.ParsedCommand
import com.alfred.assistant.tts.AlfredSpeaker
import com.alfred.assistant.speech.CommandRecognizer
import com.alfred.assistant.speech.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AlfredService : Service() {

    companion object {
        private const val TAG = "AlfredService"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.alfred.assistant.ACTION_STOP"
        const val ACTION_STATUS_UPDATE = "com.alfred.assistant.STATUS_UPDATE"

        fun start(context: Context) {
            val intent = Intent(context, AlfredService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlfredService::class.java)
            context.stopService(intent)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var wakeWordDetector: WakeWordDetector? = null
    private var commandRecognizer: CommandRecognizer? = null
    private var alfredSpeaker: com.alfred.assistant.tts.AlfredSpeaker? = null

    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlfredService onCreate")
        createNotificationChannel()
        initializeComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlfredService onStartCommand, action=${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop action received, stopping service")
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startListening()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "AlfredService onDestroy")
        stopListening()
        destroyComponents()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Notification ────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AlfredApplication.CHANNEL_ID_SERVICE,
                "Alfred Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Alfred voice assistant background service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AlfredService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tapping the notification opens the main activity
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                1,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        return NotificationCompat.Builder(this, AlfredApplication.CHANNEL_ID_SERVICE)
            .setContentTitle("Alfred")
            .setContentText("Alfred is listening... (\"Hey Alfred\")")
            .setSmallIcon(R.drawable.ic_alfred_notification)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_stop,
                "Stop",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ── Component lifecycle ─────────────────────────────────────────────

    private fun initializeComponents() {
        mainHandler.post {
            alfredSpeaker = com.alfred.assistant.tts.AlfredSpeaker(this)
            initWakeWordDetector()
            initCommandRecognizer()
        }
    }

    private fun initWakeWordDetector() {
        wakeWordDetector = WakeWordDetector(
            context = this,
            onWakeWordDetected = { commandText ->
                Log.d(TAG, "Wake word detected! Trailing command: $commandText")
                broadcastStatus("Processing...", commandText)

                if (!commandText.isNullOrBlank()) {
                    // Command was part of the same utterance as the wake word
                    handleCommand(commandText.trim())
                } else {
                    // Need to listen for the command separately
                    alfredSpeaker?.speak("Yes, sir?") {
                        mainHandler.postDelayed({
                            startCommandCapture()
                        }, 300)
                    }
                }
            }
        )
    }

    private fun initCommandRecognizer() {
        commandRecognizer = CommandRecognizer(
            context = this,
            onCommandRecognized = { text ->
                Log.d(TAG, "Command recognized: $text")
                if (text.isBlank()) {
                    alfredSpeaker?.speak("I didn't catch that, sir. Please try again.") {
                        resumeWakeWordListening()
                    }
                } else {
                    handleCommand(text)
                }
            }
        )
    }

    private fun destroyComponents() {
        mainHandler.post {
            wakeWordDetector?.destroy()
            wakeWordDetector = null
            commandRecognizer?.destroy()
            commandRecognizer = null
            alfredSpeaker?.shutdown()
            alfredSpeaker = null
        }
    }

    // ── Listening control ───────────────────────────────────────────────

    private fun startListening() {
        if (isListening) return
        isListening = true
        broadcastStatus("Listening...", null)
        mainHandler.post {
            wakeWordDetector?.start()
        }
        Log.d(TAG, "Started listening for wake word")
    }

    private fun stopListening() {
        isListening = false
        broadcastStatus("Alfred is paused", null)
        mainHandler.post {
            wakeWordDetector?.stop()
            commandRecognizer?.stop()
        }
        Log.d(TAG, "Stopped listening")
    }

    private fun startCommandCapture() {
        mainHandler.post {
            wakeWordDetector?.stop()
            broadcastStatus("Listening for command...", null)
            commandRecognizer?.startListening()
        }
    }

    private fun resumeWakeWordListening() {
        broadcastStatus("Listening...", null)
        mainHandler.post {
            commandRecognizer?.stop()
            wakeWordDetector?.start()
        }
    }

    // ── Command handling ────────────────────────────────────────────────

    private fun handleCommand(rawCommand: String) {
        Log.d(TAG, "Handling command: $rawCommand")
        broadcastStatus("Processing...", rawCommand)

        serviceScope.launch {
            try {
                val parsed = CommandParser.parse(rawCommand)
                val response = CommandExecutor.execute(this@AlfredService, parsed)

                broadcastStatus("Alfred is ready", rawCommand, response)

                if (response.isNotBlank()) {
                    alfredSpeaker?.speak(response) {
                        resumeWakeWordListening()
                    }
                } else {
                    resumeWakeWordListening()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling command", e)
                val errorMsg = "I'm terribly sorry, sir, but I encountered an error: ${e.message}"
                broadcastStatus("Alfred is ready", rawCommand, errorMsg)
                alfredSpeaker?.speak(errorMsg) {
                    resumeWakeWordListening()
                }
            }
        }
    }

    // ── Status broadcast ────────────────────────────────────────────────

    private fun broadcastStatus(
        status: String,
        lastCommand: String? = null,
        response: String? = null
    ) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra("status", status)
            if (lastCommand != null) {
                putExtra("command", lastCommand)
                putExtra("lastCommand", lastCommand)
            }
            if (response != null) {
                putExtra("response", response)
            }
            putExtra("running", isListening)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
