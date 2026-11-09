package com.decmike.canonremote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.decmike.canonremote.ccapi.CameraControl
import com.decmike.canonremote.ccapi.CameraSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class BleDevice(val name: String, val address: String, val rssi: Int?, val bonded: Boolean) {
    val looksLikeCanon: Boolean
        get() = Regex("(?i)canon|eos|powershot|ixus|zr").containsMatchIn(name)
}

sealed class BleState {
    data object Idle : BleState()
    data class Connecting(val step: String) : BleState()
    data class Connected(val name: String, val address: String) : BleState()
    data class Failed(val message: String) : BleState()
}

/**
 * Télécommande Bluetooth Low Energy : l'application se présente à l'appareil
 * comme une télécommande Canon BR-E1. Aucun Wi-Fi ni activation CCAPI requis.
 *
 * Protocole (service 00050000-…-d8492fffa821) :
 *  - caractéristique 00050002 : appairage, on écrit 0x03 + nom de la télécommande
 *  - caractéristique 00050003 : boutons, un octet = mode | boutons enfoncés
 */
@SuppressLint("MissingPermission")
object BleRemote : CameraControl {

    val SERVICE: UUID = UUID.fromString("00050000-0000-1000-0000-d8492fffa821")
    val PAIRING: UUID = UUID.fromString("00050002-0000-1000-0000-d8492fffa821")
    val SHUTTER: UUID = UUID.fromString("00050003-0000-1000-0000-d8492fffa821")

    const val BUTTON_RELEASE = 0x80
    const val BUTTON_FOCUS = 0x40
    const val BUTTON_TELE = 0x20
    const val BUTTON_WIDE = 0x10
    const val MODE_IMMEDIATE = 0x0C
    const val MODE_DELAY = 0x04
    const val MODE_MOVIE = 0x08

    private const val REMOTE_NAME = "CanonRemote"

    private lateinit var app: Context
    private val prefs by lazy { app.getSharedPreferences("ble", Context.MODE_PRIVATE) }

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var shutterChar: BluetoothGattCharacteristic? = null
    private val opLock = Mutex()
    private var pendingWrite: CompletableDeferred<Int>? = null
    private var pendingConnect: CompletableDeferred<Boolean>? = null
    private var pendingServices: CompletableDeferred<Boolean>? = null
    private var userDisconnect = false

    /** Mode B en cours (le BR-E1 ouvre puis ferme l'obturateur à chaque appui). */
    private var bulbOpen = false

    fun init(context: Context) { app = context.applicationContext }

    private val adapter: BluetoothAdapter?
        get() = app.getSystemService(BluetoothManager::class.java)?.adapter

    val isEnabled: Boolean get() = adapter?.isEnabled == true
    val isConnected: Boolean get() = _state.value is BleState.Connected

    val lastAddress: String? get() = prefs.getString("address", null)
    val lastName: String? get() = prefs.getString("name", null)

    // ------------------------------------------------------------------ recherche

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = result.scanRecord?.deviceName ?: d.name ?: return
            val canonService = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE } == true
            val dev = BleDevice(if (canonService && !name.contains("canon", true)) "$name (Canon)" else name,
                d.address, result.rssi, d.bondState == BluetoothDevice.BOND_BONDED)
            _devices.update { list -> (list.filter { it.address != dev.address } + dev).sortedWith(order) }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            _state.value = BleState.Failed("Recherche Bluetooth impossible (code $errorCode)")
        }
    }

    private val order = compareByDescending<BleDevice> { it.looksLikeCanon }.thenByDescending { it.rssi ?: -200 }

    fun startScan() {
        val a = adapter ?: return
        val bonded = a.bondedDevices.orEmpty()
            .map { BleDevice(it.name ?: it.address, it.address, null, true) }
            .filter { it.looksLikeCanon }
        _devices.value = bonded.sortedWith(order)
        val scanner = a.bluetoothLeScanner ?: return
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        _scanning.value = true
    }

    fun stopScan() {
        if (!_scanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    // ------------------------------------------------------------------ connexion

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                pendingConnect?.complete(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pendingConnect?.complete(false)
                pendingWrite?.complete(-1)
                g.close()
                if (gatt === g) {
                    gatt = null
                    shutterChar = null
                    bulbOpen = false
                    if (_state.value is BleState.Connected && !userDisconnect) {
                        _state.value = BleState.Failed("Connexion Bluetooth perdue (appareil éteint ou en veille ?)")
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pendingServices?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status)
        }
    }

    private suspend fun waitBond(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return true
        val done = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                    i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (d?.address != device.address) return
                when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDED -> done.complete(true)
                    BluetoothDevice.BOND_NONE -> done.complete(false)
                }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        return try {
            if (!device.createBond()) return device.bondState == BluetoothDevice.BOND_BONDED
            withTimeoutOrNull(45_000) { done.await() } ?: false
        } finally {
            runCatching { app.unregisterReceiver(receiver) }
        }
    }

    /**
     * Se connecte à l'appareil. Au premier appairage, l'appareil doit être en
     * mode « Appairage télécommande » (menu Bluetooth › Télécommande).
     */
    suspend fun connect(address: String, name: String, forcePairing: Boolean = false) {
        stopScan()
        disconnect()
        userDisconnect = false
        val a = adapter ?: run { _state.value = BleState.Failed("Bluetooth indisponible"); return }
        val device = a.getRemoteDevice(address)
        val firstTime = forcePairing || prefs.getString("paired", null) != address
        try {
            if (firstTime) {
                _state.value = BleState.Connecting("Association avec l'appareil…")
                // L'appareil accepte l'association « just works » ; on continue même si elle échoue
                waitBond(device)
            }
            _state.value = BleState.Connecting("Connexion…")
            val connected = CompletableDeferred<Boolean>().also { pendingConnect = it }
            val g = device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt = g
            if (withTimeoutOrNull(20_000) { connected.await() } != true) error("L'appareil ne répond pas")

            _state.value = BleState.Connecting("Découverte des services…")
            val services = CompletableDeferred<Boolean>().also { pendingServices = it }
            g.discoverServices()
            if (withTimeoutOrNull(15_000) { services.await() } != true) error("Services Bluetooth introuvables")
            val service = g.getService(SERVICE)
                ?: error("Cet appareil n'accepte pas la télécommande Bluetooth Canon (BR-E1)")
            val pairing = service.getCharacteristic(PAIRING)
            shutterChar = service.getCharacteristic(SHUTTER) ?: error("Caractéristique de déclenchement absente")

            if (firstTime && pairing != null) {
                _state.value = BleState.Connecting("Appairage de la télécommande…")
                val payload = byteArrayOf(0x03) + REMOTE_NAME.toByteArray()
                val st = write(pairing, payload)
                if (st != BluetoothGatt.GATT_SUCCESS) error("Appairage refusé (code $st). Mettez l'appareil en mode appairage télécommande.")
            }
            prefs.edit().putString("address", address).putString("name", name).putString("paired", address).apply()
            _state.value = BleState.Connected(name, address)
        } catch (e: Exception) {
            closeGatt()
            _state.value = BleState.Failed(e.message ?: e.toString())
        }
    }

    fun forget() {
        prefs.edit().clear().apply()
        disconnect()
        _state.value = BleState.Idle
    }

    fun disconnect() {
        userDisconnect = true
        closeGatt()
        if (_state.value !is BleState.Failed) _state.value = BleState.Idle
    }

    private fun closeGatt() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        shutterChar = null
        bulbOpen = false
    }

    // ------------------------------------------------------------------ écriture

    private suspend fun write(c: BluetoothGattCharacteristic, value: ByteArray): Int = opLock.withLock {
        val g = gatt ?: return -1
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val result = CompletableDeferred<Int>().also { pendingWrite = it }
        val started = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, value, type) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { c.writeType = type; c.value = value; g.writeCharacteristic(c) }
        }
        if (!started) return -2
        withTimeoutOrNull(5_000) { result.await() } ?: -3
    }

    private suspend fun buttons(code: Int) {
        val c = shutterChar ?: throw IllegalStateException("Télécommande Bluetooth non connectée")
        val st = write(c, byteArrayOf(code.toByte()))
        if (st != BluetoothGatt.GATT_SUCCESS) throw IllegalStateException("Commande Bluetooth refusée (code $st)")
    }

    /** Appui bref : enfoncer puis relâcher. */
    suspend fun click(code: Int, holdMs: Long = 200) {
        val mode = code and 0x0F
        buttons(code)
        delay(holdMs)
        buttons(mode)
    }

    suspend fun focusPress() = buttons(MODE_IMMEDIATE or BUTTON_FOCUS)
    suspend fun focusRelease() = buttons(MODE_IMMEDIATE)

    /** Déclenchement avec le retardateur 2 s de l'appareil. */
    suspend fun shootDelayed() = click(MODE_DELAY or BUTTON_FOCUS or BUTTON_RELEASE)

    /** Démarre / arrête l'enregistrement vidéo. */
    suspend fun movie() = click(MODE_MOVIE or BUTTON_RELEASE)

    /** Zoom motorisé (PowerShot) : maintenir. */
    suspend fun zoom(tele: Boolean, start: Boolean) =
        buttons(if (start) MODE_IMMEDIATE or (if (tele) BUTTON_TELE else BUTTON_WIDE) else MODE_IMMEDIATE)

    // ------------------------------------------------------------------ CameraControl

    override val supportsSettings: Boolean get() = false

    override suspend fun getSetting(key: String): CameraSetting? = null

    override suspend fun setSetting(key: String, value: String) =
        throw UnsupportedOperationException("Réglage impossible en Bluetooth : utilisez la connexion Wi-Fi")

    override suspend fun shoot(af: Boolean) =
        click(MODE_IMMEDIATE or BUTTON_RELEASE or (if (af) BUTTON_FOCUS else 0))

    /**
     * En pose B, le BR-E1 ouvre l'obturateur au premier appui et le ferme au
     * second : "full_press" ouvre, "release" ferme.
     */
    override suspend fun shutterManual(action: String, af: Boolean) {
        when (action) {
            "full_press" -> if (!bulbOpen) { click(MODE_IMMEDIATE or BUTTON_RELEASE); bulbOpen = true }
            "release" -> if (bulbOpen) { click(MODE_IMMEDIATE or BUTTON_RELEASE); bulbOpen = false }
            "half_press" -> focusPress()
        }
    }

    override suspend fun driveFocus(step: String) =
        throw UnsupportedOperationException("Mise au point motorisée impossible en Bluetooth : utilisez la connexion Wi-Fi")

    val bulbIsOpen: Boolean get() = bulbOpen
}
