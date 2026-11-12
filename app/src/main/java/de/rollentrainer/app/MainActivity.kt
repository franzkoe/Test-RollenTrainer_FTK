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

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import de.rollentrainer.app.data.ExportFormat
import de.rollentrainer.app.ui.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var engine: Engine

    /** Inhalt, der nach der Dateiauswahl („Als Datei speichern“) geschrieben wird. */
    private var pendingSave: ByteArray? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            engine.refreshEnvironment()
        }

    private val saveLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri -> writePendingSave(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = Engine.get(this)
        enableEdgeToEdge()
        setContent {
            App(
                vm = engine,
                requestPermissions = { requestAppPermissions() },
                enableBluetooth = { enableBluetooth() },
                openBatterySettings = { openBatterySettings() },
                onShare = { shareRide(it) },
                onSave = { saveRide(it) },
            )
        }
        if (savedInstanceState == null && (!engine.permissionsGranted.value || !notificationsGranted())) {
            requestAppPermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        engine.onForeground()
    }

    override fun onResume() {
        super.onResume()
        engine.refreshEnvironment()
    }

    override fun onStop() {
        engine.onBackground()
        super.onStop()
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Bluetooth („Geräte in der Nähe“) und Benachrichtigungen (für die laufende Einheit). */
    private fun requestAppPermissions() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.BLUETOOTH_SCAN)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(list.toTypedArray())
    }

    private fun enableBluetooth() {
        try {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (e2: Exception) {
                // ignorieren
            }
        }
    }

    /** Öffnet die Abfrage „Akkuoptimierung ignorieren“, ersatzweise die App-Einstellungen. */
    private fun openBatterySettings() {
        val pkg = Uri.parse("package:$packageName")
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
            } catch (e2: Exception) {
                // ignorieren
            }
        }
    }

    // ---------------------------------------------------------------- Export

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /** Exportiert die geöffnete Fahrt und öffnet das Teilen-Menü (Strava, Garmin, Mail …). */
    private fun shareRide(format: ExportFormat) {
        engine.scope.launch {
            try {
                val export = engine.exportBytes(format)
                if (export == null) {
                    toast("Keine Daten zum Exportieren")
                    return@launch
                }
                val file = withContext(Dispatchers.IO) {
                    val dir = File(cacheDir, "export")
                    dir.mkdirs()
                    File(dir, export.first).also { it.writeBytes(export.second) }
                }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType(format.mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newRawUri(export.first, uri)
                startActivity(Intent.createChooser(send, "Fahrt exportieren"))
            } catch (e: Exception) {
                toast("Export fehlgeschlagen")
            }
        }
    }

    /** Exportiert die geöffnete Fahrt in eine Datei an einem frei wählbaren Ort. */
    private fun saveRide(format: ExportFormat) {
        engine.scope.launch {
            try {
                val export = engine.exportBytes(format)
                if (export == null) {
                    toast("Keine Daten zum Exportieren")
                    return@launch
                }
                pendingSave = export.second
                saveLauncher.launch(export.first)
            } catch (e: Exception) {
                toast("Export fehlgeschlagen")
            }
        }
    }

    private fun writePendingSave(uri: Uri?) {
        val bytes = pendingSave
        pendingSave = null
        if (uri == null || bytes == null) return
        engine.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) "Datei gespeichert" else "Speichern fehlgeschlagen")
        }
    }
}
