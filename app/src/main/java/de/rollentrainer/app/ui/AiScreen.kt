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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rollentrainer.app.AI_ANALYSIS
import de.rollentrainer.app.AI_QUESTION
import de.rollentrainer.app.AI_SESSION
import de.rollentrainer.app.Engine
import de.rollentrainer.app.data.AI_PRESETS
import de.rollentrainer.app.data.AiConfig

/**
 * KI-Auswertung: Der Trainingsstand geht an einen frei wählbaren KI-Dienst, der ihn auswertet
 * oder eine Einheit vorschlägt. Ohne eigenen Zugang lässt sich die Anfrage als Text in jede
 * andere App geben.
 */
@Composable
fun PlanAiScreen(vm: Engine) {
    val conn by vm.connState.collectAsState()
    val config by vm.aiConfig.collectAsState()
    val ai by vm.ai.collectAsState()
    val plan by vm.planState.collectAsState()
    val profile by vm.profile.collectAsState()
    val w = win()
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    BackHandler { vm.back() }

    // Anfragetext zum Weitergeben: die eigene Frage, sonst die Auswertung
    fun promptText(): String =
        if (question.isBlank()) vm.aiPrompt(AI_ANALYSIS, "") else vm.aiPrompt(AI_QUESTION, question)

    val ask: @Composable () -> Unit = {
        AiCard("Was soll die KI tun?") {
            Button(
                onClick = { vm.askAi(AI_ANALYSIS) },
                enabled = !ai.busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Stand auswerten", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
            FilledTonalButton(
                onClick = { vm.askAi(AI_SESSION) },
                enabled = !ai.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Einheit für heute vorschlagen", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Eigene Frage") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = { vm.askAi(AI_QUESTION, question) },
                enabled = !ai.busy && question.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Frage stellen", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            AiHint(
                "Mit jeder Anfrage gehen dein Profil, der Stand des Plans und die letzten " +
                    "12 Fahrten an den eingestellten Dienst. Sonst verlässt nichts das Gerät."
            )
        }
    }

    val share: @Composable () -> Unit = {
        AiCard("Ohne eigenen Zugang") {
            AiHint(
                "Gib die Anfrage als Text an eine andere App weiter, zum Beispiel an die App " +
                    "deiner KI, und lies die Antwort dort."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, promptText())
                        try {
                            context.startActivity(Intent.createChooser(send, "Anfrage teilen"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Teilen nicht möglich", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Teilen", fontSize = 16.sp) }
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Trainingsstand", promptText()))
                        Toast.makeText(context, "Anfrage kopiert", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Kopieren", fontSize = 16.sp) }
            }
        }
    }

    val answer: @Composable () -> Unit = {
        if (ai.busy) {
            AiCard("Antwort") {
                AiHint("Die Anfrage läuft. Das kann bis zu einer Minute dauern.")
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                TextButton(onClick = { vm.cancelAi() }) {
                    Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val error = ai.error
        if (error != null) {
            AiCard("Das hat nicht geklappt") {
                Text(error, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        if (ai.text.isNotEmpty()) {
            AiCard("Antwort") {
                SelectionContainer {
                    Text(ai.text, fontSize = 16.sp, lineHeight = 24.sp)
                }
            }
        }
        val s = ai.session
        if (s != null) {
            AiCard("Vorgeschlagene Einheit") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.name, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${s.minutes} min · Belastung ${s.load()}",
                        fontSize = 14.sp,
                        style = Tnum,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WorkoutChart(s.phases(profile), Modifier.fillMaxWidth().height(56.dp))
                val open = plan?.next() != null
                if (open) {
                    Button(
                        onClick = { vm.startAiSession(forPlan = true) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Statt der nächsten Plan-Einheit fahren", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    FilledTonalButton(
                        onClick = { vm.startAiSession(forPlan = false) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Zusätzlich fahren", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                } else {
                    Button(
                        onClick = { vm.startAiSession(forPlan = false) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { IconLabel(AppIcons.Play, "Starten") }
                }
            }
        } else if (ai.text.isNotEmpty() && ai.text.contains("\"segments\"")) {
            AiHint("Die vorgeschlagene Einheit ließ sich nicht lesen. Frag am besten noch einmal.")
        }
    }

    val service: @Composable () -> Unit = {
        ServiceCard(config, editing || !config.ready, onEdit = { editing = true }) {
            vm.saveAiConfig(it)
            editing = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("KI-Auswertung", conn, onBack = { vm.back() })
        if (w.landscape) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = w.gutter),
                horizontalArrangement = Arrangement.spacedBy(if (w.tablet) 24.dp else 12.dp),
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ask()
                    share()
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    answer()
                    service()
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = w.gutter)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Solange kein Dienst eingerichtet ist, steht die Einrichtung oben.
                // Die Antwort erscheint direkt unter den Anfragen.
                if (!config.ready) service()
                ask()
                answer()
                share()
                if (config.ready) service()
            }
        }
    }
}

/** Zugang zum KI-Dienst: eingerichtet als eine Zeile, sonst das Formular. */
@Composable
private fun ServiceCard(
    config: AiConfig,
    editing: Boolean,
    onEdit: () -> Unit,
    onSave: (AiConfig) -> Unit,
) {
    if (!editing) {
        Row(
            Modifier.fillMaxWidth().card().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("KI-Dienst", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    config.model + " · " + config.url.substringAfter("://").substringBefore("/"),
                    fontSize = 15.sp,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onEdit) {
                Text("Ändern", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    var url by remember(config) { mutableStateOf(config.url) }
    var key by remember(config) { mutableStateOf(config.key) }
    var model by remember(config) { mutableStateOf(config.model) }
    AiCard("KI-Dienst einrichten") {
        AiHint(
            "Du kannst jeden Dienst nutzen, der das verbreitete Format „Chat Completions“ " +
                "versteht, auch einen eigenen Server. Schlüssel und Modellnamen bekommst du " +
                "bei deinem Anbieter."
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AI_PRESETS.forEach { (name, address) ->
                OutlinedButton(onClick = { url = address }, modifier = Modifier.height(40.dp)) {
                    Text(name, fontSize = 14.sp)
                }
            }
        }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Adresse der Schnittstelle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Schlüssel (API-Key)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Modell") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AiHint(
            "Der Schlüssel bleibt auf diesem Gerät. Adressen mit http statt https sind " +
                "unverschlüsselt und nur für einen Server im eigenen Netz gedacht."
        )
        Button(
            onClick = { onSave(AiConfig(url, key, model)) },
            enabled = url.isNotBlank() && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Speichern", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun AiCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun AiHint(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
