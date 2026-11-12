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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.data.ExportFormat
import de.rollentrainer.app.data.RideSummary
import de.rollentrainer.app.data.Sample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private fun formatDate(ms: Long): String =
    SimpleDateFormat("EEE, dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(ms)) + " Uhr"

private fun km(m: Double): String = String.format(Locale.GERMANY, "%.2f", m / 1000.0)

private fun one(d: Double): String = String.format(Locale.GERMANY, "%.1f", d)

/** Kacheln der Zusammenfassung – nur Werte, die aufgezeichnet wurden. */
fun summaryMetrics(s: RideSummary): List<Metric> {
    val list = ArrayList<Metric>()
    list.add(Metric("Dauer", formatMs(s.durationSec), ""))
    list.add(Metric("Strecke", km(s.distanceM), "km"))
    s.avgPower?.let { list.add(Metric("Ø Leistung", it.toString(), "W")) }
    s.maxPower?.let { list.add(Metric("Max. Leistung", it.toString(), "W")) }
    s.avgSpeed?.let { list.add(Metric("Ø Geschw.", one(it), "km/h")) }
    s.maxSpeed?.let { list.add(Metric("Max. Geschw.", one(it), "km/h")) }
    s.avgCadence?.let { list.add(Metric("Ø Kadenz", it.toString(), "rpm")) }
    s.avgHr?.let { list.add(Metric("Ø Puls", it.toString(), "bpm")) }
    s.maxHr?.let { list.add(Metric("Max. Puls", it.toString(), "bpm")) }
    s.kcal?.let { list.add(Metric("Kalorien", it.toString(), "kcal")) }
    return list
}

// ================================================================ Verlaufskurven

/** Verdichtet eine Messreihe auf höchstens [target] Punkte (Mittelwert je Abschnitt). */
private fun downsample(values: List<Float?>, target: Int): List<Float?> {
    if (values.size <= target) return values
    val out = ArrayList<Float?>(target)
    for (b in 0 until target) {
        val from = b * values.size / target
        val to = max((b + 1) * values.size / target, from + 1)
        var sum = 0f
        var n = 0
        for (i in from until to) {
            val v = values[i]
            if (v != null) {
                sum += v
                n++
            }
        }
        out.add(if (n == 0) null else sum / n)
    }
    return out
}

/** Verlaufskurve über die aktive Fahrzeit; Lücken (fehlende Werte) werden nicht verbunden. */
@Composable
fun LineChart(
    title: String,
    unit: String,
    values: List<Float?>,
    color: Color,
    durationSec: Int,
    decimals: Boolean = false,
) {
    val points = remember(values) { downsample(values, 360) }
    val present = points.filterNotNull()
    if (present.isEmpty()) return
    val maxV = max(present.max(), 1f)
    val avg = values.filterNotNull().average()
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    fun fmt(v: Double): String = if (decimals) one(v) else v.roundToInt().toString()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Ø ${fmt(avg)} · max. ${fmt(maxV.toDouble())} $unit",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val top = 4.dp.toPx()
            val h = size.height - top
            // Hilfslinien: Maximum, Mitte, Null
            listOf(top, top + h / 2f, size.height).forEach { y ->
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val n = points.size
            val path = Path()
            var pen = false
            points.forEachIndexed { i, v ->
                if (v == null) {
                    pen = false
                } else {
                    val x = if (n > 1) size.width * i / (n - 1) else 0f
                    val y = top + h * (1f - (v / maxV).coerceIn(0f, 1f))
                    if (pen) path.lineTo(x, y) else path.moveTo(x, y)
                    pen = true
                }
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
        }
        Row {
            Text("00:00", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                formatMs(durationSec),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Kurven für Leistung, Geschwindigkeit, Puls und Kadenz (jeweils nur, wenn Daten vorliegen). */
@Composable
fun RideCharts(s: RideSummary, samples: List<Sample>) {
    if (samples.isEmpty()) return
    LineChart(
        "Leistung", "W", samples.map { it.powerW?.toFloat() },
        Color(0xFFFF9500), s.durationSec,
    )
    LineChart(
        "Geschwindigkeit", "km/h", samples.map { it.speedKmh?.toFloat() },
        Color(0xFF4FD1C5), s.durationSec, decimals = true,
    )
    LineChart(
        "Puls", "bpm", samples.map { it.heartRate?.takeIf { v -> v > 0 }?.toFloat() },
        Color(0xFFFF453A), s.durationSec,
    )
    LineChart(
        "Kadenz", "rpm", samples.map { it.cadenceRpm?.toFloat() },
        Color(0xFF8AB4F8), s.durationSec,
    )
}

// ================================================================ Zusammenfassung nach der Fahrt

@Composable
fun SummaryScreen(vm: Engine) {
    val pending by vm.pending.collectAsState()
    val conn by vm.connState.collectAsState()
    var askDiscard by remember { mutableStateOf(false) }

    // Zurück verwirft nicht versehentlich: erst nachfragen
    BackHandler { askDiscard = true }

    val p = pending
    Column(Modifier.fillMaxSize()) {
        TopBar("Einheit beendet", conn)
        if (p != null) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(p.summary.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Möchtest du diese Fahrt speichern?",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MetricGrid(summaryMetrics(p.summary), columns = 2, compact = true)
                RideCharts(p.summary, p.samples)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { askDiscard = true },
                modifier = Modifier.weight(1f).height(68.dp),
            ) {
                Text(
                    "Verwerfen",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { vm.savePending() },
                modifier = Modifier.weight(1f).height(68.dp),
            ) { Text("Speichern", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Fahrt verwerfen?") },
            text = { Text("Die Aufzeichnung wird nicht gespeichert.", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        askDiscard = false
                        vm.discardPending()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false }) { Text("Abbrechen") }
            },
        )
    }
}

// ================================================================ Verlauf

@Composable
fun HistoryScreen(vm: Engine) {
    val rides by vm.rides.collectAsState()
    val conn by vm.connState.collectAsState()

    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Verlauf", conn, onBack = { vm.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rides.isEmpty()) {
                Text(
                    "Noch keine Fahrt gespeichert. Nach jeder Freien Fahrt und jedem Training " +
                        "kannst du die Aufzeichnung speichern.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rides.forEach { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.openRide(r) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        r.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatDate(r.startMs),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatMs(r.durationSec) + "  ·  " + km(r.distanceM) + " km" +
                            (r.avgPower?.let { "  ·  Ø $it W" } ?: ""),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ================================================================ Detailansicht

@Composable
fun RideDetailScreen(
    vm: Engine,
    onShare: (ExportFormat) -> Unit,
    onSave: (ExportFormat) -> Unit,
) {
    val detail by vm.detail.collectAsState()
    val conn by vm.connState.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    BackHandler { vm.back() }

    val d = detail
    Column(Modifier.fillMaxSize()) {
        TopBar(d?.summary?.name ?: "Fahrt", conn, onBack = { vm.back() })
        if (d != null) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    formatDate(d.summary.startMs),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MetricGrid(summaryMetrics(d.summary), columns = 2, compact = true)
                RideCharts(d.summary, d.samples)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRename = true },
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Umbenennen", maxLines = 1) }
                    OutlinedButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Löschen", maxLines = 1, color = MaterialTheme.colorScheme.error) }
                }
            }
            Button(
                onClick = { showExport = true },
                enabled = d.samples.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            ) {
                Text("Exportieren (FIT / TCX)", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Fahrt exportieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "FIT ist das gängige Format für Strava, Garmin Connect und " +
                            "intervals.icu. TCX ist die Alternative, falls ein Dienst FIT " +
                            "nicht annimmt.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            showExport = false
                            onShare(ExportFormat.FIT)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("FIT teilen …") }
                    FilledTonalButton(
                        onClick = {
                            showExport = false
                            onSave(ExportFormat.FIT)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("FIT als Datei speichern") }
                    Button(
                        onClick = {
                            showExport = false
                            onShare(ExportFormat.TCX)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("TCX teilen …") }
                    FilledTonalButton(
                        onClick = {
                            showExport = false
                            onSave(ExportFormat.TCX)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("TCX als Datei speichern") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExport = false }) { Text("Abbrechen") }
            },
        )
    }

    if (showRename && d != null) {
        var name by remember(d.summary.id) { mutableStateOf(d.summary.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Fahrt umbenennen") },
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
                    vm.renameRide(name)
                    showRename = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Abbrechen") }
            },
        )
    }

    if (showDelete && d != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Fahrt löschen?") },
            text = { Text("„${d.summary.name}“ wird dauerhaft gelöscht.", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showDelete = false
                        vm.deleteRide()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}
