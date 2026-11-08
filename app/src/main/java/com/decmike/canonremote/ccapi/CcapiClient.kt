package com.decmike.canonremote.ccapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Client pour la Canon Camera Control API (CCAPI), l'API HTTP/JSON exposée
 * en Wi-Fi par les boîtiers Canon récents (EOS R, M50, 90D, PowerShot…).
 *
 * Toutes les routes sont découvertes via GET /ccapi afin d'utiliser la
 * version la plus récente proposée par le boîtier (ver100, ver110…).
 */
class CcapiClient(val host: String, val port: Int = 8080) : CameraControl {

    val baseUrl = "http://$host:$port"

    private val json = "application/json; charset=utf-8".toMediaType()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Client à long délai pour l'event polling et les téléchargements. */
    private val longHttp: OkHttpClient = http.newBuilder()
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** feature ("shooting/settings") -> chemin complet ("/ccapi/ver110/shooting/settings") */
    private val routes = mutableMapOf<String, String>()

    // ---------------------------------------------------------------- bas niveau

    suspend fun discover(): Set<String> = withContext(Dispatchers.IO) {
        val root = JSONObject(rawRequest("GET", "/ccapi", null, http))
        routes.clear()
        val versions = root.keys().asSequence().sorted().toList() // ver100 < ver110 < ver120
        for (ver in versions) {
            val arr = root.optJSONArray(ver) ?: continue
            for (i in 0 until arr.length()) {
                val full = arr.optJSONObject(i)?.optString("path") ?: continue
                val path = "/ccapi" + full.substringAfter("/ccapi", "")
                val feature = path.substringAfter("/ccapi/").substringAfter('/')
                routes[feature] = path // la version la plus récente écrase la précédente
            }
        }
        routes.keys
    }

    fun supports(feature: String) = routes.isEmpty() || routes.containsKey(feature)

    private fun route(feature: String): String = routes[feature] ?: "/ccapi/ver100/$feature"

    private fun rawRequest(method: String, path: String, body: JSONObject?, client: OkHttpClient): String {
        val url = if (path.startsWith("http")) path else baseUrl + path
        val reqBody = when {
            body != null -> body.toString().toRequestBody(json)
            method == "POST" || method == "PUT" -> "{}".toRequestBody(json)
            else -> null
        }
        val request = Request.Builder().url(url).method(method, reqBody).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: resp.message.ifBlank { "HTTP ${resp.code}" }
                throw CcapiException(resp.code, msg)
            }
            return text
        }
    }

    /** Requête avec nouvel essai automatique quand l'appareil répond « occupé » (503). */
    private suspend fun request(
        method: String,
        feature: String,
        body: JSONObject? = null,
        query: String = "",
        retries: Int = 20,
        client: OkHttpClient = http,
    ): String = withContext(Dispatchers.IO) {
        val path = (if (feature.startsWith("/") || feature.startsWith("http")) feature else route(feature)) + query
        var attempt = 0
        while (true) {
            try {
                return@withContext rawRequest(method, path, body, client)
            } catch (e: CcapiException) {
                if (e.busy && attempt < retries) {
                    attempt++
                    delay(250)
                } else throw e
            }
        }
        @Suppress("UNREACHABLE_CODE") ""
    }

    private suspend fun getJson(feature: String, query: String = "") =
        JSONObject(request("GET", feature, query = query).ifBlank { "{}" })

    // ---------------------------------------------------------------- appareil

    suspend fun deviceInfo(): DeviceInfo {
        val o = getJson("deviceinformation")
        return DeviceInfo(
            manufacturer = o.optString("manufacturer"),
            productName = o.optString("productname"),
            serialNumber = o.optString("serialnumber"),
            firmware = o.optString("firmwareversion"),
            mac = o.optString("macaddress"),
        )
    }

    suspend fun status(): CameraStatus {
        var battery: String? = null
        var level: Int? = null
        runCatching {
            val b = if (supports("devicestatus/batterylist")) {
                getJson("devicestatus/batterylist").optJSONArray("batterylist")?.optJSONObject(0)
            } else getJson("devicestatus/battery")
            if (b != null) {
                val l = b.optString("level")
                battery = l
                level = l.toIntOrNull() ?: when (l) {
                    "full" -> 100; "high" -> 75; "half" -> 50; "quarter" -> 25; "low" -> 10
                    else -> null
                }
            }
        }
        val lens = runCatching { getJson("devicestatus/lens").optString("name").takeIf { it.isNotBlank() } }.getOrNull()
        val temp = runCatching { getJson("devicestatus/temperature").optString("status") }.getOrNull()
        val storages = runCatching {
            val arr = getJson("devicestatus/storage").optJSONArray("storagelist") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                StorageInfo(
                    name = s.optString("name"),
                    maxSize = s.optLong("maxsize"),
                    freeSize = s.optLong("spacesize"),
                    contents = s.optInt("contentsnumber"),
                )
            }
        }.getOrDefault(emptyList())
        return CameraStatus(battery, level, lens, temp, storages)
    }

    /** Synchronise l'horloge de l'appareil sur celle du téléphone. */
    suspend fun syncDateTime(now: Date = Date()) {
        val tz = TimeZone.getDefault()
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply { timeZone = tz }
        val dst = tz.inDaylightTime(now)
        // La CCAPI attend l'heure standard + indicateur DST
        val standard = Date(now.time - if (dst) tz.dstSavings else 0)
        request("PUT", "functions/datetime", JSONObject().put("datetime", fmt.format(standard)).put("dst", dst))
    }

    // ---------------------------------------------------------------- réglages

    suspend fun allSettings(): List<CameraSetting> {
        val o = getJson("shooting/settings")
        return o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { parseSetting(k, it) } }.toList()
    }

    override suspend fun getSetting(key: String): CameraSetting? =
        runCatching { parseSetting(key, getJson("shooting/settings/$key")) }.getOrNull()

    override suspend fun setSetting(key: String, value: String) {
        request("PUT", "shooting/settings/$key", JSONObject().put("value", value.toIntOrNull()?.takeIf { key == "colortemperature" } ?: value))
    }

    suspend fun setSettingObject(key: String, value: Map<String, String>) {
        val v = JSONObject()
        value.forEach { (k, s) -> v.put(k, s) }
        request("PUT", "shooting/settings/$key", JSONObject().put("value", v))
    }

    // ---------------------------------------------------------------- prise de vue

    override suspend fun shoot(af: Boolean) {
        request("POST", "shooting/control/shutterbutton", JSONObject().put("af", af))
    }

    /** action : "half_press", "full_press" ou "release". */
    override suspend fun shutterManual(action: String, af: Boolean) {
        request("POST", "shooting/control/shutterbutton/manual", JSONObject().put("action", action).put("af", af))
    }

    /** step : near1..near3 / far1..far3 (1 = petit pas, 3 = grand pas). */
    override suspend fun driveFocus(step: String) {
        request("POST", "shooting/control/drivefocus", JSONObject().put("value", step))
    }

    suspend fun movieMode(on: Boolean) {
        request("POST", "shooting/control/moviemode", JSONObject().put("action", if (on) "on" else "off"))
    }

    suspend fun record(start: Boolean) {
        request("POST", "shooting/control/recbutton", JSONObject().put("action", if (start) "start" else "stop"))
    }

    // ---------------------------------------------------------------- visée directe

    /** size : "small" / "medium" / "off" ; display : "on" / "off" / "keep". */
    suspend fun liveView(size: String, display: String = "keep") {
        request("POST", "shooting/liveview", JSONObject().put("liveviewsize", size).put("cameradisplay", display))
    }

    suspend fun liveViewFrame(): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + route("shooting/liveview/flip")).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw CcapiException(resp.code, resp.message.ifBlank { "HTTP ${resp.code}" })
            resp.body?.bytes() ?: ByteArray(0)
        }
    }

    /**
     * Informations de visée (cadres AF, zone visible…). Selon le boîtier la
     * réponse est du JSON pur ou un flux binaire contenant un bloc JSON : on
     * extrait le premier objet JSON trouvé.
     */
    suspend fun liveViewInfo(): JSONObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + route("shooting/liveview/flipdetail") + "?kind=info").get().build()
        val bytes = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.bytes() else null } ?: return@withContext null
        val start = bytes.indexOfFirst { it == '{'.code.toByte() }
        val end = bytes.indexOfLast { it == '}'.code.toByte() }
        if (start < 0 || end <= start) return@withContext null
        runCatching { JSONObject(String(bytes, start, end - start + 1, Charsets.UTF_8)) }.getOrNull()
    }

    /** Taille du repère de coordonnées AF (largeur, hauteur) déduite des infos de visée. */
    suspend fun liveViewCoordinateSpace(): Pair<Int, Int>? {
        val info = liveViewInfo() ?: return null
        var best: Pair<Int, Int>? = null
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    val w = o.optInt("positionwidth", 0)
                    val h = o.optInt("positionheight", 0)
                    if (w > 0 && h > 0 && (best == null || w.toLong() * h > best!!.first.toLong() * best!!.second)) best = w to h
                    o.keys().forEach { walk(o.opt(it)) }
                }
                is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        walk(info)
        return best
    }

    suspend fun setAfPosition(x: Int, y: Int) {
        request("PUT", "shooting/liveview/afframeposition", JSONObject().put("positionx", x).put("positiony", y))
    }

    // ---------------------------------------------------------------- événements

    /** Attente longue des changements (nouvelles images, réglages modifiés…). */
    suspend fun pollEvents(long: Boolean = true): JSONObject =
        JSONObject(request("GET", "event/polling", query = if (long) "?timeout=long" else "?timeout=immediately", client = longHttp).ifBlank { "{}" })

    // ---------------------------------------------------------------- contenus

    private fun pathList(o: JSONObject): List<String> {
        val arr = o.optJSONArray("path") ?: o.optJSONArray("url") ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }.map { toAbsolute(it) }
    }

    fun toAbsolute(p: String) = if (p.startsWith("http")) p else baseUrl + p

    suspend fun storages(): List<String> = pathList(getJson("contents"))

    suspend fun folders(storageUrl: String): List<String> = pathList(JSONObject(request("GET", storageUrl)))

    suspend fun files(folderUrl: String): List<CameraFile> {
        val pages = runCatching {
            JSONObject(request("GET", folderUrl, query = "?type=all&kind=number")).optInt("pagenumber", 1)
        }.getOrNull()
        if (pages == null) return pathList(JSONObject(request("GET", folderUrl))).map { CameraFile(it) }
        val out = mutableListOf<CameraFile>()
        for (p in 1..maxOf(pages, 1)) {
            out += pathList(JSONObject(request("GET", folderUrl, query = "?type=all&kind=list&page=$p"))).map { CameraFile(it) }
        }
        return out
    }

    suspend fun fileInfo(file: CameraFile): JSONObject = JSONObject(request("GET", file.url, query = "?kind=info"))

    suspend fun deleteFile(file: CameraFile) {
        request("DELETE", file.url)
    }

    /** Télécharge l'original dans [out]. [progress] reçoit (octets lus, total ou -1). */
    suspend fun download(file: CameraFile, out: OutputStream, progress: (Long, Long) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            var attempt = 0
            while (true) {
                val req = Request.Builder().url(file.url).get().build()
                val resp = longHttp.newCall(req).execute()
                if (resp.code == 503 && attempt++ < 20) { resp.close(); delay(300); continue }
                resp.use {
                    if (!it.isSuccessful) throw CcapiException(it.code, it.message)
                    val body = it.body ?: throw CcapiException(-1, "Réponse vide")
                    val total = body.contentLength()
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            progress(read, total)
                        }
                    }
                }
                break
            }
        }

    // ---------------------------------------------------------------- parsing

    companion object {
        fun parseSetting(key: String, o: JSONObject): CameraSetting {
            val rawValue = o.opt("value")
            val ability = o.opt("ability")
            var options: List<String>? = null
            var range: SettingRange? = null
            var subOptions: Map<String, List<String>>? = null
            var subValues: Map<String, String>? = null

            when (ability) {
                is JSONArray -> options = (0 until ability.length()).map { ability.get(it).toString() }
                is JSONObject -> {
                    if (ability.has("min") && ability.has("max")) {
                        range = SettingRange(ability.optInt("min"), ability.optInt("max"), ability.optInt("step", 1).coerceAtLeast(1))
                    } else {
                        val m = mutableMapOf<String, List<String>>()
                        for (k in ability.keys()) {
                            val a = ability.optJSONArray(k) ?: continue
                            m[k] = (0 until a.length()).map { a.get(it).toString() }
                        }
                        if (m.isNotEmpty()) subOptions = m
                    }
                }
            }
            val value: String? = when (rawValue) {
                null, JSONObject.NULL -> null
                is JSONObject -> {
                    subValues = rawValue.keys().asSequence().associateWith { rawValue.get(it).toString() }
                    rawValue.toString()
                }
                else -> rawValue.toString()
            }
            return CameraSetting(key, value, options, range, subOptions, subValues)
        }
    }
}
