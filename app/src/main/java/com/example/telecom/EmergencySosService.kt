/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Opt-in background watch that starts the Emergency SOS countdown when the phone is shaken
 * firmly.
 *
 * This is a real foreground service: Android requires a persistent, user-visible notification
 * for background sensor work. The notification is also the reliable cancellation surface, both
 * for an in-progress countdown and for turning the watch off entirely.
 */
class EmergencySosService : Service(), SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var countdownJob: Job? = null
    private var countdownRemaining = 0
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var lastShakeAt = 0L
    private var shakeHits = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_WATCH) {
            stopWatch()
            return START_NOT_STICKY
        }

        if (!ensureForeground()) return START_NOT_STICKY

        if (intent?.action == ACTION_CANCEL_COUNTDOWN) {
            cancelCountdown()
            return START_STICKY
        }

        // A null intent means Android restarted the sticky service. Re-arm the notification
        // without starting a countdown.
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return

        val gForce = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        ) / SensorManager.GRAVITY_EARTH

        val now = System.currentTimeMillis()
        if (gForce > SHAKE_G_THRESHOLD) {
            if (now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                lastShakeAt = now
                shakeHits = 1
            } else {
                shakeHits++
            }
            if (shakeHits >= SHAKE_HITS_REQUIRED) {
                shakeHits = 0
                lastShakeAt = now
                startCountdown()
            }
        } else if (now - lastShakeAt > SHAKE_RESET_MS) {
            shakeHits = 0
        }
    }

    private fun startCountdown() {
        if (countdownJob?.isActive == true) return

        val total = EmergencySosPrefs.getCountdownSeconds(this)
        if (total <= 0) {
            runSos()
            return
        }

        countdownRemaining = total
        updateNotification()
        countdownJob = scope.launch {
            while (countdownRemaining > 0) {
                delay(1_000)
                countdownRemaining--
                if (countdownRemaining > 0) updateNotification()
            }
            runSos()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdownRemaining = 0
        shakeHits = 0
        updateNotification()
    }

    private fun runSos() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    EmergencySosManager.trigger(this@EmergencySosService)
                } catch (_: Exception) {
                }
            }
            // The alert has been handed to Telecom/SMS. The watch can stop; the persistent
            // notification goes away with it.
            stopWatch()
        }
    }

    private fun stopWatch() {
        countdownJob?.cancel()
        countdownJob = null
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun ensureForeground(): Boolean {
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) {
            // Missing FGS type/permission or a refused background start: do not keep an
            // invisible sensor service running.
            stopSelf()
            false
        }
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // The foreground-service notification still appears in the task manager, but
            // runtime notification updates require this permission.
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val countdownActive = countdownRemaining > 0
        val title = if (countdownActive) {
            getString(R.string.sos_countdown_notification_title, countdownRemaining)
        } else {
            getString(R.string.sos_watch_notification_title)
        }
        val text = if (countdownActive) {
            getString(R.string.sos_countdown_notification_text)
        } else {
            getString(R.string.sos_watch_notification_text)
        }

        val cancelCountdownIntent = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            Intent(this, EmergencySosService::class.java).setAction(ACTION_CANCEL_COUNTDOWN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopWatchIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, EmergencySosService::class.java).setAction(ACTION_STOP_WATCH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        if (countdownActive) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.sos_cancel_countdown_action),
                cancelCountdownIntent
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.sos_stop_watch_action),
            stopWatchIntent
        )
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sos_watch_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.sos_watch_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val SHAKE_G_THRESHOLD = 2.6f
        private const val SHAKE_HITS_REQUIRED = 2
        private const val SHAKE_COOLDOWN_MS = 1_200L
        private const val SHAKE_RESET_MS = 1_500L

        private const val CHANNEL_ID = "sos_shake_watch"
        private const val NOTIFICATION_ID = 7301
        private const val REQUEST_CANCEL = 7302
        private const val REQUEST_STOP = 7303
        private const val ACTION_CANCEL_COUNTDOWN = "com.example.telecom.SOS_CANCEL_COUNTDOWN"
        private const val ACTION_STOP_WATCH = "com.example.telecom.SOS_STOP_WATCH"

        /** Starts the opt-in shake watch as a visible foreground service. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EmergencySosService::class.java)
                )
            } catch (_: Exception) {
                // Android 12+ may refuse a background FGS start; the watch stays off until
                // the user opens the app again.
            }
        }

        /** Stops the shake watch and removes its notification. */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, EmergencySosService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
