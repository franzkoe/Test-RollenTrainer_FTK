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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.Screen
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.data.ExportFormat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import de.rollentrainer.app.data.Workout
import java.util.Locale

@Composable
fun App(
    vm: Engine,
    requestPermissions: () -> Unit,
    enableBluetooth: () -> Unit,
    openBatterySettings: () -> Unit,
    onShare: (ExportFormat) -> Unit,
    onSave: (ExportFormat) -> Unit,
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
                        Screen.SETTINGS -> LevelSettingsScreen(vm)
                        Screen.FREE_RIDE -> FreeRideScreen(vm)
                        Screen.WORKOUTS -> WorkoutListScreen(vm)
                        Screen.EDITOR -> WorkoutEditorScreen(vm)
                        Screen.WORKOUT_RUN -> WorkoutRunScreen(vm)
                        Screen.SUMMARY -> SummaryScreen(vm)
                        Screen.HISTORY -> HistoryScreen(vm)
                        Screen.RIDE_DETAIL -> RideDetailScreen(vm, onShare, onSave)
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
                                    "„Keine Beschränkungen“ ein, damit Verbindung und " +
                                    "Aufzeichnung auch im Standby und im Hintergrund " +
                                    "weiterlaufen.",
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
    vm: Engine,
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
            text = "Bluetooth ist ausgeschaltet",
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        Button(onClick = onClick, modifier = Modifier.height(48.dp)) { Text(button) }
    }
}

// ---------------------------------------------------------------- Hauptmenü

/**
 * Hauptmenü. Die beiden Wege aufs Rad (Freie Fahrt, Trainingsauswahl) sind die großen Kacheln und
 * zeigen etwas Echtes aus der App: die Stufenskala bzw. das Profil des ersten Trainings.
 * Geräte und Verlauf sind kompakte Zeilen mit dem aktuellen Stand.
 */
@Composable
fun HomeScreen(vm: Engine, requestPermissions: () -> Unit, enableBluetooth: () -> Unit) {
    val conn by vm.connState.collectAsState()
    val connAddress by vm.connectedAddress.collectAsState()
    val devices by vm.devices.collectAsState()
    val workouts by vm.workouts.collectAsState()
    val light by vm.lightTheme.collectAsState()
    val screenOn by vm.keepScreenOn.collectAsState()
    val rides by vm.rides.collectAsState()

    val trainerName = devices.firstOrNull { it.address == connAddress }?.name
    val firstWorkout = workouts.firstOrNull()
    val lastRide = rides.firstOrNull()

    val config by vm.levelConfig.collectAsState()
    val w = win()
    // Höhe des „Moments“ in den großen Kacheln
    val moment = if (w.tablet) 64.dp else 32.dp

    val devicesRow: @Composable () -> Unit = {
        MenuRow(
            icon = AppIcons.Bluetooth,
            title = "Geräte",
            subtitle = when (conn) {
                ConnState.READY -> (trainerName ?: "Trainer") + " verbunden"
                ConnState.CONNECTING -> "Verbindung zu " + (trainerName ?: "Trainer") + " wird aufgebaut"
                ConnState.DISCONNECTED ->
                    if (devices.isEmpty()) "Trainer suchen und verbinden" else "Kein Trainer verbunden"
            },
            onClick = { vm.open(Screen.DEVICES) },
        )
    }
    val freeRideTile: @Composable (Modifier) -> Unit = { modifier ->
        MenuTile(
            icon = AppIcons.Bike,
            title = "Freie Fahrt",
            subtitle = "Widerstand in ${config.levels + 1} Stufen, von Leerlauf bis " +
                "${config.watts(config.levels)} W",
            modifier = modifier,
            dense = w.short,
            onClick = { vm.openFreeRide() },
        ) {
            LevelScale(level = config.levels, config = config, height = moment)
        }
    }
    val workoutTile: @Composable (Modifier) -> Unit = { modifier ->
        MenuTile(
            icon = AppIcons.Timer,
            title = "Trainingsauswahl",
            subtitle = if (firstWorkout == null) {
                "Intervalltraining anlegen und fahren"
            } else if (workouts.size == 1) {
                firstWorkout.name + " · " + formatMs(firstWorkout.totalSeconds())
            } else {
                "${workouts.size} Trainings, zuerst " + firstWorkout.name
            },
            modifier = modifier,
            dense = w.short,
            onClick = { vm.open(Screen.WORKOUTS) },
        ) {
            // Ohne gespeichertes Training: das Profil, mit dem ein neues Training beginnt
            val preview = firstWorkout ?: Workout(id = "", name = "")
            WorkoutChart(preview.phases(), Modifier.fillMaxWidth().height(moment))
        }
    }
    val historyRow: @Composable () -> Unit = {
        MenuRow(
            icon = AppIcons.Chart,
            title = "Verlauf",
            subtitle = if (lastRide == null) {
                "Noch keine Fahrt gespeichert"
            } else {
                "Zuletzt " + formatMs(lastRide.durationSec) + " · " +
                    String.format(Locale.GERMANY, "%.1f", lastRide.distanceM / 1000.0) + " km" +
                    (lastRide.avgPower?.let { " · Ø $it W" } ?: "")
            },
            onClick = { vm.openHistory() },
        )
    }
    val settingsRow: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.toggleTheme() }) {
                Text(
                    if (light) "Design: hell" else "Design: dunkel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.toggleKeepScreenOn() }) {
                Text(
                    if (screenOn) "Bildschirm bleibt an" else "Bildschirm darf ausgehen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.open(Screen.SETTINGS) }) {
                Icon(
                    AppIcons.Sliders,
                    contentDescription = "Stufen der Freien Fahrt einstellen",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (w.w >= 600) {
                    Spacer(Modifier.width(8.dp))
                    Text("Stufen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Rollentrainer", conn)
        if (w.landscape) {
            // Quer: links Stand und Einstellungen, rechts die beiden Wege aufs Rad
            Row(
                Modifier.fillMaxSize().padding(horizontal = w.gutter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (w.tablet) 24.dp else 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EnvironmentBanners(vm, requestPermissions, enableBluetooth)
                        devicesRow()
                        historyRow()
                    }
                    settingsRow()
                }
                Column(
                    Modifier.weight(if (w.tablet) 1.4f else 1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    freeRideTile(Modifier.weight(1f))
                    workoutTile(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = w.gutter).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EnvironmentBanners(vm, requestPermissions, enableBluetooth)
                devicesRow()
                freeRideTile(Modifier.weight(1f))
                workoutTile(Modifier.weight(1f))
                historyRow()
                settingsRow()
            }
        }
    }
}

@Composable
private fun MenuHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleSize: Int,
    oneLine: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = titleSize.sp, lineHeight = (titleSize + 6).sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (oneLine) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            AppIcons.Chevron,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Kompakte Menüzeile mit aktuellem Stand. */
@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .card()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        MenuHeader(icon, title, subtitle, titleSize = 18)
    }
}

/** Große Menükachel; [moment] zeigt unten etwas Echtes aus der App. */
@Composable
private fun MenuTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    onClick: () -> Unit,
    moment: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .card()
            .clickable(onClick = onClick)
            .padding(if (dense) 16.dp else 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        MenuHeader(icon, title, subtitle, titleSize = if (dense) 20 else 24, oneLine = dense)
        moment()
    }
}
