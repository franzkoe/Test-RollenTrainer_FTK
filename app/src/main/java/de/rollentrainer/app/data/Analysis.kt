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

package de.rollentrainer.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Auswertung für das Dashboard. Reine Logik ohne Android-Abhängigkeiten.
 *
 * Grundlagen (so auch in TrainingPeaks, intervals.icu, Garmin):
 * - Belastung je Fahrt wie TSS: Dauer × (gewichtete Leistung / FTP)², eine Stunde an der FTP = 100.
 *   Ohne FTP aus der Zeit in den Pulszonen.
 * - Fitness = gleitender Schnitt der täglichen Belastung über 42 Tage, Ermüdung über 7 Tage,
 *   Form = Fitness − Ermüdung.
 * - Leistung pro Herzschlag (Efficiency Factor): gewichtete Leistung / Durchschnittspuls. Steigt
 *   der Wert bei vergleichbaren Fahrten, ist die Ausdauer besser geworden.
 * - Erholung: HRV und Ruhepuls im Vergleich zum eigenen Schnitt der letzten vier Wochen.
 */

fun epochDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

// ---------------------------------------------------------------- Kennzahlen je Fahrt

/** Höchster Durchschnitt über [window] Sekunden, null wenn die Fahrt kürzer ist. */
fun rollingBest(power: IntArray, window: Int): Int? {
    if (window <= 0 || power.size < window) return null
    var sum = 0L
    for (i in 0 until window) sum += power[i]
    var best = sum
    for (i in window until power.size) {
        sum += power[i] - power[i - window]
        if (sum > best) best = sum
    }
    return (best.toDouble() / window).roundToInt()
}

/** Gewichtete Leistung (Normalized Power): 30-s-Schnitte, vierte Potenz gemittelt. */
fun normalizedPower(power: IntArray): Int? {
    if (power.isEmpty()) return null
    if (power.size < 30) return power.average().roundToInt()
    var sum = 0L
    for (i in 0 until 30) sum += power[i]
    var acc = (sum / 30.0).pow(4)
    var n = 1
    for (i in 30 until power.size) {
        sum += power[i] - power[i - 30]
        acc += (sum / 30.0).pow(4)
        n++
    }
    return (acc / n).pow(0.25).roundToInt()
}

/** Typische Intensität (Anteil an der Schwelle) je Pulszone 1 bis 5. */
private val ZONE_INTENSITY = doubleArrayOf(0.50, 0.65, 0.83, 0.98, 1.12)

/**
 * Berechnet die Kennzahlen einer Fahrt und trägt sie in die Zusammenfassung ein. Die Belastung
 * richtet sich nach der FTP zum Zeitpunkt der Berechnung, ohne FTP nach den Pulszonen.
 */
fun withMetrics(s: RideSummary, samples: List<Sample>, profile: Profile): RideSummary {
    val hasPower = samples.any { it.powerW != null }
    val power = if (hasPower) IntArray(samples.size) { samples[it].powerW ?: 0 } else IntArray(0)
    val np = normalizedPower(power)
    val load: Int? = when {
        np != null && profile.ftp > 0 -> {
            val intensity = np.toDouble() / profile.ftp
            (samples.size / 36.0 * intensity * intensity).roundToInt()
        }

        samples.any { (it.heartRate ?: 0) > 0 } -> {
            var acc = 0.0
            samples.forEach { p ->
                val hr = p.heartRate ?: 0
                if (hr > 0) {
                    val f = ZONE_INTENSITY[profile.zoneOfHr(hr) - 1]
                    acc += f * f
                }
            }
            (acc / 36.0).roundToInt()
        }

        else -> null
    }
    return s.copy(
        np = np,
        best5 = rollingBest(power, 300),
        best20 = rollingBest(power, 1200),
        load = load,
        metrics = true,
    )
}

// ---------------------------------------------------------------- Fitness-Verlauf

class FitnessPoint(val day: Long, val fitness: Double, val fatigue: Double) {
    val form: Double get() = fitness - fatigue
}

/** Fitness und Ermüdung je Tag für die letzten [days] Tage bis [today]. */
fun fitnessSeries(
    rides: List<RideSummary>,
    today: Long,
    days: Int = 84,
    zone: ZoneId = ZoneId.systemDefault(),
): List<FitnessPoint> {
    val perDay = HashMap<Long, Int>()
    rides.forEach { r ->
        val load = r.load
        if (r.analysis && load != null) {
            val d = epochDay(r.startMs, zone)
            if (d <= today) perDay[d] = (perDay[d] ?: 0) + load
        }
    }
    val from = today - days
    val start = minOf(perDay.keys.minOrNull() ?: from, from)
    var fitness = 0.0
    var fatigue = 0.0
    val out = ArrayList<FitnessPoint>()
    for (d in start..today) {
        val load = (perDay[d] ?: 0).toDouble()
        fitness += (load - fitness) / 42.0
        fatigue += (load - fatigue) / 7.0
        if (d >= from) out.add(FitnessPoint(d, fitness, fatigue))
    }
    return out
}

/** Einordnung der Form (Fitness − Ermüdung). */
fun formText(form: Double): String = when {
    form > 5 -> "frisch"
    form >= -10 -> "ausgeglichen"
    form >= -30 -> "im Aufbau, ermüdet"
    else -> "stark ermüdet"
}

// ---------------------------------------------------------------- Verbesserung

/** Wert jetzt und im Vergleichszeitraum davor. */
class Change(val now: Double, val before: Double) {
    val diff: Double get() = now - before
    val pct: Double get() = if (before == 0.0) 0.0 else (now - before) / before * 100.0
}

const val UP = 1
const val FLAT = 0
const val DOWN = -1

/**
 * Drei Hinweise, ob man besser wird: Fitness (heute gegen vor vier Wochen), Leistung pro
 * Herzschlag (letzte vier Wochen gegen die vier davor) und beste 20 Minuten (letzte sechs Wochen
 * gegen die sechs davor). Fehlen Daten, bleibt der jeweilige Wert null.
 */
class Improvement(
    val fitness: Change?,
    val efficiency: Change?,
    val best20: Change?,
    val form: Double,
    val rides: Int,
) {
    fun fitnessDir(): Int? = fitness?.let { if (it.diff > 1.5) UP else if (it.diff < -1.5) DOWN else FLAT }
    fun efficiencyDir(): Int? = efficiency?.let { if (it.pct > 2) UP else if (it.pct < -2) DOWN else FLAT }
    fun best20Dir(): Int? = best20?.let { if (it.pct > 2) UP else if (it.pct < -2) DOWN else FLAT }

    /** Gesamturteil: Mehrheit der vorhandenen Hinweise, null wenn keiner vorliegt. */
    val verdict: Int?
        get() {
            val dirs = listOfNotNull(fitnessDir(), efficiencyDir(), best20Dir())
            if (dirs.isEmpty()) return null
            val ups = dirs.count { it == UP }
            val downs = dirs.count { it == DOWN }
            return if (ups > downs) UP else if (downs > ups) DOWN else FLAT
        }
}

fun verdictText(v: Int?): String = when (v) {
    UP -> "Du wirst besser"
    DOWN -> "Zuletzt rückläufig"
    FLAT -> "Du hältst dein Niveau"
    else -> "Noch zu wenig Daten"
}

/** Leistung pro Herzschlag einer Fahrt, null wenn Leistung oder Puls fehlen oder sie zu kurz ist. */
fun efficiencyOf(r: RideSummary): Double? {
    val p = r.np ?: r.avgPower ?: return null
    val hr = r.avgHr ?: return null
    if (hr <= 0 || p <= 0 || r.durationSec < 1200) return null
    return p.toDouble() / hr
}

fun improvement(
    all: List<RideSummary>,
    today: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Improvement {
    val rides = all.filter { it.analysis }
    fun day(r: RideSummary) = epochDay(r.startMs, zone)

    val series = fitnessSeries(rides, today, 84, zone)
    val withLoad = rides.filter { it.load != null }
    val first = withLoad.minOfOrNull { day(it) }
    val fitness = if (withLoad.size >= 3 && first != null && first <= today - 14) {
        Change(series.last().fitness, series[series.size - 1 - 28].fitness)
    } else {
        null
    }

    fun avgEf(from: Long, to: Long): Double? {
        val list = rides.filter { day(it) in from..to }.mapNotNull { efficiencyOf(it) }
        return if (list.size >= 2) list.average() else null
    }
    val efNow = avgEf(today - 27, today)
    val efBefore = avgEf(today - 55, today - 28)
    val efficiency = if (efNow != null && efBefore != null) Change(efNow, efBefore) else null

    fun best(from: Long, to: Long): Int? =
        rides.filter { day(it) in from..to }.mapNotNull { it.best20 }.maxOrNull()
    val bNow = best(today - 41, today)
    val bBefore = best(today - 83, today - 42)
    val best20 = if (bNow != null && bBefore != null) Change(bNow.toDouble(), bBefore.toDouble()) else null

    return Improvement(fitness, efficiency, best20, series.last().form, rides.size)
}

// ---------------------------------------------------------------- Erholung

/**
 * Erholungswerte eines Tages: Ruhepuls, HRV (ms), Schlaf in Minuten und Gefühl von 1 (sehr müde)
 * bis 5 (sehr gut). Alles optional.
 */
data class Recovery(
    val day: Long,
    val restHr: Int? = null,
    val hrv: Int? = null,
    val sleepMin: Int? = null,
    val feel: Int? = null,
) {
    val empty: Boolean get() = restHr == null && hrv == null && sleepMin == null && feel == null
}

val FEEL_NAMES = listOf("sehr müde", "müde", "normal", "gut", "sehr gut")

enum class RecoveryLevel(val label: String) {
    UNKNOWN("Keine Werte für heute"),
    GOOD("Gut erholt"),
    MEDIUM("Mäßig erholt"),
    LOW("Nicht erholt"),
}

class RecoveryStatus(
    val level: RecoveryLevel,
    val reasons: List<String>,
    val entry: Recovery?,
    val baseHrv: Int?,
    val baseRestHr: Int?,
)

/**
 * Bewertet die Werte von [today] gegen den eigenen Schnitt der 28 Tage davor (mindestens drei
 * Einträge). HRV deutlich darunter, Ruhepuls deutlich darüber, wenig Schlaf oder ein schlechtes
 * Gefühl zählen als Warnzeichen.
 */
fun recoveryStatus(entries: List<Recovery>, today: Long): RecoveryStatus {
    val past = entries.filter { it.day in (today - 28) until today }
    val hrvs = past.mapNotNull { it.hrv }
    val rests = past.mapNotNull { it.restHr }
    val baseHrv = if (hrvs.size >= 3) hrvs.average().roundToInt() else null
    val baseRest = if (rests.size >= 3) rests.average().roundToInt() else null
    val e = entries.firstOrNull { it.day == today && !it.empty }
        ?: return RecoveryStatus(RecoveryLevel.UNKNOWN, emptyList(), null, baseHrv, baseRest)

    var points = 0
    val reasons = ArrayList<String>()
    if (e.hrv != null && baseHrv != null && baseHrv > 0) {
        val pct = ((e.hrv - baseHrv) * 100.0 / baseHrv).roundToInt()
        if (pct <= -20) {
            points += 2
            reasons.add("HRV ${-pct} % unter deinem Schnitt")
        } else if (pct <= -10) {
            points += 1
            reasons.add("HRV ${-pct} % unter deinem Schnitt")
        }
    }
    if (e.restHr != null && baseRest != null) {
        val diff = e.restHr - baseRest
        if (diff >= 8) {
            points += 2
            reasons.add("Ruhepuls $diff Schläge über deinem Schnitt")
        } else if (diff >= 5) {
            points += 1
            reasons.add("Ruhepuls $diff Schläge über deinem Schnitt")
        }
    }
    if (e.sleepMin != null) {
        if (e.sleepMin < 300) {
            points += 2
            reasons.add("weniger als 5 Stunden Schlaf")
        } else if (e.sleepMin < 360) {
            points += 1
            reasons.add("weniger als 6 Stunden Schlaf")
        }
    }
    if (e.feel != null) {
        if (e.feel <= 1) {
            points += 2
            reasons.add("du fühlst dich sehr müde")
        } else if (e.feel == 2) {
            points += 1
            reasons.add("du fühlst dich müde")
        }
    }
    val level = when {
        points >= 2 -> RecoveryLevel.LOW
        points == 1 -> RecoveryLevel.MEDIUM
        else -> RecoveryLevel.GOOD
    }
    return RecoveryStatus(level, reasons, e, baseHrv, baseRest)
}

/** Fügt Einträge zusammen: je Tag gilt der neuere, leere Einträge löschen den Tag. */
fun mergeRecovery(old: List<Recovery>, new: List<Recovery>): List<Recovery> {
    val map = LinkedHashMap<Long, Recovery>()
    old.forEach { map[it.day] = it }
    new.forEach { if (it.empty) map.remove(it.day) else map[it.day] = it }
    return map.values.sortedByDescending { it.day }.take(730)
}

private fun parseDay(text: String): Long? {
    val t = text.trim().trim('"')
    return try {
        when {
            Regex("""\d{4}-\d{2}-\d{2}.*""").matches(t) -> LocalDate.parse(t.substring(0, 10)).toEpochDay()
            Regex("""\d{1,2}\.\d{1,2}\.\d{4}.*""").matches(t) -> {
                val p = t.split('.', ' ')
                LocalDate.of(p[2].take(4).toInt(), p[1].toInt(), p[0].toInt()).toEpochDay()
            }

            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun number(text: String?): Double? =
    text?.trim()?.trim('"')?.replace(',', '.')?.toDoubleOrNull()

/**
 * Liest Erholungswerte aus einer CSV-Datei. Mit Kopfzeile werden die Spalten am Namen erkannt
 * (Datum, Ruhepuls, HRV, Schlaf, Gefühl, auch englisch), ohne Kopfzeile gilt diese Reihenfolge.
 * Trennzeichen: Semikolon, Tabulator oder Komma. Schlaf in Stunden oder Minuten.
 */
fun parseRecoveryCsv(text: String): List<Recovery> {
    val lines = text.replace("﻿", "").lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return emptyList()
    val sep = when {
        lines[0].contains(';') -> ';'
        lines[0].contains('\t') -> '\t'
        else -> ','
    }
    fun cells(line: String) = line.split(sep).map { it.trim().trim('"') }

    var iDay = 0
    var iRest = 1
    var iHrv = 2
    var iSleep = 3
    var iFeel = 4
    var startAt = 0
    val head = cells(lines[0]).map { it.lowercase() }
    if (parseDay(head[0]) == null) {
        startAt = 1
        fun find(vararg keys: String) = head.indexOfFirst { h -> keys.any { h.contains(it) } }
        iDay = find("datum", "date", "tag", "day").let { if (it < 0) 0 else it }
        iRest = find("ruhepuls", "ruhe", "rhr", "resting", "rest")
        iHrv = find("hrv", "rmssd", "variab")
        iSleep = find("schlaf", "sleep")
        iFeel = find("gefühl", "gefuehl", "feel", "befinden")
    }
    val out = ArrayList<Recovery>()
    for (n in startAt until lines.size) {
        val c = cells(lines[n])
        val day = c.getOrNull(iDay)?.let { parseDay(it) } ?: continue
        val rest = number(c.getOrNull(iRest))?.roundToInt()?.takeIf { it in 25..150 }
        val hrv = number(c.getOrNull(iHrv))?.roundToInt()?.takeIf { it in 1..300 }
        val sleep = number(c.getOrNull(iSleep))?.let { v ->
            (if (v <= 24) v * 60 else v).roundToInt().takeIf { it in 30..1200 }
        }
        val feel = number(c.getOrNull(iFeel))?.roundToInt()?.takeIf { it in 1..5 }
        val r = Recovery(day, rest, hrv, sleep, feel)
        if (!r.empty) out.add(r)
    }
    return out
}

// ---------------------------------------------------------------- VO2max

/** Von Hand eingetragene VO2max-Schätzung (ml/kg/min), z. B. von der Sportuhr. */
data class Vo2(val day: Long, val value: Double)

fun mergeVo2(old: List<Vo2>, new: Vo2): List<Vo2> =
    (old.filter { it.day != new.day } + new).sortedByDescending { it.day }.take(365)

fun dayText(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return String.format(java.util.Locale.GERMANY, "%d.%d.%d", d.dayOfMonth, d.monthValue, d.year)
}
