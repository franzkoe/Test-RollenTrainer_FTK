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

/* Speicherformat (JSON) für Erholungswerte und VO2max-Einträge. */

fun recoveryToJson(list: List<Recovery>): String {
    val arr = JSONArray()
    list.forEach { r ->
        val o = JSONObject().put("d", r.day)
        r.restHr?.let { o.put("r", it) }
        r.hrv?.let { o.put("h", it) }
        r.sleepMin?.let { o.put("s", it) }
        r.feel?.let { o.put("f", it) }
        arr.put(o)
    }
    return arr.toString()
}

fun parseRecovery(json: String?): List<Recovery> {
    if (json.isNullOrEmpty()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Recovery(
                day = o.getLong("d"),
                restHr = if (o.has("r")) o.getInt("r") else null,
                hrv = if (o.has("h")) o.getInt("h") else null,
                sleepMin = if (o.has("s")) o.getInt("s") else null,
                feel = if (o.has("f")) o.getInt("f") else null,
            )
        }.sortedByDescending { it.day }
    } catch (e: Exception) {
        emptyList()
    }
}

fun vo2ToJson(list: List<Vo2>): String {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("d", it.day).put("v", it.value)) }
    return arr.toString()
}

fun parseVo2(json: String?): List<Vo2> {
    if (json.isNullOrEmpty()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Vo2(o.getLong("d"), o.getDouble("v"))
        }.sortedByDescending { it.day }
    } catch (e: Exception) {
        emptyList()
    }
}
