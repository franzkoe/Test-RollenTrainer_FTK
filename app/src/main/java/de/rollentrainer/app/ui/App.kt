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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.AppViewModel
import de.rollentrainer.app.Screen
import de.rollentrainer.app.ble.ConnState

@Composable
fun App(
    vm: AppViewModel,
    requestPermissions: () -> Unit,
    enableBluetooth: () -> Unit,
    openBatterySettings: () -> Unit,
) {
    val light by vm.lightTheme.collectAsState()
    RollentrainerTheme(light) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val screen by vm.screen.collectAsState()
                Crossfade(targetState = screen, label = "navigation") { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(vm, requestPermissions, enableBluetooth)
                        Screen.DEVICES -> DevicesScreen(vm, requestPermissions, enableBluetooth)
                        Screen.FREE_RIDE -> FreeRideScreen(vm)
                        Screen.WORKOUTS -> WorkoutListScreen(vm)
                        Screen.EDITOR -> WorkoutEditorScreen(vm)
                        Screen.WORKOUT_RUN -> WorkoutRunScreen(vm)
                    }
                }

                // Hinweis: kein Trainer verbunden
                val needTrainer by vm.needTrainer.collectAsState()
                if (needTrainer) {
                    AlertDialog(
                        onDismissRequest = { vm.dismissNeedTrainer() },
                        title = { Text("Kein Trainer verbunden") },
                        text = {
                            Text(
                                "Freie Fahrt und Trainings lassen sich nur starten, wenn ein " +
                                    "Trainer verbunden ist. Verbinde zuerst deinen Trainer.",
                                fontSize = 16.sp,
                            )
                        },
                        confirmButton = {
                            Button(onClick = { vm.open(Screen.DEVICES) }) { Text("Zu „Geräte“") }
                        },
                        dismissButton = {
                            TextButton(onClick = { vm.dismissNeedTrainer() }) { Text("Abbrechen") }
                        },
                    )
                }

                // Hinweis beim ersten Start: Akkuoptimierung
                val batteryHint by vm.batteryHint.collectAsState()
                if (batteryHint) {
                    AlertDialog(
                        onDismissRequest = { vm.dismissBatteryHint() },
                        title = { Text("Akkuoptimierung deaktivieren") },
                        text = {
                            Text(
                                "Xiaomi/HyperOS beendet Bluetooth-Verbindungen von Apps, die " +
                                    "Akku sparen sollen. Stelle für diese App bitte " +
                                    "„Keine Beschränkungen“ ein, damit die Verbindung zum " +
                                    "Trainer während der Fahrt stabil bleibt.",
                                fontSize = 16.sp,
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                vm.dismissBatteryHint()
                                openBatterySettings()
                            }) { Text("Einstellungen öffnen") }
                        },
                        dismissButton = {
                            TextButton(onClick = { vm.dismissBatteryHint() }) { Text("Später") }
                        },
                    )
                }
            }
        }
    }
}

/** Banner für fehlende Berechtigung bzw. ausgeschaltetes Bluetooth. */
@Composable
fun EnvironmentBanners(
    vm: AppViewModel,
    requestPermissions: () -> Unit,
    enableBluetooth: () -> Unit,
) {
    val perms by vm.permissionsGranted.collectAsState()
    val btOn by vm.btEnabled.collectAsState()
    if (!perms) {
        Banner(
            text = "Die App braucht die Bluetooth-Berechtigung („Geräte in der Nähe“), " +
                "um den Trainer zu finden.",
            button = "Berechtigung erteilen",
            onClick = requestPermissions,
        )
    } else if (!btOn) {
        Banner(
            text = "Bluetooth ist ausgeschaltet.",
            button = "Bluetooth einschalten",
            onClick = enableBluetooth,
        )
    }
}

@Composable
private fun Banner(text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 15.sp)
        Button(onClick = onClick) { Text(button) }
    }
}

// ---------------------------------------------------------------- Hauptmenü

@Composable
fun HomeScreen(vm: AppViewModel, requestPermissions: () -> Unit, enableBluetooth: () -> Unit) {
    val conn by vm.connState.collectAsState()
    val light by vm.lightTheme.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("Rollentrainer", conn)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EnvironmentBanners(vm, requestPermissions, enableBluetooth)
            MenuTile(
                emoji = "📡",
                title = "Geräte",
                subtitle = when (conn) {
                    ConnState.READY -> "Trainer verbunden"
                    ConnState.CONNECTING -> "Verbindung wird aufgebaut …"
                    ConnState.DISCONNECTED -> "Trainer suchen und verbinden"
                },
                modifier = Modifier.weight(1f),
                onClick = { vm.open(Screen.DEVICES) },
            )
            MenuTile(
                emoji = "🚴",
                title = "Freie Fahrt",
                subtitle = "Widerstand in 21 Stufen frei wählen",
                modifier = Modifier.weight(1f),
                onClick = { vm.openFreeRide() },
            )
            MenuTile(
                emoji = "⏱️",
                title = "Trainingsauswahl",
                subtitle = "Intervalltrainings erstellen und fahren",
                modifier = Modifier.weight(1f),
                onClick = { vm.open(Screen.WORKOUTS) },
            )
            TextButton(
                onClick = { vm.toggleTheme() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(if (light) "Dunkles Design verwenden" else "Helles Design verwenden")
            }
        }
    }
}

@Composable
private fun MenuTile(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
