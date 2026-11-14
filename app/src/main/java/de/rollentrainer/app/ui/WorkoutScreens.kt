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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
    val win = win()

    Column(Modifier.fillMaxSize()) {
        TopBar("Trainingsauswahl", conn, onBack = { vm.back() })
        if (workouts.isEmpty()) {
            EmptyState(
                title = "Noch kein Training gespeichert",
                text = "Lege unten ein Intervalltraining an: Aufwärmen, Intervalle, " +
                    "Pausen und Ausfahren.",
                modifier = Modifier.weight(1f),
            )
        } else Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = win.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardGrid(workouts, win.columns) { w, modifier ->
                WorkoutCard(
                    modifier = modifier,
                    w = w,
                    onStart = { vm.startWorkout(w) },
                    onEdit = { vm.editWorkout(w) },
                    onDuplicate = { vm.duplicateWorkout(w) },
                    onDelete = { deleteTarget = w },
                )
            }
        }
        BottomBar {
            Button(
                onClick = { vm.newWorkout() },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                IconLabel(AppIcons.Plus, "Neues Training erstellen")
            }
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
    modifier: Modifier,
    w: Workout,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(w.name, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            Text(
                summary(w),
                fontSize = 14.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WorkoutChart(w.phases(), Modifier.fillMaxWidth().height(48.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            IconLabel(AppIcons.Play, "Starten")
        }
        // Verwaltung bleibt ruhig: Textaktionen statt dreier gleich lauter Buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onEdit) {
                Text("Bearbeiten", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDuplicate) {
                Text("Duplizieren", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete) {
                Text("Löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ================================================================ Editor

@Composable
fun WorkoutEditorScreen(vm: Engine) {
    val w by vm.editing.collectAsState()
    val conn by vm.connState.collectAsState()
    val win = win()

    BackHandler { vm.back() }

    val nameField: @Composable () -> Unit = {
        OutlinedTextField(
            value = w.name,
            onValueChange = { vm.updateEditing(w.copy(name = it)) },
            label = { Text("Name des Trainings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Live-Vorschau; [chart] bestimmt die Höhe des Profils
    val preview: @Composable (Modifier, Modifier) -> Unit = { modifier, chart ->
        Column(
            modifier
                .fillMaxWidth()
                .card()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ablauf",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Gesamtdauer " + formatMs(w.totalSeconds()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    style = Tnum,
                )
            }
            WorkoutChart(w.phases(), chart.fillMaxWidth())
        }
    }

    val sections: List<@Composable () -> Unit> = listOf(
        {
            PhaseEditor(
                title = "Aufwärmen",
                minutes = w.warmupMin,
                watts = w.warmupW,
                minMinutes = 0,
                onMinutes = { vm.updateEditing(w.copy(warmupMin = it)) },
                onWatts = { vm.updateEditing(w.copy(warmupW = it)) },
            )
        },
        {
            PhaseEditor(
                title = "Intervall",
                minutes = w.intervalMin,
                watts = w.intervalW,
                minMinutes = 1,
                onMinutes = { vm.updateEditing(w.copy(intervalMin = it)) },
                onWatts = { vm.updateEditing(w.copy(intervalW = it)) },
            )
        },
        {
            PhaseEditor(
                title = "Pause zwischen Intervallen",
                minutes = w.restMin,
                watts = w.restW,
                minMinutes = 0,
                onMinutes = { vm.updateEditing(w.copy(restMin = it)) },
                onWatts = { vm.updateEditing(w.copy(restW = it)) },
            )
        },
        {
            Section("Wiederholungen") {
                Stepper(
                    label = "Anzahl",
                    valueText = w.reps.toString(),
                    onMinus = { vm.updateEditing(w.copy(reps = (w.reps - 1).coerceAtLeast(1))) },
                    onPlus = { vm.updateEditing(w.copy(reps = (w.reps + 1).coerceAtMost(30))) },
                )
            }
        },
        {
            val cooldownOn = w.cooldownMin > 0
            Column(
                Modifier
                    .fillMaxWidth()
                    .card()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ausfahren",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
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
        },
    )

    val buttons: @Composable RowScope.() -> Unit = {
        FilledTonalButton(
            onClick = { vm.saveEditing() },
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text("Speichern", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        Button(
            onClick = { vm.startEditing() },
            modifier = Modifier.weight(1f).height(56.dp),
        ) { IconLabel(AppIcons.Play, "Start") }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Training bearbeiten", conn, onBack = { vm.back() })
        if (win.landscape) {
            // Quer: links Name, Profil und Aktionen dauerhaft sichtbar, rechts die Einstellungen
            Row(
                Modifier.fillMaxSize().padding(horizontal = win.gutter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (win.tablet) 24.dp else 12.dp),
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    nameField()
                    preview(Modifier.weight(1f), Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = buttons)
                }
                Column(
                    Modifier.weight(if (win.tablet) 1.2f else 1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sections.forEach { it() }
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = win.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                nameField()
                preview(Modifier, Modifier.height(if (win.tablet) 160.dp else 88.dp))
                if (win.columns > 1) {
                    // Tablet hochkant: Einstellungen in zwei Spalten
                    sections.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { section -> Box(Modifier.weight(1f)) { section() } }
                            if (row.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    sections.forEach { it() }
                }
            }
            BottomBar(content = buttons)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
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
        dotWatts = watts,
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
fun Stepper(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    dotWatts: Int? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = onMinus,
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Icon(AppIcons.Minus, contentDescription = "weniger", modifier = Modifier.size(24.dp)) }
        // Wert in Textfarbe; die Intensität zeigt der Punkt, nicht die Schrift
        Row(
            Modifier.width(120.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dotWatts != null) {
                IntensityDot(dotWatts)
                Spacer(Modifier.width(8.dp))
            }
            Text(valueText, fontSize = 20.sp, fontWeight = FontWeight.Medium, style = Tnum)
        }
        FilledTonalButton(
            onClick = onPlus,
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Icon(AppIcons.Plus, contentDescription = "mehr", modifier = Modifier.size(24.dp)) }
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

    val w = win()

    Column(Modifier.fillMaxSize()) {
        TopBar(run.workoutName, conn, onBack = { if (!run.finished) askEnd = true })

        val countdown = run.countdown
        when {
            run.finished -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Training abgeschlossen",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            countdown != null -> BigCountdown(countdown)

            run.phases.isNotEmpty() -> {
                val metrics = buildMetrics(data, heartRate, live.distanceM, live.kcal)
                val big = w.tablet
                if (w.landscape) {
                    // Quer: links Phase und Bedienung, rechts Ablauf und Live-Werte
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = w.gutter).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (big) 24.dp else 16.dp),
                    ) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PhasePanel(run, big)
                            RunButtons(run, big, onPause = { vm.togglePause() }, onEnd = { askEnd = true })
                        }
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProgressPanel(run, chartHeight = if (big) 120.dp else 56.dp)
                            if (big) {
                                MetricGrid(metrics, columns = 2, compact = false, large = true)
                            } else {
                                MetricGrid(metrics, columns = 3, compact = true)
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = w.gutter)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PhasePanel(run, big)
                        ProgressPanel(run, chartHeight = if (big) 120.dp else 56.dp)
                        if (big) {
                            MetricGrid(metrics, columns = 3, compact = false, large = true)
                        } else {
                            MetricGrid(metrics, columns = 3, compact = true)
                        }
                        RunButtons(run, big, onPause = { vm.togglePause() }, onEnd = { askEnd = true })
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

/** Aktuelle Phase, Zielwatt und Restzeit der Phase – groß und farblich gekennzeichnet. */
@Composable
private fun PhasePanel(run: RunUi, big: Boolean = false) {
    val phase = run.phases[run.phaseIndex.coerceIn(0, run.phases.size - 1)]
    val shownWatts = if (run.paused) 0 else phase.watts
    val color by animateColorAsState(
        intensityColor(shownWatts), tween(300, easing = AppEasing), label = "phaseColor"
    )

    // Die Phase färbt nur den Streifen oben; Schrift und Fläche bleiben ruhig und gut lesbar
    Column(Modifier.fillMaxWidth().card()) {
      Box(Modifier.fillMaxWidth().height(8.dp).background(color))
      Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AnimatedContent(
            targetState = if (run.paused) "Pausiert" else phase.label,
            transitionSpec = {
                fadeIn(tween(160, easing = AppEasing)) togetherWith
                    fadeOut(tween(160, easing = AppEasing))
            },
            label = "phaseLabel",
        ) { label ->
            Text(
                label,
                fontSize = if (big) 36.sp else 24.sp,
                lineHeight = if (big) 44.sp else 30.sp,
                fontWeight = FontWeight.Medium,
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
                    fontSize = if (big) {
                        if (phase.watts == 0) 56.sp else 88.sp
                    } else {
                        if (phase.watts == 0) 32.sp else 44.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    style = Tnum,
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
                    fontSize = if (big) 88.sp else 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = Tnum,
                )
            }
        }
      }
    }
}

/** Gesamt-Restzeit, Fortschritt über den gesamten Ablauf, Vorschau auf die nächste Phase. */
@Composable
private fun ProgressPanel(run: RunUi, chartHeight: androidx.compose.ui.unit.Dp = 56.dp) {
    val fraction = if (run.totalSec > 0) run.elapsedSec.toFloat() / run.totalSec else 0f
    val animated by animateFloatAsState(fraction, label = "progress")
    val next = run.phases.getOrNull(run.phaseIndex + 1)

    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                fontWeight = FontWeight.Medium,
                style = Tnum,
            )
        }
        WorkoutChart(run.phases, Modifier.fillMaxWidth().height(chartHeight), progress = animated)
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Als Nächstes  ",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (next == null) {
                Text("Ende des Trainings", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            } else {
                IntensityDot(next.watts)
                Spacer(Modifier.width(8.dp))
                Text(
                    next.label + "  ·  " + (if (next.watts == 0) "Leerlauf" else "${next.watts} W") +
                        "  ·  " + formatMs(next.seconds),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = Tnum,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RunButtons(run: RunUi, big: Boolean, onPause: () -> Unit, onEnd: () -> Unit) {
    val height = if (big) 80.dp else 64.dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPause,
            modifier = Modifier.weight(1f).height(height),
        ) {
            if (run.paused) {
                IconLabel(AppIcons.Play, "Weiter", fontSize = 20, iconSize = 24)
            } else {
                IconLabel(AppIcons.Pause, "Pause", fontSize = 20, iconSize = 24)
            }
        }
        OutlinedButton(
            onClick = onEnd,
            modifier = Modifier.weight(1f).height(height),
        ) {
            Text(
                "Beenden",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
