package com.decmike.canonremote.data

import android.content.Context
import com.decmike.canonremote.ccapi.CameraFile
import com.decmike.canonremote.ccapi.CameraSetting
import com.decmike.canonremote.ccapi.CameraStatus
import com.decmike.canonremote.ccapi.CcapiClient
import com.decmike.canonremote.ccapi.CcapiException
import com.decmike.canonremote.ccapi.DeviceInfo
import com.decmike.canonremote.ccapi.NetworkHelper
import com.decmike.canonremote.jobs.JobProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class ConnState {
    data object Disconnected : ConnState()
    data class Connecting(val host: String) : ConnState()
    data class Connected(val host: String, val info: DeviceInfo) : ConnState()
    data class Failed(val message: String) : ConnState()
}

/** État global partagé entre les écrans et le service de prise de vue. */
object CameraRepository {

    lateinit var prefs: Prefs
        private set
    private lateinit var appContext: Context

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _settings = MutableStateFlow<Map<String, CameraSetting>>(emptyMap())
    val settings: StateFlow<Map<String, CameraSetting>> = _settings.asStateFlow()

    private val _status = MutableStateFlow(CameraStatus())
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    /** Dernières images ajoutées sur la carte (nouvelle prise de vue). */
    private val _recent = MutableStateFlow<List<CameraFile>>(emptyList())
    val recent: StateFlow<List<CameraFile>> = _recent.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _job = MutableStateFlow(JobProgress())
    val jobProgress: StateFlow<JobProgress> = _job.asStateFlow()
    fun publishJob(p: JobProgress) { _job.value = p }

    var client: CcapiClient? = null
        private set

    private var eventJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = Prefs(appContext)
    }

    fun message(text: String) { _messages.tryEmit(text) }

    fun requireClient(): CcapiClient = client ?: throw CcapiException(-1, "Appareil non connecté")

    suspend fun connect(host: String, port: Int = 8080) {
        disconnect()
        _state.value = ConnState.Connecting(host)
        NetworkHelper.bindToWifi(appContext)
        try {
            val c = CcapiClient(host.trim(), port)
            c.discover()
            val info = runCatching { c.deviceInfo() }.getOrDefault(DeviceInfo(productName = "Appareil Canon"))
            client = c
            prefs.lastHost = host.trim()
            prefs.port = port
            _state.value = ConnState.Connected(host, info)
            refreshSettings()
            refreshStatus()
            startEvents(c)
        } catch (e: Exception) {
            client = null
            _state.value = ConnState.Failed(
                "Connexion impossible à $host:$port\n${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /** [releaseNetwork] : rend aussi le Wi-Fi direct de l'appareil (bouton Déconnecter). */
    fun disconnect(releaseNetwork: Boolean = false) {
        if (releaseNetwork) com.decmike.canonremote.ccapi.DirectWifi.release(appContext)
        eventJob?.cancel()
        eventJob = null
        val c = client
        client = null
        if (c != null) scope.launch { runCatching { c.liveView("off") } }
        _settings.value = emptyMap()
        _status.value = CameraStatus()
        _recent.value = emptyList()
        if (_state.value is ConnState.Connected) _state.value = ConnState.Disconnected
    }

    suspend fun refreshSettings() {
        val c = client ?: return
        runCatching { c.allSettings() }
            .onSuccess { list -> _settings.value = list.associateBy { it.key } }
            .onFailure { message("Lecture des réglages impossible : ${it.message}") }
    }

    suspend fun refreshStatus() {
        val c = client ?: return
        runCatching { c.status() }.onSuccess { _status.value = it }
    }

    suspend fun setSetting(key: String, value: String): Boolean {
        val c = client ?: return false
        return try {
            c.setSetting(key, value)
            c.getSetting(key)?.let { s -> _settings.update { it + (key to s) } }
            true
        } catch (e: Exception) {
            message("« $key » : ${e.message}")
            false
        }
    }

    suspend fun setSettingObject(key: String, value: Map<String, String>): Boolean {
        val c = client ?: return false
        return try {
            c.setSettingObject(key, value)
            c.getSetting(key)?.let { s -> _settings.update { it + (key to s) } }
            true
        } catch (e: Exception) {
            message("« $key » : ${e.message}")
            false
        }
    }

    // ------------------------------------------------------------ événements

    private fun startEvents(c: CcapiClient) {
        eventJob?.cancel()
        eventJob = scope.launch {
            var failures = 0
            var lastStatus = 0L
            while (isActive && client === c) {
                try {
                    handleEvent(c, c.pollEvents(long = true))
                    failures = 0
                } catch (e: CcapiException) {
                    // 503 = déjà une requête de polling en cours / appareil occupé
                    delay(if (e.busy) 500 else 2000)
                } catch (e: Exception) {
                    failures++
                    if (failures >= 4) {
                        if (client === c) {
                            client = null
                            _state.value = ConnState.Failed("Connexion perdue avec l'appareil")
                        }
                        break
                    }
                    delay(1500)
                }
                if (System.currentTimeMillis() - lastStatus > 30_000) {
                    refreshStatus(); lastStatus = System.currentTimeMillis()
                }
            }
        }
    }

    private suspend fun handleEvent(c: CcapiClient, ev: JSONObject) {
        // Réglages modifiés sur l'appareil (molettes…)
        val changed = mutableMapOf<String, CameraSetting>()
        for (k in ev.keys()) {
            val o = ev.optJSONObject(k) ?: continue
            if (o.has("value") && (_settings.value.containsKey(k))) {
                changed[k] = CcapiClient.parseSetting(k, o).let { s ->
                    // un événement peut ne contenir que la valeur : on garde les options connues
                    val old = _settings.value[k]
                    if (s.options == null && s.range == null && old != null) old.copy(value = s.value, subValues = s.subValues) else s
                }
            }
        }
        if (changed.isNotEmpty()) _settings.update { it + changed }

        // Nouvelles images
        val added = ev.optJSONArray("addedcontents")
        if (added != null && added.length() > 0) {
            val files = (0 until added.length()).map { CameraFile(c.toAbsolute(added.getString(it))) }
            _recent.update { (files.reversed() + it).take(50) }
            if (prefs.autoDownload) {
                for (f in files) {
                    if (f.isRaw && !prefs.autoDownloadRaw) continue
                    if (f.isVideo) continue
                    runCatching { Downloader.save(appContext, c, f) }
                        .onSuccess { message("${f.name} enregistrée sur le téléphone") }
                        .onFailure { message("Transfert de ${f.name} impossible : ${it.message}") }
                }
            }
        }
    }
}
