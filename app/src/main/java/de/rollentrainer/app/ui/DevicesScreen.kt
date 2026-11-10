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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import de.rollentrainer.app.AppViewModel
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.data.SavedDevice

@Composable
fun DevicesScreen(vm: AppViewModel, requestPermissions: () -> Unit, enableBluetooth: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val conn by vm.connState.collectAsState()
    val connAddress by vm.connectedAddress.collectAsState()
    val perms by vm.permissionsGranted.collectAsState()
    val btOn by vm.btEnabled.collectAsState()
    val status by vm.trainerStatus.collectAsState()

    var showScan by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedDevice?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedDevice?>(null) }

    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Geräte", conn, onBack = { vm.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnvironmentBanners(vm, requestPermissions, enableBluetooth)
            if (devices.isEmpty()) {
                Text(
                    "Noch kein Gerät gespeichert. Schalte den Trainer ein und tippe auf " +
                        "„Neues Gerät hinzufügen“.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            devices.forEach { d ->
                val state = if (connAddress == d.address) conn else ConnState.DISCONNECTED
                DeviceCard(
                    device = d,
                    state = state,
                    onConnect = { vm.connectDevice(d) },
                    onDisconnect = { vm.disconnectDevice() },
                    onRename = { renameTarget = d },
                    onDelete = { deleteTarget = d },
                )
            }
            if (status.isNotEmpty()) {
                Text(
                    "Status: $status",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = { showScan = true },
            enabled = perms && btOn,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
        ) {
            Text("＋  Neues Gerät hinzufügen", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showScan) {
        ScanDialog(vm, onClose = { showScan = false })
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
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    device.address,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                when (state) {
                    ConnState.READY -> "Verbunden"
                    ConnState.CONNECTING -> "Verbinde …"
                    ConnState.DISCONNECTED -> "Getrennt"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (state) {
                    ConnState.READY -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state == ConnState.DISCONNECTED) {
                Button(onClick = onConnect, modifier = Modifier.height(50.dp)) { Text("Verbinden") }
            } else {
                FilledTonalButton(onClick = onDisconnect, modifier = Modifier.height(50.dp)) {
                    Text("Trennen")
                }
            }
            OutlinedButton(onClick = onRename, modifier = Modifier.height(50.dp)) {
                Text("Umbenennen")
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.height(50.dp)) {
                Text("Löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** BLE-Scan nach FTMS-Geräten; Antippen verbindet und speichert das Gerät. */
@Composable
private fun ScanDialog(vm: AppViewModel, onClose: () -> Unit) {
    val found by vm.foundDevices.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val saved by vm.devices.collectAsState()
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(showAll) { vm.startScan(showAll) }
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trainer suchen", modifier = Modifier.weight(1f))
                if (scanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
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
                            "Suche läuft … Trainer einschalten und kurz treten, damit er " +
                                "aufwacht. Er darf mit keiner anderen App verbunden sein.",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    found.forEach { f ->
                        val known = saved.any { it.address == f.address }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    vm.addDevice(f)
                                    onClose()
                                }
                                .padding(14.dp),
                        ) {
                            Text(f.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                f.address + "  ·  " + f.rssi + " dBm" +
                                    if (known) "  ·  gespeichert" else "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
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
