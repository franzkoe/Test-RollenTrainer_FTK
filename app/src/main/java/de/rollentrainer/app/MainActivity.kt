package de.rollentrainer.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import de.rollentrainer.app.ui.App

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.refreshEnvironment()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(
                vm = vm,
                requestPermissions = { requestBlePermissions() },
                enableBluetooth = { enableBluetooth() },
                openBatterySettings = { openBatterySettings() },
            )
        }
        if (savedInstanceState == null && !vm.permissionsGranted.value) {
            requestBlePermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshEnvironment()
    }

    private fun requestBlePermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        )
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
}
