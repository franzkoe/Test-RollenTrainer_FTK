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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.data.LevelConfig
import java.util.Locale
import kotlin.math.roundToInt

/** Einstellungen der Freien Fahrt: Anzahl der Stufen und Leistung der höchsten Stufe. */
@Composable
fun LevelSettingsScreen(vm: Engine) {
    val config by vm.levelConfig.collectAsState()
    val conn by vm.connState.collectAsState()
    val w = win()

    BackHandler { vm.back() }

    val controls: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().card().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Stufen", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Stepper(
                label = "Anzahl",
                valueText = config.levels.toString(),
                onMinus = { vm.setLevelConfig(config.levels - 1, config.maxWatts) },
                onPlus = { vm.setLevelConfig(config.levels + 1, config.maxWatts) },
            )
            Text(
                "Stufe 0 ist immer Leerlauf und zählt nicht mit. Möglich sind " +
                    "${LevelConfig.MIN_LEVELS} bis ${LevelConfig.MAX_LEVELS} Stufen.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.fillMaxWidth().card().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Maximale Leistung", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Stepper(
                label = "Höchste Stufe",
                valueText = "${config.maxWatts} W",
                onMinus = { vm.setLevelConfig(config.levels, config.maxWatts - LevelConfig.WATT_STEP) },
                onPlus = { vm.setLevelConfig(config.levels, config.maxWatts + LevelConfig.WATT_STEP) },
                dotWatts = config.maxWatts,
            )
            Slider(
                value = config.maxWatts.toFloat(),
                onValueChange = {
                    val step = LevelConfig.WATT_STEP
                    vm.setLevelConfig(config.levels, (it / step).roundToInt() * step)
                },
                valueRange = LevelConfig.MIN_WATTS.toFloat()..LevelConfig.MAX_WATTS.toFloat(),
            )
            Text(
                "Der Trainer begrenzt die Leistung zusätzlich auf seinen eigenen Bereich.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val preview: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().card().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row {
                Text(
                    "So fährt sich das",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Schritt etwa " + String.format(
                        Locale.GERMANY, "%.1f", config.maxWatts.toDouble() / config.levels
                    ) + " W",
                    fontSize = 14.sp,
                    style = Tnum,
                    fontWeight = FontWeight.Medium,
                )
            }
            LevelScale(level = config.levels, config = config, height = 48.dp)
            // Alle Stufen mit ihrer Leistung
            val perRow = if (w.w >= 600 && !w.landscape) 6 else 4
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..config.levels).chunked(perRow).forEach { row ->
                    Row {
                        row.forEach { level ->
                            Row(Modifier.weight(1f)) {
                                Text(
                                    level.toString(),
                                    fontSize = 14.sp,
                                    style = Tnum,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text("${config.watts(level)} W", fontSize = 14.sp, style = Tnum)
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Stufen der Freien Fahrt", conn, onBack = { vm.back() })
        if (w.landscape) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = w.gutter),
                horizontalArrangement = Arrangement.spacedBy(if (w.tablet) 24.dp else 12.dp),
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { controls() }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { preview() }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = w.gutter)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                preview()
                controls()
            }
        }
    }
}
