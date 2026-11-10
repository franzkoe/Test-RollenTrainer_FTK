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

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF12504A),
    onPrimaryContainer = Color(0xFFB5F2EB),
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF0B1F3A),
    secondaryContainer = Color(0xFF263447),
    onSecondaryContainer = Color(0xFFD6E3F7),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF1C222B),
    onSurfaceVariant = Color(0xFFA9B4C2),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF151A21),
    surfaceContainer = Color(0xFF1A2028),
    surfaceContainerHigh = Color(0xFF1F2630),
    surfaceContainerHighest = Color(0xFF262E39),
    outline = Color(0xFF5B6675),
    error = Color(0xFFFF6B60),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF3B5B8C),
    secondaryContainer = Color(0xFFDCE6F5),
    onSecondaryContainer = Color(0xFF14233A),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF14181D),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFE6EAF0),
    onSurfaceVariant = Color(0xFF4A5563),
    surfaceContainerLow = Color(0xFFEFF2F6),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE4E9EF),
    surfaceContainerHighest = Color(0xFFDEE4EB),
)

@Composable
fun RollentrainerTheme(light: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = light
                controller.isAppearanceLightNavigationBars = light
            }
        }
    }
    MaterialTheme(
        colorScheme = if (light) LightColors else DarkColors,
        content = content,
    )
}

/** Farbliche Kennzeichnung der Intensität: grau (Leerlauf) → grün → gelb → orange → rot. */
fun intensityColor(watts: Int): Color = when {
    watts <= 0 -> Color(0xFF7A8594)
    watts < 130 -> Color(0xFF34C759)
    watts < 220 -> Color(0xFFA6CE39)
    watts < 300 -> Color(0xFFFFCC00)
    watts < 400 -> Color(0xFFFF9500)
    else -> Color(0xFFFF453A)
}
