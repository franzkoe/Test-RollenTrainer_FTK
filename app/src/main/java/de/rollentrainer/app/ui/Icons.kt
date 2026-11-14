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

package de.rollentrainer.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Linien-Symbol auf 24er-Raster, eine Strichstärke für alle (Stil angelehnt an Lucide, ISC). */
private fun lineIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object AppIcons {
    val Back = lineIcon("back", "M19 12H5 M12 19l-7-7 7-7")
    val Chevron = lineIcon("chevron", "M9 18l6-6-6-6")
    val Plus = lineIcon("plus", "M12 5v14 M5 12h14")
    val Minus = lineIcon("minus", "M5 12h14")
    val Play = lineIcon("play", "M7 4.5v15l12-7.5z")
    val Pause = lineIcon("pause", "M8 5v14 M16 5v14")
    val Bluetooth = lineIcon("bluetooth", "M7 7l10 10-5 5V2l5 5L7 17")
    val Bike = lineIcon(
        "bike",
        "M2 17.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0 " +
            "M15 17.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0 " +
            "M14 5a1 1 0 1 0 2 0a1 1 0 1 0-2 0 " +
            "M12 17.5V14l-3-3 4-3 2 3h2",
    )
    val Timer = lineIcon("timer", "M10 2h4 M12 14l3-3 M4 14a8 8 0 1 0 16 0a8 8 0 1 0-16 0")
    val Chart = lineIcon("chart", "M3 3v18h18 M7 15l4-5 4 3 5-7")
    val Heart = lineIcon(
        "heart",
        "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2" +
            "-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z",
    )
}
