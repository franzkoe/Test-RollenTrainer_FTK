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

const val TYPE_TRAINER = "trainer"
const val TYPE_HR = "hr"

/** Gespeichertes Gerät; [type] ist [TYPE_TRAINER] oder [TYPE_HR] (Pulsmesser). */
data class SavedDevice(val address: String, val name: String, val type: String = TYPE_TRAINER)

enum class PhaseType { WARMUP, INTERVAL, REST, COOLDOWN }

data class Phase(val type: PhaseType, val label: String, val seconds: Int, val watts: Int)

/** Trainingsvorlage. Dauer 0 bei Aufwärmen/Ausfahren bedeutet: Phase entfällt. */
data class Workout(
    val id: String,
    val name: String,
    val warmupMin: Int = 10,
    val warmupW: Int = 100,
    val intervalMin: Int = 3,
    val intervalW: Int = 200,
    val restMin: Int = 2,
    val restW: Int = 80,
    val reps: Int = 5,
    val cooldownMin: Int = 5,
    val cooldownW: Int = 80,
) {
    fun phases(): List<Phase> {
        val list = ArrayList<Phase>()
        if (warmupMin > 0) list.add(Phase(PhaseType.WARMUP, "Aufwärmen", warmupMin * 60, warmupW))
        for (i in 1..reps) {
            if (intervalMin > 0) {
                list.add(Phase(PhaseType.INTERVAL, "Intervall $i von $reps", intervalMin * 60, intervalW))
            }
            if (i < reps && restMin > 0) {
                list.add(Phase(PhaseType.REST, "Pause $i von ${reps - 1}", restMin * 60, restW))
            }
        }
        if (cooldownMin > 0) list.add(Phase(PhaseType.COOLDOWN, "Ausfahren", cooldownMin * 60, cooldownW))
        return list
    }

    fun totalSeconds(): Int = phases().sumOf { it.seconds }
}

/** Freie Fahrt: 21 Stufen (0–20), linear, auf 5 W gerundet. */
val LEVEL_WATTS = intArrayOf(
    0, 30, 55, 85, 110, 140, 165, 195, 220, 250, 275,
    305, 330, 360, 385, 415, 440, 470, 495, 525, 550,
)

const val MAX_LEVEL = 20
const val MAX_WATTS = 600

// ---------------------------------------------------------------- Aufzeichnung

/** Ein Messpunkt pro Sekunde aktiver Fahrzeit. */
data class Sample(
    val tSec: Int,
    val epochMs: Long,
    val powerW: Int?,
    val speedKmh: Double?,
    val distanceM: Double,
    val cadenceRpm: Double?,
    val heartRate: Int?,
)

data class RideSummary(
    val id: String,
    val name: String,
    val startMs: Long,
    val durationSec: Int,
    val distanceM: Double,
    val avgPower: Int?,
    val maxPower: Int?,
    val avgSpeed: Double?,
    val maxSpeed: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val avgCadence: Int?,
    val kcal: Int?,
)

fun summarize(id: String, name: String, samples: List<Sample>, kcal: Int?): RideSummary {
    val p = samples.mapNotNull { it.powerW }
    val v = samples.mapNotNull { it.speedKmh }
    val h = samples.mapNotNull { it.heartRate }.filter { it > 0 }
    val c = samples.mapNotNull { it.cadenceRpm }
    return RideSummary(
        id = id,
        name = name,
        startMs = samples.firstOrNull()?.let { it.epochMs - it.tSec * 1000L } ?: System.currentTimeMillis(),
        durationSec = samples.lastOrNull()?.tSec ?: 0,
        distanceM = samples.lastOrNull()?.distanceM ?: 0.0,
        avgPower = if (p.isEmpty()) null else Math.round(p.average()).toInt(),
        maxPower = p.maxOrNull(),
        avgSpeed = if (v.isEmpty()) null else v.average(),
        maxSpeed = v.maxOrNull(),
        avgHr = if (h.isEmpty()) null else Math.round(h.average()).toInt(),
        maxHr = h.maxOrNull(),
        avgCadence = if (c.isEmpty()) null else Math.round(c.average()).toInt(),
        kcal = kcal,
    )
}
