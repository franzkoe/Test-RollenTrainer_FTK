package de.rollentrainer.app

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.rollentrainer.app.ble.BikeData
import de.rollentrainer.app.ble.ConnState
import de.rollentrainer.app.ble.FoundDevice
import de.rollentrainer.app.ble.FtmsManager
import de.rollentrainer.app.data.LEVEL_WATTS
import de.rollentrainer.app.data.MAX_LEVEL
import de.rollentrainer.app.data.Phase
import de.rollentrainer.app.data.SavedDevice
import de.rollentrainer.app.data.Store
import de.rollentrainer.app.data.Workout
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { HOME, DEVICES, FREE_RIDE, WORKOUTS, EDITOR, WORKOUT_RUN }

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
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val ble = FtmsManager(app, viewModelScope)

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
        store.lightTheme.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ---- Trainer ----
    val connState: StateFlow<ConnState> = ble.state
    val connectedAddress: StateFlow<String?> = ble.address
    val bike: StateFlow<BikeData> = ble.bike
    val foundDevices: StateFlow<List<FoundDevice>> = ble.found
    val scanning: StateFlow<Boolean> = ble.scanning
    val trainerStatus: StateFlow<String> = ble.status

    // ---- Gespeicherte Daten ----
    val devices: StateFlow<List<SavedDevice>> =
        store.devices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workouts: StateFlow<List<Workout>> =
        store.workouts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Fahrt / Training ----
    private val _ride = MutableStateFlow(RideUi())
    val ride: StateFlow<RideUi> = _ride
    private val _run = MutableStateFlow(RunUi())
    val run: StateFlow<RunUi> = _run
    private val _editing = MutableStateFlow(Workout(id = "", name = ""))
    val editing: StateFlow<Workout> = _editing

    /** Zählerstände des Trainers beim Start der Einheit (Distanz m, Energie kcal). */
    private val _baseline = MutableStateFlow(Pair(0, 0))
    val baseline: StateFlow<Pair<Int, Int>> = _baseline

    /** Zielwerte (Watt; 0 = Leerlauf) – nur der jeweils neueste Wert wird gesendet. */
    private val targetCh = Channel<Int>(Channel.CONFLATED)
    private var currentTarget = 0
    private var sessionActive = false
    private var sessionJob: Job? = null
    /** true, nachdem der Nutzer bewusst „Trennen“ gewählt hat – dann kein Auto-Connect. */
    private var manualDisconnect = false

    private val vibrator: Vibrator? = app.getSystemService(Vibrator::class.java)
    private var tone: ToneGenerator? = null

    init {
        // Sendet Zielwerte nacheinander an den Trainer
        viewModelScope.launch {
            for (w in targetCh) {
                if (w <= 0) ble.setIdle() else ble.setTargetPower(w)
            }
        }
        // Nach einem Reconnect während einer Einheit: Kontrolle + Zielwert wiederherstellen
        viewModelScope.launch {
            var prev = ble.state.value
            ble.state.collect { s ->
                if (s == ConnState.READY && prev != ConnState.READY && sessionActive) {
                    ble.startSession()
                    targetCh.trySend(currentTarget)
                }
                prev = s
            }
        }
        viewModelScope.launch {
            if (!store.batteryHintShown.first()) _batteryHint.value = true
        }
    }

    // ------------------------------------------------------------------ Umgebung

    /** Wird bei onResume und nach der Berechtigungsabfrage aufgerufen. */
    fun refreshEnvironment() {
        _permissionsGranted.value = ble.hasPermissions()
        _btEnabled.value = ble.isBluetoothEnabled()
        tryAutoConnect()
    }

    /** Verbindet automatisch mit dem zuletzt genutzten Gerät. */
    private fun tryAutoConnect() {
        if (manualDisconnect || !ble.hasPermissions() || !ble.isBluetoothEnabled()) return
        viewModelScope.launch {
            val last = store.lastDevice.first() ?: return@launch
            if (ble.state.value == ConnState.DISCONNECTED) ble.connect(last)
        }
    }

    fun dismissBatteryHint() {
        _batteryHint.value = false
        viewModelScope.launch { store.setBatteryHintShown() }
    }

    fun dismissNeedTrainer() {
        _needTrainer.value = false
    }

    fun toggleTheme() {
        val next = !lightTheme.value
        viewModelScope.launch { store.setLightTheme(next) }
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

            Screen.WORKOUTS -> _screen.value = Screen.HOME
            Screen.EDITOR -> _screen.value = Screen.WORKOUTS
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ Geräte

    fun startScan(showAll: Boolean) = ble.startScan(showAll)
    fun stopScan() = ble.stopScan()

    fun addDevice(found: FoundDevice) {
        ble.stopScan()
        viewModelScope.launch {
            val list = devices.value
            if (list.none { it.address == found.address }) {
                store.saveDevices(list + SavedDevice(found.address, found.name))
            }
            store.setLastDevice(found.address)
            manualDisconnect = false
            ble.connect(found.address)
        }
    }

    fun connectDevice(device: SavedDevice) {
        viewModelScope.launch {
            store.setLastDevice(device.address)
            manualDisconnect = false
            ble.connect(device.address)
        }
    }

    fun disconnectDevice() {
        manualDisconnect = true
        ble.disconnect()
    }

    fun renameDevice(device: SavedDevice, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            store.saveDevices(devices.value.map {
                if (it.address == device.address) it.copy(name = clean) else it
            })
        }
    }

    fun deleteDevice(device: SavedDevice) {
        viewModelScope.launch {
            if (ble.address.value == device.address) ble.disconnect()
            if (store.lastDevice.first() == device.address) store.setLastDevice(null)
            store.saveDevices(devices.value.filter { it.address != device.address })
        }
    }

    // ------------------------------------------------------------------ Gemeinsames

    private fun setTarget(watts: Int) {
        currentTarget = watts
        targetCh.trySend(watts)
    }

    private fun markBaseline() {
        val d = ble.bike.value
        _baseline.value = Pair(d.distanceM ?: 0, d.energyKcal ?: 0)
    }

    private fun signal(long: Boolean = false) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, if (long) 600 else 250)
        } catch (e: Exception) {
            // Ton ist optional
        }
        try {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    if (long) 600L else 200L, VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (e: Exception) {
            // Vibration ist optional
        }
    }

    private fun requireTrainer(): Boolean {
        if (ble.state.value == ConnState.READY) return true
        _needTrainer.value = true
        return false
    }

    /** „Einheit beenden? – Ja“ bzw. „Beenden“: Trainer stoppen, zurück zum Hauptmenü. */
    fun endSession() {
        sessionJob?.cancel()
        sessionJob = null
        sessionActive = false
        currentTarget = 0
        viewModelScope.launch {
            ble.setIdle()
            ble.stopSession()
        }
        _screen.value = Screen.HOME
    }

    // ------------------------------------------------------------------ Freie Fahrt

    fun openFreeRide() {
        if (!requireTrainer()) return
        _ride.value = RideUi()
        _screen.value = Screen.FREE_RIDE
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            for (c in 3 downTo 1) {
                _ride.update { it.copy(countdown = c) }
                delay(1000)
            }
            _ride.update { it.copy(countdown = 0) }
            signal()
            sessionActive = true
            currentTarget = 0
            ble.startSession()
            setTarget(0) // Start immer auf Stufe 0 = Leerlauf
            delay(700)
            markBaseline()
            _ride.update { it.copy(countdown = null) }
            val t0 = SystemClock.elapsedRealtime()
            while (isActive) {
                val sec = ((SystemClock.elapsedRealtime() - t0) / 1000).toInt()
                _ride.update { it.copy(elapsedSec = sec) }
                delay(250)
            }
        }
    }

    fun changeLevel(delta: Int) {
        val r = _ride.value
        if (r.countdown != null) return
        val next = (r.level + delta).coerceIn(0, MAX_LEVEL)
        if (next == r.level) return
        _ride.value = r.copy(level = next)
        setTarget(LEVEL_WATTS[next])
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
        viewModelScope.launch { upsert(w) }
        _screen.value = Screen.WORKOUTS
    }

    /** „Start“ im Editor: speichert die Vorlage und startet sie. */
    fun startEditing() {
        if (!requireTrainer()) return
        val w = cleaned(_editing.value)
        viewModelScope.launch { upsert(w) }
        startWorkout(w)
    }

    fun duplicateWorkout(w: Workout) {
        viewModelScope.launch {
            store.saveWorkouts(
                workouts.value + w.copy(
                    id = UUID.randomUUID().toString(),
                    name = w.name + " (Kopie)",
                )
            )
        }
    }

    fun deleteWorkout(w: Workout) {
        viewModelScope.launch {
            store.saveWorkouts(workouts.value.filter { it.id != w.id })
        }
    }

    fun startWorkout(w: Workout) {
        if (!requireTrainer()) return
        val phases = w.phases()
        if (phases.isEmpty()) return
        val total = phases.sumOf { it.seconds }
        _run.value = RunUi(
            workoutName = w.name,
            phases = phases,
            countdown = 3,
            phaseRemainingSec = phases[0].seconds,
            totalRemainingSec = total,
            totalSec = total,
        )
        _screen.value = Screen.WORKOUT_RUN
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            for (c in 3 downTo 1) {
                _run.update { it.copy(countdown = c) }
                delay(1000)
            }
            _run.update { it.copy(countdown = 0) }
            signal()
            sessionActive = true
            ble.startSession()
            setTarget(phases[0].watts)
            delay(700)
            markBaseline()
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
                if (sec >= total) break

                var start = 0
                var i = 0
                while (i < phases.size - 1 && sec >= start + phases[i].seconds) {
                    start += phases[i].seconds
                    i++
                }
                if (i != index) {
                    index = i
                    if (!paused) setTarget(phases[i].watts) // ERG-Ziel bei Phasenwechsel
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
            _screen.value = Screen.HOME
        }
    }

    fun togglePause() {
        val r = _run.value
        if (r.countdown != null || r.finished || r.phases.isEmpty()) return
        val paused = !r.paused
        _run.value = r.copy(paused = paused)
        // Pause: Widerstand auf Leerlauf; Weiter: Zielwatt der aktuellen Phase
        setTarget(if (paused) 0 else r.phases[r.phaseIndex].watts)
    }

    override fun onCleared() {
        ble.disconnect()
        try {
            tone?.release()
        } catch (e: Exception) {
            // ignorieren
        }
        super.onCleared()
    }
}
