package de.rollentrainer.app.data

data class SavedDevice(val address: String, val name: String)

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
