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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Gestaltung nach der Methode „unslop“ von Damir Sokolovsky (@heydamir, CC BY 4.0,
 * https://github.com/heydamir/unslop):
 * eine neutrale Graphit-Farbreihe für alle Flächen, drei Textrollen, ein Akzent (Petrol),
 * Tiefe über Flächenstufen und Haarlinien, Radius nach Größe statt Pillenform.
 * Farbe tragen nur die Momente: Intensitätsskala, Trainingsprofil und Verlaufskurven.
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF06201D),
    primaryContainer = Color(0xFF123F3A),
    onPrimaryContainer = Color(0xFFB5F2EB),
    secondary = Color(0xFFA1A1A6),
    onSecondary = Color(0xFF141415),
    secondaryContainer = Color(0xFF232325), // angehobene Fläche (Zweitaktionen, Regler)
    onSecondaryContainer = Color(0xFFEDEDED),
    background = Color(0xFF141415),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF141415),
    onSurface = Color(0xFFEDEDED), // Text primär
    surfaceVariant = Color(0xFF19191B), // Karte
    onSurfaceVariant = Color(0xFFA1A1A6), // Text sekundär
    surfaceContainerLowest = Color(0xFF0F0F10),
    surfaceContainerLow = Color(0xFF19191B),
    surfaceContainer = Color(0xFF19191B),
    surfaceContainerHigh = Color(0xFF1E1E20),
    surfaceContainerHighest = Color(0xFF232325),
    outline = Color(0xFF85858B), // Text gedämpft, Feldrahmen
    outlineVariant = Color(0xFF2A2A2D),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3A1A1C),
    onErrorContainer = Color(0xFFF5C6C8),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCEBE6),
    onPrimaryContainer = Color(0xFF06201D),
    secondary = Color(0xFF525252),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF171717),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF525252),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF4F4F5),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val Base = Typography()

/** Standardschrift des Systems; Laufweite 0 statt der leichten Sperrung von Material. */
private val AppTypography = Typography(
    bodyLarge = Base.bodyLarge.copy(letterSpacing = 0.sp),
    bodyMedium = Base.bodyMedium.copy(letterSpacing = 0.sp),
    bodySmall = Base.bodySmall.copy(letterSpacing = 0.sp),
    labelLarge = Base.labelLarge.copy(letterSpacing = 0.sp),
    labelMedium = Base.labelMedium.copy(letterSpacing = 0.sp),
    labelSmall = Base.labelSmall.copy(letterSpacing = 0.sp),
    titleMedium = Base.titleMedium.copy(letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(letterSpacing = 0.sp),
    headlineSmall = Base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp),
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
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/**
 * Intensitätsskala (die Kennfarbe der App): grau (Leerlauf) → grün → gelb → orange → rot.
 * Wird nur als Fläche verwendet (Balken, Streifen, Punkte), nie als Textfarbe.
 */
fun intensityColor(watts: Int): Color = when {
    watts <= 0 -> Color(0xFF8A8A90)
    watts < 130 -> Color(0xFF3FAE77)
    watts < 220 -> Color(0xFF93B83F)
    watts < 300 -> Color(0xFFDDAE2F)
    watts < 400 -> Color(0xFFE08A3C)
    else -> Color(0xFFD9534F)
}

/** Zustandsfarben, gedämpft, passend zu beiden Farbreihen. */
val StatusPositive = Color(0xFF34A86B)
val StatusPending = Color(0xFFDDAE2F)
val StatusNegative = Color(0xFFE5484D)
