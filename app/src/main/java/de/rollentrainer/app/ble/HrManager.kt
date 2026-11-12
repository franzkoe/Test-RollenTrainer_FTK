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

package de.rollentrainer.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pulsmesser über den Bluetooth-Standarddienst „Heart Rate“ (0x180D):
 * verbindet direkt per GATT, abonniert „Heart Rate Measurement“ (0x2A37) und
 * verbindet sich bei Abbruch automatisch neu.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class HrManager(private val context: Context, private val scope: CoroutineScope) {

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Heart Rate Measurement: Flags-Bit 0 = 0 → uint8, 1 → uint16. */
        fun parseHeartRate(b: ByteArray): Int? {
            if (b.size < 2) return null
            val wide = (b[0].toInt() and 0x01) != 0
            val v = if (wide) {
                if (b.size < 3) return null
                (b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)
            } else {
                b[1].toInt() and 0xFF
            }
            return if (v in 1..250) v else null
        }
    }

    private val btManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var wantedAddress: String? = null
    private var reconnectJob: Job? = null

    private fun usable(): Boolean = try {
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED && btManager.adapter?.isEnabled == true
    } catch (e: Exception) {
        false
    }

    fun connect(address: String) {
        if (!usable()) return
        if (wantedAddress == address && _state.value != ConnState.DISCONNECTED) return
        closeGatt()
        wantedAddress = address
        openGatt(address)
    }

    fun disconnect() {
        wantedAddress = null
        closeGatt()
    }

    private fun openGatt(address: String) {
        val found: BluetoothDevice? = try {
            btManager.adapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (found == null) {
            _state.value = ConnState.DISCONNECTED
            return
        }
        _state.value = ConnState.CONNECTING
        _address.value = address
        gatt = found.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun closeGatt() {
        reconnectJob?.cancel()
        val g = gatt
        gatt = null
        if (g != null) {
            try {
                g.disconnect()
                g.close()
            } catch (e: Exception) {
                // ignorieren
            }
        }
        _state.value = ConnState.DISCONNECTED
        _address.value = null
        _heartRate.value = null
    }

    private fun handleDisconnect(g: BluetoothGatt) {
        try {
            g.close()
        } catch (e: Exception) {
            // ignorieren
        }
        gatt = null
        _heartRate.value = null
        val wanted = wantedAddress
        if (wanted == null) {
            _state.value = ConnState.DISCONNECTED
            _address.value = null
            return
        }
        _state.value = ConnState.CONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            val still = wantedAddress
            if (still != null && gatt == null) {
                if (usable()) {
                    openGatt(still)
                } else {
                    _state.value = ConnState.DISCONNECTED
                    _address.value = null
                }
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                scope.launch {
                    delay(400)
                    if (g === gatt) g.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handleDisconnect(g)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            val desc = ch?.getDescriptor(CCCD)
            if (status != BluetoothGatt.GATT_SUCCESS || ch == null || desc == null) {
                // Kein Pulsmesser: nicht weiter versuchen
                scope.launch { disconnect() }
                return
            }
            try {
                g.setCharacteristicNotification(ch, true)
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    g.writeDescriptor(desc)
                }
            } catch (e: Exception) {
                // ignorieren
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (g === gatt && status == BluetoothGatt.GATT_SUCCESS) {
                _state.value = ConnState.READY
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val v = characteristic.value ?: return
            if (characteristic.uuid == HR_MEASUREMENT) handle(v)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == HR_MEASUREMENT) handle(value)
        }
    }

    private fun handle(value: ByteArray) {
        _heartRate.value = parseHeartRate(value)
        if (_state.value != ConnState.READY && gatt != null) _state.value = ConnState.READY
    }
}
