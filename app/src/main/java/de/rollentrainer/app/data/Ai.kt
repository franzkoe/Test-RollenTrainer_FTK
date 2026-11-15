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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Schnittstelle zu einem frei wählbaren KI-Dienst. Gesprochen wird das verbreitete
 * „Chat Completions“-Format, das die meisten Anbieter und auch eigene Server verstehen.
 * Daten verlassen das Gerät nur, wenn der Nutzer selbst eine Anfrage abschickt.
 */

/** Zugang zum KI-Dienst: Adresse der Schnittstelle, Schlüssel und Modellname. */
data class AiConfig(val url: String = "", val key: String = "", val model: String = "") {
    val ready: Boolean get() = url.isNotBlank() && model.isNotBlank()
}

/** Bekannte Adressen zum schnellen Ausfüllen. Schlüssel und Modell nennt der Anbieter. */
val AI_PRESETS: List<Pair<String, String>> = listOf(
    "OpenAI" to "https://api.openai.com/v1",
    "Anthropic" to "https://api.anthropic.com/v1",
    "Google Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
    "Mistral" to "https://api.mistral.ai/v1",
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "Eigener Server (Ollama)" to "http://192.168.0.10:11434/v1",
)

fun aiConfigToJson(c: AiConfig): String =
    JSONObject().put("url", c.url).put("key", c.key).put("model", c.model).toString()

fun parseAiConfig(json: String?): AiConfig {
    if (json.isNullOrEmpty()) return AiConfig()
    return try {
        val o = JSONObject(json)
        AiConfig(o.optString("url"), o.optString("key"), o.optString("model"))
    } catch (e: Exception) {
        AiConfig()
    }
}

class AiException(message: String) : Exception(message)

fun aiEndpoint(url: String): String {
    val base = url.trim().trimEnd('/')
    return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
}

/** Schickt eine Anfrage und liefert den Antworttext. Fehler kommen als [AiException]. */
suspend fun aiChat(cfg: AiConfig, system: String, user: String): String =
    withContext(Dispatchers.IO) {
        val conn = try {
            URL(aiEndpoint(cfg.url)).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("Die Adresse des KI-Dienstes ist ungültig.")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 180_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            if (cfg.key.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + cfg.key.trim())
            }
            val body = JSONObject()
                .put("model", cfg.model.trim())
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)),
                )
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val detail = try {
                    val o = JSONObject(text)
                    val err = o.opt("error")
                    when (err) {
                        is JSONObject -> err.optString("message")
                        is String -> err
                        else -> o.optString("message")
                    }
                } catch (e: Exception) {
                    ""
                }
                val reason = when (code) {
                    401, 403 -> "Der Schlüssel wurde abgelehnt"
                    404 -> "Adresse oder Modell nicht gefunden"
                    429 -> "Zu viele Anfragen oder Guthaben aufgebraucht"
                    else -> "Der KI-Dienst meldet Fehler $code"
                }
                throw AiException(if (detail.isNullOrBlank()) "$reason." else "$reason: ${detail.take(300)}")
            }
            val content = try {
                val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                when (val c = msg.opt("content")) {
                    is String -> c
                    // Manche Dienste liefern den Text in Teilen
                    is JSONArray -> (0 until c.length()).joinToString("") { i ->
                        c.optJSONObject(i)?.optString("text") ?: ""
                    }
                    else -> ""
                }
            } catch (e: Exception) {
                throw AiException("Die Antwort des KI-Dienstes hat ein unbekanntes Format.")
            }
            if (content.isBlank()) throw AiException("Der KI-Dienst hat eine leere Antwort geliefert.")
            content.trim()
        } catch (e: AiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw AiException("Der KI-Dienst hat nicht rechtzeitig geantwortet.")
        } catch (e: Exception) {
            throw AiException(
                "Keine Verbindung zum KI-Dienst. Prüfe Internetverbindung und Adresse."
            )
        } finally {
            conn.disconnect()
        }
    }

// ---------------------------------------------------------------- Anfragen

const val AI_SYSTEM =
    "Du bist ein erfahrener Radsporttrainer. Du berätst jemanden, der zu Hause auf einem " +
        "Rollentrainer trainiert. Antworte auf Deutsch, konkret und knapp, ohne Formatierung " +
        "mit Sternchen oder Rauten. Stelle keine medizinischen Diagnosen."

private fun date(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(ms))

/** Trainingsstand als Text: Profil, Plan mit Fortschritt, nächste Einheit, letzte Fahrten. */
fun aiContext(profile: Profile, state: PlanState?, rides: List<RideSummary>, nowMs: Long): String {
    val sb = StringBuilder()
    sb.append("Heute ist der ").append(date(nowMs)).append(".\n\nPROFIL\n")
    if (profile.ftp > 0) sb.append("FTP: ${profile.ftp} W\n") else sb.append("FTP: nicht bekannt\n")
    sb.append(
        if (profile.power) "Steuerung: Watt werden aus der FTP vorgegeben\n"
        else "Steuerung: Training nach Pulszonen, Widerstand wird selbst gewählt\n"
    )
    sb.append("Pulszonen (Schläge pro Minute): ")
    sb.append((1..5).joinToString(", ") { "Zone $it ${profile.zoneText(it)}" }).append("\n")

    sb.append("\nTRAININGSPLAN\n")
    if (state == null) {
        sb.append("Kein Plan aktiv.\n")
    } else {
        val spec = state.plan.spec
        sb.append("${spec.name}: ${LEVEL_NAMES[spec.level.coerceIn(0, 2)]}, ${spec.summary()}, ")
        sb.append("Steigerung ${spec.stepPct} % pro Woche, gestartet am ${date(state.startMs)}\n")
        sb.append(
            "Stand: ${state.done.size} von ${state.plan.total} Einheiten abgearbeitet, davon " +
                "${state.ridden - state.alternatives} wie geplant, ${state.alternatives} als " +
                "leichtere Alternative, ${state.skipped} übersprungen\n"
        )
        state.plan.weeks.forEachIndexed { w, list ->
            sb.append("Woche ${w + 1}" + (if (isRecoveryWeek(w)) " (Entlastung)" else "") + ": ")
            sb.append(list.mapIndexed { i, s ->
                val d = state.entry(w, i)
                val mark = when (d?.status) {
                    ST_DONE -> "erledigt am ${date(d.dateMs)}" +
                        (d.avgPower?.let { ", Ø $it W" } ?: "") + (d.avgHr?.let { ", Ø Puls $it" } ?: "")
                    ST_ALT -> "ersetzt durch „${d.name}“ am ${date(d.dateMs)}"
                    ST_SKIP -> "übersprungen"
                    else -> "offen"
                }
                "${s.name} [$mark]"
            }.joinToString("; ")).append("\n")
        }
        state.next()?.let { (w, i) ->
            val s = state.plan.weeks[w][i]
            sb.append("Nächste Einheit: ${s.name}, ${s.minutes} min. Ablauf: ")
            sb.append(compactSegs(s)).append("\n")
        }
    }

    sb.append("\nLETZTE FAHRTEN\n")
    if (rides.isEmpty()) sb.append("Noch keine Fahrt gespeichert.\n")
    rides.take(12).forEach { r ->
        sb.append("${date(r.startMs)}: ${r.name}, ${r.durationSec / 60} min")
        sb.append(String.format(Locale.GERMANY, ", %.1f km", r.distanceM / 1000.0))
        r.avgPower?.let { sb.append(", Ø $it W") }
        r.maxPower?.let { sb.append(", max. $it W") }
        r.avgHr?.let { sb.append(", Ø Puls $it") }
        r.maxHr?.let { sb.append(", max. Puls $it") }
        r.avgCadence?.let { sb.append(", Ø Kadenz $it") }
        sb.append("\n")
    }
    return sb.toString()
}

/** Ablauf einer Einheit in Kurzform, gleiche Abschnitte hintereinander zusammengefasst. */
fun compactSegs(s: Session): String {
    val parts = ArrayList<String>()
    s.segs.forEach { g ->
        val t = if (g.seconds % 60 == 0) "${g.seconds / 60} min" else "${g.seconds} s"
        parts.add("$t bei ${g.pct} %")
    }
    return parts.joinToString(", ") + " (Prozent der FTP)"
}

const val AI_ASK_ANALYSIS =
    "Werte meinen Trainingsstand aus. Was läuft gut, was fällt auf, und was sollte ich in den " +
        "nächsten zwei Wochen ändern? Beziehe dich auf die Zahlen. Höchstens 220 Wörter."

fun aiAskSession(profile: Profile): String =
    "Schlage mir genau eine Einheit für heute vor, die zu meinem Stand passt. Schreibe zuerst " +
        "zwei bis drei Sätze Begründung. Hänge danach die Einheit als JSON an, genau in diesem " +
        "Format und ohne weiteren Text danach:\n" +
        "{\"name\":\"Name der Einheit\",\"segments\":[{\"minutes\":10,\"percent\":55," +
        "\"label\":\"Aufwärmen\"}]}\n" +
        "percent ist die Intensität in Prozent der FTP (40 bis 130), minutes darf Kommazahlen " +
        "enthalten. Gesamtdauer höchstens 120 Minuten." +
        if (profile.power) "" else
            " Ich trainiere nach Puls: Wähle percent passend zur Zone (Zone 1: 50, Zone 2: 65, " +
                "Zone 3: 83, Zone 4: 98, Zone 5: 112) und vermeide Abschnitte unter 2 Minuten."

/** Antwort der KI: Text und, falls enthalten, die vorgeschlagene Einheit. */
class AiAnswer(val text: String, val session: Session?)

/** Sucht in der Antwort das JSON der Einheit, prüft die Werte und trennt es vom Text. */
fun parseAiAnswer(raw: String): AiAnswer {
    var i = raw.indexOf('{')
    while (i >= 0) {
        var depth = 0
        var inString = false
        var escaped = false
        var end = -1
        var j = i
        while (j < raw.length) {
            val c = raw[j]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else if (c == '"') {
                inString = true
            } else if (c == '{') {
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0) {
                    end = j
                    break
                }
            }
            j++
        }
        if (end < 0) break
        val session = try {
            sessionFromAi(JSONObject(raw.substring(i, end + 1)))
        } catch (e: Exception) {
            null
        }
        if (session != null) {
            val text = (raw.substring(0, i) + raw.substring(end + 1))
                .replace("```json", "").replace("```", "").trim()
            return AiAnswer(text, session)
        }
        i = raw.indexOf('{', i + 1)
    }
    return AiAnswer(raw.trim(), null)
}

private fun sessionFromAi(o: JSONObject): Session? {
    val arr = o.optJSONArray("segments") ?: return null
    val segs = ArrayList<Seg>()
    for (i in 0 until arr.length()) {
        val g = arr.optJSONObject(i) ?: continue
        val sec = Math.round(g.optDouble("minutes", 0.0) * 60).toInt()
        if (sec < 10) continue
        val pct = g.optInt("percent", 60).coerceIn(30, 150)
        val label = g.optString("label").trim().take(40).ifEmpty { zoneName(zoneOfPct(pct)) }
        segs.add(Seg(sec.coerceAtMost(180 * 60), pct, label, PhaseType.INTERVAL))
    }
    if (segs.isEmpty() || segs.sumOf { it.seconds } > 180 * 60) return null
    val name = o.optString("name").trim().take(50).ifEmpty { "Vorschlag der KI" }
    return Session(name, Kind.CUSTOM, segs, "Vorschlag der KI")
}
