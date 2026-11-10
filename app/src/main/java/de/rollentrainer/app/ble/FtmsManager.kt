package de.rollentrainer.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class ConnState { DISCONNECTED, CONNECTING, READY }

data class FoundDevice(val address: String, val name: String, val rssi: Int)

/**
 * Komplette BLE-/FTMS-Logik: Scan, Verbindung (ohne System-Pairing), automatischer Reconnect,
 * serialisierte GATT-Operationen und die Steuerbefehle über den Fitness Machine Control Point.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class FtmsManager(private val context: Context, private val scope: CoroutineScope) {

    companion object {
        private fun uuid16(hex: String): UUID =
            UUID.fromString("0000$hex-0000-1000-8000-00805f9b34fb")

        val FTMS_SERVICE: UUID = uuid16("1826")
        val CH_FEATURE: UUID = uuid16("2acc")
        val CH_BIKE_DATA: UUID = uuid16("2ad2")
        val CH_RESISTANCE_RANGE: UUID = uuid16("2ad6")
        val CH_POWER_RANGE: UUID = uuid16("2ad8")
        val CH_CONTROL_POINT: UUID = uuid16("2ad9")
        val CH_STATUS: UUID = uuid16("2ada")
        val CCCD: UUID = uuid16("2902")

        private const val RESULT_SUCCESS = 0x01
        private const val RESULT_NOT_PERMITTED = 0x05
        private const val OP_TIMEOUT_MS = 3000L
    }

    private val btManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = btManager.adapter

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state

    private val _address = MutableStateFlow<String?>(null)
    /** Adresse des Geräts, mit dem gerade verbunden wird bzw. das verbunden ist. */
    val address: StateFlow<String?> = _address

    private val _bike = MutableStateFlow(BikeData())
    val bike: StateFlow<BikeData> = _bike

    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _powerRange = MutableStateFlow<PowerRange?>(null)
    val powerRange: StateFlow<PowerRange?> = _powerRange

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var cpChar: BluetoothGattCharacteristic? = null
    @Volatile private var wantedAddress: String? = null
    @Volatile private var targetFeatures: Int? = null
    @Volatile private var resistanceMin: Int = 0
    private var reconnectJob: Job? = null
    private var setupJob: Job? = null

    // ---- Queue: immer nur eine GATT-Operation gleichzeitig ----
    private val opMutex = Mutex()
    @Volatile private var pendingOp: CompletableDeferred<Int>? = null
    @Volatile private var pendingRead: CompletableDeferred<ByteArray?>? = null
    @Volatile private var cpResponse: CompletableDeferred<Int>? = null

    // ------------------------------------------------------------------ Umgebung

    fun hasPermissions(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun isBluetoothEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (e: Exception) {
        false
    }

    private fun usable() = hasPermissions() && isBluetoothEnabled()

    // ------------------------------------------------------------------ Scan

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = result.scanRecord?.deviceName ?: dev.name ?: "Unbekanntes Gerät"
            val entry = FoundDevice(dev.address, name, result.rssi)
            val list = _found.value.filter { it.address != entry.address } + entry
            _found.value = list.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            _status.value = "Scan fehlgeschlagen (Code $errorCode)"
        }
    }

    /** Startet den Scan. Standard: nur FTMS-Geräte; [showAll] zeigt alle BLE-Geräte. */
    fun startScan(showAll: Boolean) {
        if (!usable()) return
        stopScan()
        _found.value = emptyList()
        val scanner = adapter?.bluetoothLeScanner ?: return
        val filters = if (showAll) {
            emptyList()
        } else {
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(FTMS_SERVICE)).build())
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(filters, settings, scanCallback)
            _scanning.value = true
        } catch (e: Exception) {
            _status.value = "Scan konnte nicht gestartet werden"
        }
    }

    fun stopScan() {
        if (!_scanning.value) return
        _scanning.value = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // ignorieren
        }
    }

    // ------------------------------------------------------------------ Verbindung

    /** Verbindet direkt per GATT (kein Pairing/Bonding) und hält die Verbindung aufrecht. */
    fun connect(address: String) {
        if (!usable()) return
        if (wantedAddress == address && _state.value != ConnState.DISCONNECTED) return
        closeGatt()
        wantedAddress = address
        openGatt(address)
    }

    /** Trennt und beendet den automatischen Reconnect. */
    fun disconnect() {
        wantedAddress = null
        closeGatt()
    }

    private fun openGatt(address: String) {
        val found: BluetoothDevice? = try {
            adapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (found == null) {
            _state.value = ConnState.DISCONNECTED
            return
        }
        val dev: BluetoothDevice = found
        _state.value = ConnState.CONNECTING
        _address.value = address
        gatt = dev.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun closeGatt() {
        reconnectJob?.cancel()
        setupJob?.cancel()
        val g = gatt
        gatt = null
        cpChar = null
        if (g != null) {
            try {
                g.disconnect()
                g.close()
            } catch (e: Exception) {
                // ignorieren
            }
        }
        failPending()
        _state.value = ConnState.DISCONNECTED
        _address.value = null
        _bike.value = BikeData()
    }

    private fun failPending() {
        pendingOp?.complete(BluetoothGatt.GATT_FAILURE)
        pendingRead?.complete(null)
        cpResponse?.complete(-1)
    }

    private fun handleDisconnect(g: BluetoothGatt) {
        setupJob?.cancel()
        try {
            g.close()
        } catch (e: Exception) {
            // ignorieren
        }
        gatt = null
        cpChar = null
        failPending()
        _bike.value = BikeData()
        val wanted = wantedAddress
        if (wanted == null) {
            _state.value = ConnState.DISCONNECTED
            _address.value = null
            return
        }
        // Automatischer Reconnect: so lange erneut versuchen, bis der Trainer wieder da ist
        _state.value = ConnState.CONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2000)
            val still = wantedAddress
            if (still != null && gatt == null) {
                if (usable()) {
                    openGatt(still)
                } else {
                    _state.value = ConnState.DISCONNECTED
                    _address.value = null
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                scope.launch {
                    delay(400)
                    if (g === gatt) g.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handleDisconnect(g)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupJob?.cancel()
                setupJob = scope.launch { setup(g) }
            } else {
                g.disconnect()
            }
        }

        // --- Lesen (alte und neue Callback-Variante) ---
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val v = if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null
            pendingRead?.complete(v)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            pendingRead?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingOp?.complete(status)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            pendingOp?.complete(status)
        }

        // --- Notify/Indicate (alte und neue Callback-Variante) ---
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val v = characteristic.value ?: return
            handleNotify(characteristic.uuid, v)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotify(characteristic.uuid, value)
        }
    }

    private fun handleNotify(uuid: UUID, value: ByteArray) {
        when (uuid) {
            CH_BIKE_DATA -> {
                val parsed = FtmsParser.parseIndoorBikeData(value) ?: return
                _bike.value = _bike.value.merge(parsed)
            }

            CH_CONTROL_POINT -> {
                val result = FtmsParser.parseControlResponse(value) ?: return
                cpResponse?.complete(result)
            }

            CH_STATUS -> {
                FtmsParser.describeStatus(value)?.let { _status.value = it }
            }
        }
    }

    /** Nach dem Service-Discovery: Fähigkeiten lesen, Notify/Indicate aktivieren, Kontrolle anfordern. */
    private suspend fun setup(g: BluetoothGatt) {
        val svc = g.getService(FTMS_SERVICE)
        if (svc == null) {
            _status.value = "Gerät bietet keinen FTMS-Dienst"
            disconnect()
            return
        }
        targetFeatures = null
        resistanceMin = 0
        _powerRange.value = null

        svc.getCharacteristic(CH_FEATURE)?.let { ch ->
            readChar(g, ch)?.let { v -> targetFeatures = FtmsParser.parseTargetFeatures(v) }
        }
        svc.getCharacteristic(CH_POWER_RANGE)?.let { ch ->
            readChar(g, ch)?.let { v -> _powerRange.value = FtmsParser.parsePowerRange(v) }
        }
        svc.getCharacteristic(CH_RESISTANCE_RANGE)?.let { ch ->
            readChar(g, ch)?.let { v ->
                resistanceMin = (FtmsParser.parseResistanceMin(v) ?: 0).coerceIn(0, 255)
            }
        }

        val cp = svc.getCharacteristic(CH_CONTROL_POINT)
        cpChar = cp
        if (cp != null) enableNotify(g, cp, indicate = true)
        svc.getCharacteristic(CH_BIKE_DATA)?.let { ch -> enableNotify(g, ch, indicate = false) }
        svc.getCharacteristic(CH_STATUS)?.let { ch -> enableNotify(g, ch, indicate = false) }

        if (g !== gatt) return
        requestControl()
        if (g !== gatt) return
        _status.value = if (cp == null) "Verbunden (ohne Steuerung)" else "Verbunden"
        _state.value = ConnState.READY
    }

    // ------------------------------------------------------------------ GATT-Operationen

    private suspend fun readChar(g: BluetoothGatt, ch: BluetoothGattCharacteristic): ByteArray? =
        opMutex.withLock {
            val d = CompletableDeferred<ByteArray?>()
            pendingRead = d
            val started = try {
                g.readCharacteristic(ch)
            } catch (e: Exception) {
                false
            }
            if (!started) {
                pendingRead = null
                return@withLock null
            }
            val r = withTimeoutOrNull(OP_TIMEOUT_MS) { d.await() }
            pendingRead = null
            r
        }

    private suspend fun enableNotify(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        indicate: Boolean,
    ): Boolean = opMutex.withLock {
        val desc = ch.getDescriptor(CCCD) ?: return@withLock false
        val value = if (indicate) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        val d = CompletableDeferred<Int>()
        pendingOp = d
        val started = try {
            if (!g.setCharacteristicNotification(ch, true)) {
                false
            } else if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(desc, value) == BluetoothStatusCodes.SUCCESS
            } else {
                desc.setValue(value)
                g.writeDescriptor(desc)
            }
        } catch (e: Exception) {
            false
        }
        if (!started) {
            pendingOp = null
            return@withLock false
        }
        val s = withTimeoutOrNull(OP_TIMEOUT_MS) { d.await() }
        pendingOp = null
        s == BluetoothGatt.GATT_SUCCESS
    }

    /**
     * Schreibt einen Befehl auf den Control Point und wartet auf die Indication-Antwort.
     * @return Result-Code (0x01 = Erfolg) oder null bei Fehler/Timeout.
     */
    private suspend fun control(vararg bytes: Int): Int? {
        val data = ByteArray(bytes.size) { bytes[it].toByte() }
        return opMutex.withLock {
            val g = gatt ?: return@withLock null
            val ch = cpChar ?: return@withLock null
            val w = CompletableDeferred<Int>()
            val r = CompletableDeferred<Int>()
            pendingOp = w
            cpResponse = r
            val started = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(
                        ch, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.setValue(data)
                    g.writeCharacteristic(ch)
                }
            } catch (e: Exception) {
                false
            }
            if (!started) {
                pendingOp = null
                cpResponse = null
                return@withLock null
            }
            val ws = withTimeoutOrNull(OP_TIMEOUT_MS) { w.await() }
            pendingOp = null
            if (ws != BluetoothGatt.GATT_SUCCESS) {
                cpResponse = null
                return@withLock null
            }
            val res = withTimeoutOrNull(OP_TIMEOUT_MS) { r.await() }
            cpResponse = null
            if (res == null || res < 0) null else res
        }
    }

    // ------------------------------------------------------------------ Steuerung

    /** Request Control (0x00). */
    suspend fun requestControl(): Boolean = control(0x00) == RESULT_SUCCESS

    /** Request Control (0x00) → Start/Resume (0x07). */
    suspend fun startSession() {
        requestControl()
        control(0x07)
    }

    /** Set Target Power (0x05, sint16 Watt) – ERG-Modus. Wert wird auf den Bereich des Trainers begrenzt. */
    suspend fun setTargetPower(watts: Int): Boolean {
        var w = watts
        val range = _powerRange.value
        if (range != null) w = w.coerceIn(range.min, range.max)
        w = w.coerceIn(0, 32767)
        var r = control(0x05, w and 0xFF, (w shr 8) and 0xFF)
        if (r == RESULT_NOT_PERMITTED) {
            requestControl()
            r = control(0x05, w and 0xFF, (w shr 8) and 0xFF)
        }
        return r == RESULT_SUCCESS
    }

    /**
     * Leerlauf (Stufe 0): möglichst kein Widerstand. Kein ERG-Ziel von 0 W, sondern
     * 1. Simulation mit 0 % Steigung, ohne Roll-/Luftwiderstand (0x11), sonst
     * 2. minimale Widerstandsstufe (0x04), sonst
     * 3. kleinste unterstützte Zielleistung.
     */
    suspend fun setIdle(): Boolean {
        val f = targetFeatures
        val simSupported = f == null || (f and (1 shl 13)) != 0
        val resistanceSupported = f == null || (f and (1 shl 2)) != 0

        if (simSupported) {
            // Wind sint16 = 0, Steigung sint16 = 0 (0,00 %), Crr uint8 = 0, Cw uint8 = 0
            var r = control(0x11, 0, 0, 0, 0, 0, 0)
            if (r == RESULT_NOT_PERMITTED) {
                requestControl()
                r = control(0x11, 0, 0, 0, 0, 0, 0)
            }
            if (r == RESULT_SUCCESS) return true
        }
        if (resistanceSupported) {
            val lvl = resistanceMin
            if (control(0x04, lvl and 0xFF) == RESULT_SUCCESS) return true
            if (control(0x04, lvl and 0xFF, 0) == RESULT_SUCCESS) return true
        }
        val min = _powerRange.value?.min ?: 0
        return setTargetPower(if (min > 0) min else 0)
    }

    /** Stop/Pause (0x08) mit Parameter 0x01 = Stop. */
    suspend fun stopSession() {
        control(0x08, 0x01)
    }
}
