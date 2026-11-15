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

import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Trainingsplan: Profil (FTP oder Pulszonen), Einheiten, fertige Pläne, Plan-Generator und
 * Fortschritt. Reine Logik ohne Android-Abhängigkeiten.
 *
 * Grundlagen:
 * - Leistungszonen in % der FTP (Coggan): Z1 bis 55, Z2 56–75, Z3 76–90, Z4 91–105, Z5 ab 106.
 * - Pulszonen in % des Maximalpulses: 50–60, 60–70, 70–80, 80–90, 90–100.
 * - Steigerung höchstens etwa 10 % pro Woche, jede vierte Woche Entlastung mit rund 40 % weniger
 *   Umfang, etwa 80 % der Zeit locker und 20 % intensiv.
 * - Einheiten: Tempo 76–90 %, Sweet Spot 88–93 %, Schwelle 95–105 %, Über-Unter 95/105 %,
 *   VO2max 106–120 % (z. B. 5 × 4 min oder 30/15 s).
 */

// ---------------------------------------------------------------- Profil

/** Obergrenzen der Pulszonen 1 bis 4 aus dem Maximalpuls (60, 70, 80, 90 %). */
fun hrZonesFromMax(maxHr: Int): List<Int> = listOf(60, 70, 80, 90).map { (maxHr * it + 50) / 100 }

/**
 * Trainingsprofil. Mit FTP steuert die App den Widerstand über Watt vor. Ohne FTP (oder wenn
 * [byHr] gewählt ist) wird nach Pulszonen trainiert und der Widerstand von Hand gestellt.
 * [zoneMax] enthält die Obergrenzen der Zonen 1 bis 4 in Schlägen pro Minute.
 */
data class Profile(
    val ftp: Int = 0,
    val byHr: Boolean = true,
    val zoneMax: List<Int> = hrZonesFromMax(185),
    /** Körpergewicht in kg, 0 = nicht angegeben. */
    val weight: Int = 0,
) {
    /** FTP pro kg Körpergewicht, null wenn FTP oder Gewicht fehlen. */
    val wattsPerKg: Double? get() = if (ftp > 0 && weight > 0) ftp.toDouble() / weight else null

    /** Watt pro kg als Text, z. B. „3,14 W/kg“. */
    fun wattsPerKgText(): String? =
        wattsPerKg?.let { String.format(java.util.Locale.GERMANY, "%.2f W/kg", it) }

    /** true: Steuerung über Watt (ERG). false: Training nach Puls mit eigener Stufenwahl. */
    val power: Boolean get() = !byHr && ftp > 0

    fun zoneOfHr(hr: Int): Int {
        for (i in 0 until 4) if (hr <= zoneMax[i]) return i + 1
        return 5
    }

    /** Untere und obere Grenze einer Zone; null = offen. */
    fun zoneBounds(zone: Int): Pair<Int?, Int?> = when {
        zone <= 1 -> null to zoneMax[0]
        zone >= 5 -> zoneMax[3] + 1 to null
        else -> zoneMax[zone - 2] + 1 to zoneMax[zone - 1]
    }

    fun zoneText(zone: Int): String {
        val (lo, hi) = zoneBounds(zone)
        return when {
            lo == null -> "bis $hi"
            hi == null -> "ab $lo"
            else -> "$lo–$hi"
        }
    }

    companion object {
        const val FTP_MIN = 50
        const val FTP_MAX = 500
        const val HR_MIN = 60
        const val HR_MAX = 230
        const val WEIGHT_MIN = 30
        const val WEIGHT_MAX = 200
    }
}

/** Leistungszone (1 bis 5) zu einem Anteil an der FTP. */
fun zoneOfPct(pct: Int): Int = when {
    pct <= 55 -> 1
    pct <= 75 -> 2
    pct <= 90 -> 3
    pct <= 105 -> 4
    else -> 5
}

val ZONE_NAMES = listOf("Erholung", "Grundlage", "Tempo", "Schwelle", "VO2max")

fun zoneName(zone: Int): String = ZONE_NAMES[(zone - 1).coerceIn(0, 4)]

/** Untere Grenze der Leistungszone in % der FTP (Zone 1 beginnt bei 0). */
val ZONE_PCT_FROM = listOf(0, 56, 76, 91, 106)

fun round5(v: Double): Int = Math.floor(v / 5.0 + 0.5).toInt() * 5

// ---------------------------------------------------------------- Einheiten

enum class Kind(val label: String, val hard: Boolean) {
    RECOVERY("Erholung", false),
    ENDURANCE("Grundlage", false),
    TEMPO("Tempo", true),
    SWEETSPOT("Sweet Spot", true),
    THRESHOLD("Schwelle", true),
    VO2("VO2max", true),
    CUSTOM("Eigene Einheit", true),
}

/** Abschnitt einer Einheit: Dauer und Intensität in % der FTP. */
data class Seg(val seconds: Int, val pct: Int, val label: String, val type: PhaseType)

data class Session(
    val name: String,
    val kind: Kind,
    val segs: List<Seg>,
    val note: String = "",
) {
    val seconds: Int get() = segs.sumOf { it.seconds }
    val minutes: Int get() = (seconds + 30) / 60

    /** Mindestens fünf Minuten oberhalb der Grundlage: zählt als harte Einheit. */
    val hard: Boolean get() = segs.filter { it.pct > 75 }.sumOf { it.seconds } >= 300

    /** Belastung der Einheit (wie TSS: eine Stunde an der FTP ergibt 100). */
    fun load(): Int =
        (segs.sumOf { it.seconds * (it.pct / 100.0) * (it.pct / 100.0) } / 36.0).roundToInt()

    /** Ablauf für die Trainingsansicht: mit FTP als Watt, sonst nur mit Zielzone. */
    fun phases(profile: Profile): List<Phase> = segs.map { s ->
        Phase(
            type = s.type,
            label = s.label,
            seconds = s.seconds,
            watts = if (profile.power) round5(profile.ftp * s.pct / 100.0) else 0,
            zone = zoneOfPct(s.pct),
        )
    }
}

val LEVEL_NAMES = listOf("Einsteiger", "Fortgeschritten", "Sportlich")

/** Intensität der Kernabschnitte je Niveau (Einsteiger, Fortgeschritten, Sportlich). */
private fun corePct(kind: Kind, level: Int): Int {
    val l = level.coerceIn(0, 2)
    return when (kind) {
        Kind.TEMPO -> listOf(80, 83, 85)[l]
        Kind.SWEETSPOT -> listOf(88, 90, 92)[l]
        Kind.THRESHOLD -> listOf(95, 98, 100)[l]
        Kind.VO2 -> listOf(108, 112, 115)[l]
        else -> 65
    }
}

private const val EASY = 55

/**
 * Baut eine Einheit der Art [kind], die [minutes] Minuten füllt: Aufwärmen, Kernabschnitte mit
 * Pausen, restliche Zeit als lockere Grundlage, Ausfahren. [variant] 1 wählt bei Schwelle
 * Über-Unter-Blöcke und bei VO2max kurze 30/15-Intervalle.
 */
fun buildSession(kind: Kind, minutes: Int, level: Int = 1, variant: Int = 0): Session {
    val total = minutes.coerceIn(10, 180)
    val segs = ArrayList<Seg>()
    fun add(sec: Int, pct: Int, label: String, type: PhaseType) {
        if (sec > 0) segs.add(Seg(sec, pct, label, type))
    }

    if (kind == Kind.RECOVERY) {
        add(total * 60, 50, "Locker rollen", PhaseType.INTERVAL)
        return Session(
            "Erholungsfahrt $total min", kind, segs,
            "Ganz locker treten. Die Beine sollen sich danach besser anfühlen als vorher.",
        )
    }

    val warm = if (kind == Kind.ENDURANCE) {
        if (total >= 30) 5 else 3
    } else {
        if (total >= 45) 10 else if (total >= 30) 8 else 5
    }
    val cool = if (total >= 30) 5 else 3
    val main = (total - warm - cool).coerceAtLeast(1)
    add(warm * 60, EASY, "Aufwärmen", PhaseType.WARMUP)

    if (kind == Kind.ENDURANCE || kind == Kind.CUSTOM) {
        val pct = if (variant == 2) 60 else if (variant == 1) 70 else 65
        add(main * 60, pct, "Grundlage", PhaseType.INTERVAL)
        add(cool * 60, 50, "Ausfahren", PhaseType.COOLDOWN)
        return Session(
            (if (variant == 2) "Lockere Grundlage" else "Grundlage") + " $total min",
            Kind.ENDURANCE, segs,
            "Gleichmäßig fahren, sodass du dich noch unterhalten könntest.",
        )
    }

    val pct = corePct(kind, level)
    var name: String
    val note: String
    var used: Int // Minuten, die der Kernteil belegt

    if (kind == Kind.THRESHOLD && variant == 1 && main >= 9) {
        // Über-Unter: je Block 3 × (2 min knapp unter, 1 min knapp über der Schwelle)
        val rest = 5
        val blocks = ((main + rest) / (9 + rest)).coerceIn(1, 4)
        for (b in 1..blocks) {
            for (r in 1..3) {
                add(120, 95, "Unter der Schwelle · Block $b von $blocks", PhaseType.INTERVAL)
                add(60, 105, "Über der Schwelle · Block $b von $blocks", PhaseType.INTERVAL)
            }
            if (b < blocks) add(rest * 60, EASY, "Pause $b von ${blocks - 1}", PhaseType.REST)
        }
        used = blocks * 9 + (blocks - 1) * rest
        name = "Über-Unter $blocks × 9 min"
        note = "Wechsel knapp unter und über der Schwelle. Schult das Erholen unter Last."
    } else if (kind == Kind.VO2 && variant == 1 && main >= 7) {
        // 30/15: 9 × (30 s hart, 15 s locker) je Satz
        val rest = 5
        val sets = ((main + rest) / (7 + rest)).coerceIn(1, 3)
        for (s in 1..sets) {
            for (r in 1..9) {
                add(30, pct + 5, "Hart $r von 9 · Satz $s von $sets", PhaseType.INTERVAL)
                add(15, 50, "Locker · Satz $s von $sets", PhaseType.REST)
            }
            add(15, 50, "Locker · Satz $s von $sets", PhaseType.REST)
            if (s < sets) add(rest * 60, EASY, "Satzpause $s von ${sets - 1}", PhaseType.REST)
        }
        used = sets * 7 + (sets - 1) * rest
        name = "VO2max 30/15 · $sets " + if (sets == 1) "Satz" else "Sätze"
        note = "Kurze harte Antritte mit sehr kurzen Pausen. Viel Zeit bei hoher Sauerstoffaufnahme."
    } else {
        // Gleich lange Kernabschnitte mit Pausen
        val baseLen: Int
        val maxLen: Int
        val rest: Int
        val cap: Int // höchste Kernzeit in Minuten
        when (kind) {
            Kind.TEMPO -> { baseLen = 15; maxLen = 20; rest = 4; cap = 60 }
            Kind.SWEETSPOT -> { baseLen = 12; maxLen = 20; rest = 4; cap = 60 }
            Kind.THRESHOLD -> { baseLen = 8; maxLen = 15; rest = 4; cap = 40 }
            else -> { baseLen = 4; maxLen = 5; rest = 4; cap = 20 }
        }
        var len = baseLen
        var reps = ((main + rest) / (len + rest)).coerceAtLeast(1)
        if (reps * len > cap) reps = (cap / len).coerceAtLeast(1)
        if (reps == 1 && main < len) len = main
        // Übrige Zeit zuerst in längere Kernabschnitte stecken
        var left = main - (reps * len + (reps - 1) * rest)
        while (left >= reps && len < maxLen && (len + 1) * reps <= cap) {
            len++
            left -= reps
        }
        val restPct = if (kind == Kind.VO2) 50 else EASY
        for (r in 1..reps) {
            add(len * 60, pct, if (reps == 1) kind.label else "Intervall $r von $reps", PhaseType.INTERVAL)
            if (r < reps) add(rest * 60, restPct, "Pause $r von ${reps - 1}", PhaseType.REST)
        }
        used = reps * len + (reps - 1) * rest
        name = kind.label + " " + (if (reps == 1) "$len min" else "$reps × $len min")
        note = when (kind) {
            Kind.TEMPO -> "Zügig, aber kontrolliert. Stärkt die Ausdauer bei höherem Tempo."
            Kind.SWEETSPOT -> "Knapp unter der Schwelle. Viel Wirkung bei gut verkraftbarer Belastung."
            Kind.THRESHOLD -> "An der Schwelle. Hebt die Leistung, die du lange halten kannst."
            else -> "Sehr hart. Verbessert die maximale Sauerstoffaufnahme."
        }
    }

    val left = main - used
    if (left > 0) add(left * 60, 65, "Grundlage", PhaseType.INTERVAL)
    add(cool * 60, 50, "Ausfahren", PhaseType.COOLDOWN)
    return Session(name, kind, segs, note)
}

/** Leichtere Einheit für Tage, an denen man nicht erholt ist. */
fun alternativeFor(s: Session): Session = when {
    s.kind == Kind.RECOVERY -> buildSession(Kind.RECOVERY, 20)
    s.kind == Kind.ENDURANCE ->
        buildSession(Kind.RECOVERY, round5(s.minutes * 0.6).coerceIn(20, 40))
    else -> buildSession(Kind.ENDURANCE, round5(s.minutes * 0.7).coerceIn(20, 60), variant = 2)
}

// ---------------------------------------------------------------- Pläne

/**
 * Schwerpunkt eines Plans: welche harten Einheiten vorkommen. [maxHard] begrenzt bei den reinen
 * Schwerpunkten die harten Einheiten pro Woche, die übrigen bleiben lockere Grundlage.
 */
enum class Focus(val label: String, val text: String, val maxHard: Int) {
    MIXED(
        "Gemischt",
        "Wie bisher: zwei harte Einheiten pro Woche, von Tempo und Sweet Spot über die " +
            "Schwelle bis VO2max, dazwischen Grundlage.",
        2,
    ),
    NO_VO2(
        "Ohne VO2max",
        "Zwei harte Einheiten pro Woche mit Tempo, Sweet Spot und Schwelle. Keine sehr " +
            "harten VO2max-Intervalle.",
        2,
    ),
    BASE(
        "Nur Grundlage",
        "Nur gleichmäßige Fahrten in Zone 2, ohne harte Abschnitte. Baut die Ausdauer auf " +
            "und schont den Körper.",
        0,
    ),
    THRESHOLD(
        "Nur Schwelle",
        "Sweet Spot, Schwelle und Über-Unter. Höchstens vier solche Einheiten pro Woche, " +
            "weitere Einheiten bleiben lockere Grundlage.",
        4,
    ),
    VO2(
        "Nur VO2max",
        "Nur VO2max-Intervalle, abwechselnd 4-Minuten-Intervalle und 30/15. Höchstens drei " +
            "pro Woche, weitere Einheiten bleiben lockere Grundlage.",
        3,
    ),
}

/**
 * Eckdaten eines Plans: Niveau (0 bis 2), Einheiten pro Woche, Dauer je Einheit in Minuten,
 * Wochenzahl, Steigerung des Umfangs pro Woche in Prozent und Schwerpunkt.
 */
data class PlanSpec(
    val name: String,
    val level: Int = 1,
    val perWeek: Int = 3,
    val minutes: Int = 45,
    val weeks: Int = 8,
    val stepPct: Int = 5,
    val text: String = "",
    val focus: Focus = Focus.MIXED,
) {
    /** Harte Einheiten in einer Belastungswoche. */
    val hardPerWeek: Int
        get() = when (focus) {
            Focus.MIXED, Focus.NO_VO2 -> 2
            else -> minOf(perWeek, focus.maxHard)
        }

    fun summary(): String =
        "$perWeek × pro Woche · $minutes min · $weeks Wochen"

    companion object {
        const val MIN_PER_WEEK = 2
        const val MAX_PER_WEEK = 6
        const val MIN_MINUTES = 20
        const val MAX_MINUTES = 120
        const val MIN_WEEKS = 4
        const val MAX_WEEKS = 16
        const val MAX_STEP = 10
    }
}

data class Plan(val spec: PlanSpec, val weeks: List<List<Session>>) {
    val total: Int get() = weeks.sumOf { it.size }
    fun weekMinutes(w: Int): Int = weeks[w].sumOf { it.minutes }
}

/** Jede vierte Woche ist eine Entlastungswoche. */
fun isRecoveryWeek(week: Int): Boolean = (week + 1) % 4 == 0

/** Dauer je Einheit in der Woche [week] (ab 0): Steigerung je Belastungswoche, Entlastung 60 %. */
fun minutesInWeek(spec: PlanSpec, week: Int): Int {
    val loadWeeksBefore = (0 until week).count { !isRecoveryWeek(it) }
    val step = 1.0 + spec.stepPct.coerceIn(0, PlanSpec.MAX_STEP) / 100.0
    return if (isRecoveryWeek(week)) {
        val before = (spec.minutes * step.pow(loadWeeksBefore - 1)).coerceAtMost(120.0)
        round5(before * 0.6).coerceIn(20, 120)
    } else {
        round5(spec.minutes * step.pow(loadWeeksBefore)).coerceIn(20, 120)
    }
}

/** Die beiden harten Einheiten einer Woche je Niveau und Abschnitt des Plans. */
private fun hardPair(level: Int, part: Int, week: Int, focus: Focus): List<Pair<Kind, Int>> {
    val pair = mixedPair(level, part, week)
    if (focus != Focus.NO_VO2) return pair
    // Ohne VO2max: an deren Stelle Schwelle bzw. Über-Unter
    return pair.mapIndexed { i, p ->
        if (p.first == Kind.VO2) (if (i == 0) Kind.THRESHOLD to 0 else Kind.THRESHOLD to 1) else p
    }
}

/**
 * Harte Einheiten bei einem reinen Schwerpunkt: [count] Einheiten, je Abschnitt des Plans
 * etwas härter, innerhalb der Woche abwechselnd.
 */
private fun focusHard(focus: Focus, level: Int, part: Int, count: Int): List<Pair<Kind, Int>> {
    val cycle: List<Pair<Kind, Int>> = when (focus) {
        Focus.THRESHOLD -> when (part) {
            // Grundlage: viel Sweet Spot, Aufbau: Schwelle, Spitze: Über-Unter
            0 -> listOf(Kind.SWEETSPOT to 0, if (level == 0) Kind.SWEETSPOT to 0 else Kind.THRESHOLD to 0)
            1 -> listOf(Kind.THRESHOLD to 0, Kind.SWEETSPOT to 0)
            else -> listOf(if (level == 0) Kind.THRESHOLD to 0 else Kind.THRESHOLD to 1, Kind.SWEETSPOT to 0, Kind.THRESHOLD to 0)
        }

        // VO2max: lange und kurze Intervalle abwechselnd
        else -> listOf(Kind.VO2 to 0, Kind.VO2 to 1)
    }
    return List(count) { cycle[it % cycle.size] }
}

/**
 * Harte und lockere Einheiten möglichst abwechselnd, die lange Fahrt zuletzt. Gibt es weniger
 * lockere als harte Einheiten, werden die harten in gleich große Gruppen geteilt.
 */
private fun interleave(hard: List<Session>, easy: List<Session>, long: Session?): List<Session> {
    val out = ArrayList<Session>()
    if (easy.size >= hard.size) {
        hard.forEachIndexed { i, s ->
            out.add(s)
            out.add(easy[i])
        }
        out.addAll(easy.drop(hard.size))
    } else {
        val groups = easy.size + 1
        var h = 0
        for (g in 0 until groups) {
            val size = hard.size / groups + if (g < hard.size % groups) 1 else 0
            repeat(size) { out.add(hard[h++]) }
            if (g < easy.size) out.add(easy[g])
        }
    }
    if (long != null) out.add(long)
    return out
}

private fun mixedPair(level: Int, part: Int, week: Int): List<Pair<Kind, Int>> {
    val alt = week % 2
    return when (level.coerceIn(0, 2)) {
        0 -> when (part) {
            0 -> listOf(Kind.TEMPO to 0, Kind.TEMPO to 0)
            1 -> listOf(Kind.SWEETSPOT to 0, Kind.TEMPO to 0)
            else -> listOf(Kind.THRESHOLD to 0, Kind.SWEETSPOT to 0)
        }

        1 -> when (part) {
            0 -> listOf(Kind.SWEETSPOT to 0, Kind.TEMPO to 0)
            1 -> listOf(Kind.THRESHOLD to 0, Kind.SWEETSPOT to 0)
            else -> listOf(Kind.VO2 to 0, Kind.THRESHOLD to alt)
        }

        else -> when (part) {
            0 -> listOf(Kind.SWEETSPOT to 0, Kind.THRESHOLD to 0)
            1 -> listOf(Kind.THRESHOLD to alt, Kind.VO2 to 0)
            else -> listOf(Kind.VO2 to alt, Kind.THRESHOLD to 1)
        }
    }
}

/**
 * Erzeugt den Plan: Die Wochen gliedern sich in Grundlage, Aufbau und Spitze. Gemischt pro Woche
 * zwei harte Einheiten, der Rest locker; bei reinen Schwerpunkten siehe [Focus]. Jede vierte Woche
 * entlastet.
 */
fun generatePlan(input: PlanSpec): Plan {
    val spec = input.copy(
        level = input.level.coerceIn(0, 2),
        perWeek = input.perWeek.coerceIn(PlanSpec.MIN_PER_WEEK, PlanSpec.MAX_PER_WEEK),
        minutes = input.minutes.coerceIn(PlanSpec.MIN_MINUTES, PlanSpec.MAX_MINUTES),
        weeks = input.weeks.coerceIn(PlanSpec.MIN_WEEKS, PlanSpec.MAX_WEEKS),
        stepPct = input.stepPct.coerceIn(0, PlanSpec.MAX_STEP),
    )
    val weeks = ArrayList<List<Session>>()
    for (w in 0 until spec.weeks) {
        val m = minutesInWeek(spec, w)
        val long = round5(m * 1.25).coerceAtMost(150)
        val easy = round5(m * 0.5).coerceIn(20, 45)
        val list = ArrayList<Session>()
        val part = (w * 3 / spec.weeks).coerceIn(0, 2)
        if (spec.focus == Focus.BASE) {
            // Nur Grundlage: alles in Zone 2, ab vier Einheiten eine lange Fahrt
            val n = spec.perWeek
            for (i in 0 until n) {
                list.add(
                    if (n >= 4 && i == n - 1 && !isRecoveryWeek(w)) {
                        buildSession(Kind.ENDURANCE, long, spec.level, variant = 1)
                    } else {
                        buildSession(Kind.ENDURANCE, m, spec.level)
                    }
                )
            }
        } else if (isRecoveryWeek(w)) {
            // Entlastung: alles locker, eine Einheit ganz leicht
            for (i in 0 until spec.perWeek) {
                list.add(
                    if (i == 1 && spec.perWeek >= 3) buildSession(Kind.RECOVERY, easy)
                    else buildSession(Kind.ENDURANCE, m, spec.level)
                )
            }
        } else if (spec.focus == Focus.THRESHOLD || spec.focus == Focus.VO2) {
            val hardCount = spec.hardPerWeek
            val hard = focusHard(spec.focus, spec.level, part, hardCount)
                .map { buildSession(it.first, m, spec.level, it.second) }
            val easyCount = spec.perWeek - hardCount
            // Bleibt nach dem Wechsel hart/locker eine lockere Einheit übrig, wird sie zur langen Fahrt
            val longRide = if (easyCount > hardCount) buildSession(Kind.ENDURANCE, long, spec.level, variant = 1) else null
            val easyList = List(easyCount - (if (longRide != null) 1 else 0)) {
                buildSession(Kind.ENDURANCE, m, spec.level)
            }
            list.addAll(interleave(hard, easyList, longRide))
        } else {
            val (a, b) = hardPair(spec.level, part, w, spec.focus)
            val hardA = buildSession(a.first, m, spec.level, a.second)
            val hardB = buildSession(b.first, m, spec.level, b.second)
            val base = buildSession(Kind.ENDURANCE, m, spec.level)
            val longRide = buildSession(Kind.ENDURANCE, long, spec.level, variant = 1)
            val recovery = buildSession(Kind.RECOVERY, easy)
            when (spec.perWeek) {
                2 -> list.addAll(listOf(hardA, base))
                3 -> list.addAll(listOf(hardA, base, hardB))
                4 -> list.addAll(listOf(hardA, base, hardB, longRide))
                5 -> list.addAll(listOf(hardA, recovery, hardB, base, longRide))
                else -> list.addAll(
                    listOf(
                        hardA, recovery, hardB, base,
                        if (spec.level == 0) base else buildSession(Kind.TEMPO, m, spec.level),
                        longRide,
                    )
                )
            }
        }
        weeks.add(list)
    }
    return Plan(spec, weeks)
}

/** Fertige Pläne, sortiert nach Niveau und Häufigkeit. */
val PRESET_PLANS: List<PlanSpec> = listOf(
    PlanSpec(
        "Einstieg", level = 0, perWeek = 2, minutes = 30, weeks = 6, stepPct = 5,
        text = "Für den Anfang oder den Wiedereinstieg. Zwei kurze Einheiten, eine davon zügig.",
    ),
    PlanSpec(
        "Einstieg Plus", level = 0, perWeek = 3, minutes = 40, weeks = 8, stepPct = 5,
        text = "Regelmäßig fahren und die Ausdauer aufbauen, von Tempo bis Sweet Spot.",
    ),
    PlanSpec(
        "Aufbau", level = 1, perWeek = 3, minutes = 60, weeks = 8, stepPct = 5,
        text = "Sweet Spot und Schwelle heben die Dauerleistung, am Ende kommt VO2max dazu.",
    ),
    PlanSpec(
        "Aufbau Plus", level = 1, perWeek = 4, minutes = 60, weeks = 10, stepPct = 5,
        text = "Wie „Aufbau“, mit einer längeren Grundlagenfahrt pro Woche.",
    ),
    PlanSpec(
        "Leistung", level = 2, perWeek = 4, minutes = 70, weeks = 10, stepPct = 4,
        text = "Schwelle, Über-Unter und VO2max für alle, die schon regelmäßig hart trainieren.",
    ),
    PlanSpec(
        "Leistung Plus", level = 2, perWeek = 5, minutes = 75, weeks = 12, stepPct = 4,
        text = "Hoher Umfang mit zwei harten Tagen, Erholungsfahrt und langer Grundlage.",
    ),
)

// ---------------------------------------------------------------- Fortschritt

const val ST_DONE = "done"
const val ST_ALT = "alt"
const val ST_SKIP = "skip"

/** Erledigte, ersetzte oder übersprungene Einheit des Plans. */
data class Done(
    val week: Int,
    val index: Int,
    val status: String,
    val dateMs: Long,
    val name: String,
    val seconds: Int = 0,
    val avgPower: Int? = null,
    val avgHr: Int? = null,
    val hard: Boolean = false,
)

data class PlanState(val plan: Plan, val startMs: Long, val done: List<Done> = emptyList()) {

    fun entry(week: Int, index: Int): Done? =
        done.firstOrNull { it.week == week && it.index == index }

    /** Nächste offene Einheit (Woche, Index) oder null, wenn der Plan abgeschlossen ist. */
    fun next(): Pair<Int, Int>? {
        plan.weeks.forEachIndexed { w, list ->
            for (i in list.indices) if (entry(w, i) == null) return w to i
        }
        return null
    }

    val finished: Boolean get() = next() == null

    /** Gefahrene Einheiten (wie geplant oder als Alternative). */
    val ridden: Int get() = done.count { it.status != ST_SKIP }
    val alternatives: Int get() = done.count { it.status == ST_ALT }
    val skipped: Int get() = done.count { it.status == ST_SKIP }
    val riddenSeconds: Int get() = done.sumOf { it.seconds }

    /** Anteil der abgearbeiteten Einheiten (0 bis 1). */
    val progress: Float get() = if (plan.total == 0) 0f else done.size.toFloat() / plan.total

    /** Gefahrene Minuten in einer Woche. */
    fun riddenMinutes(week: Int): Int =
        done.filter { it.week == week }.sumOf { it.seconds } / 60

    /** Letzte harte Einheit liegt weniger als 36 Stunden zurück. */
    fun hardRecently(nowMs: Long): Boolean {
        val last = done.filter { it.status != ST_SKIP }.maxByOrNull { it.dateMs } ?: return false
        return last.hard && nowMs - last.dateMs < 36 * 3600_000L
    }
}
