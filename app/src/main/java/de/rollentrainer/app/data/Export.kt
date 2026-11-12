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

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class ExportFormat(val ext: String, val mime: String) {
    FIT("fit", "application/octet-stream"),
    TCX("tcx", "application/vnd.garmin.tcx+xml"),
}

fun exportFileName(s: RideSummary, format: ExportFormat): String {
    val f = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    return "Rollentrainer_" + f.format(Date(s.startMs)) + "." + format.ext
}

/** Schreibt eine FIT-Aktivitätsdatei (Indoor Cycling) für Strava, Garmin Connect, intervals.icu usw. */
object FitEncoder {

    private const val FIT_EPOCH_OFFSET = 631065600L // 31.12.1989 00:00 UTC

    private const val ENUM = 0x00
    private const val UINT8 = 0x02
    private const val UINT16 = 0x84
    private const val UINT32 = 0x86
    private const val UINT32Z = 0x8C

    private val CRC_TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun crc(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0
        for (i in from until to) {
            val b = data[i].toInt() and 0xFF
            var tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[b and 0xF]
            tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[(b shr 4) and 0xF]
        }
        return crc
    }

    private class Field(val num: Int, val type: Int) {
        val size: Int
            get() = when (type) {
                ENUM, UINT8 -> 1
                UINT16 -> 2
                else -> 4
            }
    }

    private class Out {
        val bytes = ByteArrayOutputStream()

        fun define(local: Int, global: Int, fields: List<Field>) {
            bytes.write(0x40 or local)
            bytes.write(0) // reserviert
            bytes.write(0) // Little Endian
            bytes.write(global and 0xFF)
            bytes.write((global shr 8) and 0xFF)
            bytes.write(fields.size)
            fields.forEach {
                bytes.write(it.num)
                bytes.write(it.size)
                bytes.write(it.type)
            }
        }

        /** Datensatz; null-Werte werden als „ungültig“ kodiert. */
        fun data(local: Int, fields: List<Field>, values: List<Long?>) {
            bytes.write(local)
            fields.forEachIndexed { i, f ->
                val max = when (f.size) {
                    1 -> 0xFFL
                    2 -> 0xFFFFL
                    else -> 0xFFFFFFFFL
                }
                val invalid = if (f.type == UINT32Z) 0L else max
                val raw = values[i]
                val v = if (raw == null) invalid else raw.coerceIn(0L, max - 1)
                for (k in 0 until f.size) bytes.write(((v shr (8 * k)) and 0xFF).toInt())
            }
        }
    }

    private fun fitTime(epochMs: Long): Long = epochMs / 1000 - FIT_EPOCH_OFFSET

    fun encode(s: RideSummary, samples: List<Sample>): ByteArray {
        val out = Out()
        val start = fitTime(s.startMs)
        val end = if (samples.isEmpty()) start else fitTime(samples.last().epochMs)
        val elapsedMs = (end - start).coerceAtLeast(0) * 1000
        val timerMs = s.durationSec * 1000L
        val distCm = (s.distanceM * 100).roundToLong()

        // file_id
        val fileId = listOf(
            Field(0, ENUM), Field(1, UINT16), Field(2, UINT16), Field(3, UINT32Z), Field(4, UINT32),
        )
        out.define(0, 0, fileId)
        out.data(0, fileId, listOf(4L, 255L, 0L, 1L, start)) // 4 = activity, 255 = development

        // event: Timer Start
        val event = listOf(Field(253, UINT32), Field(0, ENUM), Field(1, ENUM), Field(4, UINT8))
        out.define(1, 21, event)
        out.data(1, event, listOf(start, 0L, 0L, 0L))

        // record: timestamp, heart_rate, cadence, distance, speed, power
        val record = listOf(
            Field(253, UINT32), Field(3, UINT8), Field(4, UINT8),
            Field(5, UINT32), Field(6, UINT16), Field(7, UINT16),
        )
        out.define(2, 20, record)
        var lastTs = start
        samples.forEach { p ->
            val ts = fitTime(p.epochMs).coerceAtLeast(lastTs)
            lastTs = ts
            out.data(
                2, record,
                listOf(
                    ts,
                    p.heartRate?.takeIf { it > 0 }?.toLong(),
                    p.cadenceRpm?.roundToLong(),
                    (p.distanceM * 100).roundToLong(),
                    p.speedKmh?.let { (it / 3.6 * 1000).roundToLong() },
                    p.powerW?.coerceAtLeast(0)?.toLong(),
                ),
            )
        }

        // event: Timer Stop
        out.data(1, event, listOf(end, 0L, 4L, 0L))

        // lap
        val lap = listOf(
            Field(254, UINT16), Field(253, UINT32), Field(0, ENUM), Field(1, ENUM),
            Field(2, UINT32), Field(7, UINT32), Field(8, UINT32), Field(9, UINT32),
        )
        out.define(3, 19, lap)
        out.data(3, lap, listOf(0L, end, 9L, 1L, start, elapsedMs, timerMs, distCm))

        // session
        val session = listOf(
            Field(254, UINT16), Field(253, UINT32), Field(0, ENUM), Field(1, ENUM),
            Field(2, UINT32), Field(5, ENUM), Field(6, ENUM),
            Field(7, UINT32), Field(8, UINT32), Field(9, UINT32), Field(11, UINT16),
            Field(14, UINT16), Field(15, UINT16), Field(16, UINT8), Field(17, UINT8),
            Field(18, UINT8), Field(20, UINT16), Field(21, UINT16),
            Field(25, UINT16), Field(26, UINT16),
        )
        out.define(4, 18, session)
        out.data(
            4, session,
            listOf(
                0L, end, 8L, 1L,
                start, 2L, 6L, // Sport: Radfahren, Indoor Cycling
                elapsedMs, timerMs, distCm, s.kcal?.toLong(),
                s.avgSpeed?.let { (it / 3.6 * 1000).roundToLong() },
                s.maxSpeed?.let { (it / 3.6 * 1000).roundToLong() },
                s.avgHr?.toLong(), s.maxHr?.toLong(),
                s.avgCadence?.toLong(), s.avgPower?.toLong(), s.maxPower?.toLong(),
                0L, 1L,
            ),
        )

        // activity
        val activity = listOf(
            Field(253, UINT32), Field(0, UINT32), Field(1, UINT16),
            Field(2, ENUM), Field(3, ENUM), Field(4, ENUM), Field(5, UINT32),
        )
        out.define(5, 34, activity)
        val tzOffsetSec = TimeZone.getDefault().getOffset(s.startMs) / 1000
        out.data(5, activity, listOf(end, timerMs, 1L, 0L, 26L, 1L, end + tzOffsetSec))

        val body = out.bytes.toByteArray()
        val file = ByteArrayOutputStream()
        val header = ByteArray(14)
        header[0] = 14
        header[1] = 0x10 // Protokoll 1.0
        header[2] = (2132 and 0xFF).toByte() // Profilversion 21.32
        header[3] = (2132 shr 8).toByte()
        for (k in 0 until 4) header[4 + k] = ((body.size shr (8 * k)) and 0xFF).toByte()
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()
        val hc = crc(header, 0, 12)
        header[12] = (hc and 0xFF).toByte()
        header[13] = (hc shr 8).toByte()
        file.write(header)
        file.write(body)
        val all = file.toByteArray()
        val fc = crc(all)
        file.write(fc and 0xFF)
        file.write(fc shr 8)
        return file.toByteArray()
    }
}

/** Schreibt eine TCX-Datei (Garmin Training Center XML). */
object TcxWriter {

    private fun iso(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun num(d: Double) = String.format(Locale.US, "%.2f", d)

    fun write(s: RideSummary, samples: List<Sample>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" ")
        sb.append("xmlns:ns3=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\">\n")
        sb.append(" <Activities>\n  <Activity Sport=\"Biking\">\n")
        sb.append("   <Id>").append(iso(s.startMs)).append("</Id>\n")
        sb.append("   <Lap StartTime=\"").append(iso(s.startMs)).append("\">\n")
        sb.append("    <TotalTimeSeconds>").append(s.durationSec).append("</TotalTimeSeconds>\n")
        sb.append("    <DistanceMeters>").append(num(s.distanceM)).append("</DistanceMeters>\n")
        s.maxSpeed?.let { sb.append("    <MaximumSpeed>").append(num(it / 3.6)).append("</MaximumSpeed>\n") }
        sb.append("    <Calories>").append(s.kcal ?: 0).append("</Calories>\n")
        s.avgHr?.let {
            sb.append("    <AverageHeartRateBpm><Value>").append(it).append("</Value></AverageHeartRateBpm>\n")
        }
        s.maxHr?.let {
            sb.append("    <MaximumHeartRateBpm><Value>").append(it).append("</Value></MaximumHeartRateBpm>\n")
        }
        sb.append("    <Intensity>Active</Intensity>\n")
        s.avgCadence?.let { sb.append("    <Cadence>").append(it.coerceIn(0, 254)).append("</Cadence>\n") }
        sb.append("    <TriggerMethod>Manual</TriggerMethod>\n    <Track>\n")
        samples.forEach { p ->
            sb.append("     <Trackpoint><Time>").append(iso(p.epochMs)).append("</Time>")
            sb.append("<DistanceMeters>").append(num(p.distanceM)).append("</DistanceMeters>")
            p.heartRate?.takeIf { it > 0 }?.let {
                sb.append("<HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>")
            }
            p.cadenceRpm?.let { sb.append("<Cadence>").append(it.roundToInt().coerceIn(0, 254)).append("</Cadence>") }
            if (p.speedKmh != null || p.powerW != null) {
                sb.append("<Extensions><ns3:TPX>")
                p.speedKmh?.let { sb.append("<ns3:Speed>").append(num(it / 3.6)).append("</ns3:Speed>") }
                p.powerW?.let { sb.append("<ns3:Watts>").append(it.coerceAtLeast(0)).append("</ns3:Watts>") }
                sb.append("</ns3:TPX></Extensions>")
            }
            sb.append("</Trackpoint>\n")
        }
        sb.append("    </Track>\n   </Lap>\n")
        sb.append("   <Notes>").append(esc(s.name)).append("</Notes>\n")
        sb.append("  </Activity>\n </Activities>\n</TrainingCenterDatabase>\n")
        return sb.toString()
    }
}
