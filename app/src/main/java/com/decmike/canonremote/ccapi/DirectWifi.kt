package com.decmike.canonremote.ccapi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address

sealed class DirectState {
    data object Idle : DirectState()
    data class Connecting(val step: String) : DirectState()
    data class Connected(val ssid: String?) : DirectState()
    data class Failed(val message: String) : DirectState()
}

/**
 * Connexion directe au point d'accès Wi-Fi de l'appareil photo, sans box.
 * Utilise WifiNetworkSpecifier (Android 10+) : Android affiche une fenêtre
 * de confirmation, puis le téléphone rejoint le réseau de l'appareil sans
 * perdre les données mobiles.
 */
object DirectWifi {

    /** Les SSID Canon contiennent « Canon » (ex. EOSR6-123_Canon0A). */
    const val CANON_PATTERN = ".*Canon.*"

    private val _state = MutableStateFlow<DirectState>(DirectState.Idle)
    val state: StateFlow<DirectState> = _state.asStateFlow()

    /** Réseau de l'appareil quand la connexion directe est active. */
    @Volatile var network: Network? = null
        private set

    private var callback: ConnectivityManager.NetworkCallback? = null

    val active: Boolean get() = network != null

    /**
     * Rejoint le Wi-Fi de l'appareil puis renvoie son adresse IP, ou null.
     * [ssid] vide : Android propose les réseaux dont le nom contient « Canon ».
     */
    suspend fun connect(context: Context, ssid: String?, password: String?): String? {
        release(context)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val pass = password?.trim().orEmpty()
        if (pass.isNotEmpty() && pass.length !in 8..63) {
            _state.value = DirectState.Failed("Le mot de passe Wi-Fi doit contenir 8 à 63 caractères")
            return null
        }
        val spec = WifiNetworkSpecifier.Builder().apply {
            if (!ssid.isNullOrBlank()) setSsid(ssid.trim())
            else setSsidPattern(PatternMatcher(CANON_PATTERN, PatternMatcher.PATTERN_SIMPLE_GLOB))
            if (pass.isNotEmpty()) setWpa2Passphrase(pass)
        }.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()

        val available = CompletableDeferred<Network?>()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                network = n
                cm.bindProcessToNetwork(n)
                available.complete(n)
            }

            override fun onUnavailable() {
                available.complete(null)
            }

            override fun onLost(n: Network) {
                if (network == n) {
                    network = null
                    cm.bindProcessToNetwork(null)
                    _state.value = DirectState.Failed("Wi-Fi de l'appareil perdu (appareil éteint ou hors de portée ?)")
                }
            }
        }
        callback = cb
        _state.value = DirectState.Connecting("Validez le réseau de l'appareil dans la fenêtre Android…")
        cm.requestNetwork(request, cb)

        val n = withTimeoutOrNull(90_000) { available.await() }
        if (n == null) {
            release(context)
            _state.value = DirectState.Failed(
                "Connexion au Wi-Fi de l'appareil annulée ou impossible.\n" +
                    "Vérifiez que l'appareil est en mode point d'accès et le mot de passe."
            )
            return null
        }

        _state.value = DirectState.Connecting("Recherche de l'appareil sur son réseau…")
        val host = findCamera(context, cm, n)
        if (host == null) {
            _state.value = DirectState.Failed("Connecté au Wi-Fi, mais l'appareil ne répond pas sur le port 8080. La CCAPI est-elle activée ?")
            return null
        }
        _state.value = DirectState.Connected(ssid?.takeIf { it.isNotBlank() })
        return host
    }

    /**
     * En mode point d'accès, l'appareil est la passerelle / le serveur DHCP
     * du réseau. À défaut, on balaie le sous-réseau.
     */
    private suspend fun findCamera(context: Context, cm: ConnectivityManager, n: Network): String? {
        val lp = cm.getLinkProperties(n)
        val candidates = buildList {
            lp?.routes?.mapNotNull { it.gateway }?.filterIsInstance<Inet4Address>()
                ?.filter { !it.isAnyLocalAddress }?.forEach { add(it.hostAddress) }
            if (Build.VERSION.SDK_INT >= 30) (lp?.dhcpServerAddress as? Inet4Address)?.let { add(it.hostAddress) }
        }.filterNotNull().distinct()
        for (h in candidates) {
            if (runCatching { CcapiClient(h).discover().isNotEmpty() }.getOrDefault(false)) return h
        }
        return NetworkHelper.scan(context).firstOrNull()
    }

    /** Libère le réseau de l'appareil (le téléphone revient à son Wi-Fi habituel). */
    fun release(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        if (network != null) cm.bindProcessToNetwork(null)
        network = null
        if (_state.value !is DirectState.Failed) _state.value = DirectState.Idle
    }
}
