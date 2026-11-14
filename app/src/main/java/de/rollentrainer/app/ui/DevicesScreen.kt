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

package de.rollentrainer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.data.SavedDevice
import de.rollentrainer.app.data.TYPE_HR
import de.rollentrainer.app.data.TYPE_TRAINER

@Composable
fun DevicesScreen(vm: Engine, requestPermissions: () -> Unit, enableBluetooth: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val conn by vm.connState.collectAsState()
    val connAddress by vm.connectedAddress.collectAsState()
    val hrConn by vm.hrState.collectAsState()
    val hrAddress by vm.hrAddress.collectAsState()
    val perms by vm.permissionsGranted.collectAsState()
    val btOn by vm.btEnabled.collectAsState()
    val status by vm.trainerStatus.collectAsState()

    var scanType by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<SavedDevice?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedDevice?>(null) }

    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Geräte", conn, onBack = { vm.back() })
        if (devices.isEmpty()) {
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                EnvironmentBanners(vm, requestPermissions, enableBluetooth)
                EmptyState(
                    title = "Noch kein Gerät gespeichert",
                    text = "Trainer einschalten, kurz treten und unten hinzufügen. Ein " +
                        "Pulsmesser (Brustgurt, Armband oder Uhr mit " +
                        "Herzfrequenz-Übertragung) lässt sich zusätzlich verbinden.",
                    modifier = Modifier.weight(1f),
                )
            }
        } else Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnvironmentBanners(vm, requestPermissions, enableBluetooth)
            devices.forEach { d ->
                val state = if (d.type == TYPE_HR) {
                    if (hrAddress == d.address) hrConn else ConnState.DISCONNECTED
                } else {
                    if (connAddress == d.address) conn else ConnState.DISCONNECTED
                }
                DeviceCard(
                    device = d,
                    state = state,
                    onConnect = { vm.connectDevice(d) },
                    onDisconnect = { vm.disconnectDevice(d) },
                    onRename = { renameTarget = d },
                    onDelete = { deleteTarget = d },
                )
            }
            if (status.isNotEmpty()) {
                Text(
                    "Trainer meldet: $status",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { scanType = TYPE_TRAINER },
                enabled = perms && btOn,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                IconLabel(AppIcons.Plus, "Trainer")
            }
            FilledTonalButton(
                onClick = { scanType = TYPE_HR },
                enabled = perms && btOn,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                IconLabel(AppIcons.Plus, "Pulsmesser")
            }
        }
    }

    scanType?.let { type ->
        ScanDialog(vm, type, onClose = { scanType = null })
    }

    renameTarget?.let { target ->
        var name by remember(target.address) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Gerät umbenennen") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.renameDevice(target, name)
                    renameTarget = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Abbrechen") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Gerät löschen?") },
            text = { Text("„${target.name}“ wird aus der Liste entfernt.", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteDevice(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun DeviceCard(
    device: SavedDevice,
    state: ConnState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Gerätetyp links, Zustand rechts: Punkt und ruhiger Text
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (device.type == TYPE_HR) {
                    Icon(
                        AppIcons.Heart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (device.type == TYPE_HR) "Pulsmesser" else "Trainer",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when (state) {
                            ConnState.READY -> StatusPositive
                            ConnState.CONNECTING -> StatusPending
                            ConnState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (state) {
                        ConnState.READY -> "Verbunden"
                        ConnState.CONNECTING -> "Verbinde …"
                        ConnState.DISCONNECTED -> "Getrennt"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(device.name, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            Text(
                device.address,
                fontSize = 13.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == ConnState.DISCONNECTED) {
                Button(onClick = onConnect, modifier = Modifier.height(48.dp)) { Text("Verbinden") }
            } else {
                FilledTonalButton(onClick = onDisconnect, modifier = Modifier.height(48.dp)) {
                    Text("Trennen")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRename) {
                Text("Umbenennen", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete) {
                Text("Löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** BLE-Scan nach Trainern (FTMS) bzw. Pulsmessern; Antippen verbindet und speichert das Gerät. */
@Composable
private fun ScanDialog(vm: Engine, type: String, onClose: () -> Unit) {
    val found by vm.foundDevices.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val saved by vm.devices.collectAsState()
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(showAll) { vm.startScan(type, showAll) }
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (type == TYPE_HR) "Pulsmesser suchen" else "Trainer suchen",
                    modifier = Modifier.weight(1f),
                )
                if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (found.isEmpty()) {
                        Text(
                            if (type == TYPE_HR) {
                                "Pulsmesser anlegen, damit er sendet. Bei Uhren " +
                                    "„Herzfrequenz übertragen“ einschalten."
                            } else {
                                "Trainer einschalten und kurz treten, damit er " +
                                    "aufwacht. Er darf mit keiner anderen App verbunden sein."
                            },
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    found.forEach { f ->
                        val known = saved.any { it.address == f.address }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .card(12.dp)
                                .clickable {
                                    vm.addDevice(f, type)
                                    onClose()
                                }
                                .padding(12.dp),
                        ) {
                            Text(f.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(
                                f.address + "  ·  " + f.rssi + " dBm" +
                                    if (known) "  ·  gespeichert" else "",
                                fontSize = 12.sp,
                                style = Tnum,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    Modifier.clickable { showAll = !showAll },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = showAll, onCheckedChange = { showAll = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Alle Bluetooth-Geräte anzeigen", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Schließen") }
        },
    )
}
