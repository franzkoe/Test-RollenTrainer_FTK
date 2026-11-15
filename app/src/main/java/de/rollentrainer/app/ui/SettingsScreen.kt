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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.Screen

/** Schalter mit Titel und Erklärung; die ganze Zeile ist antippbar. */
@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Alle Einstellungen an einer Stelle. Erreichbar aus dem Hauptmenü und aus der Freien Fahrt;
 * eine laufende Fahrt geht währenddessen weiter.
 */
@Composable
fun SettingsScreen(vm: Engine, openBatterySettings: () -> Unit) {
    val conn by vm.connState.collectAsState()
    val config by vm.levelConfig.collectAsState()
    val light by vm.lightTheme.collectAsState()
    val screenOn by vm.keepScreenOn.collectAsState()
    val signals by vm.signals.collectAsState()
    val analysisMode by vm.analysisMode.collectAsState()
    val profile by vm.profile.collectAsState()
    val ai by vm.aiConfig.collectAsState()
    val w = win()
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    BackHandler { vm.back() }

    val first: @Composable () -> Unit = {
        PlanCard("Freie Fahrt") {
            LinkRow(
                AppIcons.Sliders, "Stufen und Maximalleistung",
                "${config.levels} Stufen bis ${config.watts(config.levels)} W",
            ) { vm.open(Screen.SETTINGS_LEVELS) }
            Text("Freie Fahrten für die Analyse verwenden", fontSize = 16.sp)
            Choice(listOf("Jedes Mal fragen", "Immer", "Nie"), analysisMode) { vm.setAnalysisMode(it) }
            Hint(
                "Gilt beim Speichern einer Freien Fahrt. Fahrten aus Trainings und " +
                    "Trainingsplan zählen immer."
            )
        }
        PlanCard("Anzeige") {
            SwitchRow("Helles Design", "", light) { vm.toggleTheme() }
            SwitchRow(
                "Bildschirm bleibt an",
                "Während Freier Fahrt und Training",
                screenOn,
            ) { vm.toggleKeepScreenOn() }
        }
        PlanCard("Signale") {
            SwitchRow(
                "Ton und Vibration",
                "Beim Start und bei jedem Wechsel des Abschnitts",
                signals,
            ) { vm.toggleSignals() }
        }
    }

    val second: @Composable () -> Unit = {
        PlanCard("Training") {
            LinkRow(AppIcons.User, "Profil", profileLine(profile)) { vm.open(Screen.PLAN_PROFILE) }
            LinkRow(
                AppIcons.Bluetooth, "Geräte",
                "Trainer und Pulsmesser verbinden",
            ) { vm.open(Screen.DEVICES) }
            LinkRow(
                AppIcons.Message, "KI-Dienst",
                if (ai.ready) ai.model else "Nicht eingerichtet",
            ) { vm.open(Screen.PLAN_AI) }
        }
        PlanCard("System") {
            Hint(
                "Damit Verbindung und Aufzeichnung im Standby weiterlaufen, sollte die " +
                    "Akku-Optimierung für diese App auf „Keine Beschränkungen“ stehen."
            )
            OutlinedButton(
                onClick = openBatterySettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Akku-Einstellungen öffnen", fontSize = 16.sp) }
        }
        PlanCard("Über die App") {
            Hint(
                "Rollentrainer $version · freie Software unter der GNU GPL 3.0 oder später · " +
                    "Quellcode: github.com/franzkoe/Test-RollenTrainer_FTK"
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Einstellungen", conn, onBack = { vm.back() })
        Panes(w, Modifier.weight(1f), first, second)
    }
}
