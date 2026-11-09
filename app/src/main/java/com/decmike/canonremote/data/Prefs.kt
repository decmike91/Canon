package com.decmike.canonremote.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("canon_remote", Context.MODE_PRIVATE)

    var lastHost: String
        get() = sp.getString("host", "192.168.1.2") ?: "192.168.1.2"
        set(v) = sp.edit().putString("host", v).apply()

    var port: Int
        get() = sp.getInt("port", 8080)
        set(v) = sp.edit().putInt("port", v).apply()

    /** true = connexion directe au Wi-Fi de l'appareil, false = même réseau (box). */
    var directMode: Boolean
        get() = sp.getBoolean("direct_mode", true)
        set(v) = sp.edit().putBoolean("direct_mode", v).apply()

    var directSsid: String
        get() = sp.getString("direct_ssid", "") ?: ""
        set(v) = sp.edit().putString("direct_ssid", v).apply()

    var directPassword: String
        get() = sp.getString("direct_pass", "") ?: ""
        set(v) = sp.edit().putString("direct_pass", v).apply()

    var autoDownload: Boolean
        get() = sp.getBoolean("auto_download", false)
        set(v) = sp.edit().putBoolean("auto_download", v).apply()

    var autoDownloadRaw: Boolean
        get() = sp.getBoolean("auto_download_raw", false)
        set(v) = sp.edit().putBoolean("auto_download_raw", v).apply()

    var liveViewSize: String
        get() = sp.getString("lv_size", "small") ?: "small"
        set(v) = sp.edit().putString("lv_size", v).apply()

    /** Mire de composition (nom de CompositionGuide). */
    var guide: String
        get() = sp.getString("guide", "THIRDS") ?: "THIRDS"
        set(v) = sp.edit().putString("guide", v).apply()

    var guideAspect: String
        get() = sp.getString("guide_aspect", "AUTO") ?: "AUTO"
        set(v) = sp.edit().putString("guide_aspect", v).apply()

    var guideColor: Int
        get() = sp.getInt("guide_color", 0)
        set(v) = sp.edit().putInt("guide_color", v).apply()

    var guideFlipH: Boolean
        get() = sp.getBoolean("guide_flip_h", false)
        set(v) = sp.edit().putBoolean("guide_flip_h", v).apply()

    var guideFlipV: Boolean
        get() = sp.getBoolean("guide_flip_v", false)
        set(v) = sp.edit().putBoolean("guide_flip_v", v).apply()

    var histogram: Boolean
        get() = sp.getBoolean("histogram", true)
        set(v) = sp.edit().putBoolean("histogram", v).apply()

    var cameraScreenOff: Boolean
        get() = sp.getBoolean("camera_screen_off", false)
        set(v) = sp.edit().putBoolean("camera_screen_off", v).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(v) = sp.edit().putBoolean("keep_screen_on", v).apply()
}
