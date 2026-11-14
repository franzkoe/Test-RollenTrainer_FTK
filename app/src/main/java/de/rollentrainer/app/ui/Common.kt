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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.ble.BikeData
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.data.LevelConfig
import androidx.compose.ui.platform.LocalConfiguration
import de.rollentrainer.app.data.Phase
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Gestaltungsbausteine

/** Radius für Bedienelemente (Buttons, Eingaben); Karten haben 16, Dialoge 20. */
val ControlShape: Shape = RoundedCornerShape(12.dp)

/** Zahlen mit Tabellenziffern, damit laufende Werte nicht springen. */
val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** Eine Kurve für alle Bewegungen. */
val AppEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Haarlinie: Textfarbe mit geringer Deckkraft. */
@Composable
fun hairline(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

/** Karte: ruhige Fläche mit Haarlinie statt Schatten. */
@Composable
fun Modifier.card(radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, hairline(), shape)
}

/** Hauptaktion: der Akzent. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ControlShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    contentPadding = contentPadding,
    content = content,
)

/** Zweitaktion: angehobene neutrale Fläche mit Haarlinie. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ControlShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.FilledTonalButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    border = BorderStroke(1.dp, hairline()),
    contentPadding = contentPadding,
    content = content,
)

/** Ruhige Aktion: nur Haarlinie. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ControlShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
    contentPadding = contentPadding,
    content = content,
)

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = ControlShape,
    content = content,
)

/** Symbol und Beschriftung mit festem Abstand, für Buttons. */
@Composable
fun IconLabel(icon: ImageVector, text: String, fontSize: Int = 18, iconSize: Int = 20) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, fontSize = fontSize.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

/** Kleiner Punkt in der Farbe der Intensitätsskala. */
@Composable
fun IntensityDot(watts: Int, size: Dp = 8.dp) {
    val color by animateColorAsState(intensityColor(watts), tween(160), label = "intensity")
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Leerer Zustand: mittig im freien Raum statt oben angeklebt. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- Kopfzeile

/** Kopfzeile jeder Seite: gut sichtbare Zurück-Taste, Verbindungsstatus, Titel. */
@Composable
fun TopBar(title: String, conn: ConnState, onBack: (() -> Unit)? = null) {
    val w = win()
    // Bei wenig Höhe (Handy quer) steht der Titel in der Zeile der Zurück-Taste
    val inline = w.short
    Column(Modifier.fillMaxWidth().padding(horizontal = w.gutter, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                FilledTonalButton(
                    onClick = onBack,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                ) {
                    IconLabel(AppIcons.Back, "Zurück", fontSize = 16)
                }
                if (inline) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        title,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                }
            } else {
                Text(title, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
            }
            if (!(inline && onBack != null)) Spacer(Modifier.weight(1f))
            StatusChip(conn)
        }
        if (onBack != null && !inline) {
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Verbindungsstatus des Trainers: Punkt und ruhiger Text. */
@Composable
fun StatusChip(conn: ConnState) {
    val target = when (conn) {
        ConnState.READY -> StatusPositive
        ConnState.CONNECTING -> StatusPending
        ConnState.DISCONNECTED -> StatusNegative
    }
    val color by animateColorAsState(target, tween(160), label = "status")
    val text = when (conn) {
        ConnState.READY -> "Trainer verbunden"
        ConnState.CONNECTING -> "Verbinde …"
        ConnState.DISCONNECTED -> "Nicht verbunden"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Helfer

@Composable
fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

fun formatHms(totalSec: Int): String {
    val s = max(totalSec, 0)
    return String.format(Locale.GERMANY, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun formatMs(totalSec: Int): String {
    val s = max(totalSec, 0)
    return if (s >= 3600) formatHms(s) else String.format(Locale.GERMANY, "%02d:%02d", s / 60, s % 60)
}

// ---------------------------------------------------------------- Countdown

/** Großer Countdown: 3 – 2 – 1 – Start (value 0 = „Start“). */
@Composable
fun BigCountdown(value: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(tween(300, easing = AppEasing)) +
                    scaleIn(tween(300, easing = AppEasing), initialScale = 0.92f)) togetherWith
                    fadeOut(tween(160, easing = AppEasing))
            },
            label = "countdown",
        ) { v ->
            Text(
                text = if (v <= 0) "Start" else v.toString(),
                fontSize = if (v <= 0) 72.sp else 160.sp,
                fontWeight = FontWeight.SemiBold,
                style = Tnum,
                color = if (v <= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- Live-Werte

data class Metric(val label: String, val value: String, val unit: String)

/**
 * Baut die Kachelliste – nur Werte, die tatsächlich vorliegen. Strecke und Kalorien rechnet die
 * App für die laufende Einheit selbst mit, der Puls kommt vom Pulsmesser oder vom Trainer.
 */
fun buildMetrics(d: BikeData, heartRate: Int?, distanceM: Double, kcal: Int?): List<Metric> {
    val list = ArrayList<Metric>()
    d.powerW?.let { list.add(Metric("Leistung", it.toString(), "W")) }
    d.cadenceRpm?.let { list.add(Metric("Kadenz", it.roundToInt().toString(), "rpm")) }
    d.speedKmh?.let {
        list.add(Metric("Geschwindigkeit", String.format(Locale.GERMANY, "%.1f", it), "km/h"))
    }
    if (d.speedKmh != null || d.distanceM != null) {
        list.add(Metric("Distanz", String.format(Locale.GERMANY, "%.2f", distanceM / 1000.0), "km"))
    }
    kcal?.let { list.add(Metric("Kalorien", it.toString(), "kcal")) }
    heartRate?.let { list.add(Metric("Herzfrequenz", it.toString(), "bpm")) }
    return list
}

@Composable
fun MetricGrid(metrics: List<Metric>, columns: Int, compact: Boolean, large: Boolean = false) {
    if (metrics.isEmpty()) {
        Text(
            "Warte auf Daten vom Trainer …",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m -> MetricTile(m, Modifier.weight(1f), compact, large) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Kennzahl: Bezeichnung, große Zahl, Einheit – sonst nichts. */
@Composable
fun MetricTile(
    m: Metric,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    large: Boolean = false,
) {
    Column(
        modifier = modifier
            .card()
            .padding(
                horizontal = if (large) 20.dp else if (compact) 12.dp else 16.dp,
                vertical = if (large) 20.dp else 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            // In der schmalen Kachel passt das lange Wort nicht
            if (compact && m.label == "Geschwindigkeit") "Geschw." else m.label,
            fontSize = if (large) 16.sp else 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                m.value,
                fontSize = if (large) 64.sp else if (compact) 24.sp else 36.sp,
                fontWeight = FontWeight.Medium,
                style = Tnum,
                maxLines = 1,
            )
            if (m.unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    m.unit,
                    fontSize = if (large) 18.sp else if (compact) 12.sp else 14.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        bottom = if (large) 12.dp else if (compact) 4.dp else 6.dp
                    ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Trainingsprofil

/**
 * Trainingsprofil: Balkenhöhe = Zielleistung, Farbe = Intensitätsskala.
 * [progress] (0..1) zeichnet die aktuelle Position ein.
 */
@Composable
fun WorkoutChart(phases: List<Phase>, modifier: Modifier = Modifier, progress: Float? = null) {
    val markerColor = MaterialTheme.colorScheme.onSurface
    val base = hairline()
    Canvas(modifier) {
        val total = phases.sumOf { it.seconds }.toFloat()
        if (total <= 0f) return@Canvas
        val maxW = max(phases.maxOf { it.watts }, 100).toFloat()
        val gap = 2.dp.toPx()
        var x = 0f
        phases.forEach { p ->
            val w = size.width * p.seconds / total
            val h = size.height * (0.10f + 0.90f * p.watts / maxW)
            drawRect(
                color = intensityColor(p.watts),
                topLeft = Offset(x, size.height - h),
                size = Size(max(w - gap, 1f), h),
            )
            x += w
        }
        drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (progress != null) {
            val px = size.width * progress.coerceIn(0f, 1f)
            drawLine(
                color = markerColor,
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

// ---------------------------------------------------------------- Stufenskala

/**
 * Die Widerstandsstufen als ansteigende Balken in den Farben der Intensitätsskala.
 * Stufen bis [level] sind farbig, die übrigen neutral.
 */
@Composable
fun LevelScale(level: Int, config: LevelConfig, height: Dp, modifier: Modifier = Modifier) {
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val n = config.levels
        val gap = (if (n > 24) 2 else 4).dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 1..n) {
            val h = size.height * (0.30f + 0.70f * i / n)
            drawRect(
                color = if (i <= level) intensityColor(config.watts(i)) else idle,
                topLeft = Offset((i - 1) * (w + gap), size.height - h),
                size = Size(w, h),
            )
        }
    }
}

// ---------------------------------------------------------------- Fenstergröße

/**
 * Fenstergröße in dp. Handy hochkant bleibt einspaltig; Querformat und Tablets nutzen die
 * Breite mit mehreren Spalten bzw. zwei Bereichen nebeneinander.
 */
data class Win(val w: Int, val h: Int) {
    /** Breiter als hoch: zwei Bereiche nebeneinander. */
    val landscape: Boolean get() = w > h

    /** Tablet: auch die kurze Seite ist groß – größere Zahlen und Bedienelemente. */
    val tablet: Boolean get() = minOf(w, h) >= 600

    /** Wenig Höhe (Handy quer): Kopfzeile einzeilig, dichter gesetzt. */
    val short: Boolean get() = h < 480

    /** Spaltenzahl für Kartenlisten. */
    val columns: Int
        get() = when {
            w >= 1100 -> 3
            w >= 600 -> 2
            else -> 1
        }

    /** Seitenrand. */
    val gutter: Dp get() = if (tablet) 24.dp else 16.dp
}

@Composable
fun win(): Win {
    val c = LocalConfiguration.current
    return Win(c.screenWidthDp, c.screenHeightDp)
}

/**
 * Karten in [columns] Spalten. Bei einer Spalte entstehen die Karten direkt untereinander in
 * der umgebenden Spalte (wie bisher auf dem Handy).
 */
@Composable
fun <T> CardGrid(
    items: List<T>,
    columns: Int,
    content: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    if (columns <= 1) {
        items.forEach { content(it, Modifier.fillMaxWidth()) }
    } else {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { content(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Aktionsleiste am unteren Rand. Auf breiten Fenstern rechtsbündig mit begrenzter Breite,
 * damit Buttons nicht über den ganzen Bildschirm laufen.
 */
@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    val w = win()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = w.gutter, vertical = if (w.short) 8.dp else 16.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            if (w.w >= 600) Modifier.width(480.dp) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
