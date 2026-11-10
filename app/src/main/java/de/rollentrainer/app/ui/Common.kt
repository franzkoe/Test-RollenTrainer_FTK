package de.rollentrainer.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.ble.BikeData
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.data.Phase
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Kopfzeile

/** Kopfzeile jeder Seite: gut sichtbare Zurück-Taste, Verbindungsstatus, Titel. */
@Composable
fun TopBar(title: String, conn: ConnState, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                FilledTonalButton(
                    onClick = onBack,
                    modifier = Modifier.height(54.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Text("←  Zurück", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            StatusChip(conn)
        }
        if (onBack != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusChip(conn: ConnState) {
    val target = when (conn) {
        ConnState.READY -> Color(0xFF34C759)
        ConnState.CONNECTING -> Color(0xFFFFCC00)
        ConnState.DISCONNECTED -> Color(0xFFFF453A)
    }
    val color by animateColorAsState(target, label = "status")
    val text = when (conn) {
        ConnState.READY -> "Trainer verbunden"
        ConnState.CONNECTING -> "Verbinde …"
        ConnState.DISCONNECTED -> "Nicht verbunden"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- Helfer

@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
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

/** Großer animierter Countdown: 3 – 2 – 1 – Start! (value 0 = „Start!“). */
@Composable
fun BigCountdown(value: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (scaleIn(initialScale = 2.2f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.4f) + fadeOut())
            },
            label = "countdown",
        ) { v ->
            Text(
                text = if (v <= 0) "Start!" else v.toString(),
                fontSize = if (v <= 0) 84.sp else 180.sp,
                fontWeight = FontWeight.Black,
                color = if (v <= 0) Color(0xFF34C759) else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- Live-Werte

data class Metric(val label: String, val value: String, val unit: String)

/** Baut die Kachelliste – nur Werte, die der Trainer tatsächlich liefert. */
fun buildMetrics(d: BikeData, baseDistance: Int, baseEnergy: Int): List<Metric> {
    val list = ArrayList<Metric>()
    d.powerW?.let { list.add(Metric("Leistung", it.toString(), "W")) }
    d.cadenceRpm?.let { list.add(Metric("Kadenz", it.roundToInt().toString(), "rpm")) }
    d.speedKmh?.let {
        list.add(Metric("Geschwindigkeit", String.format(Locale.GERMANY, "%.1f", it), "km/h"))
    }
    d.distanceM?.let {
        val m = if (it >= baseDistance) it - baseDistance else it
        list.add(Metric("Distanz", String.format(Locale.GERMANY, "%.2f", m / 1000.0), "km"))
    }
    d.energyKcal?.let {
        val k = if (it >= baseEnergy) it - baseEnergy else it
        list.add(Metric("Kalorien", k.toString(), "kcal"))
    }
    d.heartRate?.let { if (it > 0) list.add(Metric("Herzfrequenz", it.toString(), "bpm")) }
    return list
}

@Composable
fun MetricGrid(metrics: List<Metric>, columns: Int, compact: Boolean) {
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
                row.forEach { m -> MetricTile(m, Modifier.weight(1f), compact) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun MetricTile(m: Metric, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
    ) {
        Text(
            m.label,
            fontSize = if (compact) 12.sp else 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                m.value,
                fontSize = if (compact) 26.sp else 38.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                m.unit,
                fontSize = if (compact) 12.sp else 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = if (compact) 4.dp else 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Trainingsdiagramm

/** Balkendiagramm des Trainingsablaufs; [progress] (0..1) zeichnet eine Positionsmarke. */
@Composable
fun WorkoutChart(phases: List<Phase>, modifier: Modifier = Modifier, progress: Float? = null) {
    val markerColor = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val total = phases.sumOf { it.seconds }.toFloat()
        if (total <= 0f) return@Canvas
        val maxW = max(phases.maxOf { it.watts }, 100).toFloat()
        var x = 0f
        phases.forEach { p ->
            val w = size.width * p.seconds / total
            val h = size.height * (0.12f + 0.88f * p.watts / maxW)
            drawRect(
                color = intensityColor(p.watts),
                topLeft = Offset(x + 1f, size.height - h),
                size = Size(max(w - 2f, 1f), h),
            )
            x += w
        }
        if (progress != null) {
            val px = size.width * progress.coerceIn(0f, 1f)
            drawLine(
                color = markerColor,
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 3.dp.toPx(),
            )
        }
    }
}
