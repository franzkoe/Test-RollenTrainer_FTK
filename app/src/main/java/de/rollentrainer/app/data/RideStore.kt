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

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Speichert aufgezeichnete Fahrten als Dateien im App-Speicher:
 * `<id>.meta.json` (Zusammenfassung) und `<id>.data.json` (Messpunkte).
 */
class RideStore(context: Context) {

    private val dir = File(context.filesDir, "rides")

    private fun metaFile(id: String) = File(dir, "$id.meta.json")
    private fun dataFile(id: String) = File(dir, "$id.data.json")

    suspend fun list(): List<RideSummary> = withContext(Dispatchers.IO) {
        val files = dir.listFiles { f -> f.name.endsWith(".meta.json") } ?: return@withContext emptyList()
        files.mapNotNull { f ->
            try {
                parseSummary(JSONObject(f.readText()))
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.startMs }
    }

    suspend fun save(s: RideSummary, samples: List<Sample>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dataFile(s.id).writeText(samplesToJson(samples).toString())
        metaFile(s.id).writeText(summaryToJson(s).toString())
    }

    suspend fun updateSummary(s: RideSummary) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        metaFile(s.id).writeText(summaryToJson(s).toString())
    }

    suspend fun loadSamples(id: String): List<Sample> = withContext(Dispatchers.IO) {
        try {
            parseSamples(JSONObject(dataFile(id).readText()))
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        metaFile(id).delete()
        dataFile(id).delete()
        Unit
    }

    // ---------------------------------------------------------------- JSON

    private fun JSONObject.putOpt2(key: String, v: Any?): JSONObject =
        put(key, v ?: JSONObject.NULL)

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else getDouble(key)

    private fun summaryToJson(s: RideSummary): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("startMs", s.startMs)
        .put("durationSec", s.durationSec)
        .put("distanceM", s.distanceM)
        .putOpt2("avgPower", s.avgPower)
        .putOpt2("maxPower", s.maxPower)
        .putOpt2("avgSpeed", s.avgSpeed)
        .putOpt2("maxSpeed", s.maxSpeed)
        .putOpt2("avgHr", s.avgHr)
        .putOpt2("maxHr", s.maxHr)
        .putOpt2("avgCadence", s.avgCadence)
        .putOpt2("kcal", s.kcal)

    private fun parseSummary(o: JSONObject) = RideSummary(
        id = o.getString("id"),
        name = o.getString("name"),
        startMs = o.getLong("startMs"),
        durationSec = o.getInt("durationSec"),
        distanceM = o.getDouble("distanceM"),
        avgPower = o.intOrNull("avgPower"),
        maxPower = o.intOrNull("maxPower"),
        avgSpeed = o.doubleOrNull("avgSpeed"),
        maxSpeed = o.doubleOrNull("maxSpeed"),
        avgHr = o.intOrNull("avgHr"),
        maxHr = o.intOrNull("maxHr"),
        avgCadence = o.intOrNull("avgCadence"),
        kcal = o.intOrNull("kcal"),
    )

    private fun samplesToJson(samples: List<Sample>): JSONObject {
        val t = JSONArray()
        val e = JSONArray()
        val p = JSONArray()
        val s = JSONArray()
        val d = JSONArray()
        val c = JSONArray()
        val h = JSONArray()
        samples.forEach {
            t.put(it.tSec)
            e.put(it.epochMs)
            p.put(it.powerW ?: JSONObject.NULL)
            s.put(it.speedKmh ?: JSONObject.NULL)
            d.put(it.distanceM)
            c.put(it.cadenceRpm ?: JSONObject.NULL)
            h.put(it.heartRate ?: JSONObject.NULL)
        }
        return JSONObject().put("t", t).put("e", e).put("p", p).put("s", s)
            .put("d", d).put("c", c).put("h", h)
    }

    private fun parseSamples(o: JSONObject): List<Sample> {
        val t = o.getJSONArray("t")
        val e = o.getJSONArray("e")
        val p = o.getJSONArray("p")
        val s = o.getJSONArray("s")
        val d = o.getJSONArray("d")
        val c = o.getJSONArray("c")
        val h = o.getJSONArray("h")
        return (0 until t.length()).map { i ->
            Sample(
                tSec = t.getInt(i),
                epochMs = e.getLong(i),
                powerW = if (p.isNull(i)) null else p.getInt(i),
                speedKmh = if (s.isNull(i)) null else s.getDouble(i),
                distanceM = d.getDouble(i),
                cadenceRpm = if (c.isNull(i)) null else c.getDouble(i),
                heartRate = if (h.isNull(i)) null else h.getInt(i),
            )
        }
    }
}
