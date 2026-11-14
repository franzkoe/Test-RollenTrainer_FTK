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

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.data.LEVEL_WATTS
import de.rollentrainer.app.data.MAX_LEVEL

@Composable
fun FreeRideScreen(vm: Engine) {
    val ride by vm.ride.collectAsState()
    val data by vm.bike.collectAsState()
    val conn by vm.connState.collectAsState()
    val live by vm.live.collectAsState()
    val heartRate by vm.heartRate.collectAsState()
    val screenOn by vm.keepScreenOn.collectAsState()
    var askEnd by remember { mutableStateOf(false) }

    KeepScreenOn(screenOn)
    // Auch die System-Zurück-Geste löst den Dialog aus
    BackHandler { askEnd = true }

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(Modifier.fillMaxSize()) {
        TopBar("Freie Fahrt", conn, onBack = { askEnd = true })

        val countdown = ride.countdown
        if (countdown != null) {
            BigCountdown(countdown)
        } else {
            val metrics = buildMetrics(data, heartRate, live.distanceM, live.kcal)
            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ElapsedTime(ride.elapsedSec, small = true)
                        MetricGrid(metrics, columns = 3, compact = true)
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LevelControl(ride.level, onChange = { vm.changeLevel(it) })
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ElapsedTime(ride.elapsedSec, small = false)
                    MetricGrid(metrics, columns = 2, compact = false)
                    LevelControl(ride.level, onChange = { vm.changeLevel(it) })
                }
            }
        }
    }

    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text("Einheit beenden?") },
            confirmButton = {
                Button(
                    onClick = {
                        askEnd = false
                        vm.endSession()
                    },
                    modifier = Modifier.height(48.dp),
                ) { Text("Ja", fontSize = 18.sp) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { askEnd = false },
                    modifier = Modifier.height(48.dp),
                ) { Text("Nein", fontSize = 18.sp) }
            },
        )
    }
}

@Composable
private fun ElapsedTime(seconds: Int, small: Boolean) {
    Text(
        formatHms(seconds),
        fontSize = if (small) 48.sp else 64.sp,
        fontWeight = FontWeight.Medium,
        style = Tnum,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Widerstandssteuerung: große „–“/„+“-Tasten, Stufe und Watt, Stufenleiste. */
@Composable
private fun LevelControl(level: Int, onChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val watts = LEVEL_WATTS[level]

    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChange(-1)
                },
                enabled = level > 0,
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Icon(AppIcons.Minus, contentDescription = "Stufe runter", modifier = Modifier.size(40.dp)) }

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Stufe",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                    targetState = level,
                    transitionSpec = {
                        // Kurz und klein: die Zahl rückt ein Stück in Schaltrichtung nach
                        val spec = tween<androidx.compose.ui.unit.IntOffset>(160, easing = AppEasing)
                        val fade = tween<Float>(160, easing = AppEasing)
                        if (targetState > initialState) {
                            (slideInVertically(spec) { it / 4 } + fadeIn(fade)) togetherWith
                                (slideOutVertically(spec) { -it / 4 } + fadeOut(fade))
                        } else {
                            (slideInVertically(spec) { -it / 4 } + fadeIn(fade)) togetherWith
                                (slideOutVertically(spec) { it / 4 } + fadeOut(fade))
                        }
                    },
                    label = "level",
                ) { l ->
                    Text(
                        l.toString(),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = Tnum,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IntensityDot(watts, size = 12.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (level == 0) "Leerlauf" else "$watts W",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        style = Tnum,
                    )
                }
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChange(1)
                },
                enabled = level < MAX_LEVEL,
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Icon(AppIcons.Plus, contentDescription = "Stufe hoch", modifier = Modifier.size(40.dp)) }
        }

        LevelScale(level = level, height = 32.dp)
    }
}
