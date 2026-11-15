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

import org.json.JSONArray
import org.json.JSONObject

/* Speicherformat (JSON) für Profil, Plan und Fortschritt. */

fun profileToJson(p: Profile): String = JSONObject()
    .put("ftp", p.ftp)
    .put("byHr", p.byHr)
    .put("zones", JSONArray(p.zoneMax))
    .put("weight", p.weight)
    .toString()

fun parseProfile(json: String?): Profile {
    if (json.isNullOrEmpty()) return Profile()
    return try {
        val o = JSONObject(json)
        val arr = o.optJSONArray("zones")
        val zones = if (arr != null && arr.length() == 4) {
            (0 until 4).map { arr.getInt(it) }
        } else {
            Profile().zoneMax
        }
        Profile(
            ftp = o.optInt("ftp", 0).coerceIn(0, Profile.FTP_MAX),
            byHr = o.optBoolean("byHr", true),
            zoneMax = zones,
            weight = o.optInt("weight", 0).let { if (it < Profile.WEIGHT_MIN || it > Profile.WEIGHT_MAX) 0 else it },
        )
    } catch (e: Exception) {
        Profile()
    }
}

fun sessionToJson(s: Session): JSONObject {
    val segs = JSONArray()
    s.segs.forEach { g ->
        segs.put(JSONArray().put(g.seconds).put(g.pct).put(g.type.name).put(g.label))
    }
    return JSONObject().put("name", s.name).put("kind", s.kind.name).put("note", s.note).put("segs", segs)
}

fun parseSession(o: JSONObject): Session {
    val arr = o.getJSONArray("segs")
    val segs = (0 until arr.length()).map { i ->
        val g = arr.getJSONArray(i)
        Seg(
            seconds = g.getInt(0),
            pct = g.getInt(1),
            label = g.getString(3),
            type = try {
                PhaseType.valueOf(g.getString(2))
            } catch (e: Exception) {
                PhaseType.INTERVAL
            },
        )
    }
    val kind = try {
        Kind.valueOf(o.optString("kind"))
    } catch (e: Exception) {
        Kind.CUSTOM
    }
    return Session(o.getString("name"), kind, segs, o.optString("note", ""))
}

private fun specToJson(s: PlanSpec): JSONObject = JSONObject()
    .put("name", s.name)
    .put("level", s.level)
    .put("perWeek", s.perWeek)
    .put("minutes", s.minutes)
    .put("weeks", s.weeks)
    .put("stepPct", s.stepPct)
    .put("text", s.text)
    .put("focus", s.focus.name)

private fun parseSpec(o: JSONObject): PlanSpec = PlanSpec(
    name = o.optString("name", "Trainingsplan"),
    level = o.optInt("level", 1),
    perWeek = o.optInt("perWeek", 3),
    minutes = o.optInt("minutes", 45),
    weeks = o.optInt("weeks", 8),
    stepPct = o.optInt("stepPct", 5),
    text = o.optString("text", ""),
    focus = try {
        Focus.valueOf(o.optString("focus", Focus.MIXED.name))
    } catch (e: Exception) {
        Focus.MIXED
    },
)

fun planStateToJson(st: PlanState): String {
    val weeks = JSONArray()
    st.plan.weeks.forEach { list ->
        val arr = JSONArray()
        list.forEach { arr.put(sessionToJson(it)) }
        weeks.put(arr)
    }
    val done = JSONArray()
    st.done.forEach { d ->
        val o = JSONObject()
            .put("week", d.week)
            .put("index", d.index)
            .put("status", d.status)
            .put("date", d.dateMs)
            .put("name", d.name)
            .put("seconds", d.seconds)
            .put("hard", d.hard)
        if (d.avgPower != null) o.put("avgPower", d.avgPower)
        if (d.avgHr != null) o.put("avgHr", d.avgHr)
        done.put(o)
    }
    return JSONObject()
        .put("spec", specToJson(st.plan.spec))
        .put("weeks", weeks)
        .put("start", st.startMs)
        .put("done", done)
        .toString()
}

fun parsePlanState(json: String?): PlanState? {
    if (json.isNullOrEmpty()) return null
    return try {
        val o = JSONObject(json)
        val wa = o.getJSONArray("weeks")
        val weeks = (0 until wa.length()).map { w ->
            val arr = wa.getJSONArray(w)
            (0 until arr.length()).map { parseSession(arr.getJSONObject(it)) }
        }
        val da = o.optJSONArray("done") ?: JSONArray()
        val done = (0 until da.length()).map { i ->
            val d = da.getJSONObject(i)
            Done(
                week = d.getInt("week"),
                index = d.getInt("index"),
                status = d.optString("status", ST_DONE),
                dateMs = d.optLong("date", 0L),
                name = d.optString("name", ""),
                seconds = d.optInt("seconds", 0),
                avgPower = if (d.has("avgPower")) d.getInt("avgPower") else null,
                avgHr = if (d.has("avgHr")) d.getInt("avgHr") else null,
                hard = d.optBoolean("hard", false),
            )
        }
        if (weeks.isEmpty() || weeks.any { it.isEmpty() }) return null
        PlanState(Plan(parseSpec(o.getJSONObject("spec")), weeks), o.optLong("start", 0L), done)
    } catch (e: Exception) {
        null
    }
}
