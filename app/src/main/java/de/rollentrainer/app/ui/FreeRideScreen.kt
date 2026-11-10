package de.rollentrainer.app.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.AppViewModel
import de.rollentrainer.app.data.LEVEL_WATTS
import de.rollentrainer.app.data.MAX_LEVEL

@Composable
fun FreeRideScreen(vm: AppViewModel) {
    val ride by vm.ride.collectAsState()
    val data by vm.bike.collectAsState()
    val conn by vm.connState.collectAsState()
    val base by vm.baseline.collectAsState()
    var askEnd by remember { mutableStateOf(false) }

    KeepScreenOn()
    // Auch die System-Zurück-Geste löst den Dialog aus
    BackHandler { askEnd = true }

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(Modifier.fillMaxSize()) {
        TopBar("Freie Fahrt", conn, onBack = { askEnd = true })

        val countdown = ride.countdown
        if (countdown != null) {
            BigCountdown(countdown)
        } else {
            val metrics = buildMetrics(data, base.first, base.second)
            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ElapsedTime(ride.elapsedSec, small = true)
                        MetricGrid(metrics, columns = 3, compact = true)
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LevelControl(ride.level, onChange = { vm.changeLevel(it) })
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ElapsedTime(ride.elapsedSec, small = false)
                    MetricGrid(metrics, columns = 2, compact = false)
                    LevelControl(ride.level, onChange = { vm.changeLevel(it) })
                }
            }
        }
    }

    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text("Einheit beenden?") },
            confirmButton = {
                Button(
                    onClick = {
                        askEnd = false
                        vm.endSession()
                    },
                    modifier = Modifier.height(52.dp),
                ) { Text("Ja", fontSize = 18.sp) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { askEnd = false },
                    modifier = Modifier.height(52.dp),
                ) { Text("Nein", fontSize = 18.sp) }
            },
        )
    }
}

@Composable
private fun ElapsedTime(seconds: Int, small: Boolean) {
    Text(
        formatHms(seconds),
        fontSize = if (small) 52.sp else 68.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Widerstandssteuerung: große „–“/„+“-Tasten, Stufe und Watt, Stufenleiste. */
@Composable
private fun LevelControl(level: Int, onChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val watts = LEVEL_WATTS[level]
    val color by animateColorAsState(intensityColor(watts), label = "levelColor")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChange(-1)
                },
                enabled = level > 0,
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(26.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("–", fontSize = 56.sp, fontWeight = FontWeight.Bold) }

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Stufe",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                    targetState = level,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { it } + fadeIn()) togetherWith
                                (slideOutVertically { -it } + fadeOut())
                        } else {
                            (slideInVertically { -it } + fadeIn()) togetherWith
                                (slideOutVertically { it } + fadeOut())
                        }
                    },
                    label = "level",
                ) { l ->
                    Text(
                        l.toString(),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                    )
                }
                Text(
                    if (level == 0) "Leerlauf" else "$watts W",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChange(1)
                },
                enabled = level < MAX_LEVEL,
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(26.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("+", fontSize = 56.sp, fontWeight = FontWeight.Bold) }
        }

        // Stufenleiste
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (i in 1..MAX_LEVEL) {
                val active = i <= level
                Box(
                    Modifier
                        .weight(1f)
                        .height((10 + i * 1.2f).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (active) intensityColor(LEVEL_WATTS[i])
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                )
            }
        }
    }
}
