package com.decmike.canonremote.ccapi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

object NetworkHelper {

    private fun wifiNetwork(context: Context): Network? {
        DirectWifi.network?.let { return it }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /**
     * Le Wi-Fi de l'appareil photo n'a pas d'accès Internet : Android a tendance
     * à envoyer le trafic sur les données mobiles. On force donc l'application
     * à utiliser le réseau Wi-Fi.
     */
    fun bindToWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = wifiNetwork(context) ?: return false
        return cm.bindProcessToNetwork(wifi)
    }

    fun unbind(context: Context) {
        context.getSystemService(ConnectivityManager::class.java).bindProcessToNetwork(null)
    }

    /** Adresse IPv4 du téléphone sur le Wi-Fi, ex. "192.168.1.5". */
    fun phoneWifiIp(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = wifiNetwork(context) ?: return null
        return cm.getLinkProperties(wifi)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    /**
     * Recherche les appareils exposant la CCAPI (port 8080) sur le sous-réseau /24.
     * [onProgress] reçoit le nombre d'adresses testées sur 254.
     */
    suspend fun scan(context: Context, port: Int = 8080, onProgress: (Int) -> Unit = {}): List<String> =
        withContext(Dispatchers.IO) {
            val ip = phoneWifiIp(context) ?: return@withContext emptyList()
            val prefix = ip.substringBeforeLast('.')
            val sem = Semaphore(48)
            var done = 0
            val lock = Any()
            coroutineScope {
                (1..254).map { i ->
                    async {
                        val host = "$prefix.$i"
                        val open = if (host == ip) false else sem.withPermit {
                            runCatching {
                                Socket().use { s -> s.connect(InetSocketAddress(host, port), 350); true }
                            }.getOrDefault(false)
                        }
                        synchronized(lock) { done++; onProgress(done) }
                        if (open && isCcapi(host, port)) host else null
                    }
                }.awaitAll().filterNotNull()
            }
        }

    private suspend fun isCcapi(host: String, port: Int): Boolean =
        runCatching { CcapiClient(host, port).discover().isNotEmpty() }.getOrDefault(false)
}
