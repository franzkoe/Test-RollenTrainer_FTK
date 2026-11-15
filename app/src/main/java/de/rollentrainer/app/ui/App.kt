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
import androidx.compose.runtime.remember
import de.rollentrainer.app.data.improvement
import de.rollentrainer.app.data.verdictText
import java.util.Locale

@Composable
fun App(
    vm: Engine,
    requestPermissions: () -> Unit,
    enableBluetooth: () -> Unit,
    openBatterySettings: () -> Unit,
    onShare: (ExportFormat) -> Unit,
    onSave: (ExportFormat) -> Unit,
    onImportRecovery: () -> Unit = {},
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
                        Screen.SETTINGS -> SettingsScreen(vm, openBatterySettings)
                        Screen.SETTINGS_LEVELS -> LevelSettingsScreen(vm)
                        Screen.DASHBOARD -> DashboardScreen(vm, onImportRecovery)
                        Screen.FREE_RIDE -> FreeRideScreen(vm)
                        Screen.WORKOUTS -> WorkoutListScreen(vm)
                        Screen.EDITOR -> WorkoutEditorScreen(vm)
                        Screen.WORKOUT_RUN -> WorkoutRunScreen(vm)
                        Screen.SUMMARY -> SummaryScreen(vm)
                        Screen.HISTORY -> HistoryScreen(vm)
                        Screen.RIDE_DETAIL -> RideDetailScreen(vm, onShare, onSave)
                        Screen.PLAN -> PlanScreen(vm)
                        Screen.PLAN_NEW -> PlanNewScreen(vm)
                        Screen.PLAN_PROFILE -> PlanProfileScreen(vm)
                        Screen.PLAN_AI -> PlanAiScreen(vm)
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
    val rides by vm.rides.collectAsState()
    val planState by vm.planState.collectAsState()
    val config by vm.levelConfig.collectAsState()
    val w = win()

    val trainerName = devices.firstOrNull { it.address == connAddress }?.name
    val firstWorkout = workouts.firstOrNull()
    val lastRide = rides.firstOrNull()
    val today = vm.today()
    val trend = remember(rides, today) { improvement(rides, today) }
    val plan = planState
    val next = plan?.next()

    // Alle Menüpunkte sehen gleich aus und sind gleich groß
    val items = listOf(
        MenuItem(
            AppIcons.Bluetooth, "Geräte",
            when (conn) {
                ConnState.READY -> (trainerName ?: "Trainer") + " verbunden"
                ConnState.CONNECTING -> "Verbindung zu " + (trainerName ?: "Trainer") + " wird aufgebaut"
                ConnState.DISCONNECTED ->
                    if (devices.isEmpty()) "Trainer suchen und verbinden" else "Kein Trainer verbunden"
            },
        ) { vm.open(Screen.DEVICES) },
        MenuItem(
            AppIcons.Bike, "Freie Fahrt",
            "${config.levels + 1} Stufen, von Leerlauf bis ${config.watts(config.levels)} W",
        ) { vm.openFreeRide() },
        MenuItem(
            AppIcons.Timer, "Trainingsauswahl",
            if (firstWorkout == null) {
                "Intervalltraining anlegen und fahren"
            } else if (workouts.size == 1) {
                firstWorkout.name + " · " + formatMs(firstWorkout.totalSeconds())
            } else {
                "${workouts.size} Trainings, zuerst " + firstWorkout.name
            },
        ) { vm.open(Screen.WORKOUTS) },
        MenuItem(
            AppIcons.Calendar, "Mein Trainingsplan",
            when {
                plan == null -> "Plan wählen oder selbst erstellen"
                next == null -> plan.plan.spec.name + " abgeschlossen"
                else -> "Woche ${next.first + 1} von ${plan.plan.weeks.size} · als Nächstes " +
                    plan.plan.weeks[next.first][next.second].name
            },
        ) { vm.open(Screen.PLAN) },
        MenuItem(
            AppIcons.Activity, "Dashboard",
            if (trend.verdict == null) {
                "Analyse, Erholung, VO2max und Profil"
            } else {
                verdictText(trend.verdict) + (trend.fitness?.let {
                    String.format(Locale.GERMANY, " · Fitness %.0f · Form %+.0f", it.now, trend.form)
                } ?: "")
            },
        ) { vm.open(Screen.DASHBOARD) },
        MenuItem(
            AppIcons.Chart, "Verlauf",
            if (lastRide == null) {
                "Noch keine Fahrt gespeichert"
            } else {
                "Zuletzt " + formatMs(lastRide.durationSec) + " · " +
                    String.format(Locale.GERMANY, "%.1f", lastRide.distanceM / 1000.0) + " km" +
                    (lastRide.avgPower?.let { " · Ø $it W" } ?: "")
            },
        ) { vm.openHistory() },
        MenuItem(
            AppIcons.Sliders, "Einstellungen",
            "Stufen, Anzeige, Signale, Profil",
        ) { vm.open(Screen.SETTINGS) },
    )

    val columns = if (w.tablet || w.landscape) 2 else 1
    val gap = 12.dp
    Column(Modifier.fillMaxSize()) {
        TopBar("Rollentrainer", conn)
        if (w.short) {
            // Wenig Höhe (Handy quer): feste Höhe, scrollen
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = w.gutter)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                EnvironmentBanners(vm, requestPermissions, enableBluetooth)
                items.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { MenuRow(it, Modifier.weight(1f).height(80.dp)) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            // Genug Höhe: die Menüpunkte teilen sich den Bildschirm gleichmäßig
            Column(
                Modifier.fillMaxSize().padding(horizontal = w.gutter).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                EnvironmentBanners(vm, requestPermissions, enableBluetooth)
                items.chunked(columns).forEach { row ->
                    Row(
                        Modifier.weight(1f).heightIn(max = if (w.tablet) 200.dp else 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        row.forEach { MenuRow(it, Modifier.weight(1f).fillMaxSize()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private class MenuItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun MenuHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleSize: Int,
    oneLine: Boolean = false,
    big: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(if (big) 32.dp else 24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = titleSize.sp, lineHeight = (titleSize + 6).sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = if (big) 17.sp else 14.sp,
                lineHeight = if (big) 24.sp else 20.sp,
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

/** Menüpunkt mit aktuellem Stand. Alle Menüpunkte nutzen diese eine Form. */
@Composable
private fun MenuRow(item: MenuItem, modifier: Modifier = Modifier) {
    Column(
        modifier
            .card()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val big = win().tablet
        MenuHeader(item.icon, item.title, item.subtitle, titleSize = if (big) 22 else 18, big = big)
    }
}
