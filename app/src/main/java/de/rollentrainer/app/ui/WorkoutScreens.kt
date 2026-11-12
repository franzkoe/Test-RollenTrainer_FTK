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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.RunUi
import de.rollentrainer.app.data.MAX_WATTS
import de.rollentrainer.app.data.Workout
import kotlin.math.roundToInt

// ================================================================ Liste

@Composable
fun WorkoutListScreen(vm: Engine) {
    val workouts by vm.workouts.collectAsState()
    val conn by vm.connState.collectAsState()
    var deleteTarget by remember { mutableStateOf<Workout?>(null) }

    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Trainingsauswahl", conn, onBack = { vm.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (workouts.isEmpty()) {
                Text(
                    "Noch kein Training gespeichert. Tippe auf „Neues Training erstellen“.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            workouts.forEach { w ->
                WorkoutCard(
                    w = w,
                    onStart = { vm.startWorkout(w) },
                    onEdit = { vm.editWorkout(w) },
                    onDuplicate = { vm.duplicateWorkout(w) },
                    onDelete = { deleteTarget = w },
                )
            }
        }
        Button(
            onClick = { vm.newWorkout() },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
        ) {
            Text("＋  Neues Training erstellen", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Training löschen?") },
            text = { Text("„${target.name}“ wird dauerhaft gelöscht.", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteWorkout(target)
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

private fun summary(w: Workout): String {
    val total = w.totalSeconds() / 60
    return "${w.reps} × ${w.intervalMin} min @ ${w.intervalW} W  ·  gesamt $total min"
}

@Composable
private fun WorkoutCard(
    w: Workout,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
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
        Text(w.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(summary(w), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorkoutChart(w.phases(), Modifier.fillMaxWidth().height(48.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("▶  Starten", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Bearbeiten", maxLines = 1) }
            OutlinedButton(
                onClick = onDuplicate,
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Duplizieren", maxLines = 1) }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Löschen", maxLines = 1, color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ================================================================ Editor

@Composable
fun WorkoutEditorScreen(vm: Engine) {
    val w by vm.editing.collectAsState()
    val conn by vm.connState.collectAsState()

    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Training bearbeiten", conn, onBack = { vm.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = w.name,
                onValueChange = { vm.updateEditing(w.copy(name = it)) },
                label = { Text("Name des Trainings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Live-Vorschau
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vorschau", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        "Gesamtdauer " + formatMs(w.totalSeconds()),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                WorkoutChart(w.phases(), Modifier.fillMaxWidth().height(90.dp))
            }

            PhaseEditor(
                title = "Aufwärmen",
                minutes = w.warmupMin,
                watts = w.warmupW,
                minMinutes = 0,
                onMinutes = { vm.updateEditing(w.copy(warmupMin = it)) },
                onWatts = { vm.updateEditing(w.copy(warmupW = it)) },
            )
            PhaseEditor(
                title = "Intervall",
                minutes = w.intervalMin,
                watts = w.intervalW,
                minMinutes = 1,
                onMinutes = { vm.updateEditing(w.copy(intervalMin = it)) },
                onWatts = { vm.updateEditing(w.copy(intervalW = it)) },
            )
            PhaseEditor(
                title = "Pause zwischen Intervallen",
                minutes = w.restMin,
                watts = w.restW,
                minMinutes = 0,
                onMinutes = { vm.updateEditing(w.copy(restMin = it)) },
                onWatts = { vm.updateEditing(w.copy(restW = it)) },
            )

            Section("Wiederholungen") {
                Stepper(
                    label = "Anzahl",
                    valueText = w.reps.toString(),
                    onMinus = { vm.updateEditing(w.copy(reps = (w.reps - 1).coerceAtLeast(1))) },
                    onPlus = { vm.updateEditing(w.copy(reps = (w.reps + 1).coerceAtMost(30))) },
                )
            }

            val cooldownOn = w.cooldownMin > 0
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ausfahren (optional)",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = cooldownOn,
                        onCheckedChange = { on ->
                            vm.updateEditing(w.copy(cooldownMin = if (on) 5 else 0))
                        },
                    )
                }
                if (cooldownOn) {
                    PhaseFields(
                        minutes = w.cooldownMin,
                        watts = w.cooldownW,
                        minMinutes = 1,
                        onMinutes = { vm.updateEditing(w.copy(cooldownMin = it)) },
                        onWatts = { vm.updateEditing(w.copy(cooldownW = it)) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = { vm.saveEditing() },
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text("Speichern", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { vm.startEditing() },
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text("▶  Start", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun PhaseEditor(
    title: String,
    minutes: Int,
    watts: Int,
    minMinutes: Int,
    onMinutes: (Int) -> Unit,
    onWatts: (Int) -> Unit,
) {
    Section(title) {
        PhaseFields(minutes, watts, minMinutes, onMinutes, onWatts)
    }
}

@Composable
private fun PhaseFields(
    minutes: Int,
    watts: Int,
    minMinutes: Int,
    onMinutes: (Int) -> Unit,
    onWatts: (Int) -> Unit,
) {
    Stepper(
        label = "Dauer",
        valueText = "$minutes min",
        onMinus = { onMinutes((minutes - 1).coerceAtLeast(minMinutes)) },
        onPlus = { onMinutes((minutes + 1).coerceAtMost(180)) },
    )
    Stepper(
        label = "Leistung",
        valueText = if (watts == 0) "Leerlauf" else "$watts W",
        valueColor = intensityColor(watts),
        onMinus = { onWatts((watts - 5).coerceAtLeast(0)) },
        onPlus = { onWatts((watts + 5).coerceAtMost(MAX_WATTS)) },
    )
    Slider(
        value = watts.toFloat(),
        onValueChange = { onWatts(((it / 5f).roundToInt() * 5).coerceIn(0, MAX_WATTS)) },
        valueRange = 0f..MAX_WATTS.toFloat(),
    )
}

@Composable
private fun Stepper(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    valueColor: Color = Color.Unspecified,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        FilledTonalButton(
            onClick = onMinus,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("–", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        Text(
            valueText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(112.dp),
        )
        FilledTonalButton(
            onClick = onPlus,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
    }
}

// ================================================================ Trainingsansicht

@Composable
fun WorkoutRunScreen(vm: Engine) {
    val run by vm.run.collectAsState()
    val data by vm.bike.collectAsState()
    val conn by vm.connState.collectAsState()
    val live by vm.live.collectAsState()
    val heartRate by vm.heartRate.collectAsState()
    val screenOn by vm.keepScreenOn.collectAsState()
    var askEnd by remember { mutableStateOf(false) }

    KeepScreenOn(screenOn)
    // Zurück-Taste verhält sich wie „Beenden“ (mit Bestätigung)
    BackHandler { if (!run.finished) askEnd = true }

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(Modifier.fillMaxSize()) {
        TopBar(run.workoutName, conn, onBack = { if (!run.finished) askEnd = true })

        val countdown = run.countdown
        when {
            run.finished -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Training abgeschlossen! 🎉",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            countdown != null -> BigCountdown(countdown)

            run.phases.isNotEmpty() -> {
                val metrics = buildMetrics(data, heartRate, live.distanceM, live.kcal)
                if (landscape) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PhasePanel(run)
                        }
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ProgressPanel(run)
                            MetricGrid(metrics, columns = 3, compact = true)
                            RunButtons(run, onPause = { vm.togglePause() }, onEnd = { askEnd = true })
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PhasePanel(run)
                        ProgressPanel(run)
                        MetricGrid(metrics, columns = 3, compact = true)
                        RunButtons(run, onPause = { vm.togglePause() }, onEnd = { askEnd = true })
                    }
                }
            }
        }
    }

    if (askEnd && !run.finished) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text("Training beenden?") },
            text = { Text("Das Training wird abgebrochen.", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        askEnd = false
                        vm.endSession()
                    },
                    modifier = Modifier.height(52.dp),
                ) { Text("Ja", fontSize = 18.sp) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { askEnd = false },
                    modifier = Modifier.height(52.dp),
                ) { Text("Nein", fontSize = 18.sp) }
            },
        )
    }
}

/** Aktuelle Phase, Zielwatt und Restzeit der Phase – groß und farblich gekennzeichnet. */
@Composable
private fun PhasePanel(run: RunUi) {
    val phase = run.phases[run.phaseIndex.coerceIn(0, run.phases.size - 1)]
    val shownWatts = if (run.paused) 0 else phase.watts
    val color by animateColorAsState(intensityColor(shownWatts), label = "phaseColor")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnimatedContent(
            targetState = if (run.paused) "Pausiert" else phase.label,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "phaseLabel",
        ) { label ->
            Text(
                label,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Zielwatt",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (phase.watts == 0) "Leerlauf" else "${phase.watts} W",
                    fontSize = if (phase.watts == 0) 34.sp else 46.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Restzeit Phase",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatMs(run.phaseRemainingSec),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Gesamt-Restzeit, Fortschritt über den gesamten Ablauf, Vorschau auf die nächste Phase. */
@Composable
private fun ProgressPanel(run: RunUi) {
    val fraction = if (run.totalSec > 0) run.elapsedSec.toFloat() / run.totalSec else 0f
    val animated by animateFloatAsState(fraction, label = "progress")
    val next = run.phases.getOrNull(run.phaseIndex + 1)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Gesamt-Restzeit",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatMs(run.totalRemainingSec),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        WorkoutChart(run.phases, Modifier.fillMaxWidth().height(54.dp), progress = animated)
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Als Nächstes: ",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (next == null) {
                Text("Ende des Trainings", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(intensityColor(next.watts))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    next.label + "  ·  " + (if (next.watts == 0) "Leerlauf" else "${next.watts} W") +
                        "  ·  " + formatMs(next.seconds),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RunButtons(run: RunUi, onPause: () -> Unit, onEnd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPause,
            modifier = Modifier.weight(1f).height(72.dp),
            shape = RoundedCornerShape(22.dp),
        ) {
            Text(
                if (run.paused) "▶  Weiter" else "⏸  Pause",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(
            onClick = onEnd,
            modifier = Modifier.weight(1f).height(72.dp),
            shape = RoundedCornerShape(22.dp),
        ) {
            Text(
                "Beenden",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
