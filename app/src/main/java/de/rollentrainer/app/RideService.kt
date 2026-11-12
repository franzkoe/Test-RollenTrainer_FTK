/*
 * Rollentrainer – Android-App zur Steuerung eines FTMS-Rollentrainers
 * Copyright (C) 2026 franzkoe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.rollentrainer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Vordergrunddienst während einer Einheit. Er hält den App-Prozess (und damit Bluetooth-Verbindung,
 * Zeitmessung, Trainingsablauf und Aufzeichnung in [Engine]) am Leben, wenn das Handy im Standby
 * ist oder eine andere App im Vordergrund liegt, und zeigt den Stand als Benachrichtigung.
 */
class RideService : Service() {

    companion object {
        private const val CHANNEL = "ride"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RideService::class.java))
            } catch (e: Exception) {
                // Ohne Dienst läuft die Einheit weiter, solange die App sichtbar ist
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RideService::class.java))
            } catch (e: Exception) {
                // ignorieren
            }
        }
    }

    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Laufende Einheit", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = Engine.get(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                build(engine.notificationText()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!engine.sessionRunning) {
            stopSelf()
            return START_NOT_STICKY
        }

        // CPU wach halten, damit Zeitmessung und Phasenwechsel im Standby pünktlich bleiben
        if (wakeLock == null) {
            try {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rollentrainer:ride").also {
                    it.setReferenceCounted(false)
                    it.acquire(8 * 60 * 60 * 1000L)
                }
            } catch (e: Exception) {
                // ignorieren
            }
        }

        if (job == null) {
            val nm = getSystemService(NotificationManager::class.java)
            job = engine.scope.launch {
                var last: Pair<String, String>? = null
                while (isActive) {
                    val text = engine.notificationText()
                    if (text != last) {
                        last = text
                        try {
                            nm.notify(NOTIFICATION_ID, build(text))
                        } catch (e: Exception) {
                            // ignorieren
                        }
                    }
                    delay(1000)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        try {
            wakeLock?.release()
        } catch (e: Exception) {
            // ignorieren
        }
        wakeLock = null
        super.onDestroy()
    }

    private fun build(text: Pair<String, String>): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle(text.first)
            .setContentText(text.second)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
