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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.Screen
import de.rollentrainer.app.data.Change
import de.rollentrainer.app.data.DOWN
import de.rollentrainer.app.data.FEEL_NAMES
import de.rollentrainer.app.data.FitnessPoint
import de.rollentrainer.app.data.Recovery
import de.rollentrainer.app.data.RecoveryLevel
import de.rollentrainer.app.data.UP
import de.rollentrainer.app.data.Vo2
import de.rollentrainer.app.data.dayText
import de.rollentrainer.app.data.fitnessSeries
import de.rollentrainer.app.data.formText
import de.rollentrainer.app.data.improvement
import de.rollentrainer.app.data.recoveryStatus
import de.rollentrainer.app.data.verdictText
import java.util.Locale

private fun fmt(v: Double, decimals: Int = 0): String =
    String.format(Locale.GERMANY, "%.${decimals}f", v)

private fun signed(v: Double, decimals: Int = 0): String =
    String.format(Locale.GERMANY, "%+.${decimals}f", v)

private fun sleepText(min: Int): String =
    String.format(Locale.GERMANY, "%d:%02d h", min / 60, min % 60)

@Composable
private fun dirColor(dir: Int?): Color = when (dir) {
    UP -> StatusPositive
    DOWN -> StatusNegative
    null -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Dashboard: Wirst du besser? Dazu Erholung, VO2max, Stand des Trainingsplans und das Profil.
 * Das Profil ist dasselbe wie im Trainingsplan.
 */
@Composable
fun DashboardScreen(vm: Engine, onImportRecovery: () -> Unit) {
    val conn by vm.connState.collectAsState()
    val rides by vm.rides.collectAsState()
    val profile by vm.profile.collectAsState()
    val planState by vm.planState.collectAsState()
    val recoveryList by vm.recovery.collectAsState()
    val vo2 by vm.vo2.collectAsState()
    val importNote by vm.importNote.collectAsState()
    val w = win()
    val today = vm.today()
    BackHandler { vm.back() }

    val series = remember(rides, today) { fitnessSeries(rides, today) }
    val trend = remember(rides, today) { improvement(rides, today) }
    val recovery = remember(recoveryList, today) { recoveryStatus(recoveryList, today) }

    val analysis: @Composable () -> Unit = {
        PlanCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Wirst du besser?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(dirColor(trend.verdict), 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        verdictText(trend.verdict),
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            FitnessChart(series, Modifier.fillMaxWidth().height(if (w.tablet) 220.dp else 140.dp))
            Row {
                Text(
                    "vor 12 Wochen",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Text("heute", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
            val last = series.last()
            if (series.any { it.fitness > 0.0 }) {
                Text(
                    "Fitness ${fmt(last.fitness)} · Ermüdung ${fmt(last.fatigue)} · " +
                        "Form ${signed(last.form)} (${formText(last.form)})",
                    fontSize = 15.sp,
                    style = Tnum,
                )
            }
            Hint(
                "Fitness ist deine durchschnittliche Belastung der letzten sechs Wochen " +
                    "(kräftige Linie), Ermüdung die der letzten Woche (dünne Linie). Form ist " +
                    "die Differenz: im Minus baust du auf, im Plus bist du frisch."
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendTile(
                "Fitness", trend.fitness?.let { fmt(it.now) }, "",
                trend.fitness?.let { signed(it.diff) + " seit 4 Wochen" },
                trend.fitnessDir(), Modifier.weight(1f),
            )
            TrendTile(
                "Watt pro Puls", trend.efficiency?.let { fmt(it.now, 2) }, "",
                trend.efficiency?.let { signed(it.pct) + " % zu den 4 Wochen davor" },
                trend.efficiencyDir(), Modifier.weight(1f),
            )
            TrendTile(
                "Beste 20 min", trend.best20?.let { fmt(it.now) }, "W",
                trend.best20?.let { signed(it.diff) + " W zu den 6 Wochen davor" },
                trend.best20Dir(), Modifier.weight(1f),
            )
        }
        val counted = rides.count { it.analysis }
        Hint(
            if (counted == 0) {
                "Sobald Fahrten gespeichert sind, erscheint hier die Auswertung. Für die " +
                    "Belastung braucht die App deine FTP oder einen Pulsmesser."
            } else {
                "Grundlage: $counted " + (if (counted == 1) "Fahrt" else "Fahrten") +
                    ", die für die Analyse freigegeben sind. Watt pro Puls vergleicht Fahrten " +
                    "ab 20 Minuten mit Leistung und Puls. Steigt der Wert, leistest du bei " +
                    "gleichem Puls mehr."
            }
        )
        val st = planState
        val next = st?.next()
        LinkRow(
            AppIcons.Calendar, "Mein Trainingsplan",
            when {
                st == null -> "Kein Plan aktiv"
                next == null -> st.plan.spec.name + " abgeschlossen"
                else -> "Woche ${next.first + 1} von ${st.plan.weeks.size} · " +
                    "${st.done.size} von ${st.plan.total} Einheiten · als Nächstes " +
                    st.plan.weeks[next.first][next.second].name
            },
        ) { vm.open(Screen.PLAN) }
    }

    val body: @Composable () -> Unit = {
        RecoveryCard(
            status = recovery,
            entries = recoveryList,
            today = today,
            importNote = importNote,
            onSave = { vm.saveRecovery(it) },
            onImport = onImportRecovery,
            onDismissNote = { vm.dismissImportNote() },
        )
        Vo2Card(vo2, today, onSave = { vm.saveVo2(it) }, onDelete = { vm.deleteVo2(it) })
        LinkRow(AppIcons.User, "Dein Profil", profileLine(profile)) { vm.open(Screen.PLAN_PROFILE) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Dashboard", conn, onBack = { vm.back() })
        Panes(w, Modifier.weight(1f), analysis, body)
    }
}

/** Kennzahl mit Veränderung zum Vergleichszeitraum. */
@Composable
private fun TrendTile(
    label: String,
    value: String?,
    unit: String,
    change: String?,
    dir: Int?,
    modifier: Modifier,
) {
    Column(
        modifier.card(12.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "–",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                style = Tnum,
                maxLines = 1,
            )
            if (value != null && unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dir != null) {
                ColorDot(dirColor(dir), 6.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                change ?: "zu wenig Daten",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fitness (kräftig, Akzent) und Ermüdung (dünn, neutral) über zwölf Wochen. */
@Composable
private fun FitnessChart(series: List<FitnessPoint>, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier) {
        if (series.size < 2) return@Canvas
        val max = (series.maxOf { maxOf(it.fitness, it.fatigue) } * 1.1).coerceAtLeast(10.0).toFloat()
        val stepX = size.width / (series.size - 1)
        fun y(v: Double) = size.height - (v.toFloat() / max) * size.height
        for (i in 0..2) {
            val gy = size.height * i / 2f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
        }
        fun path(value: (FitnessPoint) -> Double): Path {
            val p = Path()
            series.forEachIndexed { i, pt ->
                if (i == 0) p.moveTo(0f, y(value(pt))) else p.lineTo(i * stepX, y(value(pt)))
            }
            return p
        }
        drawPath(
            path { it.fatigue }, neutral,
            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path { it.fitness }, accent,
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(accent, 4.dp.toPx(), Offset(size.width, y(series.last().fitness)))
    }
}

// ---------------------------------------------------------------- Erholung

@Composable
private fun RecoveryCard(
    status: de.rollentrainer.app.data.RecoveryStatus,
    entries: List<Recovery>,
    today: Long,
    importNote: String?,
    onSave: (Recovery) -> Unit,
    onImport: () -> Unit,
    onDismissNote: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(Recovery(today)) }

    PlanCard("Erholung") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(
                when (status.level) {
                    RecoveryLevel.GOOD -> StatusPositive
                    RecoveryLevel.MEDIUM -> StatusPending
                    RecoveryLevel.LOW -> StatusNegative
                    RecoveryLevel.UNKNOWN -> MaterialTheme.colorScheme.outline
                },
                10.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(status.level.label, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        if (status.reasons.isNotEmpty()) {
            Hint(status.reasons.joinToString(", ").replaceFirstChar { it.uppercase() })
        } else if (status.level == RecoveryLevel.UNKNOWN) {
            Hint(
                "Trage morgens Ruhepuls, HRV, Schlaf oder dein Gefühl ein. Die App vergleicht " +
                    "sie mit deinem Schnitt der letzten vier Wochen und empfiehlt im " +
                    "Trainingsplan bei schwachen Werten die leichtere Einheit."
            )
        }
        if (status.baseHrv != null || status.baseRestHr != null) {
            Text(
                "Dein Schnitt: " + listOfNotNull(
                    status.baseRestHr?.let { "Ruhepuls $it" },
                    status.baseHrv?.let { "HRV $it ms" },
                ).joinToString(" · "),
                fontSize = 14.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (editing) {
            fun step(v: Int?, d: Int, start: Int, min: Int, max: Int): Int? = when {
                v == null -> if (d > 0) start else null
                v + d < min -> null
                else -> (v + d).coerceAtMost(max)
            }
            Stepper(
                label = "Ruhepuls",
                valueText = draft.restHr?.toString() ?: "–",
                onMinus = { draft = draft.copy(restHr = step(draft.restHr, -1, status.baseRestHr ?: 55, 30, 120)) },
                onPlus = { draft = draft.copy(restHr = step(draft.restHr, 1, status.baseRestHr ?: 55, 30, 120)) },
            )
            Stepper(
                label = "HRV",
                valueText = draft.hrv?.let { "$it ms" } ?: "–",
                onMinus = { draft = draft.copy(hrv = step(draft.hrv, -1, status.baseHrv ?: 50, 5, 250)) },
                onPlus = { draft = draft.copy(hrv = step(draft.hrv, 1, status.baseHrv ?: 50, 5, 250)) },
            )
            Stepper(
                label = "Schlaf",
                valueText = draft.sleepMin?.let { sleepText(it) } ?: "–",
                onMinus = { draft = draft.copy(sleepMin = step(draft.sleepMin, -15, 420, 60, 840)) },
                onPlus = { draft = draft.copy(sleepMin = step(draft.sleepMin, 15, 420, 60, 840)) },
            )
            Text(
                "Gefühl: " + (draft.feel?.let { FEEL_NAMES[it - 1] } ?: "keine Angabe"),
                fontSize = 16.sp,
            )
            Choice(listOf("1", "2", "3", "4", "5"), (draft.feel ?: 0) - 1) {
                draft = draft.copy(feel = if (draft.feel == it + 1) null else it + 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { editing = false },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Abbrechen", fontSize = 16.sp) }
                Button(
                    onClick = {
                        onSave(draft.copy(day = today))
                        editing = false
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Speichern", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        draft = status.entry ?: Recovery(today)
                        editing = true
                    },
                    modifier = Modifier.weight(1.4f).height(48.dp),
                ) {
                    Text(
                        if (status.entry == null) "Werte für heute" else "Heute ändern",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Importieren", fontSize = 16.sp) }
            }
            if (importNote != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        importNote,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissNote) {
                        Text("OK", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Hint(
                    "Import: CSV-Datei mit den Spalten Datum, Ruhepuls, HRV, Schlaf, Gefühl, " +
                        "zum Beispiel ein Export aus deiner Uhren- oder Schlaf-App."
                )
            }
        }

        val shown = entries.take(5)
        if (shown.isNotEmpty() && !editing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { e ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (e.day == today) "Heute" else dayText(e.day),
                            fontSize = 14.sp,
                            style = Tnum,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(88.dp),
                        )
                        Text(
                            listOfNotNull(
                                e.restHr?.let { "Puls $it" },
                                e.hrv?.let { "HRV $it" },
                                e.sleepMin?.let { sleepText(it) },
                                e.feel?.let { FEEL_NAMES[it - 1] },
                            ).joinToString(" · "),
                            fontSize = 14.sp,
                            style = Tnum,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- VO2max

@Composable
private fun Vo2Card(list: List<Vo2>, today: Long, onSave: (Double) -> Unit, onDelete: (Long) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(45.0) }
    val latest = list.firstOrNull()
    val oldest = list.lastOrNull()

    PlanCard("VO2max") {
        if (latest == null) {
            Hint(
                "Trage hier die VO2max-Schätzung deiner Sportuhr oder aus einem Test ein. Mit " +
                    "mehreren Einträgen siehst du, wie sie sich entwickelt."
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    fmt(latest.value, 1),
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium,
                    style = Tnum,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "ml/kg/min",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp).weight(1f),
                )
                if (oldest != null && oldest.day != latest.day) {
                    val change = Change(latest.value, oldest.value)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 5.dp),
                    ) {
                        ColorDot(
                            dirColor(if (change.diff > 0.4) UP else if (change.diff < -0.4) DOWN else 0),
                            6.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            signed(change.diff, 1) + " seit " + dayText(oldest.day),
                            fontSize = 14.sp,
                            style = Tnum,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (list.size >= 2) Vo2Chart(list, Modifier.fillMaxWidth().height(56.dp))
        }

        if (editing) {
            Stepper(
                label = "Wert",
                valueText = fmt(draft, 1),
                onMinus = { draft = (draft - 0.5).coerceAtLeast(15.0) },
                onPlus = { draft = (draft + 0.5).coerceAtMost(95.0) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { editing = false },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Abbrechen", fontSize = 16.sp) }
                Button(
                    onClick = {
                        onSave(draft)
                        editing = false
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Speichern", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        } else {
            FilledTonalButton(
                onClick = {
                    draft = latest?.value ?: 45.0
                    editing = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(
                    if (latest?.day == today) "Heutigen Wert ändern" else "Wert eintragen",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            list.take(4).forEach { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (e.day == today) "Heute" else dayText(e.day),
                        fontSize = 14.sp,
                        style = Tnum,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(88.dp),
                    )
                    Text(fmt(e.value, 1), fontSize = 14.sp, style = Tnum, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDelete(e.day) }) {
                        Text("Löschen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Verlauf der eingetragenen Werte, ältester links. */
@Composable
private fun Vo2Chart(list: List<Vo2>, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val pts = list.sortedBy { it.day }
        val minV = pts.minOf { it.value }
        val maxV = pts.maxOf { it.value }
        val span = (maxV - minV).coerceAtLeast(1.0)
        val d0 = pts.first().day
        val days = (pts.last().day - d0).coerceAtLeast(1)
        val pad = 4.dp.toPx()
        fun x(p: Vo2) = pad + (size.width - 2 * pad) * (p.day - d0) / days
        fun y(p: Vo2) = size.height - pad - ((p.value - minV) / span).toFloat() * (size.height - 2 * pad)
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p)) else path.lineTo(x(p), y(p)) }
        drawPath(path, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEach { drawCircle(accent, 3.dp.toPx(), Offset(x(it), y(it))) }
    }
}
