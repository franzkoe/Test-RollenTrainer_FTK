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

package de.rollentrainer.app.ble

/** Live-Werte aus "Indoor Bike Data" (0x2AD2). Nicht gelieferte Werte bleiben null. */
data class BikeData(
    val speedKmh: Double? = null,
    val cadenceRpm: Double? = null,
    val distanceM: Int? = null,
    val powerW: Int? = null,
    val energyKcal: Int? = null,
    val heartRate: Int? = null,
    val elapsedS: Int? = null,
) {
    /** Übernimmt alle Werte aus [n], die dort vorhanden sind (FTMS darf Datensätze aufteilen). */
    fun merge(n: BikeData) = BikeData(
        speedKmh = n.speedKmh ?: speedKmh,
        cadenceRpm = n.cadenceRpm ?: cadenceRpm,
        distanceM = n.distanceM ?: distanceM,
        powerW = n.powerW ?: powerW,
        energyKcal = n.energyKcal ?: energyKcal,
        heartRate = n.heartRate ?: heartRate,
        elapsedS = n.elapsedS ?: elapsedS,
    )
}

data class PowerRange(val min: Int, val max: Int, val step: Int)

object FtmsParser {

    private class Reader(val b: ByteArray, var i: Int) {
        fun has(n: Int) = i + n <= b.size
        fun u8(): Int {
            val v = b[i].toInt() and 0xFF
            i += 1
            return v
        }

        fun u16(): Int {
            val v = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
            i += 2
            return v
        }

        fun s16(): Int = u16().toShort().toInt()
        fun u24(): Int {
            val v = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
                ((b[i + 2].toInt() and 0xFF) shl 16)
            i += 3
            return v
        }
    }

    /** Parst Indoor Bike Data gemäß Flags-Feld. Gibt null zurück, wenn das Paket zu kurz ist. */
    fun parseIndoorBikeData(b: ByteArray): BikeData? {
        if (b.size < 2) return null
        val r = Reader(b, 0)
        val flags = r.u16()

        var speed: Double? = null
        var cadence: Double? = null
        var distance: Int? = null
        var power: Int? = null
        var energy: Int? = null
        var hr: Int? = null
        var elapsed: Int? = null

        fun result() = BikeData(speed, cadence, distance, power, energy, hr, elapsed)

        // Bit 0 = "More Data": 0 bedeutet, die Momentangeschwindigkeit IST enthalten
        if (flags and 0x0001 == 0) {
            if (!r.has(2)) return result()
            speed = r.u16() / 100.0
        }
        if (flags and 0x0002 != 0) { // Durchschnittsgeschwindigkeit
            if (!r.has(2)) return result()
            r.u16()
        }
        if (flags and 0x0004 != 0) { // Momentankadenz, 0,5 rpm
            if (!r.has(2)) return result()
            cadence = r.u16() / 2.0
        }
        if (flags and 0x0008 != 0) { // Durchschnittskadenz
            if (!r.has(2)) return result()
            r.u16()
        }
        if (flags and 0x0010 != 0) { // Gesamtdistanz, uint24, Meter
            if (!r.has(3)) return result()
            distance = r.u24()
        }
        if (flags and 0x0020 != 0) { // Widerstandsstufe
            if (!r.has(2)) return result()
            r.s16()
        }
        if (flags and 0x0040 != 0) { // Momentanleistung, Watt
            if (!r.has(2)) return result()
            power = r.s16()
        }
        if (flags and 0x0080 != 0) { // Durchschnittsleistung
            if (!r.has(2)) return result()
            r.s16()
        }
        if (flags and 0x0100 != 0) { // Energie: gesamt (kcal), pro Stunde, pro Minute
            if (!r.has(5)) return result()
            val total = r.u16()
            r.u16()
            r.u8()
            if (total != 0xFFFF) energy = total
        }
        if (flags and 0x0200 != 0) { // Herzfrequenz
            if (!r.has(1)) return result()
            hr = r.u8()
        }
        if (flags and 0x0400 != 0) { // Metabolisches Äquivalent
            if (!r.has(1)) return result()
            r.u8()
        }
        if (flags and 0x0800 != 0) { // Verstrichene Zeit, s
            if (!r.has(2)) return result()
            elapsed = r.u16()
        }
        return result()
    }

    /** Supported Power Range (0x2AD8): min sint16, max sint16, Schrittweite uint16. */
    fun parsePowerRange(b: ByteArray): PowerRange? {
        if (b.size < 6) return null
        val r = Reader(b, 0)
        val min = r.s16()
        val max = r.s16()
        val step = r.u16()
        if (max <= min) return null
        return PowerRange(min, max, step)
    }

    /** Supported Resistance Level Range (0x2AD6): liefert die Minimalstufe (Rohwert). */
    fun parseResistanceMin(b: ByteArray): Int? {
        if (b.size < 2) return null
        return Reader(b, 0).s16()
    }

    /** Fitness Machine Feature (0x2ACC): zweites uint32 = Target Setting Features. */
    fun parseTargetFeatures(b: ByteArray): Int? {
        if (b.size < 8) return null
        return (b[4].toInt() and 0xFF) or ((b[5].toInt() and 0xFF) shl 8) or
            ((b[6].toInt() and 0xFF) shl 16) or ((b[7].toInt() and 0xFF) shl 24)
    }

    /** Control-Point-Antwort: [0x80, Request-Opcode, Result-Code]. Gibt den Result-Code zurück. */
    fun parseControlResponse(b: ByteArray): Int? {
        if (b.size < 3 || (b[0].toInt() and 0xFF) != 0x80) return null
        return b[2].toInt() and 0xFF
    }

    fun describeStatus(b: ByteArray): String? {
        if (b.isEmpty()) return null
        return when (b[0].toInt() and 0xFF) {
            0x01 -> "Trainer zurückgesetzt"
            0x02 -> "Trainer gestoppt/pausiert"
            0x03 -> "Trainer per Sicherheitsschalter gestoppt"
            0x04 -> "Trainer gestartet"
            0x07 -> "Widerstandsstufe geändert"
            0x08 -> if (b.size >= 3) {
                val w = ((b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)).toShort().toInt()
                "Zielleistung $w W"
            } else "Zielleistung geändert"

            0x12 -> "Simulationsmodus aktiv"
            0xFF -> "Steuerung verloren"
            else -> null
        }
    }
}
