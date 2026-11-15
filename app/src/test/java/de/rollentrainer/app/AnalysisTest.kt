package de.rollentrainer.app

import de.rollentrainer.app.data.DOWN
import de.rollentrainer.app.data.Profile
import de.rollentrainer.app.data.Recovery
import de.rollentrainer.app.data.RecoveryLevel
import de.rollentrainer.app.data.RideSummary
import de.rollentrainer.app.data.Sample
import de.rollentrainer.app.data.UP
import de.rollentrainer.app.data.Vo2
import de.rollentrainer.app.data.fitnessSeries
import de.rollentrainer.app.data.improvement
import de.rollentrainer.app.data.mergeRecovery
import de.rollentrainer.app.data.mergeVo2
import de.rollentrainer.app.data.normalizedPower
import de.rollentrainer.app.data.parseRecovery
import de.rollentrainer.app.data.parseRecoveryCsv
import de.rollentrainer.app.data.parseVo2
import de.rollentrainer.app.data.recoveryStatus
import de.rollentrainer.app.data.recoveryToJson
import de.rollentrainer.app.data.rollingBest
import de.rollentrainer.app.data.summarize
import de.rollentrainer.app.data.vo2ToJson
import de.rollentrainer.app.data.withMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Prüft die Auswertung im Dashboard: Belastung, Fitness-Verlauf, Vergleiche, Erholung. */
class AnalysisTest {

    private val utc = ZoneId.of("UTC")
    private val today = 20000L

    private fun ride(
        day: Long, load: Int?, np: Int? = null, hr: Int? = null, best20: Int? = null,
        analysis: Boolean = true,
    ) = RideSummary(
        "r$day", "Fahrt", day * 86_400_000L + 12 * 3_600_000L, 3600, 0.0, np, null, null, null,
        hr, null, null, null, analysis, np, null, best20, load, true,
    )

    @Test
    fun kennzahlenEinerFahrt() {
        assertEquals(200, normalizedPower(IntArray(3600) { 200 }))
        // Wechselnde Leistung wiegt schwerer als ihr Durchschnitt
        val mixed = IntArray(3600) { if (it % 120 < 60) 300 else 100 }
        assertTrue(normalizedPower(mixed)!! > 220)
        assertEquals(200, rollingBest(IntArray(3600) { 200 }, 1200))
        assertNull(rollingBest(IntArray(100) { 200 }, 1200))
        assertNull(normalizedPower(IntArray(0)))

        val samples = (1..3600).map { Sample(it, it * 1000L, 200, 30.0, 0.0, 85.0, 140) }
        val s = summarize("a", "n", samples, null)
        // Eine Stunde an der FTP ergibt Belastung 100
        val atFtp = withMetrics(s, samples, Profile(ftp = 200, byHr = false))
        assertEquals(100, atFtp.load)
        assertEquals(200, atFtp.np)
        assertEquals(200, atFtp.best20)
        assertTrue(atFtp.metrics)
        // Ohne FTP aus den Pulszonen (140 liegt in Zone 3)
        assertEquals(69, withMetrics(s, samples, Profile()).load)
        // Weder FTP noch Puls: keine Belastung
        val noHr = samples.map { it.copy(heartRate = null) }
        assertNull(withMetrics(s, noHr, Profile()).load)
    }

    @Test
    fun fitnessUndVerbesserung() {
        val rides = (0..60).filter { it % 2 == 0 }.map {
            ride(today - it, 60, 180 + (60 - it) / 3, 140, 200 + (60 - it) / 2)
        }
        val series = fitnessSeries(rides, today, 84, utc)
        assertEquals(85, series.size)
        assertTrue(series.last().fitness > series[series.size - 29].fitness)
        assertTrue(series.last().fatigue > series.last().fitness)

        val imp = improvement(rides, today, utc)
        assertEquals(UP, imp.fitnessDir())
        assertEquals(UP, imp.efficiencyDir())
        assertEquals(UP, imp.best20Dir())
        assertEquals(UP, imp.verdict)

        // Schwächer werdende Werte
        val worse = (0..60).filter { it % 2 == 0 }.map {
            ride(today - it, if (it < 28) 20 else 80, 220 - (60 - it) / 3, 140, 240 - (60 - it) / 2)
        }
        assertEquals(DOWN, improvement(worse, today, utc).verdict)

        // Zu wenig Daten und ausgenommene Fahrten
        assertNull(improvement(emptyList(), today, utc).verdict)
        assertNull(improvement(listOf(ride(today, 50)), today, utc).verdict)
        assertNull(improvement(rides.map { it.copy(analysis = false) }, today, utc).verdict)
        assertEquals(0.0, fitnessSeries(rides.map { it.copy(analysis = false) }, today, 84, utc).last().fitness, 0.0)
    }

    @Test
    fun erholung() {
        val base = (1..10).map { Recovery(today - it, 50, 60, 450, 3) }
        assertEquals(RecoveryLevel.UNKNOWN, recoveryStatus(base, today).level)
        assertEquals(RecoveryLevel.GOOD, recoveryStatus(base + Recovery(today, 51, 59, 440, 4), today).level)
        assertEquals(RecoveryLevel.MEDIUM, recoveryStatus(base + Recovery(today, 56, 60), today).level)
        val low = recoveryStatus(base + Recovery(today, 60, 45, 280, 1), today)
        assertEquals(RecoveryLevel.LOW, low.level)
        assertEquals(4, low.reasons.size)
        assertEquals(60, low.baseHrv)
        assertEquals(50, low.baseRestHr)
        // Ohne eigenen Schnitt zählen nur Schlaf und Gefühl
        assertEquals(RecoveryLevel.GOOD, recoveryStatus(listOf(Recovery(today, 70, 20)), today).level)
        assertEquals(RecoveryLevel.LOW, recoveryStatus(listOf(Recovery(today, feel = 1)), today).level)

        // Zusammenführen: neuer Eintrag ersetzt, leerer löscht
        val merged = mergeRecovery(base, listOf(Recovery(today - 1), Recovery(today, 50)))
        assertEquals(today, merged[0].day)
        assertEquals(today - 2, merged[1].day)
        assertEquals(10, merged.size)
        assertEquals(merged, parseRecovery(recoveryToJson(merged)))
        assertTrue(parseRecovery("kaputt").isEmpty())
    }

    @Test
    fun csvImport() {
        val a = parseRecoveryCsv("\uFEFFDatum;Ruhepuls;HRV;Schlaf;Gefühl\n15.11.2026;52;61;7,5;4\n2026-11-14;55;;420;\nkaputt;1;2\n")
        assertEquals(2, a.size)
        assertEquals(Recovery(20772, 52, 61, 450, 4), a[0])
        assertEquals(Recovery(20771, 55, null, 420, null), a[1])
        // Englische Kopfzeile, andere Reihenfolge, Komma als Trennzeichen
        val b = parseRecoveryCsv("date,hrv_rmssd,resting_hr,sleep_hours\n2026-11-15T07:00:00,61.4,52,7.25\n")
        assertEquals(listOf(Recovery(20772, 52, 61, 435, null)), b)
        // Ohne Kopfzeile
        assertEquals(listOf(Recovery(20772, 52, 61, 450, 4)), parseRecoveryCsv("2026-11-15,52,61,7.5,4"))
        assertTrue(parseRecoveryCsv("").isEmpty())
        assertTrue(parseRecoveryCsv("irgendein Text").isEmpty())
    }

    @Test
    fun vo2max() {
        var list = mergeVo2(emptyList(), Vo2(today - 30, 48.0))
        list = mergeVo2(list, Vo2(today, 50.5))
        list = mergeVo2(list, Vo2(today, 51.0))
        assertEquals(listOf(Vo2(today, 51.0), Vo2(today - 30, 48.0)), list)
        assertEquals(list, parseVo2(vo2ToJson(list)))
        assertNotNull(parseVo2(null))
    }
}
