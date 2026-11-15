package de.rollentrainer.app

import de.rollentrainer.app.data.AiConfig
import de.rollentrainer.app.data.Done
import de.rollentrainer.app.data.Focus
import de.rollentrainer.app.data.Kind
import de.rollentrainer.app.data.PRESET_PLANS
import de.rollentrainer.app.data.PlanSpec
import de.rollentrainer.app.data.PlanState
import de.rollentrainer.app.data.Profile
import de.rollentrainer.app.data.RideSummary
import de.rollentrainer.app.data.ST_ALT
import de.rollentrainer.app.data.ST_DONE
import de.rollentrainer.app.data.ST_SKIP
import de.rollentrainer.app.data.aiConfigToJson
import de.rollentrainer.app.data.aiContext
import de.rollentrainer.app.data.aiEndpoint
import de.rollentrainer.app.data.alternativeFor
import de.rollentrainer.app.data.buildSession
import de.rollentrainer.app.data.generatePlan
import de.rollentrainer.app.data.hrZonesFromMax
import de.rollentrainer.app.data.isRecoveryWeek
import de.rollentrainer.app.data.parseAiAnswer
import de.rollentrainer.app.data.parseAiConfig
import de.rollentrainer.app.data.parsePlanState
import de.rollentrainer.app.data.parseProfile
import de.rollentrainer.app.data.planStateToJson
import de.rollentrainer.app.data.profileToJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft Plan-Logik, Speicherformat und das Lesen der KI-Antwort. */
class PlanTest {

    @Test
    fun einheitenFuellenDieGewuenschteDauer() {
        for (kind in listOf(Kind.RECOVERY, Kind.ENDURANCE, Kind.TEMPO, Kind.SWEETSPOT, Kind.THRESHOLD, Kind.VO2)) {
            for (minutes in 20..150 step 5) for (level in 0..2) for (variant in 0..1) {
                val s = buildSession(kind, minutes, level, variant)
                assertEquals("${s.name} ($kind, $minutes, $level, $variant)", minutes, s.minutes)
                assertTrue(s.segs.all { it.seconds > 0 && it.pct in 40..125 })
            }
        }
    }

    @Test
    fun planHatEntlastungswochenUndSteigerung() {
        for (spec in PRESET_PLANS + PlanSpec("x", 2, 6, 120, 16, 10) + PlanSpec("y", 0, 2, 20, 4, 0)) {
            val plan = generatePlan(spec)
            assertEquals(spec.weeks, plan.weeks.size)
            assertEquals(spec.weeks * spec.perWeek, plan.total)
            for (w in plan.weeks.indices) {
                if (isRecoveryWeek(w)) {
                    assertTrue("${spec.name} Woche ${w + 1}", plan.weeks[w].none { it.hard })
                    assertTrue(plan.weekMinutes(w) <= plan.weekMinutes(w - 1))
                } else {
                    // höchstens drei harte Einheiten, nie mehr hart als locker bei 4+ Einheiten
                    assertTrue(plan.weeks[w].count { it.hard } <= 3)
                }
            }
            // Umfang wächst über die Belastungswochen nicht um mehr als die eingestellte Steigerung
            val load = plan.weeks.indices.filter { !isRecoveryWeek(it) }.map { plan.weekMinutes(it) }
            for (i in 1 until load.size) {
                assertTrue(load[i] >= load[i - 1])
                assertTrue(load[i] <= load[i - 1] * (1.0 + spec.stepPct / 100.0) + 5 * spec.perWeek + 5)
            }
        }
    }

    @Test
    fun schwerpunkte() {
        val hardKinds = setOf(Kind.TEMPO, Kind.SWEETSPOT, Kind.THRESHOLD, Kind.VO2)
        for (focus in Focus.values()) for (level in 0..2) for (perWeek in 2..6) {
            val spec = PlanSpec("x", level, perWeek, 60, 12, 5, focus = focus)
            val plan = generatePlan(spec)
            assertEquals(12 * perWeek, plan.total)
            plan.weeks.forEachIndexed { w, list ->
                val kinds = list.map { it.kind }.toSet()
                val hard = list.count { it.hard }
                when (focus) {
                    Focus.BASE -> assertTrue("$spec W$w", list.all { it.kind == Kind.ENDURANCE && !it.hard })
                    Focus.NO_VO2 -> assertFalse(Kind.VO2 in kinds)
                    Focus.THRESHOLD -> {
                        assertTrue((kinds intersect hardKinds).all { it == Kind.SWEETSPOT || it == Kind.THRESHOLD })
                        if (!isRecoveryWeek(w)) assertEquals("$spec W$w", minOf(perWeek, 4), hard)
                    }
                    Focus.VO2 -> {
                        assertTrue((kinds intersect hardKinds).all { it == Kind.VO2 })
                        if (!isRecoveryWeek(w)) assertEquals("$spec W$w", minOf(perWeek, 3), hard)
                    }
                    Focus.MIXED -> {}
                }
                if (isRecoveryWeek(w)) assertEquals(0, hard)
            }
        }
        // Gemischt bleibt wie bisher
        assertEquals(generatePlan(PRESET_PLANS[2]), generatePlan(PRESET_PLANS[2].copy(focus = Focus.MIXED)))
        // Schwerpunkt wird gespeichert
        val st = PlanState(generatePlan(PRESET_PLANS[1].copy(focus = Focus.VO2)), 0L)
        assertEquals(Focus.VO2, parsePlanState(planStateToJson(st))!!.plan.spec.focus)
    }

    @Test
    fun wattAusFtpUndZonenAusPuls() {
        val s = buildSession(Kind.SWEETSPOT, 45)
        val watts = s.phases(Profile(ftp = 200, byHr = false))
        assertEquals(listOf(110, 180, 110, 180, 100), watts.map { it.watts })
        assertEquals(listOf(1, 3, 1, 3, 1), watts.map { it.zone })
        // ohne FTP: keine Wattvorgabe, nur Zonen
        assertTrue(s.phases(Profile(ftp = 0, byHr = false)).all { it.watts == 0 && it.zone > 0 })
        assertTrue(s.phases(Profile(ftp = 200, byHr = true)).all { it.watts == 0 })

        val p = Profile(zoneMax = hrZonesFromMax(185))
        assertEquals(listOf(111, 130, 148, 167), p.zoneMax)
        assertEquals(1, p.zoneOfHr(111))
        assertEquals(2, p.zoneOfHr(112))
        assertEquals(5, p.zoneOfHr(168))
        assertEquals("bis 111", p.zoneText(1))
        assertEquals("131–148", p.zoneText(3))
        assertEquals("ab 168", p.zoneText(5))
    }

    @Test
    fun alternativeIstLeichter() {
        for (kind in Kind.values().filter { it != Kind.CUSTOM }) {
            val s = buildSession(kind, 60)
            val alt = alternativeFor(s)
            assertFalse(alt.hard)
            assertTrue("$kind", alt.load() < s.load())
        }
    }

    @Test
    fun fortschritt() {
        var st = PlanState(generatePlan(PRESET_PLANS[0]), 1000L)
        assertEquals(0 to 0, st.next())
        st = st.copy(done = st.done + Done(0, 0, ST_DONE, 5000L, "a", 1800, 150, 140, hard = true))
        assertEquals(0 to 1, st.next())
        assertTrue(st.hardRecently(6000L))
        assertFalse(st.hardRecently(5000L + 37 * 3600_000L))
        st = st.copy(done = st.done + Done(0, 1, ST_SKIP, 6000L, "b"))
        assertEquals(1 to 0, st.next())
        st = st.copy(done = st.done + Done(1, 0, ST_ALT, 7000L, "c", 1200))
        assertEquals(2, st.ridden)
        assertEquals(1, st.alternatives)
        assertEquals(1, st.skipped)
        assertEquals(3000, st.riddenSeconds)
        assertEquals(30, st.riddenMinutes(0))

        // Speichern und Laden ergibt denselben Stand
        assertEquals(st, parsePlanState(planStateToJson(st)))
        assertNull(parsePlanState(""))
        assertNull(parsePlanState("{kaputt"))

        // Alles abgearbeitet
        var all = st
        while (true) {
            val (w, i) = all.next() ?: break
            all = all.copy(done = all.done + Done(w, i, ST_DONE, 0L, "x"))
        }
        assertTrue(all.finished)
        assertEquals(1f, all.progress)
    }

    @Test
    fun profilUndZugangSpeichern() {
        val p = Profile(ftp = 245, byHr = false, zoneMax = listOf(110, 128, 146, 165))
        assertEquals(p, parseProfile(profileToJson(p)))
        assertEquals(Profile(), parseProfile(null))
        // Watt pro kg
        val heavy = Profile(ftp = 220, byHr = false, weight = 75)
        assertEquals(heavy, parseProfile(profileToJson(heavy)))
        assertEquals("2,93 W/kg", heavy.wattsPerKgText())
        assertNull(Profile(ftp = 220).wattsPerKgText())
        assertNull(Profile(weight = 75).wattsPerKgText())
        assertTrue(aiContext(heavy, null, emptyList(), 0L).contains("FTP pro kg: 2,93 W/kg"))
        val c = AiConfig("https://api.example.com/v1", "geheim", "modell-1")
        assertEquals(c, parseAiConfig(aiConfigToJson(c)))
        assertEquals("https://api.example.com/v1/chat/completions", aiEndpoint(" https://api.example.com/v1/ "))
        assertEquals("http://10.0.0.2:11434/v1/chat/completions", aiEndpoint("http://10.0.0.2:11434/v1/chat/completions"))
    }

    @Test
    fun kiAntwortLesen() {
        val raw = "Heute passt etwas Lockeres {siehe unten}.\n```json\n" +
            "{\"name\":\"Lockere 40\",\"segments\":[{\"minutes\":10,\"percent\":55,\"label\":\"Aufwärmen\"}," +
            "{\"minutes\":25,\"percent\":68,\"label\":\"Grundlage \\\"ruhig\\\"\"},{\"minutes\":5.5,\"percent\":50}]}\n```"
        val a = parseAiAnswer(raw)
        val s = a.session
        assertNotNull(s)
        assertEquals("Lockere 40", s!!.name)
        assertEquals(listOf(600, 1500, 330), s.segs.map { it.seconds })
        assertEquals(listOf(55, 68, 50), s.segs.map { it.pct })
        assertEquals("Heute passt etwas Lockeres {siehe unten}.", a.text)

        // ohne brauchbares JSON bleibt der Text unverändert
        assertNull(parseAiAnswer("Nur Text {\"a\":1}").session)
        assertEquals("Nur Text {\"a\":1}", parseAiAnswer("Nur Text {\"a\":1}").text)
        // unsinnige Werte werden begrenzt bzw. verworfen
        val odd = parseAiAnswer("{\"segments\":[{\"minutes\":5,\"percent\":900},{\"minutes\":0,\"percent\":60}]}").session
        assertEquals(1, odd!!.segs.size)
        assertEquals(150, odd.segs[0].pct)
        assertNull(parseAiAnswer("{\"segments\":[{\"minutes\":500,\"percent\":60},{\"minutes\":500,\"percent\":60}]}").session)
    }

    @Test
    fun trainingsstandAlsText() {
        val st = PlanState(generatePlan(PRESET_PLANS[2]), 0L, listOf(Done(0, 0, ST_DONE, 0L, "a", 3600, 180, 150, true)))
        val ride = RideSummary("1", "Freie Fahrt", 0L, 1800, 12000.0, 150, 300, 24.0, 30.0, 140, 160, 85, 250)
        val text = aiContext(Profile(ftp = 220, byHr = false), st, listOf(ride), 0L)
        assertTrue(text.contains("FTP: 220 W"))
        assertTrue(text.contains("Aufbau"))
        assertTrue(text.contains("1 von 24 Einheiten"))
        assertTrue(text.contains("Nächste Einheit: Grundlage 60 min"))
        assertTrue(text.contains("Freie Fahrt, 30 min, 12,0 km, Ø 150 W"))
        assertTrue(aiContext(Profile(), null, emptyList(), 0L).contains("Kein Plan aktiv"))
    }
}
