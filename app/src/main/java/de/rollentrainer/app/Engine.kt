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

package de.rollentrainer.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import de.rollentrainer.app.ble.BikeData
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.ble.FoundDevice
import de.rollentrainer.app.ble.FtmsManager
import de.rollentrainer.app.ble.HrManager
import de.rollentrainer.app.data.AI_ASK_ANALYSIS
import de.rollentrainer.app.data.AI_SYSTEM
import de.rollentrainer.app.data.AiAnswer
import de.rollentrainer.app.data.AiConfig
import de.rollentrainer.app.data.AiException
import de.rollentrainer.app.data.Done
import de.rollentrainer.app.data.ExportFormat
import de.rollentrainer.app.data.Focus
import de.rollentrainer.app.data.PlanSpec
import de.rollentrainer.app.data.PlanState
import de.rollentrainer.app.data.Profile
import de.rollentrainer.app.data.ST_ALT
import de.rollentrainer.app.data.ST_DONE
import de.rollentrainer.app.data.ST_SKIP
import de.rollentrainer.app.data.Session
import de.rollentrainer.app.data.aiAskSession
import de.rollentrainer.app.data.aiChat
import de.rollentrainer.app.data.aiContext
import de.rollentrainer.app.data.alternativeFor
import de.rollentrainer.app.data.generatePlan
import de.rollentrainer.app.data.parseAiAnswer
import de.rollentrainer.app.data.FitEncoder
import de.rollentrainer.app.data.LevelConfig
import de.rollentrainer.app.data.Phase
import de.rollentrainer.app.data.RideStore
import de.rollentrainer.app.data.RideSummary
import de.rollentrainer.app.data.Sample
import de.rollentrainer.app.data.SavedDevice
import de.rollentrainer.app.data.Store
import de.rollentrainer.app.data.TYPE_HR
import de.rollentrainer.app.data.TYPE_TRAINER
import de.rollentrainer.app.data.TcxWriter
import de.rollentrainer.app.data.Workout
import de.rollentrainer.app.data.exportFileName
import de.rollentrainer.app.data.summarize
import de.rollentrainer.app.ui.formatHms
import de.rollentrainer.app.ui.formatMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

enum class Screen {
    HOME, DEVICES, SETTINGS, FREE_RIDE, WORKOUTS, EDITOR, WORKOUT_RUN, SUMMARY, HISTORY, RIDE_DETAIL,
    PLAN, PLAN_NEW, PLAN_PROFILE, PLAN_AI,
}

/** countdown: 3, 2, 1, 0 (= „Start!“), null = Fahrt läuft. */
data class RideUi(
    val countdown: Int? = 3,
    val elapsedSec: Int = 0,
    val level: Int = 0,
)

data class RunUi(
    val workoutName: String = "",
    val phases: List<Phase> = emptyList(),
    val countdown: Int? = 3,
    val phaseIndex: Int = 0,
    val phaseRemainingSec: Int = 0,
    val totalRemainingSec: Int = 0,
    val elapsedSec: Int = 0,
    val totalSec: Int = 0,
    val paused: Boolean = false,
    val finished: Boolean = false,
    /** Training nach Puls: keine Wattvorgabe, der Widerstand wird über [level] selbst gestellt. */
    val manual: Boolean = false,
    val level: Int = 0,
)

/** Stand der KI-Anfrage: läuft, Antworttext, Fehler und ggf. die vorgeschlagene Einheit. */
data class AiUi(
    val busy: Boolean = false,
    val text: String = "",
    val error: String? = null,
    val session: Session? = null,
)

const val AI_ANALYSIS = 0
const val AI_SESSION = 1
const val AI_QUESTION = 2

/** Von der App selbst mitgerechnete Live-Werte der laufenden Einheit. */
data class LiveExtra(val distanceM: Double = 0.0, val kcal: Int? = null)

/** Fahrt mit Messpunkten (Zusammenfassung nach dem Beenden bzw. Detailansicht im Verlauf). */
data class RideDetail(val summary: RideSummary, val samples: List<Sample>)

/**
 * Zentrale Ablaufsteuerung. Lebt prozessweit (nicht an die Oberfläche gebunden), damit
 * Verbindung, Zeitmessung, Trainingsablauf und Aufzeichnung weiterlaufen, wenn das Handy
 * im Standby ist oder eine andere App im Vordergrund liegt. Während einer Einheit hält
 * [RideService] den Prozess als Vordergrunddienst am Leben.
 */
class Engine private constructor(private val app: Context) {

    companion object {
        @Volatile
        private var instance: Engine? = null

        fun get(context: Context): Engine =
            instance ?: synchronized(this) {
                instance ?: Engine(context.applicationContext).also { instance = it }
            }
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val store = Store(app)
    private val rideStore = RideStore(app)
    private val ble = FtmsManager(app, scope)
    private val hr = HrManager(app, scope)

    // ---- Navigation ----
    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen

    // ---- Umgebung ----
    private val _permissionsGranted = MutableStateFlow(ble.hasPermissions())
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted
    private val _btEnabled = MutableStateFlow(ble.isBluetoothEnabled())
    val btEnabled: StateFlow<Boolean> = _btEnabled

    private val _batteryHint = MutableStateFlow(false)
    val batteryHint: StateFlow<Boolean> = _batteryHint

    private val _needTrainer = MutableStateFlow(false)
    val needTrainer: StateFlow<Boolean> = _needTrainer

    val lightTheme: StateFlow<Boolean> =
        store.lightTheme.stateIn(scope, SharingStarted.Eagerly, false)
    val levelConfig: StateFlow<LevelConfig> =
        store.levelConfig.stateIn(scope, SharingStarted.Eagerly, LevelConfig())
    val keepScreenOn: StateFlow<Boolean> =
        store.keepScreenOn.stateIn(scope, SharingStarted.Eagerly, true)

    // ---- Trainer ----
    val connState: StateFlow<ConnState> = ble.state
    val connectedAddress: StateFlow<String?> = ble.address
    val bike: StateFlow<BikeData> = ble.bike
    val foundDevices: StateFlow<List<FoundDevice>> = ble.found
    val scanning: StateFlow<Boolean> = ble.scanning
    val trainerStatus: StateFlow<String> = ble.status

    // ---- Pulsmesser ----
    val hrState: StateFlow<ConnState> = hr.state
    val hrAddress: StateFlow<String?> = hr.address

    /** Puls: bevorzugt vom Pulsmesser, sonst vom Trainer (falls er einen liefert). */
    val heartRate: StateFlow<Int?> =
        combine(hr.heartRate, ble.bike) { a, b -> a ?: b.heartRate?.takeIf { it > 0 } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    // ---- Gespeicherte Daten ----
    val devices: StateFlow<List<SavedDevice>> =
        store.devices.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val workouts: StateFlow<List<Workout>> =
        store.workouts.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ---- Trainingsplan ----
    val profile: StateFlow<Profile> =
        store.profile.stateIn(scope, SharingStarted.Eagerly, Profile())
    val planState: StateFlow<PlanState?> =
        store.plan.stateIn(scope, SharingStarted.Eagerly, null)
    val aiConfig: StateFlow<AiConfig> =
        store.ai.stateIn(scope, SharingStarted.Eagerly, AiConfig())
    private val _ai = MutableStateFlow(AiUi())
    val ai: StateFlow<AiUi> = _ai
    private var aiJob: Job? = null

    /** Hinweis auf der Zusammenfassung, ob die Einheit im Plan eingetragen wurde. */
    private val _planNote = MutableStateFlow<String?>(null)
    val planNote: StateFlow<String?> = _planNote

    /** Laufende Einheit aus dem Trainingsplan; [week] -1 = zählt nicht für den Plan. */
    private class PlanRun(
        val week: Int,
        val index: Int,
        val alt: Boolean,
        val name: String,
        val hard: Boolean,
        val totalSec: Int,
    )

    private var planRun: PlanRun? = null

    /** Wohin es nach „Speichern“/„Verwerfen“ geht. */
    private var returnScreen = Screen.HOME

    private val _rides = MutableStateFlow<List<RideSummary>>(emptyList())
    val rides: StateFlow<List<RideSummary>> = _rides

    // ---- Fahrt / Training ----
    private val _ride = MutableStateFlow(RideUi())
    val ride: StateFlow<RideUi> = _ride
    private val _run = MutableStateFlow(RunUi())
    val run: StateFlow<RunUi> = _run
    private val _editing = MutableStateFlow(Workout(id = "", name = ""))
    val editing: StateFlow<Workout> = _editing
    private val _live = MutableStateFlow(LiveExtra())
    val live: StateFlow<LiveExtra> = _live

    /** Gerade beendete, noch nicht gespeicherte Fahrt („Speichern“ / „Verwerfen“). */
    private val _pending = MutableStateFlow<RideDetail?>(null)
    val pending: StateFlow<RideDetail?> = _pending

    /** Im Verlauf geöffnete Fahrt. */
    private val _detail = MutableStateFlow<RideDetail?>(null)
    val detail: StateFlow<RideDetail?> = _detail

    /** Zielwerte (Watt; 0 = Leerlauf) – nur der jeweils neueste Wert wird gesendet. */
    private val targetCh = Channel<Int>(Channel.CONFLATED)
    private var currentTarget = 0
    private var sessionJob: Job? = null
    private var idleJob: Job? = null

    /** true von Beginn des Countdowns bis zum Ende der Einheit. */
    @Volatile
    var sessionRunning = false
        private set

    /** true, sobald der Trainer gesteuert wird (nach dem Countdown). */
    private var sessionActive = false

    /** true, nachdem der Nutzer bewusst „Trennen“ gewählt hat – dann kein Auto-Connect. */
    private var manualDisconnect = false
    private var manualHrDisconnect = false

    // ---- Aufzeichnung ----
    private val samples = ArrayList<Sample>()
    private var recName = ""
    private var recDist = 0.0
    private var recWorkKj = 0.0
    private var recHadPower = false
    private var recLastSec = -1
    private var recLastTrainerDist: Int? = null
    private var recEnergyBase: Int? = null

    private val vibrator: Vibrator? = app.getSystemService(Vibrator::class.java)
    private var tone: ToneGenerator? = null

    init {
        // Sendet Zielwerte nacheinander an den Trainer
        scope.launch {
            for (w in targetCh) {
                if (w <= 0) ble.setIdle() else ble.setTargetPower(w)
            }
        }
        // Nach einem Reconnect während einer Einheit: Kontrolle + Zielwert wiederherstellen
        scope.launch {
            var prev = ble.state.value
            ble.state.collect { s ->
                if (s == ConnState.READY && prev != ConnState.READY && sessionActive) {
                    ble.startSession()
                    targetCh.trySend(currentTarget)
                }
                prev = s
            }
        }
        scope.launch {
            if (!store.batteryHintShown.first()) _batteryHint.value = true
        }
        reloadRides()
        // Nicht gespeicherte Fahrt aus einem früheren App-Lauf wiederherstellen
        scope.launch {
            val draft = rideStore.loadDraft() ?: return@launch
            if (sessionRunning || _pending.value != null) return@launch
            if (draft.samples.size < 2) {
                rideStore.clearDraft()
                return@launch
            }
            val summary = summarize(
                UUID.randomUUID().toString(), draft.name, draft.samples, draft.kcal
            )
            _pending.value = RideDetail(summary, draft.samples)
            if (_screen.value == Screen.HOME) _screen.value = Screen.SUMMARY
        }
    }

    // ------------------------------------------------------------------ Umgebung

    /** Wird bei onResume und nach der Berechtigungsabfrage aufgerufen. */
    fun refreshEnvironment() {
        _permissionsGranted.value = ble.hasPermissions()
        _btEnabled.value = ble.isBluetoothEnabled()
        tryAutoConnect()
    }

    /** Verbindet automatisch mit dem zuletzt genutzten Trainer und Pulsmesser. */
    private fun tryAutoConnect() {
        if (!ble.hasPermissions() || !ble.isBluetoothEnabled()) return
        scope.launch {
            if (!manualDisconnect && ble.state.value == ConnState.DISCONNECTED) {
                store.lastDevice.first()?.let { ble.connect(it) }
            }
            if (!manualHrDisconnect && hr.state.value == ConnState.DISCONNECTED) {
                store.lastHr.first()?.let { hr.connect(it) }
            }
        }
    }

    /** App sichtbar: geplantes Trennen abbrechen. */
    fun onForeground() {
        idleJob?.cancel()
        idleJob = null
    }

    /**
     * App nicht mehr sichtbar: Läuft keine Einheit, wird die Verbindung nach kurzer Zeit
     * getrennt, damit der Trainer für andere Apps frei ist. Während einer Einheit läuft alles weiter.
     */
    fun onBackground() {
        if (sessionRunning) return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(60_000)
            if (!sessionRunning) {
                ble.stopScan()
                ble.disconnect()
                hr.disconnect()
            }
        }
    }

    fun dismissBatteryHint() {
        _batteryHint.value = false
        scope.launch { store.setBatteryHintShown() }
    }

    fun dismissNeedTrainer() {
        _needTrainer.value = false
    }

    fun toggleTheme() {
        val next = !lightTheme.value
        scope.launch { store.setLightTheme(next) }
    }

    /** Stufenzahl und Maximalleistung der Freien Fahrt ändern (wirkt ab der nächsten Fahrt). */
    fun setLevelConfig(levels: Int, maxWatts: Int) {
        val next = LevelConfig(
            levels.coerceIn(LevelConfig.MIN_LEVELS, LevelConfig.MAX_LEVELS),
            maxWatts.coerceIn(LevelConfig.MIN_WATTS, LevelConfig.MAX_WATTS),
        )
        scope.launch { store.setLevelConfig(next) }
    }

    fun toggleKeepScreenOn() {
        val next = !keepScreenOn.value
        scope.launch { store.setKeepScreenOn(next) }
    }

    // ------------------------------------------------------------------ Navigation

    fun open(screen: Screen) {
        _needTrainer.value = false
        _screen.value = screen
    }

    fun back() {
        when (_screen.value) {
            Screen.DEVICES -> {
                ble.stopScan()
                _screen.value = Screen.HOME
            }

            Screen.WORKOUTS, Screen.HISTORY, Screen.SETTINGS, Screen.PLAN ->
                _screen.value = Screen.HOME

            Screen.PLAN_NEW, Screen.PLAN_PROFILE, Screen.PLAN_AI -> _screen.value = Screen.PLAN
            Screen.EDITOR -> _screen.value = Screen.WORKOUTS
            Screen.RIDE_DETAIL -> _screen.value = Screen.HISTORY
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ Geräte

    /** Scan nach Trainern (FTMS) bzw. Pulsmessern; [showAll] zeigt alle Bluetooth-Geräte. */
    fun startScan(type: String, showAll: Boolean) {
        val service = when {
            showAll -> null
            type == TYPE_HR -> HrManager.HR_SERVICE
            else -> FtmsManager.FTMS_SERVICE
        }
        ble.startScan(service)
    }

    fun stopScan() = ble.stopScan()

    fun addDevice(found: FoundDevice, type: String) {
        ble.stopScan()
        scope.launch {
            val list = devices.value
            if (list.none { it.address == found.address }) {
                store.saveDevices(list + SavedDevice(found.address, found.name, type))
            }
            connectByType(found.address, type)
        }
    }

    fun connectDevice(device: SavedDevice) {
        scope.launch { connectByType(device.address, device.type) }
    }

    private suspend fun connectByType(address: String, type: String) {
        if (type == TYPE_HR) {
            store.setLastHr(address)
            manualHrDisconnect = false
            hr.connect(address)
        } else {
            store.setLastDevice(address)
            manualDisconnect = false
            ble.connect(address)
        }
    }

    fun disconnectDevice(device: SavedDevice) {
        if (device.type == TYPE_HR) {
            manualHrDisconnect = true
            hr.disconnect()
        } else {
            manualDisconnect = true
            ble.disconnect()
        }
    }

    fun renameDevice(device: SavedDevice, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        scope.launch {
            store.saveDevices(devices.value.map {
                if (it.address == device.address) it.copy(name = clean) else it
            })
        }
    }

    fun deleteDevice(device: SavedDevice) {
        scope.launch {
            if (device.type == TYPE_HR) {
                if (hr.address.value == device.address) hr.disconnect()
                if (store.lastHr.first() == device.address) store.setLastHr(null)
            } else {
                if (ble.address.value == device.address) ble.disconnect()
                if (store.lastDevice.first() == device.address) store.setLastDevice(null)
            }
            store.saveDevices(devices.value.filter { it.address != device.address })
        }
    }

    // ------------------------------------------------------------------ Gemeinsames

    private fun setTarget(watts: Int) {
        currentTarget = watts
        targetCh.trySend(watts)
    }

    private fun signal(long: Boolean = false) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, if (long) 600 else 250)
        } catch (e: Exception) {
            // Ton ist optional
        }
        try {
            val effect = VibrationEffect.createOneShot(
                if (long) 600L else 200L, VibrationEffect.DEFAULT_AMPLITUDE
            )
            if (Build.VERSION.SDK_INT >= 33) {
                // Als „Alarm“ markiert, damit es auch bei gesperrtem Bildschirm vibriert
                vibrator?.vibrate(
                    effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                )
            } else {
                vibrator?.vibrate(effect)
            }
        } catch (e: Exception) {
            // Vibration ist optional
        }
    }

    private fun requireTrainer(): Boolean {
        if (ble.state.value == ConnState.READY) return true
        _needTrainer.value = true
        return false
    }

    /** Beginn einer Einheit (vor dem Countdown): Vordergrunddienst starten. */
    private fun prepareSession(name: String) {
        idleJob?.cancel()
        sessionRunning = true
        samples.clear()
        recName = name
        _live.value = LiveExtra()
        _pending.value = null
        RideService.start(app)
    }

    /** Nach dem Countdown: Trainersteuerung und Aufzeichnung beginnen. */
    private fun startRecording() {
        sessionActive = true
        samples.clear()
        recDist = 0.0
        recWorkKj = 0.0
        recHadPower = false
        recLastSec = -1
        recLastTrainerDist = ble.bike.value.distanceM
        recEnergyBase = ble.bike.value.energyKcal
    }

    /** Zeichnet höchstens einen Messpunkt pro Sekunde aktiver Fahrzeit auf. */
    private fun sample(sec: Int) {
        if (sec <= recLastSec) return
        val dt = if (recLastSec < 0) 1 else (sec - recLastSec).coerceAtMost(5)
        recLastSec = sec
        val d = ble.bike.value

        // Strecke: aus der Geschwindigkeit integriert; ohne Geschwindigkeit aus dem Zähler des Trainers
        val speed = d.speedKmh
        val td = d.distanceM
        val last = recLastTrainerDist
        if (speed != null) {
            recDist += speed / 3.6 * dt
        } else if (td != null && last != null && td >= last && td - last < 200) {
            recDist += (td - last).toDouble()
        }
        recLastTrainerDist = td

        // Energie: Wert des Trainers, sonst aus der Leistung geschätzt (1 kJ Arbeit ≈ 1 kcal)
        val power = d.powerW
        if (power != null) {
            recHadPower = true
            recWorkKj += power.coerceAtLeast(0) * dt / 1000.0
        }
        val te = d.energyKcal
        val base = recEnergyBase
        if (te != null && base == null) recEnergyBase = te
        val kcal = when {
            te != null && base != null && te >= base && (te - base > 0 || !recHadPower) -> te - base
            recHadPower -> recWorkKj.roundToInt()
            else -> null
        }

        samples.add(
            Sample(sec, System.currentTimeMillis(), power, speed, recDist, d.cadenceRpm, heartRate.value)
        )
        _live.value = LiveExtra(recDist, kcal)

        // Alle 30 s zwischenspeichern, damit bei einem Abbruch höchstens das verloren geht
        if (samples.size % 30 == 0) saveDraft()
    }

    private fun saveDraft() {
        val list = samples.toList()
        val name = recName
        val kcal = _live.value.kcal
        scope.launch { rideStore.saveDraft(name, kcal, list) }
    }

    /** Nach dem Ende: Zusammenfassung mit „Speichern“/„Verwerfen“ – oder direkt ins Hauptmenü. */
    private fun finishSession() {
        sessionActive = false
        sessionRunning = false
        currentTarget = 0
        RideService.stop(app)
        val pr = planRun
        planRun = null
        returnScreen = if (pr != null) Screen.PLAN else Screen.HOME
        _planNote.value = null
        if (samples.size >= 2) {
            val list = samples.toList()
            val summary = summarize(UUID.randomUUID().toString(), recName, list, _live.value.kcal)
            // Einheit aus dem Plan: zählt, wenn mindestens die Hälfte gefahren wurde
            if (pr != null && pr.week >= 0) {
                if (summary.durationSec * 2 >= pr.totalSec) {
                    addDone(
                        Done(
                            week = pr.week,
                            index = pr.index,
                            status = if (pr.alt) ST_ALT else ST_DONE,
                            dateMs = System.currentTimeMillis(),
                            name = pr.name,
                            seconds = summary.durationSec,
                            avgPower = summary.avgPower,
                            avgHr = summary.avgHr,
                            hard = pr.hard,
                        )
                    )
                    _planNote.value = "Im Trainingsplan als erledigt eingetragen"
                } else {
                    _planNote.value =
                        "Weniger als die Hälfte gefahren. Die Einheit bleibt im Trainingsplan offen."
                }
            }
            _pending.value = RideDetail(summary, list)
            _screen.value = Screen.SUMMARY
            saveDraft() // bleibt erhalten, bis „Speichern“ oder „Verwerfen“ gewählt wurde
        } else {
            scope.launch { rideStore.clearDraft() }
            _screen.value = returnScreen
        }
    }

    /** „Einheit beenden? – Ja“ bzw. „Beenden“: Trainer stoppen, dann Zusammenfassung. */
    fun endSession() {
        sessionJob?.cancel()
        sessionJob = null
        scope.launch {
            ble.setIdle()
            ble.stopSession()
        }
        finishSession()
    }

    fun savePending() {
        val p = _pending.value
        _pending.value = null
        _screen.value = returnScreen
        returnScreen = Screen.HOME
        if (p != null) {
            scope.launch {
                rideStore.save(p.summary, p.samples)
                rideStore.clearDraft()
                _rides.value = rideStore.list()
            }
        }
    }

    fun discardPending() {
        _pending.value = null
        _screen.value = returnScreen
        returnScreen = Screen.HOME
        scope.launch { rideStore.clearDraft() }
    }

    /** Titel und Text der Benachrichtigung während einer Einheit. */
    fun notificationText(): Pair<String, String> {
        val power = ble.bike.value.powerW
        val actual = if (power != null) " · Ist $power W" else ""
        return when (_screen.value) {
            Screen.FREE_RIDE -> {
                val r = _ride.value
                if (r.countdown != null) {
                    "Freie Fahrt" to "Start …"
                } else {
                    val w = if (r.level == 0) "Leerlauf" else "${levelConfig.value.watts(r.level)} W"
                    "Freie Fahrt läuft" to "${formatHms(r.elapsedSec)} · Stufe ${r.level} ($w)$actual"
                }
            }

            Screen.WORKOUT_RUN -> {
                val r = _run.value
                val phase = r.phases.getOrNull(r.phaseIndex)
                when {
                    r.countdown != null -> r.workoutName to "Start …"
                    r.finished || phase == null -> r.workoutName to "Training abgeschlossen"
                    r.paused -> r.workoutName to "Pausiert · noch ${formatMs(r.totalRemainingSec)}"
                    r.manual -> r.workoutName to
                        "${phase.label} · Zone ${phase.zone} · noch ${formatMs(r.phaseRemainingSec)}$actual"

                    else -> r.workoutName to
                        "${phase.label} · ${phase.watts} W · noch ${formatMs(r.phaseRemainingSec)}$actual"
                }
            }

            else -> "Rollentrainer" to "Einheit beendet"
        }
    }

    // ------------------------------------------------------------------ Freie Fahrt

    fun openFreeRide() {
        if (!requireTrainer()) return
        planRun = null
        _ride.value = RideUi()
        _screen.value = Screen.FREE_RIDE
        prepareSession("Freie Fahrt")
        sessionJob?.cancel()
        sessionJob = scope.launch {
            for (c in 3 downTo 1) {
                _ride.update { it.copy(countdown = c) }
                delay(1000)
            }
            _ride.update { it.copy(countdown = 0) }
            signal()
            currentTarget = 0
            ble.startSession()
            setTarget(0) // Start immer auf Stufe 0 = Leerlauf
            delay(700)
            startRecording()
            _ride.update { it.copy(countdown = null) }
            val t0 = SystemClock.elapsedRealtime()
            while (isActive) {
                val sec = ((SystemClock.elapsedRealtime() - t0) / 1000).toInt()
                _ride.update { it.copy(elapsedSec = sec) }
                if (sec > 0) sample(sec)
                delay(250)
            }
        }
    }

    fun changeLevel(delta: Int) {
        val r = _ride.value
        if (r.countdown != null) return
        val config = levelConfig.value
        val next = (r.level + delta).coerceIn(0, config.levels)
        if (next == r.level) return
        _ride.value = r.copy(level = next)
        setTarget(config.watts(next))
    }

    // ------------------------------------------------------------------ Trainings

    fun newWorkout() {
        _editing.value = Workout(id = UUID.randomUUID().toString(), name = "Neues Training")
        _screen.value = Screen.EDITOR
    }

    fun editWorkout(w: Workout) {
        _editing.value = w
        _screen.value = Screen.EDITOR
    }

    fun updateEditing(w: Workout) {
        _editing.value = w
    }

    private suspend fun upsert(w: Workout) {
        val list = workouts.value
        val next = if (list.any { it.id == w.id }) {
            list.map { if (it.id == w.id) w else it }
        } else {
            list + w
        }
        store.saveWorkouts(next)
    }

    private fun cleaned(w: Workout): Workout =
        w.copy(name = w.name.trim().ifEmpty { "Training" })

    fun saveEditing() {
        val w = cleaned(_editing.value)
        scope.launch { upsert(w) }
        _screen.value = Screen.WORKOUTS
    }

    /** „Start“ im Editor: speichert die Vorlage und startet sie. */
    fun startEditing() {
        if (!requireTrainer()) return
        val w = cleaned(_editing.value)
        scope.launch { upsert(w) }
        startWorkout(w)
    }

    fun duplicateWorkout(w: Workout) {
        scope.launch {
            store.saveWorkouts(
                workouts.value + w.copy(
                    id = UUID.randomUUID().toString(),
                    name = w.name + " (Kopie)",
                )
            )
        }
    }

    fun deleteWorkout(w: Workout) {
        scope.launch {
            store.saveWorkouts(workouts.value.filter { it.id != w.id })
        }
    }

    fun startWorkout(w: Workout) {
        if (!requireTrainer()) return
        val phases = w.phases()
        if (phases.isEmpty()) return
        planRun = null
        startRun(w.name, phases, manual = false)
    }

    /**
     * Startet einen Ablauf. [manual]: Training nach Puls, die App gibt nur die Zielzone vor und
     * der Widerstand wird wie in der Freien Fahrt über Stufen gestellt (Start im Leerlauf).
     */
    private fun startRun(name: String, phases: List<Phase>, manual: Boolean) {
        val total = phases.sumOf { it.seconds }
        _run.value = RunUi(
            workoutName = name,
            phases = phases,
            countdown = 3,
            phaseRemainingSec = phases[0].seconds,
            totalRemainingSec = total,
            totalSec = total,
            manual = manual,
        )
        _screen.value = Screen.WORKOUT_RUN
        prepareSession(name)
        sessionJob?.cancel()
        sessionJob = scope.launch {
            for (c in 3 downTo 1) {
                _run.update { it.copy(countdown = c) }
                delay(1000)
            }
            _run.update { it.copy(countdown = 0) }
            signal()
            ble.startSession()
            setTarget(if (manual) 0 else phases[0].watts)
            delay(700)
            startRecording()
            _run.update { it.copy(countdown = null) }

            var index = 0
            var activeMs = 0L
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(200)
                val now = SystemClock.elapsedRealtime()
                val paused = _run.value.paused
                if (!paused) activeMs += now - last
                last = now
                val sec = (activeMs / 1000).toInt()
                if (!paused && sec > 0) sample(sec.coerceAtMost(total))
                if (sec >= total) break

                var start = 0
                var i = 0
                while (i < phases.size - 1 && sec >= start + phases[i].seconds) {
                    start += phases[i].seconds
                    i++
                }
                if (i != index) {
                    index = i
                    if (!paused && !manual) setTarget(phases[i].watts) // ERG-Ziel bei Phasenwechsel
                    signal()
                }
                _run.update {
                    it.copy(
                        phaseIndex = i,
                        phaseRemainingSec = start + phases[i].seconds - sec,
                        totalRemainingSec = total - sec,
                        elapsedSec = sec,
                    )
                }
            }
            if (!isActive) return@launch

            // Training abgeschlossen
            sessionActive = false
            currentTarget = 0
            signal(long = true)
            _run.update {
                it.copy(finished = true, phaseRemainingSec = 0, totalRemainingSec = 0, elapsedSec = total)
            }
            ble.setIdle()
            ble.stopSession()
            delay(3000)
            sessionJob = null
            finishSession()
        }
    }

    fun togglePause() {
        val r = _run.value
        if (r.countdown != null || r.finished || r.phases.isEmpty()) return
        val paused = !r.paused
        _run.value = r.copy(paused = paused)
        // Pause: Widerstand auf Leerlauf; Weiter: Zielwatt der Phase bzw. die gewählte Stufe
        setTarget(
            when {
                paused -> 0
                r.manual -> levelConfig.value.watts(r.level)
                else -> r.phases[r.phaseIndex].watts
            }
        )
    }

    /** Training nach Puls: Widerstand eine Stufe hoch oder runter. */
    fun changeRunLevel(delta: Int) {
        val r = _run.value
        if (!r.manual || r.countdown != null || r.finished) return
        val config = levelConfig.value
        val next = (r.level + delta).coerceIn(0, config.levels)
        if (next == r.level) return
        _run.update { it.copy(level = next) }
        if (!r.paused) setTarget(config.watts(next))
    }

    // ------------------------------------------------------------------ Trainingsplan

    fun setProfile(p: Profile) {
        scope.launch { store.setProfile(p) }
    }

    /** Erzeugt den Plan aus den Eckdaten und macht ihn zum aktiven Plan. */
    fun startPlan(spec: PlanSpec) {
        val st = PlanState(generatePlan(spec), System.currentTimeMillis())
        scope.launch { store.setPlan(st) }
        _screen.value = Screen.PLAN
    }

    fun endPlan() {
        scope.launch { store.setPlan(null) }
    }

    /**
     * Ändert den Schwerpunkt des laufenden Plans. Der Plan wird mit denselben Eckdaten neu
     * erzeugt; was schon erledigt ist, bleibt eingetragen.
     */
    fun changeFocus(focus: Focus) {
        val st = planState.value ?: return
        if (st.plan.spec.focus == focus) return
        scope.launch { store.setPlan(st.copy(plan = generatePlan(st.plan.spec.copy(focus = focus)))) }
    }

    private fun addDone(d: Done) {
        val st = planState.value ?: return
        if (d.week !in st.plan.weeks.indices || st.entry(d.week, d.index) != null) return
        scope.launch { store.setPlan(st.copy(done = st.done + d)) }
    }

    /** Nächste Einheit auslassen. */
    fun skipNext() {
        val st = planState.value ?: return
        val (w, i) = st.next() ?: return
        addDone(Done(w, i, ST_SKIP, System.currentTimeMillis(), st.plan.weeks[w][i].name))
    }

    /** Nächste Einheit als gefahren eintragen (z. B. draußen oder ohne App gefahren). */
    fun markNextDone() {
        val st = planState.value ?: return
        val (w, i) = st.next() ?: return
        val s = st.plan.weeks[w][i]
        addDone(Done(w, i, ST_DONE, System.currentTimeMillis(), s.name, s.seconds, hard = s.hard))
    }

    /** Nimmt den letzten Eintrag (erledigt, ersetzt oder übersprungen) zurück. */
    fun undoLastDone() {
        val st = planState.value ?: return
        if (st.done.isEmpty()) return
        scope.launch { store.setPlan(st.copy(done = st.done.dropLast(1))) }
    }

    /** Startet die nächste Einheit des Plans; [alt] = die leichtere Alternative. */
    fun startNext(alt: Boolean) {
        val st = planState.value ?: return
        val (w, i) = st.next() ?: return
        val planned = st.plan.weeks[w][i]
        val s = if (alt) alternativeFor(planned) else planned
        startSession(s, PlanRun(w, i, alt, s.name, s.hard, s.seconds))
    }

    /**
     * Startet eine Einheit: mit FTP werden die Watt vorgegeben, sonst wird nach Pulszonen
     * gefahren und der Widerstand selbst gestellt.
     */
    private fun startSession(s: Session, ref: PlanRun) {
        if (!requireTrainer()) return
        val p = profile.value
        val phases = s.phases(p)
        if (phases.isEmpty()) return
        planRun = ref
        startRun(s.name, phases, manual = !p.power)
    }

    // ------------------------------------------------------------------ KI

    fun saveAiConfig(c: AiConfig) {
        scope.launch { store.setAi(c.copy(url = c.url.trim(), key = c.key.trim(), model = c.model.trim())) }
    }

    /** Vollständiger Anfragetext: Trainingsstand und Frage. Auch zum Teilen in eine andere App. */
    fun aiPrompt(kind: Int, question: String): String {
        val ask = when (kind) {
            AI_ANALYSIS -> AI_ASK_ANALYSIS
            AI_SESSION -> aiAskSession(profile.value)
            else -> question.trim()
        }
        return aiContext(profile.value, planState.value, rides.value, System.currentTimeMillis()) +
            "\nFRAGE\n" + ask
    }

    fun askAi(kind: Int, question: String = "") {
        val cfg = aiConfig.value
        if (!cfg.ready) {
            _ai.value = AiUi(error = "Trage zuerst Adresse und Modell des KI-Dienstes ein.")
            return
        }
        if (kind == AI_QUESTION && question.isBlank()) return
        val prompt = aiPrompt(kind, question)
        aiJob?.cancel()
        _ai.value = AiUi(busy = true)
        aiJob = scope.launch {
            try {
                val raw = aiChat(cfg, AI_SYSTEM, prompt)
                val answer = if (kind == AI_SESSION) parseAiAnswer(raw) else AiAnswer(raw, null)
                _ai.value = AiUi(text = answer.text, session = answer.session)
            } catch (e: AiException) {
                _ai.value = AiUi(error = e.message)
            }
        }
    }

    fun cancelAi() {
        aiJob?.cancel()
        aiJob = null
        _ai.value = AiUi()
    }

    /**
     * Startet die von der KI vorgeschlagene Einheit. [forPlan]: Sie ersetzt die nächste Einheit
     * des Plans, sonst läuft sie zusätzlich.
     */
    fun startAiSession(forPlan: Boolean) {
        val s = _ai.value.session ?: return
        val next = if (forPlan) planState.value?.next() else null
        startSession(
            s,
            PlanRun(next?.first ?: -1, next?.second ?: -1, true, s.name, s.hard, s.seconds),
        )
    }

    // ------------------------------------------------------------------ Verlauf

    private fun reloadRides() {
        scope.launch { _rides.value = rideStore.list() }
    }

    fun openHistory() {
        reloadRides()
        _screen.value = Screen.HISTORY
    }

    fun openRide(s: RideSummary) {
        _detail.value = RideDetail(s, emptyList())
        _screen.value = Screen.RIDE_DETAIL
        scope.launch {
            val list = rideStore.loadSamples(s.id)
            val cur = _detail.value
            if (cur != null && cur.summary.id == s.id) _detail.value = cur.copy(samples = list)
        }
    }

    fun renameRide(name: String) {
        val cur = _detail.value ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        val next = cur.summary.copy(name = clean)
        _detail.value = cur.copy(summary = next)
        scope.launch {
            rideStore.updateSummary(next)
            _rides.value = rideStore.list()
        }
    }

    fun deleteRide() {
        val cur = _detail.value ?: return
        _detail.value = null
        _screen.value = Screen.HISTORY
        scope.launch {
            rideStore.delete(cur.summary.id)
            _rides.value = rideStore.list()
        }
    }

    /** Erzeugt die Exportdatei der geöffneten Fahrt: Dateiname und Inhalt. */
    suspend fun exportBytes(format: ExportFormat): Pair<String, ByteArray>? {
        val d = _detail.value ?: return null
        if (d.samples.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            val bytes = when (format) {
                ExportFormat.FIT -> FitEncoder.encode(d.summary, d.samples)
                ExportFormat.TCX -> TcxWriter.write(d.summary, d.samples).toByteArray(Charsets.UTF_8)
            }
            exportFileName(d.summary, format) to bytes
        }
    }
}
