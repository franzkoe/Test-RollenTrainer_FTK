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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.Engine
import de.rollentrainer.app.Screen
import de.rollentrainer.app.data.LEVEL_NAMES
import de.rollentrainer.app.data.PRESET_PLANS
import de.rollentrainer.app.data.Plan
import de.rollentrainer.app.data.PlanSpec
import de.rollentrainer.app.data.PlanState
import de.rollentrainer.app.data.Profile
import de.rollentrainer.app.data.ST_ALT
import de.rollentrainer.app.data.ST_DONE
import de.rollentrainer.app.data.Session
import de.rollentrainer.app.data.ZONE_PCT_FROM
import de.rollentrainer.app.data.alternativeFor
import de.rollentrainer.app.data.generatePlan
import de.rollentrainer.app.data.hrZonesFromMax
import de.rollentrainer.app.data.isRecoveryWeek
import de.rollentrainer.app.data.round5
import de.rollentrainer.app.data.zoneName
import de.rollentrainer.app.data.zoneOfPct
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ================================================================ Bausteine

private fun shortDate(ms: Long): String =
    SimpleDateFormat("d. MMM", Locale.GERMANY).format(Date(ms))

private fun hours(seconds: Int): String =
    String.format(Locale.GERMANY, "%d:%02d", seconds / 3600, (seconds % 3600) / 60)

/** Höchste Zone, in der die Einheit mindestens zwei Minuten verbringt. */
private fun sessionZone(s: Session): Int {
    val secs = IntArray(5)
    s.segs.forEach { secs[zoneOfPct(it.pct) - 1] += it.seconds }
    for (z in 4 downTo 0) if (secs[z] >= 120) return z + 1
    return 1
}

/** Womit die Einheit gesteuert wird: Watt aus der FTP oder Pulszone. */
private fun targetLine(s: Session, profile: Profile): String {
    val zone = sessionZone(s)
    val top = s.segs.filter { zoneOfPct(it.pct) == zone }.maxOfOrNull { it.pct } ?: 50
    return if (profile.power) {
        "Bis ${round5(profile.ftp * top / 100.0)} W, der Widerstand wird vorgegeben"
    } else {
        "Bis Zone $zone (Puls ${profile.zoneText(zone)}), Widerstand stellst du selbst"
    }
}

/** Zwei Bereiche: quer nebeneinander, hochkant untereinander in einer Spalte. */
@Composable
private fun Panes(
    w: Win,
    modifier: Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    if (w.landscape) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = w.gutter),
            horizontalArrangement = Arrangement.spacedBy(if (w.tablet) 24.dp else 12.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { first() }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { second() }
        }
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = w.gutter)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            first()
            second()
        }
    }
}

@Composable
private fun PlanCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Auswahl aus wenigen Möglichkeiten: die gewählte ist gefüllt, die anderen nur umrandet. */
@Composable
private fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val modifier = Modifier.weight(1f).height(48.dp)
            if (i == selected) {
                Button(
                    onClick = { onSelect(i) },
                    modifier = modifier,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) { Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(i) },
                    modifier = modifier,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                ) { Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** Zeile, die auf eine Unterseite führt. */
@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun profileLine(p: Profile): String =
    if (p.power) {
        "FTP ${p.ftp} W · der Widerstand wird vorgegeben"
    } else {
        "Training nach Puls · Zonen bis " + p.zoneMax.joinToString(" / ")
    }

/**
 * Umfang je Woche als Balken. Ohne [state] zeigt jeder Balken die Zeit je Zone (so sieht man,
 * wann der Plan härter wird). Mit [state] ist die gefahrene Zeit gefüllt und die laufende Woche
 * unterstrichen.
 */
@Composable
fun WeekBars(plan: Plan, modifier: Modifier = Modifier, state: PlanState? = null) {
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accent = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.onSurface
    val current = state?.next()?.first
    Canvas(modifier) {
        val n = plan.weeks.size
        if (n == 0) return@Canvas
        val gap = (if (n > 10) 3 else 6).dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val minutes = IntArray(n) { plan.weekMinutes(it) }
        val maxMin = (minutes.maxOrNull() ?: 1).coerceAtLeast(1)
        val foot = if (state != null) 6.dp.toPx() else 0f
        val area = size.height - foot
        for (w in 0 until n) {
            val x = w * (bw + gap)
            val h = area * minutes[w] / maxMin
            if (state == null) {
                val secs = IntArray(5)
                plan.weeks[w].forEach { s -> s.segs.forEach { secs[zoneOfPct(it.pct) - 1] += it.seconds } }
                val total = secs.sum().coerceAtLeast(1)
                var y = area
                for (z in 0 until 5) {
                    val part = h * secs[z] / total
                    if (part > 0f) {
                        drawRect(zoneColor(z + 1), Offset(x, y - part), Size(bw, part))
                        y -= part
                    }
                }
            } else {
                drawRect(idle, Offset(x, area - h), Size(bw, h))
                val frac = (state.riddenMinutes(w).toFloat() / minutes[w].coerceAtLeast(1)).coerceIn(0f, 1f)
                if (frac > 0f) drawRect(accent, Offset(x, area - h * frac), Size(bw, h * frac))
                if (w == current) {
                    drawRect(marker, Offset(x, size.height - 2.dp.toPx()), Size(bw, 2.dp.toPx()))
                }
            }
        }
    }
}

/** Eine Einheit als Zeile: Zonenfarbe, Name, Dauer und rechts ein Zusatz (Stand). */
@Composable
private fun SessionLine(s: Session, trailing: String, dimmed: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(zoneColor(sessionZone(s)))
        Spacer(Modifier.width(12.dp))
        Text(
            s.name,
            fontSize = 15.sp,
            style = Tnum,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            trailing,
            fontSize = 14.sp,
            style = Tnum,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ================================================================ Einstieg

@Composable
fun PlanScreen(vm: Engine) {
    val state by vm.planState.collectAsState()
    val st = state
    if (st == null) PlanChooserScreen(vm) else PlanDashboardScreen(vm, st)
}

/** Kein Plan aktiv: Profil, fertige Pläne zur Auswahl, eigener Plan. */
@Composable
private fun PlanChooserScreen(vm: Engine) {
    val conn by vm.connState.collectAsState()
    val profile by vm.profile.collectAsState()
    val w = win()
    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Mein Trainingsplan", conn, onBack = { vm.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = w.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinkRow(AppIcons.User, "Dein Profil", profileLine(profile)) {
                vm.open(Screen.PLAN_PROFILE)
            }
            Text(
                "Fertige Pläne",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            CardGrid(PRESET_PLANS, w.columns) { spec, modifier ->
                PresetCard(modifier, spec) { vm.startPlan(spec) }
            }
        }
        BottomBar {
            Button(
                onClick = { vm.open(Screen.PLAN_NEW) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { IconLabel(AppIcons.Plus, "Eigenen Plan erstellen") }
        }
    }
}

@Composable
private fun PresetCard(modifier: Modifier, spec: PlanSpec, onStart: () -> Unit) {
    val plan = remember(spec) { generatePlan(spec) }
    Column(
        modifier.card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(spec.name, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            Text(
                LEVEL_NAMES[spec.level] + " · " + spec.summary(),
                fontSize = 14.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WeekBars(plan, Modifier.fillMaxWidth().height(48.dp))
        Hint(spec.text)
        FilledTonalButton(onClick = onStart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Diesen Plan starten", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ================================================================ Eigener Plan

private val LEVEL_TEXTS = listOf(
    "Du fährst noch nicht regelmäßig. Tempo und Sweet Spot, zum Schluss kurze Abschnitte an der Schwelle.",
    "Du fährst regelmäßig. Sweet Spot und Schwelle, im letzten Drittel kommt VO2max dazu.",
    "Du trainierst seit Längerem hart. Schwelle, Über-Unter und VO2max von Beginn an.",
)

@Composable
fun PlanNewScreen(vm: Engine) {
    val conn by vm.connState.collectAsState()
    val w = win()
    var spec by remember { mutableStateOf(PlanSpec("Eigener Plan", 1, 3, 45, 8, 5)) }
    val plan = remember(spec) { generatePlan(spec) }
    BackHandler { vm.back() }

    val controls: @Composable () -> Unit = {
        PlanCard("Wie fortgeschritten bist du?") {
            Choice(LEVEL_NAMES, spec.level) { spec = spec.copy(level = it) }
            Hint(LEVEL_TEXTS[spec.level])
        }
        PlanCard("Wie oft und wie lange?") {
            Stepper(
                label = "Pro Woche",
                valueText = spec.perWeek.toString(),
                onMinus = { spec = spec.copy(perWeek = (spec.perWeek - 1).coerceAtLeast(PlanSpec.MIN_PER_WEEK)) },
                onPlus = { spec = spec.copy(perWeek = (spec.perWeek + 1).coerceAtMost(PlanSpec.MAX_PER_WEEK)) },
            )
            Stepper(
                label = "Je Einheit",
                valueText = "${spec.minutes} min",
                onMinus = { spec = spec.copy(minutes = (spec.minutes - 5).coerceAtLeast(PlanSpec.MIN_MINUTES)) },
                onPlus = { spec = spec.copy(minutes = (spec.minutes + 5).coerceAtMost(PlanSpec.MAX_MINUTES)) },
            )
            Stepper(
                label = "Länge",
                valueText = "${spec.weeks} Wochen",
                onMinus = { spec = spec.copy(weeks = (spec.weeks - 1).coerceAtLeast(PlanSpec.MIN_WEEKS)) },
                onPlus = { spec = spec.copy(weeks = (spec.weeks + 1).coerceAtMost(PlanSpec.MAX_WEEKS)) },
            )
        }
        PlanCard("Wie stark willst du dich steigern?") {
            Stepper(
                label = "Pro Woche",
                valueText = "${spec.stepPct} %",
                onMinus = { spec = spec.copy(stepPct = (spec.stepPct - 1).coerceAtLeast(0)) },
                onPlus = { spec = spec.copy(stepPct = (spec.stepPct + 1).coerceAtMost(PlanSpec.MAX_STEP)) },
            )
            Hint(
                "Um so viel werden die Einheiten von Woche zu Woche länger. Üblich sind etwa " +
                    "5 %, mehr als 10 % gelten als zu viel. Jede vierte Woche ist kürzer und " +
                    "locker, damit sich der Körper anpassen kann."
            )
        }
    }

    val preview: @Composable () -> Unit = {
        PlanCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "So sieht dein Plan aus",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${plan.total} Einheiten · " +
                        hours(plan.weeks.sumOf { l -> l.sumOf { it.seconds } }) + " h",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = Tnum,
                )
            }
            WeekBars(plan, Modifier.fillMaxWidth().height(if (w.tablet) 120.dp else 72.dp))
            Row {
                Text(
                    "Woche 1: ${plan.weekMinutes(0)} min",
                    fontSize = 13.sp,
                    style = Tnum,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "längste Woche: ${plan.weeks.indices.maxOf { plan.weekMinutes(it) }} min",
                    fontSize = 13.sp,
                    style = Tnum,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlanCard("Erste Woche") {
            plan.weeks[0].forEach { SessionLine(it, "${it.minutes} min") }
        }
        if (plan.weeks.size > 1) {
            PlanCard("Letzte Belastungswoche") {
                val last = plan.weeks.indices.last { !isRecoveryWeek(it) }
                plan.weeks[last].forEach { SessionLine(it, "${it.minutes} min") }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Eigener Plan", conn, onBack = { vm.back() })
        Panes(w, Modifier.weight(1f), controls, preview)
        BottomBar {
            Button(
                onClick = { vm.startPlan(spec) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { IconLabel(AppIcons.Play, "Plan starten") }
        }
    }
}

// ================================================================ Profil

@Composable
fun PlanProfileScreen(vm: Engine) {
    val conn by vm.connState.collectAsState()
    val profile by vm.profile.collectAsState()
    val w = win()
    // Maximalpuls nur als Rechenhilfe; Startwert passend zu den gespeicherten Zonen
    var maxHr by remember { mutableStateOf((profile.zoneMax[3] / 0.9).roundToInt()) }
    BackHandler { vm.back() }

    val power: @Composable () -> Unit = {
        PlanCard("Wonach willst du trainieren?") {
            Choice(listOf("Nach FTP (Watt)", "Nach Puls"), if (profile.byHr) 1 else 0) {
                vm.setProfile(profile.copy(byHr = it == 1))
            }
            Hint(
                if (profile.byHr) {
                    "Die App zeigt in jedem Abschnitt die Zielzone. Den Widerstand stellst du " +
                        "über die Stufen selbst, bis der Puls in der Zone liegt."
                } else if (profile.ftp > 0) {
                    "Die App stellt den Widerstand in jedem Abschnitt selbst ein, berechnet " +
                        "aus deiner FTP."
                } else {
                    "Trage unten deine FTP ein. Solange sie fehlt, wird nach Puls trainiert."
                }
            )
        }
        PlanCard("Schwellenleistung (FTP)") {
            Stepper(
                label = "FTP",
                valueText = if (profile.ftp == 0) "keine" else "${profile.ftp} W",
                onMinus = {
                    val next = profile.ftp - 5
                    vm.setProfile(profile.copy(ftp = if (next < Profile.FTP_MIN) 0 else next))
                },
                onPlus = {
                    val next = if (profile.ftp == 0) 150 else profile.ftp + 5
                    vm.setProfile(profile.copy(ftp = next.coerceAtMost(Profile.FTP_MAX)))
                },
            )
            if (profile.ftp > 0) {
                Slider(
                    value = profile.ftp.toFloat(),
                    onValueChange = {
                        vm.setProfile(
                            profile.copy(
                                ftp = ((it / 5f).roundToInt() * 5)
                                    .coerceIn(Profile.FTP_MIN, Profile.FTP_MAX)
                            )
                        )
                    },
                    valueRange = Profile.FTP_MIN.toFloat()..Profile.FTP_MAX.toFloat(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (z in 1..5) {
                        // Obergrenze gerundet, die nächste Zone beginnt ein Watt darüber
                        fun top(zone: Int) = (profile.ftp * (ZONE_PCT_FROM[zone] - 1) / 100.0).roundToInt()
                        val from = if (z == 1) 0 else top(z - 1) + 1
                        val to = if (z < 5) top(z) else null
                        ZoneRow(
                            z,
                            when {
                                z == 1 -> "bis $to W"
                                to == null -> "ab $from W"
                                else -> "$from–$to W"
                            },
                        )
                    }
                }
            }
            Hint(
                "Die FTP ist die Leistung, die du etwa eine Stunde lang halten kannst. " +
                    "Faustregel: nach dem Aufwärmen 20 Minuten so hart wie möglich fahren und " +
                    "95 % der Durchschnittsleistung nehmen. Alle 6 bis 8 Wochen neu testen."
            )
        }
    }

    val pulse: @Composable () -> Unit = {
        PlanCard("Pulszonen") {
            Stepper(
                label = "Maximalpuls",
                valueText = maxHr.toString(),
                onMinus = { maxHr = (maxHr - 1).coerceAtLeast(120) },
                onPlus = { maxHr = (maxHr + 1).coerceAtMost(Profile.HR_MAX) },
            )
            FilledTonalButton(
                onClick = { vm.setProfile(profile.copy(zoneMax = hrZonesFromMax(maxHr))) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Zonen aus dem Maximalpuls berechnen", fontSize = 15.sp) }
            Hint(
                "Die Zonen enden bei 60, 70, 80 und 90 % des Maximalpulses. Kennst du deine " +
                    "Zonen genauer, stelle die Grenzen unten selbst ein."
            )
        }
        PlanCard("Obergrenzen der Zonen") {
            for (i in 0 until 4) {
                val lower = if (i == 0) Profile.HR_MIN else profile.zoneMax[i - 1] + 1
                val upper = if (i == 3) Profile.HR_MAX else profile.zoneMax[i + 1] - 1
                fun set(v: Int) {
                    val list = profile.zoneMax.toMutableList()
                    list[i] = v.coerceIn(lower, upper)
                    vm.setProfile(profile.copy(zoneMax = list))
                }
                Stepper(
                    label = "Zone ${i + 1} bis",
                    valueText = profile.zoneMax[i].toString(),
                    onMinus = { set(profile.zoneMax[i] - 1) },
                    onPlus = { set(profile.zoneMax[i] + 1) },
                )
            }
            ZoneRow(5, "ab ${profile.zoneMax[3] + 1}")
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Dein Profil", conn, onBack = { vm.back() })
        Panes(w, Modifier.weight(1f), power, pulse)
    }
}

@Composable
private fun ZoneRow(zone: Int, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(zoneColor(zone))
        Spacer(Modifier.width(12.dp))
        Text(
            "Zone $zone · ${zoneName(zone)}",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, fontSize = 15.sp, style = Tnum)
    }
}

// ================================================================ Dashboard

/** Plan aktiv: Stand und Fortschritt, nächste Einheit, leichtere Alternative, laufende Woche. */
@Composable
private fun PlanDashboardScreen(vm: Engine, st: PlanState) {
    val conn by vm.connState.collectAsState()
    val profile by vm.profile.collectAsState()
    val w = win()
    var askEnd by remember { mutableStateOf(false) }
    BackHandler { vm.back() }

    val plan = st.plan
    val next = st.next()
    val week = next?.first ?: (plan.weeks.size - 1)

    val status: @Composable () -> Unit = {
        PlanCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    plan.spec.name + " · " + LEVEL_NAMES[plan.spec.level.coerceIn(0, 2)],
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (next == null) "Plan abgeschlossen" else "Woche ${week + 1} von ${plan.weeks.size}",
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium,
                        style = Tnum,
                        modifier = Modifier.weight(1f),
                    )
                    if (next != null && isRecoveryWeek(week)) {
                        Text(
                            "Entlastung",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { st.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            Text(
                "${st.done.size} von ${plan.total} Einheiten abgearbeitet · seit " + shortDate(st.startMs),
                fontSize = 14.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WeekBars(plan, Modifier.fillMaxWidth().height(if (w.tablet) 112.dp else 64.dp), st)
            Text(
                "Umfang je Woche. Gefüllt ist, was du gefahren bist.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    val details: @Composable () -> Unit = {
        MetricGrid(
            listOf(
                Metric("Gefahren", st.ridden.toString(), "von ${plan.total}"),
                Metric("Trainingszeit", hours(st.riddenSeconds), "h"),
                Metric("Ausgelassen", st.skipped.toString(), ""),
            ),
            columns = 3,
            compact = true,
        )
        PlanCard(if (next == null) "Letzte Woche" else "Diese Woche") {
            plan.weeks[week].forEachIndexed { i, s ->
                val d = st.entry(week, i)
                SessionLine(
                    s,
                    trailing = when {
                        d == null && next?.second == i -> "als Nächstes"
                        d == null -> "${s.minutes} min"
                        d.status == ST_DONE -> "erledigt " + shortDate(d.dateMs)
                        d.status == ST_ALT -> "ersetzt " + shortDate(d.dateMs)
                        else -> "ausgelassen"
                    },
                    dimmed = d != null,
                )
            }
        }
    }

    val action: @Composable () -> Unit = {
        if (next == null) {
            PlanCard("Geschafft") {
                Hint(
                    "Du hast ${st.ridden} von ${plan.total} Einheiten gefahren, zusammen " +
                        hours(st.riddenSeconds) + " Stunden. Gönn dir ein paar lockere Tage, " +
                        "bevor du den nächsten Plan beginnst."
                )
                Button(
                    onClick = { vm.endPlan() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Neuen Plan wählen", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
            }
        } else {
            val s = plan.weeks[next.first][next.second]
            val alt = remember(s) { alternativeFor(s) }
            NextCard(s, next, plan, profile, w.tablet, vm)
            PlanCard("Nicht gut erholt?") {
                Hint(
                    if (st.hardRecently(System.currentTimeMillis())) {
                        "Deine letzte harte Einheit ist weniger als 36 Stunden her. Wenn die " +
                            "Beine noch schwer sind, fahr heute lieber das hier. Die Einheit " +
                            "zählt trotzdem für den Plan."
                    } else {
                        "Schwere Beine, schlecht geschlafen oder gerade krank gewesen? Dann " +
                            "fahr statt der geplanten Einheit das hier. Sie zählt trotzdem für " +
                            "den Plan."
                    }
                )
                SessionLine(alt, alt.kind.label)
                WorkoutChart(alt.phases(profile), Modifier.fillMaxWidth().height(32.dp))
                FilledTonalButton(
                    onClick = { vm.startNext(alt = true) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Alternative starten", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }

    val links: @Composable () -> Unit = {
        LinkRow(
            AppIcons.Message, "KI-Auswertung",
            "Stand auswerten oder eine Einheit vorschlagen lassen",
        ) { vm.open(Screen.PLAN_AI) }
        LinkRow(AppIcons.User, "Dein Profil", profileLine(profile)) { vm.open(Screen.PLAN_PROFILE) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (st.done.isNotEmpty()) {
                TextButton(onClick = { vm.undoLastDone() }) {
                    Text(
                        "Letzten Eintrag zurücknehmen",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            TextButton(onClick = { askEnd = true }) {
                Text("Plan beenden", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Mein Trainingsplan", conn, onBack = { vm.back() })
        // Hochkant: Stand, dann gleich die nächste Einheit. Quer: Stand links, Einheit rechts.
        if (w.landscape) {
            Panes(w, Modifier.weight(1f), { status(); details() }, { action(); links() })
        } else {
            Panes(w, Modifier.weight(1f), { status(); action() }, { details(); links() })
        }
    }

    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text("Plan beenden?") },
            text = {
                Text(
                    "Der Fortschritt in „${plan.spec.name}“ geht verloren. Deine " +
                        "gespeicherten Fahrten bleiben im Verlauf.",
                    fontSize = 16.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        askEnd = false
                        vm.endPlan()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Beenden") }
            },
            dismissButton = {
                TextButton(onClick = { askEnd = false }) { Text("Abbrechen") }
            },
        )
    }
}

/** Die nächste Einheit: was ansteht, wie sie aussieht, und der Start. */
@Composable
private fun NextCard(
    s: Session,
    next: Pair<Int, Int>,
    plan: Plan,
    profile: Profile,
    big: Boolean,
    vm: Engine,
) {
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Nächste Einheit",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Einheit ${next.second + 1} von ${plan.weeks[next.first].size} der Woche",
                fontSize = 14.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                s.name,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                style = Tnum,
            )
            Text(
                "${s.minutes} min · ${s.kind.label} · Belastung ${s.load()}",
                fontSize = 14.sp,
                style = Tnum,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WorkoutChart(s.phases(profile), Modifier.fillMaxWidth().height(if (big) 96.dp else 56.dp))
        if (s.note.isNotEmpty()) Hint(s.note)
        Hint(targetLine(s, profile))
        Button(
            onClick = { vm.startNext(alt = false) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { IconLabel(AppIcons.Play, "Starten") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.skipNext() }) {
                Text("Auslassen", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { vm.markNextDone() }) {
                Text("Schon gefahren", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
